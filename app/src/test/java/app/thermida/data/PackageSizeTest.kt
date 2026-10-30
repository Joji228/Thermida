package app.thermida.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class PackageSizeTest {
    private fun grams(text: String) = PackageSize.grams(text)!!

    @Test
    fun weightsAndVolumesBecomeGrams() {
        assertEquals(500.0, grams("500 g"), 0.001)
        assertEquals(1500.0, grams("1,5 kg"), 0.001)
        assertEquals(330.0, grams("330 ml"), 0.001)
        assertEquals(330.0, grams("33 cl"), 0.001)
        assertEquals(1000.0, grams("1 L"), 0.001)
        assertEquals(250.0, grams("250g"), 0.001)
        assertEquals(99.225, grams("3.5 oz"), 0.001)
    }

    @Test
    fun greekLabelsWork() {
        assertEquals(250.0, grams("250 γρ"), 0.001)
        assertEquals(1000.0, grams("1 λίτρο"), 0.001)
        assertEquals(500.0, grams("0,5 κιλό"), 0.001)
    }

    @Test
    fun aMultipackIsTheWholePack() {
        assertEquals(1980.0, grams("6 x 330 ml"), 0.001)
        assertEquals(300.0, grams("2 × 150 g"), 0.001)
    }

    @Test
    fun textWithoutAKnownSizeGivesNothing() {
        assertNull(PackageSize.grams("12 eggs"))
        assertNull(PackageSize.grams("30% less sugar"))
        assertNull(PackageSize.grams("  "))
        assertNull(PackageSize.grams(null))
        assertNull(PackageSize.grams("24 x 1 L")) // 24 kg is no package of food
    }

    @Test
    fun aGramsUnitInsideAWordIsNotASize() {
        assertNull(PackageSize.grams("5 grapes"))
    }
}
