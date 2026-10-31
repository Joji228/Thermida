package app.thermida.ui.body

import app.thermida.ui.components.ChartPoint
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalDate

class TrendAtTest {
    private val today = LocalDate.of(2026, 10, 31)
    private fun point(daysAgo: Long, trend: Double) = ChartPoint(today.minusDays(daysAgo), trend + 1, trend)

    @Test
    fun needsThreeReadingsUpToTheDay() {
        assertNull(trendAt(listOf(point(5, 22.0), point(1, 21.8)), today))
    }

    @Test
    fun givesTheTrendThenAndItsThirtyDayChange() {
        val points = listOf(point(40, 24.0), point(31, 23.5), point(10, 22.6), point(1, 22.2))
        val now = trendAt(points, today)!!
        assertEquals(22.2, now.value, 1e-9)
        assertEquals(22.2 - 23.5, now.change30!!, 1e-9)
    }

    @Test
    fun anOlderMeasurementUsesTheTrendAsOfThatDay() {
        val points = listOf(point(12, 23.0), point(8, 22.8), point(4, 22.5), point(1, 22.2))
        val then = trendAt(points, today.minusDays(4))!!
        assertEquals(22.5, then.value, 1e-9)
        assertNull(then.change30)
    }
}
