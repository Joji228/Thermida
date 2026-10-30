package app.thermida

import android.app.Application
import android.content.Context
import androidx.room.Room
import app.thermida.ai.AiService
import app.thermida.ai.AnalysisResult
import app.thermida.ble.MiScaleScanner
import app.thermida.data.AppRegion
import app.thermida.data.CoachStore
import app.thermida.data.DayClock
import app.thermida.data.DraftStore
import app.thermida.data.ThermidaRepository
import app.thermida.data.PhotoStore
import app.thermida.data.PlanRepository
import app.thermida.data.SettingsRepository
import app.thermida.data.StorageInfo
import app.thermida.update.AppUpdater
import app.thermida.ui.components.HapticsSetting
import app.thermida.data.db.ThermidaDatabase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import app.thermida.data.OpenFoodFacts
import kotlinx.coroutines.flow.MutableStateFlow
import okhttp3.OkHttpClient
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.merge
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.io.File
import java.time.LocalDate

class ThermidaApp : Application() {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
    }
}

/** A scan waiting to be reviewed on the results screen; [date] is the real current day it was taken on. */
data class MealDraft(val photos: List<File>, val note: String?, val result: AnalysisResult?, val date: LocalDate)

/** Hand-wired dependencies; small enough that a DI framework isn't worth the build time. */
class AppContainer(context: Context) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)


    // The file keeps an early name so updates find the existing data.
    private val database = Room.databaseBuilder(context, ThermidaDatabase::class.java, "morsel.db")
        .addMigrations(*ThermidaDatabase.MIGRATIONS)
        .build()
    val clock = DayClock(scope)
    val settings = SettingsRepository(context)
    val repository = ThermidaRepository(database, File(context.filesDir, "meals"))
    val plans = PlanRepository(settings, repository, clock, scope)
    val photos = PhotoStore(context)
    val storage = StorageInfo(context)
    /** One HTTP client for everything, so AI and Open Food Facts share connections and threads. */
    private val http = OkHttpClient()
    val ai = AiService(settings, http)
    val scale = MiScaleScanner(context)
    /** Looks for a newer Thermida on GitHub on each launch; the user decides when to install it. */
    val updater = AppUpdater(context, http)

    /** The free Open Food Facts product database; only used when turned on in Settings. */
    val foodFacts = OpenFoodFacts(runCatching { context.packageManager.getPackageInfo(context.packageName, 0).versionName }.getOrNull() ?: "1", http)
    /** The AI coach's current conversation (beta, off by default). */
    val coach = CoachStore(File(context.filesDir, "coach"))
    private val drafts = DraftStore(File(context.filesDir, "draft"))
    private var draftLoaded = false
    private var currentDraft: MealDraft? = null
    private val draftWrites = Mutex()

    /**
     * The scan waiting on the results screen. Also kept on disk, so it's still there if Android
     * closes the app in the background while it waits; the file is tiny, so it's read on first use.
     */
    var draft: MealDraft?
        @Synchronized get() {
            if (!draftLoaded) {
                currentDraft = drafts.load()
                draftLoaded = true
            }
            return currentDraft
        }
        @Synchronized set(value) {
            currentDraft = value
            draftLoaded = true
            // Always writes whatever is current when it runs, so quick changes can't land out of order.
            scope.launch { draftWrites.withLock { runCatching { drafts.save(draft) } } }
        }

    /** The day the dashboard is showing. Water taps go to it; scanned meals always go to today. */
    val viewedDay = MutableStateFlow(clock.today.value)

    init {
        // Viewing today at midnight? Follow along to the new today.
        scope.launch {
            var previous = clock.today.value
            clock.today.collect { now ->
                if (now != previous) {
                    if (viewedDay.value == previous) viewedDay.value = now
                    previous = now
                }
            }
        }
    }

    init {
        // Settings → Haptic feedback decides whether anything in the app vibrates.
        scope.launch { settings.hapticsEnabled.collect { HapticsSetting.enabled = it } }
        // The Greek tuning is read by the AI prompts and search from one flag; keep it in step with Settings.
        scope.launch { settings.regionGreece.collect { AppRegion.greekTuning = it } }
    }

    init {
        // Every launch looks for a newer version and offers it on the main screen.
        scope.launch { updater.check(quiet = true) }
    }

    init {
        // Housekeeping on launch: fix old model settings, drop stale captures, enforce the photo window.
        scope.launch {
            // A scan still waiting on the results screen keeps its photos.
            photos.clearCaptures(keep = draft?.photos.orEmpty())
            runCatching { repository.prunePhotos(photos.mealPhotos()) }
        }
    }
}
