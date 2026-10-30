package app.thermida.update

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class AppUpdaterTest {
    private val digest = "a".repeat(64)
    private val base = "https://github.com/Joji228/Thermida/releases/download/v0.0.1"

    private fun asset(name: String, url: String) = """{"name": "$name", "browser_download_url": "$url", "size": 1}"""

    private fun release(vararg assets: String) = """{"tag_name": "v0.0.1", "assets": [${assets.joinToString(",")}]}"""

    private fun manifest(
        versionName: String = "0.0.1",
        versionCode: Int = 30,
        apk: String = "Thermida-0.0.1.apk",
        sha256: String = digest,
    ) = """{"versionName": "$versionName", "versionCode": $versionCode, "apk": "$apk", "sha256": "$sha256"}"""

    private val manifestAsset = asset("update.json", "$base/update.json")
    private val apkAsset = asset("Thermida-0.0.1.apk", "$base/Thermida-0.0.1.apk")

    @Test
    fun releaseWithManifestAndApkIsInstallable() {
        val parsed = ReleaseParser.parse(release(manifestAsset, apkAsset), manifest())
        assertEquals(
            Release(
                version = "0.0.1",
                versionCode = 30,
                apkName = "Thermida-0.0.1.apk",
                apkUrl = "$base/Thermida-0.0.1.apk",
                sha256 = digest,
            ),
            parsed,
        )
    }

    @Test
    fun manifestIsFoundByName() {
        assertEquals("$base/update.json", ReleaseParser.manifestUrl(release(apkAsset, manifestAsset)))
        assertNull(ReleaseParser.manifestUrl(release(apkAsset)))
    }

    @Test
    fun releaseWithoutApkIsNotOffered() {
        assertNull(ReleaseParser.parse(release(manifestAsset), manifest()))
    }

    @Test
    fun apkNamedForAnotherVersionIsRefused() {
        assertNull(ReleaseParser.parse(release(manifestAsset, apkAsset), manifest(apk = "Thermida-0.0.2.apk")))
    }

    @Test
    fun incompleteManifestIsRefused() {
        assertNull(ReleaseParser.parse(release(manifestAsset, apkAsset), manifest(versionCode = 0)))
        assertNull(ReleaseParser.parse(release(manifestAsset, apkAsset), manifest(sha256 = "not-a-digest")))
        assertNull(ReleaseParser.parse(release(manifestAsset, apkAsset), manifest(versionName = "")))
    }

    @Test
    fun downloadFromAnotherHostIsRefused() {
        val elsewhere = asset("Thermida-0.0.1.apk", "https://example.com/Thermida-0.0.1.apk")
        assertNull(ReleaseParser.parse(release(manifestAsset, elsewhere), manifest()))
        val plainHttp = asset("Thermida-0.0.1.apk", "http://github.com/Joji228/Thermida/releases/download/v0.0.1/Thermida-0.0.1.apk")
        assertNull(ReleaseParser.parse(release(manifestAsset, plainHttp), manifest()))
        val elsewhereManifest = asset("update.json", "https://example.com/update.json")
        assertNull(ReleaseParser.manifestUrl(release(elsewhereManifest)))
    }
}
