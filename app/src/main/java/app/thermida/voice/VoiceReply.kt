package app.thermida.voice

import java.text.Normalizer
import java.util.Locale

/**
 * A spoken answer to the coach's proposal cards: "yes, log it" accepts them, "no" declines them.
 * Only a short reply that is clearly one or the other counts; anything longer, or anything that
 * mixes both, is a new question and is sent to the coach as usual.
 */
enum class VoiceReply {
    ACCEPT,
    DECLINE,
    ;

    companion object {
        /** At most this many words: "yes please log it" is an answer, a sentence is a question. */
        private const val MAX_WORDS = 5

        private val ACCEPT_PHRASES = setOf(
            "yes", "yeah", "yep", "yup", "sure", "ok", "okay", "accept", "accept it", "accept all", "confirm",
            "do it", "log it", "add it", "save it", "go ahead", "please do", "sounds good", "correct", "right",
            "ναι", "ναι ναι", "εντάξει", "οκ", "οκει", "βέβαια", "σωστά", "καταχώρησε", "καταχώρησέ το", "βάλ' το", "βαλ το",
            "πρόσθεσέ το", "πρόσθεσε", "αποθήκευσε", "κάν' το", "καν το", "μάλιστα", "αμέ",
        ).map(::normal).toSet()

        private val DECLINE_PHRASES = setOf(
            "no", "nope", "nah", "decline", "cancel", "don't", "dont", "do not", "never mind", "nevermind", "skip it", "not now",
            "όχι", "οχι", "άκυρο", "ακύρωσε", "μην", "μη", "όχι ευχαριστώ", "άσ' το", "ασ το", "άφησέ το", "δεν θέλω",
        ).map(::normal).toSet()

        /** Little words around the answer that don't change it ("yes please", "όχι ευχαριστώ"). */
        private val FILLER = setOf("please", "thanks", "thank you", "coach", "παρακαλώ", "ευχαριστώ").map(::normal).toSet()

        fun of(heard: String): VoiceReply? {
            val all = normal(heard)
            if (all.isEmpty() || all.split(' ').size > MAX_WORDS) return null
            var text = " $all "
            FILLER.forEach { text = text.replace(" $it ", " ") }
            val kept = text.split(' ').filter { it.isNotEmpty() }.ifEmpty { return null }
            val accept = matches(kept, ACCEPT_PHRASES)
            val decline = matches(kept, DECLINE_PHRASES)
            return when {
                accept && !decline -> ACCEPT
                decline && !accept -> DECLINE
                else -> null
            }
        }

        /** Every word is part of some phrase in [phrases], and at least one phrase is there whole. */
        private fun matches(words: List<String>, phrases: Set<String>): Boolean {
            val text = words.joinToString(" ")
            if (text in phrases) return true
            val found = phrases.filter { phrase -> " $text ".contains(" $phrase ") }
            if (found.isEmpty()) return false
            val covered = found.flatMap { it.split(' ') }.toSet()
            return words.all { it in covered }
        }

        /** Lower case, no accents or punctuation, so "Ναι!" and "ναι" are the same answer. */
        private fun normal(text: String): String =
            Normalizer.normalize(text.lowercase(Locale.ROOT), Normalizer.Form.NFD)
                .replace(Regex("""\p{Mn}+"""), "")
                .replace('’', '\'')
                .replace(Regex("""[^\p{L}\p{N}' ]"""), " ")
                .replace(Regex("""\s+"""), " ")
                .trim()
    }
}
