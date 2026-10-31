package app.thermida.domain

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ImpedanceCheckTest {
    private val usual = listOf(480, 495, 502, 488, 510)

    @Test
    fun aNormalDayIsNotFlagged() {
        assertFalse(ImpedanceCheck.unusual(505, usual))
        assertFalse(ImpedanceCheck.unusual(440, usual)) // about 10 % lower
    }

    @Test
    fun aBigJumpEitherWayIsFlagged() {
        assertTrue(ImpedanceCheck.unusual(640, usual)) // damp socks: much higher
        assertTrue(ImpedanceCheck.unusual(360, usual)) // much lower
    }

    @Test
    fun nothingIsFlaggedUntilThereIsAUsualToCompareWith() {
        assertFalse(ImpedanceCheck.unusual(900, listOf(480, 495)))
        // Weight-only readings don't count towards the usual.
        assertFalse(ImpedanceCheck.unusual(900, listOf(480, null, 495, null)))
        assertTrue(ImpedanceCheck.unusual(900, listOf(480, null, 495, 500)))
    }

    @Test
    fun theUsualFollowsTheLatestReadings() {
        // Ten old readings around 600, then ten recent ones around 480: 480 is normal now.
        val past = List(10) { 600 } + List(10) { 480 }
        assertFalse(ImpedanceCheck.unusual(485, past))
        assertTrue(ImpedanceCheck.unusual(600, past))
    }
}
