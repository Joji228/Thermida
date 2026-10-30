package app.thermida.data

import android.content.Context
import java.io.File

/** How much of the phone's storage the app takes. */
class StorageInfo(private val context: Context) {

    /** [cacheBytes] is part of [dataBytes]: temporary files the app can make again. */
    data class Usage(val appBytes: Long, val dataBytes: Long, val cacheBytes: Long = 0L) {
        val totalBytes get() = appBytes + dataBytes

        /** Meals, photos, settings and keys: what clearing the cache never touches. */
        val keptBytes get() = (dataBytes - cacheBytes).coerceAtLeast(0L)
    }

    private val cacheDirs get() = listOfNotNull(context.cacheDir, context.externalCacheDir)

    /** The installed app itself, plus everything it has saved: database, photos, settings and cache. */
    fun usage(): Usage {
        val info = context.applicationInfo
        val app = File(info.sourceDir).length() + info.splitSourceDirs.orEmpty().sumOf { File(it).length() }
        return Usage(app, folderSize(context.dataDir), cacheDirs.sumOf(::folderSize))
    }

    /**
     * Deletes what is in the cache folders and says how much that freed. Anything in [keep] (a file
     * or folder directly inside a cache folder) stays: photos still waiting on Scan or on the
     * results screen, and an update that has been downloaded but not installed yet.
     */
    fun clearCache(keep: Set<File> = emptySet()): Long {
        val kept = keep.map { it.absoluteFile }.toSet()
        val before = cacheDirs.sumOf(::folderSize)
        cacheDirs.forEach { dir -> dir.listFiles()?.filter { it.absoluteFile !in kept }?.forEach { it.deleteRecursively() } }
        return (before - cacheDirs.sumOf(::folderSize)).coerceAtLeast(0L)
    }

    private fun folderSize(file: File): Long =
        if (file.isFile) file.length() else file.listFiles()?.sumOf(::folderSize) ?: 0L
}

/** "840 KB", "3.2 MB". */
fun formatBytes(bytes: Long): String =
    if (bytes < 1024 * 1024) "${(bytes / 1024).coerceAtLeast(if (bytes > 0) 1 else 0)} KB"
    else String.format(java.util.Locale.getDefault(), "%.1f MB", bytes / (1024.0 * 1024.0))
