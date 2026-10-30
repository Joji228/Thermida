package app.thermida.ui

import android.util.Log
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeDown
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.thermida.data.CoachMessage
import app.thermida.ui.coach.CoachContent
import app.thermida.ui.theme.ThermidaTheme
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** A long answer typing itself out keeps its newest line in view, without jumping back. */
@RunWith(AndroidJUnit4::class)
class CoachScrollTest {
    @get:Rule
    val rule = createComposeRule()

    private val list = SemanticsMatcher.keyIsDefined(SemanticsProperties.VerticalScrollAxisRange)

    @Test
    fun aLongAnswerIsFollowedWhileItTypes() {
        val answer = (1..40).joinToString("\n") { "Line $it of a long answer about your protein, carbs and weight trend." }
        val question = CoachMessage(true, "Tell me everything", at = 1_000)
        val reply = CoachMessage(false, answer, at = 2_000)
        var messages by mutableStateOf(listOf(question))
        var thinking by mutableStateOf(true)
        var typingAt by mutableStateOf<Long?>(null)
        rule.setContent {
            ThermidaTheme(dark = true) {
                CoachContent(
                    messages,
                    thinking,
                    error = null,
                    loaded = true,
                    onSend = {},
                    onRetry = {},
                    onNewChat = {},
                    onBack = {},
                    typingAt = typingAt,
                    onTyped = { typingAt = null },
                )
            }
        }
        rule.waitForIdle()
        val listBottom = rule.onNode(list).fetchSemanticsNode().boundsInRoot.bottom
        rule.mainClock.autoAdvance = false
        // The answer arrives in place of "Thinking…" and starts typing out.
        messages = listOf(question, reply)
        thinking = false
        typingAt = reply.at

        // Where the answer's text ends on screen, frame by frame, against the bottom of the chat.
        val trace = mutableListOf<Float>()
        repeat(150) {
            rule.mainClock.advanceTimeByFrame()
            val text = rule.onAllNodes(hasText("Line 1 of", substring = true)).fetchSemanticsNodes().firstOrNull()
            if (text != null) trace += text.boundsInRoot.bottom
        }
        rule.mainClock.autoAdvance = true
        rule.waitForIdle()
        val end = rule.onNode(hasText("Line 40 of", substring = true)).fetchSemanticsNode().boundsInRoot.bottom
        trace.forEachIndexed { i, b -> Log.i("CoachScroll", "frame $i textBottom=$b listBottom=$listBottom") }
        Log.i("CoachScroll", "end textBottom=$end listBottom=$listBottom")

        // Bounds are cut off at the edge of the chat, so text running off below ends exactly at its
        // bottom; text that's followed ends above it, by the list's bottom padding.
        val reached = trace.indexOfFirst { it > listBottom - 80f }
        assertTrue("the answer never got longer than the screen", reached >= 0)
        val runOff = trace.drop(reached).count { it >= listBottom - 1f }
        assertTrue("the newest line was below the chat in $runOff frames", runOff <= 2)
        assertTrue("didn't end with the last line in view: text ends at $end, chat at $listBottom", end < listBottom - 1f)
    }

    @Test
    fun aFingerOnTheChatStopsTheFollowing() {
        val answer = (1..60).joinToString("\n") { "Line $it of a long answer about your protein, carbs and weight trend." }
        val question = CoachMessage(true, "Tell me everything", at = 1_000)
        val reply = CoachMessage(false, answer, at = 2_000)
        var typingAt by mutableStateOf<Long?>(null)
        rule.setContent {
            ThermidaTheme(dark = true) {
                CoachContent(listOf(question, reply), thinking = false, error = null, loaded = true, onSend = {}, onRetry = {}, onNewChat = {}, onBack = {}, typingAt = typingAt, onTyped = { typingAt = null })
            }
        }
        rule.waitForIdle()
        val listBottom = rule.onNode(list).fetchSemanticsNode().boundsInRoot.bottom
        rule.mainClock.autoAdvance = false
        typingAt = reply.at
        repeat(40) { rule.mainClock.advanceTimeByFrame() }
        // Scroll back up to reread while it's still typing.
        rule.onNode(list).performTouchInput { swipeDown() }
        rule.mainClock.autoAdvance = true
        rule.waitForIdle()
        val end = rule.onNode(hasText("Line 1 of", substring = true)).fetchSemanticsNode().boundsInRoot.bottom
        Log.i("CoachScroll", "after drag textBottom=$end listBottom=$listBottom")
        // Not followed any more: the rest of the answer runs off below the chat.
        assertTrue("kept following after the finger: text ends at $end, chat at $listBottom", end >= listBottom - 1f)
    }

    @Test
    fun theKeyboardDoesNotCoverTheLatestAnswer() {
        val answer = (1..40).joinToString("\n") { "Line $it of a long answer about your protein, carbs and weight trend." }
        val question = CoachMessage(true, "Tell me everything", at = 1_000)
        val reply = CoachMessage(false, answer, at = 2_000)
        var typingAt by mutableStateOf<Long?>(reply.at)
        // The keyboard, as the space it takes from the bottom of the chat.
        var keyboard by mutableStateOf(0.dp)
        rule.setContent {
            ThermidaTheme(dark = true) {
                Box(Modifier.fillMaxSize().padding(bottom = keyboard)) {
                    CoachContent(listOf(question, reply), thinking = false, error = null, loaded = true, onSend = {}, onRetry = {}, onNewChat = {}, onBack = {}, typingAt = typingAt, onTyped = { typingAt = null })
                }
            }
        }
        // The answer types out and the chat follows it to its last line.
        rule.waitForIdle()
        rule.waitUntil(5_000) { typingAt == null }
        rule.waitForIdle()
        keyboard = 300.dp
        rule.waitForIdle()
        val listBottom = rule.onNode(list).fetchSemanticsNode().boundsInRoot.bottom
        val end = rule.onNode(hasText("Line 1 of", substring = true)).fetchSemanticsNode().boundsInRoot.bottom
        Log.i("CoachScroll", "keyboard up: textBottom=$end listBottom=$listBottom")
        assertTrue("the keyboard covered the end of the answer: text ends at $end, chat at $listBottom", end < listBottom - 1f)
    }
}
