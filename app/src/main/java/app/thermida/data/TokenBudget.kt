package app.thermida.data

import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull
import java.time.DayOfWeek
import java.time.Instant
import java.time.ZoneId
import java.util.Locale

/** What the AI engines have used, and the day and week limits the user picked. Counted in tokens. */
data class TokenUsage(val today: Long, val week: Long, val daily: Long, val weekly: Long)

/**
 * Token limits for AI calls. Each engine reports how many tokens a reply used; those are logged,
 * and a request is refused once the day's or the week's limit is used up. The day is the local
 * calendar day; the week runs from Sunday 9:00 (see [weekStart]).
 */
object TokenBudget {
    /** The choices in Settings. 0 means no limit. */
    val dailyChoices = listOf(0L, 10_000L, 50_000L, 100_000L, 200_000L)
    val weeklyChoices = listOf(0L, 100_000L, 500_000L, 1_000_000L)

    private const val DAY_MS = 24L * 60 * 60 * 1000

    /** The weekly limit's week starts and resets at 9:00 on Sunday. */
    private const val WEEK_HOUR = 9

    /** Entries older than this are dropped from the log; a little more than a week. */
    private const val KEEP_MS = 8 * DAY_MS

    /** The log is "time:tokens" pairs separated by commas, oldest first. */
    fun parse(log: String): List<Pair<Long, Long>> = log.split(',').mapNotNull { pair ->
        val parts = pair.split(':')
        val time = parts.getOrNull(0)?.toLongOrNull() ?: return@mapNotNull null
        val tokens = parts.getOrNull(1)?.toLongOrNull() ?: return@mapNotNull null
        time to tokens
    }

    /** The log with one more request added, and anything too old left out. */
    fun add(log: String, time: Long, tokens: Long, now: Long): String =
        (parse(log) + (time to tokens))
            .filter { now - it.first < KEEP_MS }
            .joinToString(",") { "${it.first}:${it.second}" }

    fun usedSince(entries: List<Pair<Long, Long>>, since: Long): Long =
        entries.filter { it.first >= since }.sumOf { it.second }

    fun startOfToday(now: Long, zone: ZoneId): Long =
        Instant.ofEpochMilli(now).atZone(zone).toLocalDate().atStartOfDay(zone).toInstant().toEpochMilli()

    /**
     * The start of the current week: the last Sunday at 9:00 local time, or the Sunday before that
     * when it is not yet 9:00 on a Sunday. The weekly limit resets at each Sunday 9:00.
     */
    fun weekStart(now: Long, zone: ZoneId): Long {
        val local = Instant.ofEpochMilli(now).atZone(zone)
        var day = local.toLocalDate()
        while (day.dayOfWeek != DayOfWeek.SUNDAY) day = day.minusDays(1)
        var start = day.atTime(WEEK_HOUR, 0).atZone(zone)
        if (start.isAfter(local)) start = start.minusWeeks(1)
        return start.toInstant().toEpochMilli()
    }

    fun summary(entries: List<Pair<Long, Long>>, daily: Long, weekly: Long, now: Long, zone: ZoneId): TokenUsage =
        TokenUsage(
            today = usedSince(entries, startOfToday(now, zone)),
            week = usedSince(entries, weekStart(now, zone)),
            daily = daily,
            weekly = weekly,
        )

    /** Why a request must not go ahead, or null when it may. */
    fun blocked(entries: List<Pair<Long, Long>>, daily: Long, weekly: Long, now: Long, zone: ZoneId): String? {
        val usage = summary(entries, daily, weekly, now, zone)
        if (daily > 0 && usage.today >= daily) {
            return "Daily AI limit reached: ${grouped(usage.today)} of ${grouped(daily)} tokens used today. " +
                "It resets at midnight, or raise the limit in Settings → AI engine."
        }
        if (weekly > 0 && usage.week >= weekly) {
            return "Weekly AI limit reached: ${grouped(usage.week)} of ${grouped(weekly)} tokens since Sunday 9:00. " +
                "It resets next Sunday at 9:00, or raise the limit in Settings → AI engine."
        }
        return null
    }

    /** A choice's label in Settings: "Off", "10,000", "1 million". */
    fun label(tokens: Long): String = when (tokens) {
        0L -> "Off"
        1_000_000L -> "1 million"
        else -> grouped(tokens)
    }

    fun grouped(tokens: Long): String = String.format(Locale.US, "%,d", tokens)

    /**
     * The tokens a reply reported, in whichever form the engine uses: OpenAI-style "usage",
     * Claude-style "usage" with input and output, or Gemini's "usageMetadata". 0 when none is there.
     */
    fun tokensIn(root: JsonObject): Long {
        root["usage"]?.let { usageElement ->
            val usage = usageElement.jsonObject
            usage["total_tokens"]?.jsonPrimitive?.longOrNull?.let { return it }
            val input = usage["input_tokens"]?.jsonPrimitive?.longOrNull
                ?: usage["prompt_tokens"]?.jsonPrimitive?.longOrNull ?: 0L
            val output = usage["output_tokens"]?.jsonPrimitive?.longOrNull
                ?: usage["completion_tokens"]?.jsonPrimitive?.longOrNull ?: 0L
            return input + output
        }
        return root["usageMetadata"]?.jsonObject?.get("totalTokenCount")?.jsonPrimitive?.longOrNull ?: 0L
    }
}
