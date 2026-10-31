package app.thermida.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class CoachStoreTest {
    @get:Rule
    val folder = TemporaryFolder()

    @Test
    fun savesLoadsAndClears() = runBlocking {
        val dir = File(folder.root, "coach")
        val store = CoachStore(dir)
        assertTrue(store.load().isEmpty())

        val chat = listOf(CoachMessage(true, "How am I doing?", 1L), CoachMessage(false, "Down **1.2 kg**.\nKeep going.", 2L))
        store.save(chat)
        assertEquals(chat, CoachStore(dir).load())
        assertFalse(File(dir, "chat.json.tmp").exists())

        store.save(chat.take(1))
        assertEquals(chat.take(1), store.load())

        store.clear()
        assertTrue(store.load().isEmpty())
        assertFalse(File(dir, "chat.json").exists())
    }

    @Test
    fun savesStartedTogetherNeverLeaveABrokenFile() = runBlocking {
        val dir = File(folder.root, "coach")
        val store = CoachStore(dir)
        // Long chats, so unguarded writes to the shared temp file would overlap.
        val chats = (1..20).map { n -> List(200) { CoachMessage(it % 2 == 0, "Message $it of chat $n ".repeat(20), it.toLong()) } }
        chats.map { chat -> async(Dispatchers.IO) { store.save(chat) } }.awaitAll()
        assertTrue(store.load() in chats)
        assertFalse(File(dir, "chat.json.tmp").exists())
    }

    @Test
    fun brokenFileReadsAsEmpty() = runBlocking {
        val dir = folder.newFolder("coach")
        File(dir, "chat.json").writeText("{not json")
        assertTrue(CoachStore(dir).load().isEmpty())
    }
}
