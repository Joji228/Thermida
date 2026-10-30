package app.thermida.ui.settings

import app.thermida.voice.VoiceLanguage
import kotlinx.coroutines.flow.map
import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.thermida.AppContainer
import app.thermida.ai.ApiBalance
import app.thermida.ai.ApiBalances
import app.thermida.data.AiSettings
import app.thermida.data.Backup
import app.thermida.data.Provider
import app.thermida.data.StorageInfo
import app.thermida.data.formatBytes
import app.thermida.data.ThemeMode
import app.thermida.data.TokenBudget
import app.thermida.data.TokenUsage
import java.time.ZoneId
import app.thermida.data.ThinkingLevel
import app.thermida.domain.AdaptiveEstimate
import app.thermida.ui.components.HapticsSetting
import app.thermida.update.AppUpdater
import app.thermida.update.InstallStart
import app.thermida.update.UpdateState
import app.thermida.domain.Targets
import app.thermida.domain.UserProfile
import app.thermida.ui.theme.AccentColor
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json

data class SettingsState(
    val profile: UserProfile? = null,
    val targets: Targets? = null,
    val estimate: AdaptiveEstimate? = null,
    val ai: AiSettings = AiSettings(),
    val theme: ThemeMode = ThemeMode.DARK,
    val accent: AccentColor = AccentColor.Default,
    val scaleEnabled: Boolean = false,
)

/** The credit-left card at the top of the AI engine screen. */
sealed interface BalanceState {
    /** The engine used for scans can't report its balance (or has no key yet). */
    data object Unavailable : BalanceState
    data object Loading : BalanceState
    data class Ok(val balance: ApiBalance, val at: Long) : BalanceState
    data class Failed(val message: String) : BalanceState
}

/** A short reason for the balance card; the full API error belongs in the key check, not here. */
internal fun balanceProblem(service: String, message: String?): String = when {
    message == null -> "Couldn't reach $service. Tap refresh to try again."
    "401" in message || "403" in message -> "$service rejected this key, so the balance can't be shown."
    "402" in message -> "$service says the account has no credit left."
    else -> message.substringBefore(" (request_id").take(140)
}

sealed interface KeyCheck {
    data object Idle : KeyCheck
    data object Checking : KeyCheck
    /** [automatic] is what "Automatic" resolves to with this key. */
    data class Ok(val models: List<String>, val automatic: String?) : KeyCheck
    data class Failed(val message: String) : KeyCheck
}

