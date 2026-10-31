package app.thermida.ui.theme

import org.junit.Assert.assertEquals
import org.junit.Test

class AccentColorTest {
    @Test
    fun savedNamesStillLoad() {
        assertEquals(AccentColor.LIME, AccentColor.fromName("LIME"))
        assertEquals(AccentColor.PINK, AccentColor.fromName("PINK"))
    }

    @Test
    fun unknownOrMissingNamesFallBackToTheDefault() {
        assertEquals(AccentColor.Default, AccentColor.fromName(null))
        assertEquals(AccentColor.Default, AccentColor.fromName("ORANGE"))
    }
}
