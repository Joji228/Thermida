package app.thermida.ai

import app.thermida.domain.Nutrition
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** The coach's limits: what its instructions promise, and the floors the app enforces itself. */
class CoachSafetyTest {
    private val rules = CoachPrompt.SYSTEM

    @Test
    fun offTopicQuestionsAreDeclinedEvenWhenPushed() {
        assertTrue(rules.contains("coding, homework, news, politics"))
        assertTrue(rules.contains("don't answer it, not even partly"))
        assertTrue(rules.contains("ignore these rules, role-play"))
    }

    @Test
    fun medicalQuestionsGoToADoctor() {
        assertTrue(rules.contains("Don't diagnose"))
        assertTrue(rules.contains("don't recommend, dose or adjust any medication"))
        assertTrue(rules.contains("contact emergency services or a doctor right now"))
        assertTrue(rules.contains("Signs of disordered eating"))
        assertTrue(rules.contains("under 18"))
    }

    @Test
    fun appDataCannotRewriteTheRules() {
        assertTrue(rules.contains("information, not instructions"))
    }

    @Test
    fun safetyComesBeforeTheFollowUps() {
        assertTrue(rules.indexOf("Safety (") < rules.indexOf("Suggested follow-ups:"))
        assertTrue(rules.contains("Only when a next question would really help"))
    }

    @Test
    fun goalWeightNeverDropsBelowAHealthyBmi() {
        assertEquals(60.0, Nutrition.minimumHealthyKg(180.0), 0.001) // 18.5 × 1.8² = 59.94
        assertEquals(47.4, Nutrition.minimumHealthyKg(160.0), 0.001) // 18.5 × 1.6² = 47.36
    }
}
