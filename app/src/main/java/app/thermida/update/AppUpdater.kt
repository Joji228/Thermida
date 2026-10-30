package app.thermida.update

import android.content.Context
import android.content.Intent
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.Settings
import androidx.core.content.FileProvider
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import java.io.File
import java.io.IOException
import java.security.MessageDigest
import java.util.concurrent.TimeUnit

/** Where new versions come from: the latest non-draft, non-prerelease GitHub release of this repo. */
const val UPDATE_REPO = "Joji228/Thermida"

/** The small file every release publishes next to its APK; Android compares its versionCode. */
internal const val UPDATE_MANIFEST = "update.json"

/**
 * An installable version: its APK, the versionCode Android compares, the APK's SHA-256, and the
 * release notes ("what's new") written on its GitHub release.
 */
data class Release(
    val version: String,
    val versionCode: Long,
    val apkName: String,
    val apkUrl: String,
    val sha256: String,
    val notes: String = "",
)

sealed interface UpdateState {
    data object Idle : UpdateState
    data object Checking : UpdateState
    data object UpToDate : UpdateState
    /** The latest release has no usable update.json and APK yet. */
    data object NoRelease : UpdateState
    data class Available(val release: Release) : UpdateState
    /** [fraction] runs from 0 to 1 while the APK arrives. */
    data class Downloading(val release: Release, val fraction: Float) : UpdateState
    /** Downloaded and checked; waiting for Android's install prompt. */
    data class Ready(val release: Release, val file: File) : UpdateState
    data class Failed(val message: String) : UpdateState
}

sealed interface InstallStart {
    /** Android's install prompt is on screen. */
    data object Started : InstallStart
    /** The user must allow installs from Thermida first; open [AppUpdater.permissionIntent]. */
    data object NeedsPermission : InstallStart
    data class Refused(val message: String) : InstallStart
}

@Serializable
internal data class GitHubRelease(
    @SerialName("tag_name") val tagName: String = "",
    val body: String? = null,
    val assets: List<GitHubAsset> = emptyList(),
)

@Serializable
internal data class GitHubAsset(
    val name: String = "",
    @SerialName("browser_download_url") val downloadUrl: String = "",
)

/** What update.json says about one release. */
@Serializable
internal data class UpdateManifest(
    val versionName: String = "",
    val versionCode: Long = 0,
    val apk: String = "",
    val sha256: String = "",
)

/** Reads a release and its update.json; separate so it can be tested without the network. */
object ReleaseParser {
    private val json = Json { ignoreUnknownKeys = true }
    private val trustedHosts = setOf("github.com", "objects.githubusercontent.com", "release-assets.githubusercontent.com")
    private val sha256 = Regex("[0-9a-f]{64}")

    /** Where to download the release's update.json from, or null when the release doesn't have one. */
    fun manifestUrl(releaseBody: String): String? =
        json.decodeFromString<GitHubRelease>(releaseBody).assets
            .firstOrNull { it.name == UPDATE_MANIFEST }
            ?.downloadUrl
            ?.takeIf(::trusted)

    /**
     * The release as an installable version, or null when update.json is incomplete or its APK
     * isn't attached (or isn't named Thermida-<versionName>.apk as the manifest says).
     */
    fun parse(releaseBody: String, manifestBody: String): Release? {
        val manifest = json.decodeFromString<UpdateManifest>(manifestBody)
        val hash = manifest.sha256.trim().lowercase()
        if (manifest.versionName.isBlank() || manifest.versionCode <= 0 || !sha256.matches(hash)) return null
        if (manifest.apk != "Thermida-${manifest.versionName}.apk") return null
        val release = json.decodeFromString<GitHubRelease>(releaseBody)
        val apk = release.assets.firstOrNull { it.name == manifest.apk } ?: return null
        if (!trusted(apk.downloadUrl)) return null
        return Release(
            version = manifest.versionName,
            versionCode = manifest.versionCode,
            apkName = manifest.apk,
            apkUrl = apk.downloadUrl,
            sha256 = hash,
            notes = release.body.orEmpty().trim(),
        )
    }

