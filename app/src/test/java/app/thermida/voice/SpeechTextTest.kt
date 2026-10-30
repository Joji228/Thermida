package app.thermida.voice

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Locale

class SpeechTextTest {
    @Test
    fun greekLettersAreFoundWithOrWithoutAccents() {
        assertTrue(SpeechText.hasGreek("Πόσες θερμίδες;"))
        assertTrue(SpeechText.hasGreek("ἀρετή"))
        assertFalse(SpeechText.hasGreek("How many calories? 2,560 kcal"))
    }

    @Test
    fun cleaningDropsMarkdownEmojiAndLinks() {
        val raw = "## Today\n- **1,850 kcal** so far 🍝\n* Protein: __120 g__\nSee https://example.com/x for more."
        assertEquals("Today\n1,850 kcal so far\nProtein: 120 g\nSee for more.", SpeechText.clean(raw))
    }

    @Test
    fun eachSentenceGetsTheVoiceOfItsLetters() {
        val parts = SpeechText.segments("Great job today! Έφαγες 1,850 kcal. Μπράβο. Keep it up.")
        assertEquals(
            listOf(
                VoiceLanguage.ENGLISH to "Great job today!",
                VoiceLanguage.GREEK to "Έφαγες 1,850 kcal. Μπράβο.",
                VoiceLanguage.ENGLISH to "Keep it up.",
            ),
            parts,
        )
    }

    @Test
    fun numbersOnlySentencesStayWithTheOneBefore() {
        val parts = SpeechText.segments("Σήμερα:\n1,850 / 2,560\nΚαλή συνέχεια.")
        assertEquals(listOf(VoiceLanguage.GREEK to "Σήμερα: 1,850 / 2,560 Καλή συνέχεια."), parts)
    }

    @Test
    fun decimalsAreNotSplitIntoSentences() {
        assertEquals(listOf(VoiceLanguage.ENGLISH to "You lost 2.5 kg this month."), SpeechText.segments("You lost 2.5 kg this month."))
    }

    @Test
    fun heardWordsFollowWhatWasTyped() {
        assertEquals("how much protein", SpeechText.join("", "how much protein"))
        assertEquals("Today: how much protein", SpeechText.join("Today: ", "how much protein"))
        assertEquals("Today:", SpeechText.join("Today:", ""))
    }

    @Test
    fun theMicStartsInThePhonesLanguageUntilOneIsPicked() {
        assertEquals(VoiceLanguage.GREEK, VoiceLanguage.pick(null, Locale.forLanguageTag("el-GR")))
        assertEquals(VoiceLanguage.ENGLISH, VoiceLanguage.pick(null, Locale.forLanguageTag("de-DE")))
        assertEquals(VoiceLanguage.ENGLISH, VoiceLanguage.pick("ENGLISH", Locale.forLanguageTag("el-GR")))
        assertEquals(VoiceLanguage.ENGLISH, VoiceLanguage.GREEK.other())
    }
}
