package app.thermida.ui

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableDoubleStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.thermida.domain.MealType
import app.thermida.ui.components.NumberStepper
import app.thermida.ui.components.PillSelector
import app.thermida.ui.dashboard.QuickAddDialog
import app.thermida.ui.theme.ThermidaTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** The pieces people tap most, driven like a person would: no network, no database. */
@RunWith(AndroidJUnit4::class)
class ComponentsUiTest {
    @get:Rule
    val rule = createComposeRule()

    @Test
    fun pillSelectorSelectsWhatIsTapped() {
        var selected by mutableStateOf(MealType.BREAKFAST)
        rule.setContent { ThermidaTheme(dark = true) { PillSelector(MealType.entries, selected, { it.title }, { selected = it }) } }
        rule.onNodeWithText("Dinner").performClick()
        rule.runOnIdle { assertEquals(MealType.DINNER, selected) }
    }

    @Test
    fun numberStepperMovesByItsStep() {
        var weight by mutableDoubleStateOf(80.0)
        rule.setContent {
            ThermidaTheme(dark = true) { NumberStepper("Goal weight", weight, { weight = it }, 35.0..250.0, 0.5, "kg", decimals = 1) }
        }
        rule.onNodeWithContentDescription("Increase").performClick()
        rule.runOnIdle { assertEquals(80.5, weight, 0.0001) }
        rule.onNodeWithContentDescription("Decrease").performClick()
        rule.onNodeWithContentDescription("Decrease").performClick()
        rule.runOnIdle { assertEquals(79.5, weight, 0.0001) }
    }

    @Test
    fun addFoodByHand() {
        var logged: List<Any>? = null
        rule.setContent {
            ThermidaTheme(dark = true) {
                QuickAddDialog(
                    // No AI description here: it opens straight on typing it in.
                    title = "Add food · Yesterday",
                    onDismiss = {},
                    onLogManual = { name, kcal, protein, carbs, fat, type -> logged = listOf(name, kcal, protein, carbs, fat, type) },
                )
            }
        }
        rule.onNodeWithText("Add food · Yesterday").assertExists()
        // Nothing to log until there's a name and calories
        rule.onNodeWithText("Log it").performClick()
        rule.runOnIdle { assertEquals(null, logged) }
        rule.onNode(hasSetTextAction() and hasText("Food")).performTextInput("Homemade lasagna")
        rule.onNode(hasSetTextAction() and hasText("Calories (kcal)")).performTextInput("650")
        rule.onNode(hasSetTextAction() and hasText("Protein g")).performTextInput("32")
        rule.onNodeWithText("Dinner").performClick()
        rule.onNodeWithText("Log it").performClick()
        rule.runOnIdle { assertEquals(listOf("Homemade lasagna", 650.0, 32.0, 0.0, 0.0, MealType.DINNER), logged) }
    }
}