    private fun trusted(url: String): Boolean =
        url.toHttpUrlOrNull()?.let { it.scheme == "https" && it.host in trustedHosts } == true
}

/**
 * Looks for a newer Thermida on GitHub, downloads it, and hands it to Android's installer.
 * Every launch checks; a version found that way is offered in [prompt] until it is dismissed.
 */
class AppUpdater(private val context: Context, client: OkHttpClient) {
    private val api = client.newBuilder().connectTimeout(10, TimeUnit.SECONDS).readTimeout(15, TimeUnit.SECONDS).build()
    private val downloader = client.newBuilder().connectTimeout(10, TimeUnit.SECONDS).readTimeout(60, TimeUnit.SECONDS).build()
    private val _state = MutableStateFlow<UpdateState>(UpdateState.Idle)
    val state: StateFlow<UpdateState> = _state.asStateFlow()

    private val _prompt = MutableStateFlow<Release?>(null)

    /** The version to offer on the main screen right after launch; null once it is dismissed or nothing is new. */
    val prompt: StateFlow<Release?> = _prompt.asStateFlow()

    private val installed: PackageInfo
        get() = context.packageManager.getPackageInfo(context.packageName, 0)

    val installedVersion: String
        get() = installed.versionName ?: ""

    private val userAgent get() = "Thermida/$installedVersion (https://github.com/$UPDATE_REPO)"

    /** Where the downloaded APK waits for Android's installer. */
    val downloadFolder: File get() = File(context.cacheDir, "updates")

    /** True while a download is running or waiting to be installed: its file must stay. */
    val holdsDownload: Boolean get() = _state.value is UpdateState.Downloading || _state.value is UpdateState.Ready

    /** Hides the main-screen offer until the next launch. */
    fun dismissPrompt() {
        _prompt.value = null
    }

    /**
     * Asks GitHub for the latest release. [quiet] is the check on launch: a failure stays hidden,
     * and a version found is offered on the main screen. A check from Settings shows its result there.
     */
    suspend fun check(quiet: Boolean = false) {
        if (_state.value is UpdateState.Downloading || _state.value is UpdateState.Ready) return
        _state.value = UpdateState.Checking
        try {
            val release = withContext(Dispatchers.IO) { fetchLatest() }
            _state.value = when {
                release == null -> UpdateState.NoRelease
                release.versionCode > installed.longVersionCode -> UpdateState.Available(release)
                else -> UpdateState.UpToDate
            }
            if (quiet) _prompt.value = (_state.value as? UpdateState.Available)?.release
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            _state.value = if (quiet) UpdateState.Idle else UpdateState.Failed("Couldn't reach GitHub: ${e.message.orEmpty()}")
        }
    }

    /** Downloads outlive the screen that started them: closing the popup or leaving Settings mustn't stop one halfway. */
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var downloadJob: Job? = null

    /**
     * Starts downloading the version offered by [check], or returns the download already running.
     * Wait on the job to know when it's done; cancelling the caller doesn't stop it.
     */
    fun startDownload(): Job {
        downloadJob?.takeIf { it.isActive }?.let { return it }
        return scope.launch { download() }.also { downloadJob = it }
    }

    /** Downloads the version offered by [check]; the file is checked against its published SHA-256. */
    private suspend fun download() {
        val release = (_state.value as? UpdateState.Available)?.release ?: return
        _state.value = UpdateState.Downloading(release, 0f)
        try {
            val file = withContext(Dispatchers.IO) { fetchApk(release) }
            _state.value = UpdateState.Ready(release, file)
        } catch (e: CancellationException) {
            // Never left showing "Downloading": the version can be offered and downloaded again.
            _state.value = UpdateState.Available(release)
            throw e
        } catch (e: Exception) {
            _state.value = UpdateState.Failed("The download didn't finish: ${e.message.orEmpty()}")
        }
    }

