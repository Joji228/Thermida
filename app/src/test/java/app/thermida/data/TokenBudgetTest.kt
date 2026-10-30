package app.thermida.data

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.ZoneId
import java.time.ZonedDateTime

class TokenBudgetTest {
    private val zone = ZoneId.of("Europe/Athens")
    private val day = 24L * 60 * 60 * 1000

    /** Saturday 2026-10-24 15:00 in Athens. */
    private val now = at(2026, 10, 24, 15)

    private fun at(year: Int, month: Int, dayOfMonth: Int, hour: Int): Long =
        ZonedDateTime.of(year, month, dayOfMonth, hour, 0, 0, 0, zone).toInstant().toEpochMilli()

    private fun obj(text: String) = Json.parseToJsonElement(text).jsonObject

    @Test
    fun logRoundTripsAndOldEntriesAreDropped() {
        val old = now - 9 * day
        assertEquals("", TokenBudget.add("", old, 500, now))
        val recent = TokenBudget.add("", now - day, 1_000, now)
        val both = TokenBudget.add(recent, now, 2_000, now)
        assertEquals(listOf(now - day to 1_000L, now to 2_000L), TokenBudget.parse(both))
    }

    @Test
    fun todayIsTheLocalCalendarDay() {
        assertEquals(at(2026, 10, 24, 0), TokenBudget.startOfToday(now, zone))
    }

    @Test
    fun weekStartsOnSundayAtNine() {
        // Saturday: the week began on the Sunday before, at 9:00.
        assertEquals(at(2026, 10, 18, 9), TokenBudget.weekStart(now, zone))
        // Sunday after 9:00: this Sunday's 9:00 starts a new week.
        assertEquals(at(2026, 10, 25, 9), TokenBudget.weekStart(at(2026, 10, 25, 10), zone))
        // Sunday before 9:00: still the previous week.
        assertEquals(at(2026, 10, 18, 9), TokenBudget.weekStart(at(2026, 10, 25, 8), zone))
    }

    @Test
    fun dailyLimitStopsRequestsUntilMidnight() {
        val entries = listOf((now - 60_000L) to 10_000L)
        val message = TokenBudget.blocked(entries, daily = 10_000, weekly = 0, now = now, zone = zone)
        assertTrue(message!!.startsWith("Daily AI limit reached"))
        // Yesterday's use doesn't count against today.
        val yesterday = listOf((now - day) to 10_000L)
        assertNull(TokenBudget.blocked(yesterday, daily = 10_000, weekly = 0, now = now, zone = zone))
    }

    @Test
    fun weeklyLimitCountsFromSundayNineAndResetsThen() {
        // Used on Monday of this week: counts.
        val thisWeek = listOf(at(2026, 10, 19, 12) to 600_000L)
        val message = TokenBudget.blocked(thisWeek, daily = 0, weekly = 500_000, now = now, zone = zone)
        assertTrue(message!!.startsWith("Weekly AI limit reached"))
        // Used on the Sunday before this week started (before 9:00): doesn't count any more.
        val lastWeek = listOf(at(2026, 10, 18, 8) to 600_000L)
        assertNull(TokenBudget.blocked(lastWeek, daily = 0, weekly = 500_000, now = now, zone = zone))
    }

    @Test
    fun weeklyUseShowsInTheSummary() {
        val entries = listOf(at(2026, 10, 19, 12) to 300L, at(2026, 10, 18, 8) to 900L)
        val usage = TokenBudget.summary(entries, daily = 0, weekly = 0, now = now, zone = zone)
        assertEquals(300L, usage.week)
    }

    @Test
    fun noLimitMeansNeverBlocked() {
        val entries = listOf(now to 9_000_000L)
        assertNull(TokenBudget.blocked(entries, daily = 0, weekly = 0, now = now, zone = zone))
    }

    @Test
    fun tokensAreReadFromEachProvidersReply() {
        assertEquals(4_200L, TokenBudget.tokensIn(obj("""{"usage": {"total_tokens": 4200}}""")))
        assertEquals(300L, TokenBudget.tokensIn(obj("""{"usage": {"prompt_tokens": 200, "completion_tokens": 100}}""")))
        assertEquals(1_500L, TokenBudget.tokensIn(obj("""{"usage": {"input_tokens": 1000, "output_tokens": 500}}""")))
        assertEquals(900L, TokenBudget.tokensIn(obj("""{"usageMetadata": {"totalTokenCount": 900}}""")))
        assertEquals(0L, TokenBudget.tokensIn(obj("""{"choices": []}""")))
    }

    @Test
    fun choicesAreLabelled() {
        assertEquals("Off", TokenBudget.label(0))
        assertEquals("10,000", TokenBudget.label(10_000))
        assertEquals("1 million", TokenBudget.label(1_000_000))
        assertEquals(listOf(0L, 10_000L, 50_000L, 100_000L, 200_000L), TokenBudget.dailyChoices)
        assertEquals(listOf(0L, 100_000L, 500_000L, 1_000_000L), TokenBudget.weeklyChoices)
    }
}
