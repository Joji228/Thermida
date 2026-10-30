package app.thermida.data

import app.thermida.MealDraft
import app.thermida.ai.AnalysisResult
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.File
import java.time.LocalDate

/**
 * The scan waiting on the results screen, kept in one small file so it survives Android closing
 * the app in the background (opening another app, the camera, a call). Without it the results
 * screen would come back empty and an AI scan that was already paid for would be lost.
 */
class DraftStore(private val dir: File) {
    private val file get() = File(dir, "draft.json")
    private val json = Json { ignoreUnknownKeys = true }

    @Serializable
    private data class Saved(val photos: List<String>, val note: String?, val result: AnalysisResult?, val date: String)

    /** The saved scan, or null when there is none (or it can't be read). Photos that are gone are left out. */
    fun load(): MealDraft? = runCatching {
        val saved = json.decodeFromString(Saved.serializer(), file.readText())
        MealDraft(saved.photos.map(::File).filter { it.exists() }, saved.note, saved.result, LocalDate.parse(saved.date))
    }.getOrNull()

    /** Replaces the saved scan; null deletes it. Written beside the old file and swapped in, so a crash never leaves half a file. */
    fun save(draft: MealDraft?) {
        if (draft == null) {
            file.delete()
            return
        }
        dir.mkdirs()
        val temp = File(dir, "draft.json.tmp")
        temp.writeText(json.encodeToString(Saved.serializer(), Saved(draft.photos.map { it.absolutePath }, draft.note, draft.result, draft.date.toString())))
        if (!temp.renameTo(file)) {
            file.delete()
            temp.renameTo(file)
        }
    }
}
