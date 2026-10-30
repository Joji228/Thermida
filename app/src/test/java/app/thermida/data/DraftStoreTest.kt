package app.thermida.data

import app.thermida.MealDraft
import app.thermida.ai.AiItem
import app.thermida.ai.AiMeal
import app.thermida.ai.AnalysisResult
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.time.LocalDate

class DraftStoreTest {
    @get:Rule val temp = TemporaryFolder()

    @Test
    fun aScanSurvivesTheAppBeingClosed() {
        val photo = temp.newFile("capture_1.jpg")
        val result = AnalysisResult(
            meal = AiMeal(mealName = "Chicken salad", mealType = "lunch", items = listOf(AiItem(name = "Chicken", grams = 150.0, kcalPer100 = 165.0))),
            provider = Provider.DEEPSEEK,
            model = "deepseek-flash",
        )
        val draft = MealDraft(listOf(photo), "no dressing", result, LocalDate.parse("2026-10-24"))
        DraftStore(File(temp.root, "draft")).save(draft)

        // A new store, as after Android restarts the app.
        val loaded = DraftStore(File(temp.root, "draft")).load()
        assertEquals(draft.copy(photos = listOf(photo.absoluteFile)), loaded)
    }

    @Test
    fun missingPhotosAreLeftOutAndClearingDeletesIt() {
        val store = DraftStore(File(temp.root, "draft"))
        store.save(MealDraft(listOf(File(temp.root, "gone.jpg")), null, null, LocalDate.parse("2026-10-24")))
        assertEquals(emptyList<File>(), store.load()!!.photos)

        store.save(null)
        assertNull(store.load())
    }

    @Test
    fun noFileMeansNoDraft() {
        assertNull(DraftStore(File(temp.root, "nothing")).load())
    }
}
