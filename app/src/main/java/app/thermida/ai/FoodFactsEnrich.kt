package app.thermida.ai

import app.thermida.data.OpenFoodFacts
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.withTimeoutOrNull

/**
 * Checks the packaged products the AI recognized against Open Food Facts and uses the product's
 * own label values when a close match is found. Lookups run side by side, at most three, and the
 * whole step gives up after a few seconds so a slow network can't hold a scan or an estimate.
 */
suspend fun AnalysisResult.enrichFromFoodFacts(foodFacts: OpenFoodFacts): AnalysisResult {
    val products = meal.items.map { it.product.trim() }.filter { it.isNotEmpty() }.distinct().take(3)
    if (products.isEmpty()) return this
    val matches = withTimeoutOrNull(6_000) {
        coroutineScope {
            products.map { product -> async { product to runCatching { foodFacts.match(product) }.getOrNull() } }.awaitAll()
        }
    }?.toMap().orEmpty()
    val items = meal.items.map { item ->
        val fact = matches[item.product.trim()] ?: return@map item
        item.copy(
            grams = fact.packageGrams ?: item.grams,
            packageGrams = fact.packageGrams,
            kcalPer100 = fact.kcalPer100,
            proteinPer100 = fact.proteinPer100,
            carbsPer100 = fact.carbsPer100,
            fatPer100 = fact.fatPer100,
            foodFacts = fact.label,
        )
    }
    return copy(meal = meal.copy(items = items))
}
