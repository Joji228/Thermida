package app.thermida.voice

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class VoiceReplyTest {
    @Test
    fun shortYesAnswersAcceptTheCards() {
        listOf("Yes", "yes please", "Yes, log it.", "OK", "sure, go ahead", "Do it", "yeah thank you").forEach {
            assertEquals(it, VoiceReply.ACCEPT, VoiceReply.of(it))
        }
    }

    @Test
    fun shortNoAnswersDeclineThem() {
        listOf("No", "no thanks", "Nope.", "cancel", "never mind", "don't").forEach {
            assertEquals(it, VoiceReply.DECLINE, VoiceReply.of(it))
        }
    }

    @Test
    fun greekWorksWithOrWithoutAccents() {
        assertEquals(VoiceReply.ACCEPT, VoiceReply.of("Ναι!"))
        assertEquals(VoiceReply.ACCEPT, VoiceReply.of("ναι παρακαλώ"))
        assertEquals(VoiceReply.ACCEPT, VoiceReply.of("Εντάξει"))
        assertEquals(VoiceReply.DECLINE, VoiceReply.of("Όχι"))
        assertEquals(VoiceReply.DECLINE, VoiceReply.of("οχι ευχαριστω"))
        assertEquals(VoiceReply.DECLINE, VoiceReply.of("άκυρο"))
    }

    @Test
    fun anythingElseIsANewQuestion() {
        listOf(
            "yes but make it 200 grams instead",
            "no wait log it",
            "how many calories is that",
            "what about dinner",
            "thanks",
            "right now",
            "",
            "   ",
            "yes no",
        ).forEach { assertNull(it, VoiceReply.of(it)) }
    }
}
