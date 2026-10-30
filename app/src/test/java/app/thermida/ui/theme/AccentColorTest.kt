package app.thermida.ui.theme

import androidx.compose.ui.graphics.toArgb
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

class AccentColorTest {
    @Test
    fun offersFiveDistinctColors() {
        assertEquals(5, AccentColor.entries.size)
        assertEquals(5, AccentColor.entries.map { it.dark }.toSet().size)
        assertEquals(5, AccentColor.entries.map { it.light }.toSet().size)
    }

    @Test
    fun blueIsTheDefault() {
        assertEquals(AccentColor.BLUE, AccentColor.Default)
        assertEquals(0xFF6C8CFF.toInt(), AccentColor.BLUE.dark.toArgb())
    }

    @Test
    fun savedLimeStillLoads() {
        assertEquals(AccentColor.LIME, AccentColor.fromName("LIME"))
    }

    @Test
    fun unknownOrMissingNamesFallBackToTheDefault() {
        assertEquals(AccentColor.Default, AccentColor.fromName(null))
        assertEquals(AccentColor.Default, AccentColor.fromName("ORANGE"))
        assertEquals(AccentColor.PINK, AccentColor.fromName("PINK"))
    }

    @Test
    fun containersDifferFromTheAccent() {
        AccentColor.entries.forEach {
            assertNotEquals(it.dark, it.darkContainer)
            assertNotEquals(it.light, it.lightContainer)
        }
    }
}