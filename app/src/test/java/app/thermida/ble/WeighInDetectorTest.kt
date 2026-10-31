package app.thermida.ble

import app.thermida.ble.WeighInDetector.Event
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class WeighInDetectorTest {
    private fun frame(kg: Double, stable: Boolean = true, impedance: Int? = null) = MiScaleReading(kg, impedance, stable)

    @Test
    fun barefootWeighInIsReportedOnceWithImpedance() {
        val d = WeighInDetector()
        assertEquals(Event.Weighing(82.4, settled = false), d.onReading(frame(82.4, stable = false), 0))
        assertEquals(Event.Weighing(83.1, settled = true), d.onReading(frame(83.1), 1_000))
        assertEquals(Event.Done(83.1, 495), d.onReading(frame(83.1, impedance = 495), 3_000))
        // The scale keeps repeating the final frame after stepping off: not saved again.
        assertNull(d.onReading(frame(83.1, impedance = 495), 4_000))
        assertNull(d.onReading(frame(83.1), 20_000))
        assertNull(d.onTick(60_000))
    }

    @Test
    fun withShoesOnTheWeightIsSavedAloneAfterTheWait() {
        val d = WeighInDetector(impedanceWaitMs = 12_000)
        d.onReading(frame(83.4), 0)
        d.onReading(frame(83.4), 5_000) // repeated stable frames don't restart the wait
        assertNull(d.onTick(11_000))
        assertEquals(Event.Done(83.4, null), d.onTick(12_000))
        assertNull(d.onTick(30_000))
    }

    @Test
    fun steppingBackOnRestartsTheWeighIn() {
        val d = WeighInDetector(impedanceWaitMs = 12_000)
        d.onReading(frame(83.4), 0)
        d.onReading(frame(70.0, stable = false), 8_000)
        assertNull(d.onTick(12_000))
    }

    @Test
    fun someoneElseRightAfterwardsStillCounts() {
        val d = WeighInDetector()
        d.onReading(frame(83.1, impedance = 495), 0)
        assertEquals(Event.Done(61.0, 560), d.onReading(frame(61.0, impedance = 560), 30_000))
    }

    @Test
    fun theSamePersonLaterOnIsANewWeighIn() {
        val d = WeighInDetector(repeatGuardMs = 90_000)
        d.onReading(frame(83.1, impedance = 495), 0)
        assertEquals(Event.Done(83.0, 497), d.onReading(frame(83.0, impedance = 497), 120_000))
    }

    @Test
    fun withinFiveKilosOfTheLastWeighInIsYou() {
        assertTrue(WeighInDetector.looksLikeYou(83.1, 80.0))
        assertTrue(WeighInDetector.looksLikeYou(85.0, 80.0))
        assertFalse(WeighInDetector.looksLikeYou(85.1, 80.0))
        assertFalse(WeighInDetector.looksLikeYou(61.0, 83.0))
        // Nothing to compare with yet: trust it.
        assertTrue(WeighInDetector.looksLikeYou(61.0, null))
    }

    @Test
    fun aRetakeCountsTheNextStepOnButNotTheOldRepeats() {
        val d = WeighInDetector(impedanceWaitMs = 12_000)
        d.onReading(frame(83.4), 0)
        assertEquals(Event.Done(83.4, null), d.onTick(12_000))
        d.retake()
        // The scale still repeats the old, stable frame: ignored, and no new wait starts.
        assertNull(d.onReading(frame(83.4), 14_000))
        assertNull(d.onTick(40_000))
        // Socks off, stepping back on: the weight changes, then settles with the impedance.
        assertEquals(Event.Weighing(83.0, settled = false), d.onReading(frame(83.0, stable = false), 50_000))
        assertEquals(Event.Weighing(83.3, settled = true), d.onReading(frame(83.3), 51_000))
        assertEquals(Event.Done(83.3, 501), d.onReading(frame(83.3, impedance = 501), 53_000))
    }

    @Test
    fun withoutARetakeTheSameWeightSoonAfterIsIgnored() {
        val d = WeighInDetector(impedanceWaitMs = 12_000)
        d.onReading(frame(83.4), 0)
        d.onTick(12_000)
        assertNull(d.onReading(frame(83.0, stable = false), 50_000))
        assertNull(d.onReading(frame(83.3, impedance = 501), 53_000))
    }
}
