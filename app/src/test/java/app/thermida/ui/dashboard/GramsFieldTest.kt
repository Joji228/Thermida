package app.thermida.ui.dashboard

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class GramsFieldTest {
    @Test
    fun aPickedChipShowsInTheBox() {
        assertEquals("150", gramsFieldUpdate("100", 150.0))
        assertEquals("1000", gramsFieldUpdate("100", 1000.0)) // no "1,000", which would read back as 1 g
    }

    @Test
    fun whatTheUserIsTypingIsLeftAlone() {
        assertNull(gramsFieldUpdate("120", 120.0))
        assertNull(gramsFieldUpdate("12.", 12.0))
        assertNull(gramsFieldUpdate("12,5", 12.5))
    }
}
