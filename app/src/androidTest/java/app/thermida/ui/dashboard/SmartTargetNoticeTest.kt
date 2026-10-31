package app.thermida.ui.dashboard

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.thermida.domain.SmartSchedule
import app.thermida.ui.theme.ThermidaTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.time.LocalDate

/** The Diary card that says the smart target changed the calorie target. */
@RunWith(AndroidJUnit4::class)
class SmartTargetNoticeTest {
    @get:Rule
    val rule = createComposeRule()

    @Test
    fun saysWhatChangedAndWhyThenGoesAway() {
        var dismissed = 0
        rule.setContent {
            ThermidaTheme(dark = true) {
                SmartTargetNotice(SmartSchedule.Change(LocalDate.of(2026, 10, 31), 2290, 2210), onDismiss = { dismissed++ })
            }
        }
        rule.onNodeWithText("Smart target updated").assertIsDisplayed()
        rule.onNode(hasText("2,290 → 2,210 kcal a day", substring = true)).assertIsDisplayed()
        rule.onNode(hasText("burn a little less than estimated", substring = true)).assertIsDisplayed()
        rule.onNodeWithText("Got it").performClick()
        rule.runOnIdle { assertEquals(1, dismissed) }
    }

    @Test
    fun aRiseSaysTheTargetWentUp() {
        rule.setContent {
            ThermidaTheme(dark = true) {
                SmartTargetNotice(SmartSchedule.Change(LocalDate.of(2026, 10, 31), 2210, 2290), onDismiss = {})
            }
        }
        rule.onNode(hasText("so your target went up", substring = true)).assertIsDisplayed()
    }
}
