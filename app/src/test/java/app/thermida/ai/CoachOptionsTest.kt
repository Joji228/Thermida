package app.thermida.ai

import app.thermida.data.DEFAULT_PAUSE_MS
import app.thermida.data.DEFAULT_SPEECH_RATE
import app.thermida.data.MAX_SPEECH_RATE
import app.thermida.data.MIN_SPEECH_RATE
import app.thermida.voice.SpeechText
import app.thermida.voice.VoiceLanguage
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** What Settings → Coach changes in the instructions the AI gets, and the voice defaults. */
class CoachOptionsTest {
    @Test
    fun theDefaultIsShortAnswersWithSuggestedQuestions() {
        val prompt = CoachPrompt.system("DATA")
        assertFalse(prompt.contains(CoachPrompt.DETAILED))
        assertFalse(prompt.contains(CoachPrompt.NO_FOLLOW_UPS))
    }

    @Test
    fun detailedAnswersAreAskedForAfterTheRulesAndBeforeTheData() {
        val prompt = CoachPrompt.system("DATA", detailed = true)
        assertTrue(prompt.indexOf(CoachPrompt.SYSTEM) < prompt.indexOf(CoachPrompt.DETAILED))
        assertTrue(prompt.indexOf(CoachPrompt.DETAILED) < prompt.indexOf("DATA"))
        assertTrue(CoachPrompt.DETAILED.contains("safety rules still apply"))
    }

    @Test
    fun turningSuggestionsOffTellsTheCoachNotToAddThem() {
        val prompt = CoachPrompt.system("DATA", followUps = false)
        assertTrue(prompt.contains(CoachPrompt.NO_FOLLOW_UPS))
        assertTrue(prompt.endsWith("DATA"))
    }

    @Test
    fun theVoiceStartsAtNormalSpeedAndWaitsAHalfSecondLongerForYouToFinish() {
        assertEquals(1.0f, DEFAULT_SPEECH_RATE, 0.0001f)
        assertTrue(DEFAULT_SPEECH_RATE in MIN_SPEECH_RATE..MAX_SPEECH_RATE)
        assertEquals(1500, DEFAULT_PAUSE_MS)
    }

    @Test
    fun theSampleSentenceIsInTheMicsLanguage() {
        assertFalse(SpeechText.hasGreek(SpeechText.sample(VoiceLanguage.ENGLISH)))
        assertTrue(SpeechText.hasGreek(SpeechText.sample(VoiceLanguage.GREEK)))
    }
}
