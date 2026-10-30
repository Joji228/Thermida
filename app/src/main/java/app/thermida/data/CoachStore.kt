package app.thermida.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import java.io.File

/**
 * One message in the coach chat. [fromUser] false means the coach wrote it; a coach message can
 * carry [actions], changes it offers to make that the user accepts or declines.
 */
@Serializable
data class CoachMessage(
    val fromUser: Boolean,
    val text: String,
    val at: Long = System.currentTimeMillis(),
    val actions: List<CoachAction> = emptyList(),
    /** Follow-up questions offered after a coach answer (see CoachFollowUps); never sent back to the AI. */
    val suggestions: List<String> = emptyList(),
)

/**
 * The current coach conversation, kept in one small file in the app's private storage. It is
 * not in the database (no migration, nothing to break) and not in backups. Only the latest
 * conversation exists: "Clear chat", turning the coach off and erasing everything delete it.
 */
class CoachStore(private val dir: File) {
    private val file get() = File(dir, "chat.json")
    private val json = Json { ignoreUnknownKeys = true }
    private val serializer = ListSerializer(CoachMessage.serializer())

    suspend fun load(): List<CoachMessage> = withContext(Dispatchers.IO) {
        runCatching { json.decodeFromString(serializer, file.readText()) }.getOrDefault(emptyList())
    }

    suspend fun save(messages: List<CoachMessage>) = withContext(Dispatchers.IO) {
        dir.mkdirs()
        // Written next to the old file and swapped in, so a crash mid-write never leaves half a chat.
        val temp = File(dir, "chat.json.tmp")
        temp.writeText(json.encodeToString(serializer, messages))
        if (!temp.renameTo(file)) {
            file.delete()
            temp.renameTo(file)
        }
    }

    /** Fires after the chat is deleted, so an open coach screen empties itself too. */
    val cleared = MutableSharedFlow<Unit>(extraBufferCapacity = 1)

    suspend fun clear() {
        withContext(Dispatchers.IO) {
            File(dir, "chat.json.tmp").delete()
            file.delete()
        }
        cleared.tryEmit(Unit)
    }
}
