package app.thermida.data

import app.thermida.data.db.FoodItemEntity
import app.thermida.data.db.MealEntity
import app.thermida.domain.UserProfile
import kotlinx.serialization.json.Json
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class BackupTest {
    private val json = Json { ignoreUnknownKeys = true }

    private val meal = MealEntity(id = 7, date = "2026-10-01", timestamp = 0, mealType = "LUNCH", name = "Salad")
    private val item = FoodItemEntity(mealId = 7, name = "Lettuce", grams = 100.0, kcalPer100 = 15.0, proteinPer100 = 1.0, carbsPer100 = 3.0, fatPer100 = 0.2)

    @Test
    fun anyOtherJsonFileIsRefusedInsteadOfErasingEverything() {
        // Unknown fields are ignored, so these all read as an empty backup.
        listOf("{}", """{"name":"something else","items":[]}""").forEach { text ->
            assertNotNull(json.decodeFromString(Backup.serializer(), text).problem())
        }
    }

    @Test
    fun realBackupsAreAccepted() {
        assertNull(Backup(meals = listOf(meal), items = listOf(item)).problem())
        // A phone with a profile but nothing logged yet still makes a valid backup.
        assertNull(Backup(profile = UserProfile()).problem())
    }

    @Test
    fun backupsFromANewerVersionAreRefused() {
        assertNotNull(Backup(version = Backup.VERSION + 1, meals = listOf(meal)).problem())
    }

    @Test
    fun foodsWithoutTheirMealAreRefused() {
        assertNotNull(Backup(meals = listOf(meal), items = listOf(item.copy(mealId = 8))).problem())
    }
}
