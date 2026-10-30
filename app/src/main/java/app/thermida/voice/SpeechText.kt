package app.thermida.voice

import java.util.Locale

/** The two languages the coach's mic understands, and how the language chip shows them. */
enum class VoiceLanguage(val tag: String, val label: String) {
    ENGLISH("en-US", "EN"),
    GREEK("el-GR", "ΕΛ"),
    ;

    val locale: Locale get() = Locale.forLanguageTag(tag)

    fun other() = if (this == ENGLISH) GREEK else ENGLISH

    companion object {
        /** The saved choice, or the phone's language the first time. */
        fun pick(saved: String?, phone: Locale = Locale.getDefault()): VoiceLanguage =
            entries.firstOrNull { it.name == saved } ?: if (phone.language == "el") GREEK else ENGLISH
    }
}

/** Turning a coach answer into something a voice can read. No Android here, so it is unit tested. */
object SpeechText {
    fun hasGreek(text: CharSequence) = text.any { it in 'Ͱ'..'Ͽ' || it in 'ἀ'..'῿' }

    private fun hasLatin(text: CharSequence) = text.any { it in 'a'..'z' || it in 'A'..'Z' }

    /** The words of an answer as they should be heard: no markdown, emoji or links. */
    fun clean(raw: String): String = raw
        .replace(Regex("""https?://\S+"""), "")
        .replace(Regex("""(?m)^\s*#+\s*"""), "")
        .replace(Regex("""(?m)^\s*[-*•]\s+"""), "")
        .replace("**", "")
        .replace("__", "")
        .replace("`", "")
        .replace(Regex("""[\p{So}️‍]"""), "")
        .replace(Regex("""[ \t]+"""), " ")
        .replace(Regex("""\s*\n\s*"""), "\n")
        .trim()

    /**
     * The answer in runs for the Greek or the English voice, sentence by sentence: a sentence with
     * Greek letters is Greek, one with only Latin letters is English, and one with neither (just
     * numbers) stays with the sentence before it.
     */
    fun segments(text: String, start: VoiceLanguage = VoiceLanguage.ENGLISH): List<Pair<VoiceLanguage, String>> {
        val out = mutableListOf<Pair<VoiceLanguage, String>>()
        val sentences = text.split(Regex("""(?<=[.!?;;])\s+|\n+""")).map(String::trim).filter { s -> s.any(Char::isLetterOrDigit) }
        for (sentence in sentences) {
            val language = when {
                hasGreek(sentence) -> VoiceLanguage.GREEK
                hasLatin(sentence) -> VoiceLanguage.ENGLISH
                else -> out.lastOrNull()?.first ?: start
            }
            if (out.lastOrNull()?.first == language) {
                out[out.lastIndex] = language to out.last().second + " " + sentence
            } else {
                out += language to sentence
            }
        }
        return out
    }

    /** A short line to hear a speaking speed with, in the mic's language. */
    fun sample(language: VoiceLanguage) =
        if (language == VoiceLanguage.GREEK) "Γεια σου! Έτσι ακούγεται η φωνή του προπονητή." else "Hi! This is how fast your coach talks."

    /** What was typed before the mic went on, then what was heard. */
    fun join(typed: String, heard: String): String = when {
        typed.isBlank() -> heard
        heard.isBlank() -> typed
        else -> typed.trimEnd() + " " + heard
    }
}
