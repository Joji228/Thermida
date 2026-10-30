package app.thermida.data

/**
 * Reads a package size written the way labels write it ("500 g", "1,5 kg", "330 ml", "33 cl",
 * "6 x 330 ml", "250 γρ", "1 λίτρο") into grams. Millilitres count as grams, which is close enough for
 * drinks and sauces. Open Food Facts often has only this text for a product, not a number.
 */
object PackageSize {
    private const val UNITS = "kg|κιλ[όά]|mg|gr|γρ|g|ml|μλ|cl|dl|lt|λίτρ[οα]|l|oz|lb"
    private val multi = Regex("""(\d+)\s*[x×*]\s*(\d+(?:[.,]\d+)?)\s*($UNITS)\.?(?![\p{L}])""", RegexOption.IGNORE_CASE)
    private val single = Regex("""(\d+(?:[.,]\d+)?)\s*($UNITS)\.?(?![\p{L}])""", RegexOption.IGNORE_CASE)

    /** The whole package in grams, or null when the text has no size in a unit we know. */
    fun grams(text: String?): Double? {
        val t = text?.trim().orEmpty()
        if (t.isEmpty()) return null
        multi.find(t)?.let { m ->
            val each = toGrams(m.groupValues[2], m.groupValues[3]) ?: return null
            return (each * m.groupValues[1].toInt()).takeIf { it in 1.0..10_000.0 }
        }
        single.find(t)?.let { m -> return toGrams(m.groupValues[1], m.groupValues[2])?.takeIf { it in 1.0..10_000.0 } }
        return null
    }

    private fun toGrams(number: String, unit: String): Double? {
        val n = number.replace(',', '.').toDoubleOrNull() ?: return null
        val factor = when (unit.lowercase()) {
            "g", "gr", "γρ", "ml", "μλ" -> 1.0
            "kg", "κιλό", "κιλά", "l", "lt", "λίτρο", "λίτρα" -> 1000.0
            "mg" -> 0.001
            "cl" -> 10.0
            "dl" -> 100.0
            "oz" -> 28.35
            "lb" -> 453.6
            else -> return null
        }
        return n * factor
    }
}
