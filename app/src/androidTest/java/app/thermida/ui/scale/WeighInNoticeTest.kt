package app.thermida.ui.scale

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.thermida.ui.theme.ThermidaTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** The alert after a weigh-in without body composition (not barefoot) or with an unusual one. */
@RunWith(AndroidJUnit4::class)
class WeighInNoticeTest {
    @get:Rule
    val rule = createComposeRule()

    @Test
    fun notBarefootSaysWhyAndOffersAnotherGo() {
        var again = 0
        var kept = 0
        rule.setContent {
            ThermidaTheme(dark = true) {
                WeighInNoticeDialog(Notice.NotBarefoot(id = 1, kg = 84.2), onWeighAgain = { again++ }, onKeep = { kept++ })
            }
        }
        rule.onNodeWithText("Body composition not measured").assertIsDisplayed()
        rule.onNode(hasText("84.2 kg", substring = true)).assertIsDisplayed()
        rule.onNode(hasText("socks, shoes or very dry feet", substring = true)).assertIsDisplayed()
        rule.onNodeWithText("Weigh again").performClick()
        rule.onNodeWithText("Keep weight only").performClick()
        rule.runOnIdle { assertEquals(1 to 1, again to kept) }
    }

    @Test
    fun anUnusualReadingCanBeKeptOrTakenAgain() {
        var again = 0
        var kept = 0
        rule.setContent {
            ThermidaTheme(dark = true) {
                WeighInNoticeDialog(Notice.Unusual(id = 2, kg = 84.0), onWeighAgain = { again++ }, onKeep = { kept++ })
            }
        }
        rule.onNodeWithText("This reading looks unusual").assertIsDisplayed()
        rule.onNode(hasText("Damp socks, lotion", substring = true)).assertIsDisplayed()
        rule.onNodeWithText("Keep it").performClick()
        rule.onNodeWithText("Weigh again").performClick()
        rule.runOnIdle { assertEquals(1 to 1, again to kept) }
    }
}
