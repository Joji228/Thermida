package app.thermida.data

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonPrimitive

/**
 * The follow-up questions the coach suggests after an answer. It ends its reply with
 *
 *     <suggestions>["How about tomorrow?", "Is that enough protein?"]</suggestions>
 *
 * The block is taken out of the text the user reads; the questions show as small pills above
 * the text box, and tapping one asks it. They're never sent back to the AI.
 */
object CoachFollowUps {
    const val MAX = 3
    const val MAX_CHARS = 60

    private val json = Json { isLenient = true }
    private val block = Regex("""<suggestions>(.*?)(?:</suggestions>|$)""", setOf(RegexOption.DOT_MATCHES_ALL, RegexOption.IGNORE_CASE))

    data class Parsed(val text: String, val suggestions: List<String>)

    fun parse(raw: String): Parsed {
        val match = block.find(raw) ?: return Parsed(raw, emptyList())
        val payload = match.groupValues[1].trim()
        // A JSON list as asked; a model that writes one question per line instead still works.
        val items = (runCatching { json.parseToJsonElement(payload) }.getOrNull() as? JsonArray)
            ?.mapNotNull { runCatching { it.jsonPrimitive.contentOrNull }.getOrNull() }
            ?: payload.lines()
        val suggestions = items
            .map { it.replace('\n', ' ').trim().trim('"', '\'', '-', '•', ' ') }
            .filter { it.length in 2..MAX_CHARS }
            .distinctBy { it.lowercase() }
            .take(MAX)
        return Parsed(raw.removeRange(match.range).trim(), suggestions)
    }
}