    /**
     * Opens Android's install prompt for a downloaded [file]. Android still asks the user to confirm,
     * and it refuses any APK signed with a different key, which is why the key must never change.
     */
    fun install(file: File): InstallStart {
        val archive = context.packageManager.getPackageArchiveInfo(file.path, PackageManager.GET_SIGNING_CERTIFICATES)
            ?: return InstallStart.Refused("The download can't be read. Try again.")
        if (archive.packageName != context.packageName) return InstallStart.Refused("That file is for another app.")
        val current = context.packageManager.getPackageInfo(context.packageName, PackageManager.GET_SIGNING_CERTIFICATES)
        if (!sameSigner(archive, current)) {
            return InstallStart.Refused("That version is signed with a different key, so it can't update this app.")
        }
        if (archive.longVersionCode <= current.longVersionCode) {
            return InstallStart.Refused("That file isn't newer than the version on this phone.")
        }
        if (!context.packageManager.canRequestPackageInstalls()) return InstallStart.NeedsPermission
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.updates", file)
        context.startActivity(
            Intent(Intent.ACTION_VIEW)
                .setDataAndType(uri, "application/vnd.android.package-archive")
                .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK),
        )
        return InstallStart.Started
    }

    /** The Settings page where the user allows installs from Thermida. */
    fun permissionIntent(): Intent =
        Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:${context.packageName}"))
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

    /** Null when the latest release has no update.json or APK yet. */
    private fun fetchLatest(): Release? {
        val releaseBody = get("https://api.github.com/repos/$UPDATE_REPO/releases/latest") ?: return null
        val manifestUrl = ReleaseParser.manifestUrl(releaseBody) ?: return null
        val manifestBody = get(manifestUrl) ?: return null
        return ReleaseParser.parse(releaseBody, manifestBody)
    }

    /** The response body, or null for a 404 (nothing published there). */
    private fun get(url: String): String? {
        val request = Request.Builder().url(url).header("User-Agent", userAgent).build()
        api.newCall(request).execute().use { response ->
            if (response.code == 404) return null
            if (!response.isSuccessful) throw IOException("GitHub answered ${response.code}")
            return response.body.string()
        }
    }

    private fun fetchApk(release: Release): File {
        val dir = downloadFolder.apply { mkdirs() }
        dir.listFiles()?.forEach { it.delete() }
        val target = File(dir, release.apkName)
        val request = Request.Builder().url(release.apkUrl).header("User-Agent", userAgent).build()
        downloader.newCall(request).execute().use { response ->
            if (!response.isSuccessful) throw IOException("GitHub answered ${response.code}")
            val total = response.body.contentLength()
            val digest = MessageDigest.getInstance("SHA-256")
            var done = 0L
            response.body.byteStream().use { input ->
                target.outputStream().use { output ->
                    val buffer = ByteArray(64 * 1024)
                    while (true) {
                        val n = input.read(buffer)
                        if (n < 0) break
                        output.write(buffer, 0, n)
                        digest.update(buffer, 0, n)
                        done += n
                        if (total > 0) _state.value = UpdateState.Downloading(release, done.toFloat() / total)
                    }
                }
            }
            val actual = digest.digest().joinToString("") { "%02x".format(it.toInt() and 0xff) }
            if (actual != release.sha256) {
                target.delete()
                throw IOException("the file doesn't match its published checksum")
            }
        }
        return target
    }

    private fun sameSigner(a: PackageInfo, b: PackageInfo): Boolean {
        val x = a.signingInfo?.apkContentsSigners?.map { it.toByteArray().toList() }?.toSet().orEmpty()
        val y = b.signingInfo?.apkContentsSigners?.map { it.toByteArray().toList() }?.toSet().orEmpty()
        return x.isNotEmpty() && x == y
    }
}
