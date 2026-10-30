package app.thermida.data

import app.thermida.domain.Macros
import app.thermida.domain.Targets
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.LocalDateTime

class CoachFollowUpsTest {
    @Test
    fun suggestionsAreTakenOutOfTheText() {
        val raw = "You're on track: **1,650 kcal** so far.\n<suggestions>[\"What about dinner?\", \"Is my protein OK?\"]</suggestions>"
        val parsed = CoachFollowUps.parse(raw)
        assertEquals("You're on track: **1,650 kcal** so far.", parsed.text)
        assertEquals(listOf("What about dinner?", "Is my protein OK?"), parsed.suggestions)
    }

    @Test
    fun worksAfterAnActionsBlockAndKeepsIt() {
        val raw = "Here's your lunch.\n<actions>[{\"type\":\"add_water\",\"ml\":500}]</actions>\n<suggestions>[\"Log a snack too\"]</suggestions>"
        val followUps = CoachFollowUps.parse(raw)
        assertEquals(listOf("Log a snack too"), followUps.suggestions)
        val actions = CoachActions.parse(followUps.text, LocalDate.parse("2026-10-24"))
        assertEquals(1, actions.actions.size)
        assertEquals("Here's your lunch.", actions.text)
    }

    @Test
    fun noBlockMeansNoSuggestions() {
        val parsed = CoachFollowUps.parse("I can only help with your health, fitness and Thermida data.")
        assertTrue(parsed.suggestions.isEmpty())
        assertEquals("I can only help with your health, fitness and Thermida data.", parsed.text)
    }

    @Test
    fun atMostThreeShortDistinctOnes() {
        val long = "x".repeat(CoachFollowUps.MAX_CHARS + 1)
        val raw = "Ok\n<suggestions>[\"A b\", \"a B\", \"$long\", \"Two\", \"Three\", \"Four\"]</suggestions>"
        assertEquals(listOf("A b", "Two", "Three"), CoachFollowUps.parse(raw).suggestions)
    }

    @Test
    fun oneQuestionPerLineAndAMissingCloseTagStillWork() {
        val parsed = CoachFollowUps.parse("Ok\n<suggestions>\n- How about tomorrow?\n- What should I drink?")
        assertEquals(listOf("How about tomorrow?", "What should I drink?"), parsed.suggestions)
        assertEquals("Ok", parsed.text)
    }

    private val targets = Targets(kcal = 2000, protein = 150, carbs = 200, fat = 60, bmr = 1700, tdee = 2500, dailyDelta = -500, weeksToGoal = 20.0, waterMl = 3000, bmi = 27.0)

    @Test
    fun startersFollowTheDay() {
        val morning = LocalDateTime.parse("2026-10-24T08:00")
        val recent = morning.toLocalDate().minusDays(1)
        assertEquals("What's a good breakfast for my goal?", CoachStarters.pick(morning, targets, Macros(), recent).first())

        val afternoon = LocalDateTime.parse("2026-10-24T16:00")
        assertEquals("How can I hit my protein today?", CoachStarters.pick(afternoon, targets, Macros(kcal = 900.0, protein = 40.0), recent).first())
        assertEquals("I went over today. What now?", CoachStarters.pick(afternoon, targets, Macros(kcal = 2300.0, protein = 160.0), recent).first())

        val stale = CoachStarters.pick(afternoon, targets, Macros(kcal = 900.0, protein = 100.0), morning.toLocalDate().minusDays(10))
        assertTrue("How's my weight trending?" in stale)
    }

    @Test
    fun alwaysThreeAndNoWeighInQuestionWithoutWeighIns() {
        val picks = CoachStarters.pick(LocalDateTime.parse("2026-10-24T12:00"), null, Macros(kcal = 500.0), null)
        assertEquals(3, picks.size)
        assertTrue(picks.none { "last weigh-in" in it })
        assertEquals(CoachStarters.DEFAULTS, CoachStarters.pick(LocalDateTime.parse("2026-10-24T12:00"), targets, Macros(kcal = 900.0, protein = 120.0), LocalDate.parse("2026-10-23")))
    }
}
