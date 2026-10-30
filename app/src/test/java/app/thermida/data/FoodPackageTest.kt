package app.thermida.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class FoodPackageTest {
    /** One search hit with the given extra fields added after the nutrition. */
    private fun hit(extra: String) =
        "{\"hits\":[{\"code\":\"1\",\"product_name\":\"Oreo\",\"brands\":\"Oreo\"," +
            "\"nutriments\":{\"energy-kcal_100g\":480,\"proteins_100g\":5,\"carbohydrates_100g\":70,\"fat_100g\":20}," +
            extra + "}]}"

    @Test
    fun packageAndServingWeightsAreBothRead() {
        val fact = FoodFactsParser.parse(hit("\"serving_quantity\":\"30\",\"product_quantity\":\"154\"")).single()
        assertEquals(154.0, fact.packageGrams!!, 0.001)
        assertEquals(30.0, fact.servingGrams!!, 0.001)
    }

    @Test
    fun aNumericPackageWeightIsRead() {
        val fact = FoodFactsParser.parse(hit("\"product_quantity\":500")).single()
        assertEquals(500.0, fact.packageGrams!!, 0.001)
    }

    @Test
    fun theSizePrintedOnTheLabelIsUsedWhenThereIsNoNumber() {
        val fact = FoodFactsParser.parse(hit("\"serving_quantity\":\"25\",\"quantity\":\"400 g\"")).single()
        assertEquals(400.0, fact.packageGrams!!, 0.001)
        assertEquals(25.0, fact.servingGrams!!, 0.001)
    }

    @Test
    fun theNumberBeatsTheLabelText() {
        val fact = FoodFactsParser.parse(hit("\"product_quantity\":\"154\",\"quantity\":\"400 g\"")).single()
        assertEquals(154.0, fact.packageGrams!!, 0.001)
    }

    @Test
    fun aMissingPackageWeightStaysEmpty() {
        assertNull(FoodFactsParser.parse(hit("\"serving_quantity\":\"30\"")).single().packageGrams)
    }
}
