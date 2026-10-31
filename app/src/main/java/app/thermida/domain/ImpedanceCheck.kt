package app.thermida.domain

import kotlin.math.abs

/**
 * Spots a scale reading whose impedance is far from your usual one. Your own impedance barely moves
 * from one day to the next, so a big jump usually means something got between your feet and the
 * scale (thin or damp socks, lotion) or a shower or workout just before, and body fat will be off.
 */
object ImpedanceCheck {
    /** How far from the usual impedance, as a share of it, still counts as normal. */
    const val TOLERANCE = 0.20

    /** Readings with impedance needed before there's a "usual" to compare with. */
    const val MIN_HISTORY = 3

    /** How many of the latest readings make up the usual. */
    private const val HISTORY = 10

    /**
     * Whether [impedance] is unusual next to [past], the earlier readings oldest first (null for a
     * weight-only one). False until there are enough readings to know what's usual.
     */
    fun unusual(impedance: Int, past: List<Int?>): Boolean {
        val recent = past.filterNotNull().takeLast(HISTORY)
        if (recent.size < MIN_HISTORY) return false
        val usual = median(recent)
        return abs(impedance - usual) / usual > TOLERANCE
    }

    private fun median(values: List<Int>): Double {
        val sorted = values.sorted()
        val mid = sorted.size / 2
        return if (sorted.size % 2 == 1) sorted[mid].toDouble() else (sorted[mid - 1] + sorted[mid]) / 2.0
    }
}