class SettingsViewModel(private val app: AppContainer) : ViewModel() {
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true; prettyPrint = true }

    val state: StateFlow<SettingsState> = combine(
        app.plans.plan,
        app.settings.ai,
        app.settings.theme,
        app.settings.accent,
        app.settings.scaleEnabled,
    ) { plan, ai, theme, accent, scale ->
        SettingsState(
            profile = plan.profile, targets = plan.targets, estimate = plan.estimate, ai = ai, theme = theme, accent = accent,
            scaleEnabled = scale,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), SettingsState())

    /** Size and count of stored meal photos, e.g. "3.2 MB" and 41. */
    val photoStorage = MutableStateFlow<Pair<String, Int>?>(null)

    /** Everything the app takes on the phone. */
    val storage = MutableStateFlow<StorageInfo.Usage?>(null)

    init {
        refreshStorage()
    }

    private fun refreshStorage() {
        viewModelScope.launch(Dispatchers.IO) {
            val files = app.photos.mealPhotos()
            photoStorage.value = formatBytes(files.sumOf { it.length() }) to files.size
            storage.value = app.storage.usage()
        }
    }

    /** Deletes the temporary files the app can make again. Meals, photos, settings and keys stay. */
    fun clearCache() {
        viewModelScope.launch(Dispatchers.IO) {
            // Photos waiting to be analyzed or saved, and an update not installed yet, aren't "cache".
            val keep = setOfNotNull(app.photos.capturesFolder, app.updater.downloadFolder.takeIf { app.updater.holdsDownload })
            val freed = app.storage.clearCache(keep)
            refreshStorage()
            message.value = if (freed > 0) "Cache cleared. Freed ${formatBytes(freed)}." else "The cache was already empty."
        }
    }

    /** Removes every meal photo but keeps all meals and their numbers. */
    fun deleteAllPhotos() {
        viewModelScope.launch(Dispatchers.IO) {
            app.repository.deleteAllPhotos(app.photos.mealPhotos())
            refreshStorage()
            message.value = "Meal photos deleted. Your meals are still logged."
        }
    }

    val balance = MutableStateFlow<BalanceState>(BalanceState.Unavailable)

    /** Asks the engine used for scans how much credit is left (DeepSeek and OpenRouter can say). */
    fun refreshBalance() = viewModelScope.launch {
        val ai = app.settings.ai.first()
        val provider = ai.primary
        if (!ApiBalances.supported(provider, ai) || !ai.isReady(provider)) {
            balance.value = BalanceState.Unavailable
            return@launch
        }
        balance.value = BalanceState.Loading
        balance.value = try {
            app.ai.balance(provider)?.let { BalanceState.Ok(it, System.currentTimeMillis()) } ?: BalanceState.Unavailable
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            BalanceState.Failed(balanceProblem(provider.title, e.message))
        }
    }

    val keyCheck = MutableStateFlow<KeyCheck>(KeyCheck.Idle)
    val message = MutableStateFlow<String?>(null)

    /** Tokens used today and since Sunday 9:00, with the limits chosen. */
    val usage: Flow<TokenUsage> = combine(app.settings.aiUsageLog, app.settings.aiDailyLimit, app.settings.aiWeeklyLimit) { log, daily, weekly ->
        TokenBudget.summary(TokenBudget.parse(log), daily, weekly, System.currentTimeMillis(), ZoneId.systemDefault())
    }

    fun setDailyLimit(tokens: Long) = viewModelScope.launch { app.settings.setDailyLimit(tokens) }

    fun setWeeklyLimit(tokens: Long) = viewModelScope.launch { app.settings.setWeeklyLimit(tokens) }

    /** Greek foods and brands tuning, on by default. */
    val greekTuning: Flow<Boolean> = app.settings.regionGreece

    fun setGreekTuning(on: Boolean) = viewModelScope.launch { app.settings.setRegionGreece(on) }

    /** The Settings → Updates row: checking, offering a version, downloading, or ready to install. */
    val updates: StateFlow<UpdateState> = app.updater.state

    val updater: AppUpdater = app.updater

    /** The update popup, opened after a check from Settings finds a newer version. */
    val updateDialog = MutableStateFlow(false)

    fun closeUpdateDialog() {
        updateDialog.value = false
    }

    fun checkForUpdates() {
        viewModelScope.launch {
            app.updater.check()
            if (app.updater.state.value is UpdateState.Available) updateDialog.value = true
        }
    }

    /** Downloads the offered version, then opens Android's install prompt on its own. */
    fun downloadUpdate(context: Context) {
        viewModelScope.launch {
            // The download itself runs in the updater, so leaving Settings doesn't cut it off.
            app.updater.startDownload().join()
            if (app.updater.state.value is UpdateState.Ready) installUpdate(context)
        }
    }

    /** Opens Android's install prompt for the downloaded version; allowing installs first when Android asks. */
    fun installUpdate(context: Context) {
        val ready = app.updater.state.value as? UpdateState.Ready ?: return
        when (val result = app.updater.install(ready.file)) {
            InstallStart.Started -> Unit
            InstallStart.NeedsPermission -> context.startActivity(app.updater.permissionIntent())
            is InstallStart.Refused -> message.value = result.message
        }
    }

    fun setTheme(mode: ThemeMode) = viewModelScope.launch { app.settings.setTheme(mode) }

    fun setAccent(accent: AccentColor) = viewModelScope.launch { app.settings.setAccent(accent) }

    fun setScaleEnabled(enabled: Boolean) = viewModelScope.launch { app.settings.setScaleEnabled(enabled) }

    val useFoodFacts = app.settings.useFoodFacts

    fun setUseFoodFacts(enabled: Boolean) = viewModelScope.launch { app.settings.setUseFoodFacts(enabled) }

    val coachEnabled = app.settings.coachEnabled

    val coachSpeak = app.settings.coachSpeak
    val voiceLanguage = app.settings.voiceLanguage.map { VoiceLanguage.pick(it) }
    val coachHandsFree = app.settings.coachHandsFree
    val coachSpeechRate = app.settings.coachSpeechRate
    val coachPauseMs = app.settings.coachPauseMs
    val coachDetailed = app.settings.coachDetailed
    val coachFollowUps = app.settings.coachFollowUps

    fun setCoachSpeak(on: Boolean) = viewModelScope.launch { app.settings.setCoachSpeak(on) }
    fun setVoiceLanguage(language: VoiceLanguage) = viewModelScope.launch { app.settings.setVoiceLanguage(language.name) }
    fun setCoachHandsFree(on: Boolean) = viewModelScope.launch { app.settings.setCoachHandsFree(on) }
    fun setCoachSpeechRate(rate: Float) = viewModelScope.launch { app.settings.setCoachSpeechRate(rate) }
    fun setCoachPauseMs(ms: Int) = viewModelScope.launch { app.settings.setCoachPauseMs(ms) }
    fun setCoachDetailed(on: Boolean) = viewModelScope.launch { app.settings.setCoachDetailed(on) }
    fun setCoachFollowUps(on: Boolean) = viewModelScope.launch { app.settings.setCoachFollowUps(on) }
    fun resetCoachTutorial() = viewModelScope.launch { app.settings.resetCoachTutorial() }

    /** Deletes the coach conversation; an open chat screen empties itself too. */
    fun clearCoachChat() = viewModelScope.launch { app.coach.clear() }

    val hapticsEnabled = app.settings.hapticsEnabled

    fun setHapticsEnabled(enabled: Boolean) = viewModelScope.launch {
        // Applied at once so the click that confirms "on" (and the silence after "off") can't lag
        // behind the saved setting.
        HapticsSetting.enabled = enabled
        app.settings.setHapticsEnabled(enabled)
    }

    /** Turning the coach off also deletes its conversation. */
    fun setCoachEnabled(enabled: Boolean) = viewModelScope.launch {
        app.settings.setCoachEnabled(enabled)
        if (!enabled) app.coach.clear()
    }

    fun setPrimary(provider: Provider) = viewModelScope.launch { app.settings.setPrimary(provider) }

    fun setDeepseekThinking(level: ThinkingLevel) = viewModelScope.launch { app.settings.setDeepseekThinking(level) }

    /** Adds [provider] to the engine list (up to four). Its key is saved separately by [saveKey]. */
    fun addEngine(provider: Provider) = viewModelScope.launch {
        val ai = state.value.ai
        if (provider in ai.activeEngines || ai.activeEngines.size >= AiSettings.MAX_ENGINES) return@launch
        app.settings.setEngines(ai.activeEngines + provider)
    }

    /** Picking another service in an engine's dropdown. The old service's saved key stays stored. */
    fun changeEngine(old: Provider, new: Provider) = viewModelScope.launch {
        val ai = state.value.ai
        if (new == old || new in ai.activeEngines) return@launch
        // Primary first, so the list never briefly shows the old engine again.
        if (ai.primary == old) app.settings.setPrimary(new)
        app.settings.setEngines(ai.activeEngines.map { if (it == old) new else it })
    }

    /** Takes an engine off the list and deletes its saved key from this phone. */
    fun removeEngine(provider: Provider) = viewModelScope.launch {
        val ai = state.value.ai
        val remaining = ai.activeEngines - provider
        if (remaining.isEmpty()) return@launch
        if (ai.primary == provider) app.settings.setPrimary(remaining.first())
        app.settings.setEngines(remaining)
        app.settings.setKey(provider, "")
    }
    fun setModel(provider: Provider, model: String) = viewModelScope.launch { app.settings.setModel(provider, model) }

    fun setFallback(enabled: Boolean) = viewModelScope.launch { app.settings.setFallback(enabled) }

    fun setFallbackProvider(provider: Provider) = viewModelScope.launch { app.settings.setFallbackProvider(provider) }

    /** Erases meals, water, weights, photos and the profile; optionally the API keys too. */
    fun resetAll(keepKeys: Boolean, onDone: () -> Unit) {
        viewModelScope.launch {
            app.repository.restore(Backup())
            app.photos.deleteAll()
            app.coach.clear()
            app.settings.clear(keepKeys)
            app.draft = null
            onDone()
        }
    }

    fun updateProfile(transform: (UserProfile) -> UserProfile) = viewModelScope.launch {
        app.settings.profile.first()?.let { app.settings.saveProfile(transform(it)) }
    }

    /**
     * Saves the key (and, for a custom service, its address), then checks them by listing the
     * models they can use.
     */
    fun saveKey(provider: Provider, key: String, baseUrl: String = "") {
        viewModelScope.launch {
            if (provider.needsBaseUrl) app.settings.setCustomBaseUrl(baseUrl)
            app.settings.setKey(provider, key)
            if (key.isBlank()) {
                keyCheck.value = KeyCheck.Idle
                return@launch
            }
            check(provider, key.trim(), if (provider.needsBaseUrl) baseUrl.trim().trimEnd('/') else provider.baseUrl.orEmpty())
        }
    }

    fun loadModels(provider: Provider) {
        viewModelScope.launch {
            val ai = app.settings.ai.first()
            when {
                ai.keyFor(provider).isBlank() -> keyCheck.value = KeyCheck.Failed("Add a key first")
                !ai.isReady(provider) -> keyCheck.value = KeyCheck.Failed("Add the service address first")
                else -> check(provider, ai.keyFor(provider), ai.baseUrlFor(provider))
            }
        }
    }

    private suspend fun check(provider: Provider, key: String, baseUrl: String) {
        keyCheck.value = KeyCheck.Checking
        keyCheck.value = try {
            val models = app.ai.listModels(provider, key, baseUrl)
            KeyCheck.Ok(models, app.ai.pickDefault(provider, models))
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            KeyCheck.Failed(e.message ?: "Couldn't reach ${provider.title}")
        }
    }

    fun resetKeyCheck() {
        keyCheck.value = KeyCheck.Idle
    }

    fun export(context: Context, uri: Uri) {
        viewModelScope.launch {
            runCatching {
                val backup = app.repository.export(app.settings.profile.first(), app.settings.backup())
                withContext(Dispatchers.IO) {
                    context.contentResolver.openOutputStream(uri)?.use {
                        it.write(json.encodeToString(Backup.serializer(), backup).toByteArray())
                    } ?: error("Couldn't open the file")
                }
                backup
            }.onSuccess { message.value = "Exported ${it.meals.size} meals" }
                .onFailure { message.value = "Export failed: ${it.message}" }
        }
    }

    /** A backup file that was read and checked, waiting for the user to confirm it replaces their data. */
    val pendingImport = MutableStateFlow<PendingImport?>(null)

    /** Reads and checks the file. Nothing on the phone changes until [confirmImport]. */
    fun import(context: Context, uri: Uri) {
        viewModelScope.launch {
            runCatching {
                val text = withContext(Dispatchers.IO) {
                    context.contentResolver.openInputStream(uri)?.use { it.readBytes().decodeToString() } ?: error("Couldn't open the file")
                }
                val backup = runCatching { json.decodeFromString(Backup.serializer(), text) }
                    .getOrElse { error("That file isn't a Thermida backup. Nothing was changed.") }
                backup.problem()?.let { error(it) }
                PendingImport(backup, app.repository.mealCount())
            }.onSuccess { pendingImport.value = it }
                .onFailure { message.value = "Import failed: ${it.message}" }
        }
    }

    fun cancelImport() {
        pendingImport.value = null
    }

    /** Replaces everything on the phone with the checked backup. */
    fun confirmImport() {
        val backup = pendingImport.value?.backup ?: return
        pendingImport.value = null
        viewModelScope.launch {
            runCatching {
                app.repository.restore(backup)
                backup.profile?.let { app.settings.saveProfile(it) }
                backup.settings?.let { app.settings.restore(it) }
            }.onSuccess { message.value = "Imported ${backup.meals.size} meals" }
                .onFailure { message.value = "Import failed: ${it.message}. Nothing was changed." }
        }
    }
}

/** [mealsNow] is how many meals the phone has before the import replaces them. */
data class PendingImport(val backup: Backup, val mealsNow: Int)
