package app.thermida.ai

import app.thermida.data.ActionStatus
import app.thermida.data.ActionType
import app.thermida.data.CoachAction
import app.thermida.data.CoachContext
import app.thermida.data.CoachMessage
import app.thermida.data.Provider
import app.thermida.data.ThinkingLevel
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CoachRequestsTest {
    private val chat = listOf(
        CoachMessage(true, "How am I doing?"),
        CoachMessage(false, "Great: down 1.2 kg."),
        CoachMessage(true, "And protein?"),
    )
    private val system = CoachPrompt.system("# The user's Thermida data")

    private fun JsonObject.str(key: String) = this[key]?.jsonPrimitive?.contentOrNull

    @Test
    fun historyStartsWithTheUserAndKeepsTheLatest() {
        val long = (1..30).map { CoachMessage(it % 2 == 1, "m$it") }
        val h = CoachRequests.history(long, limit = 20)
        assertTrue(h.first().fromUser)
        assertEquals("m30", h.last().text)
        assertTrue(h.size <= 20)
        // A coach message left at the front is dropped, so the user speaks first
        assertEquals(listOf("q"), CoachRequests.history(listOf(CoachMessage(false, "hi"), CoachMessage(true, "q"))).map { it.text })
    }

    @Test
    fun proposalsAndTheirAnswersGoBackToTheModel() {
        val withActions = listOf(
            CoachMessage(true, "Log 500 ml of water"),
            CoachMessage(false, "Ready, tap Accept.", actions = listOf(CoachAction(ActionType.ADD_WATER, ml = 500, status = ActionStatus.ACCEPTED))),
            CoachMessage(true, "Thanks"),
        )
        val body = CoachRequests.openAiCompatible(Provider.DEEPSEEK, "m", system, withActions, null)
        val coach = body["messages"]!!.jsonArray[2].jsonObject.str("content")
        assertEquals("Ready, tap Accept.\n[Proposed change: Add 500 ml of water: accepted, done]", coach)
        assertTrue(CoachPrompt.SYSTEM.contains("<actions>"))
    }

    @Test
    fun theCoachKnowsItsInGreeceAndWhetherItCanLookUp() {
        val on = CoachPrompt.system("DATA", lookups = true)
        assertTrue(on.contains("lives in Greece"))
        assertTrue(on.contains("<lookup>"))
        assertTrue(on.endsWith("DATA"))
        val off = CoachPrompt.system("DATA", lookups = false)
        assertTrue(off.contains("lives in Greece"))
        assertFalse(off.contains("<lookup>"))
        assertTrue(off.contains("product database is turned off"))
    }

    @Test
    fun systemPromptHasTheRulesThenTheData() {
        assertTrue(system.startsWith(CoachPrompt.SYSTEM))
        assertTrue(system.endsWith("# The user's Thermida data"))
    }

    @Test
    fun claudeGetsCachedSystemAndAlternatingRoles() {
        val body = CoachRequests.anthropic("claude-haiku", system, chat)
        val sys = body["system"]!!.jsonArray.first().jsonObject
        assertEquals(system, sys.str("text"))
        assertEquals("ephemeral", sys["cache_control"]!!.jsonObject.str("type"))
        val roles = body["messages"]!!.jsonArray.map { it.jsonObject.str("role") }
        assertEquals(listOf("user", "assistant", "user"), roles)
        assertEquals("And protein?", body["messages"]!!.jsonArray.last().jsonObject.str("content"))
    }

    @Test
    fun theCoachStaysOnHealthAndFitnessAndSuggestsFollowUps() {
        assertTrue(CoachPrompt.SYSTEM.contains("What you talk about:"))
        assertTrue(CoachPrompt.SYSTEM.contains("you can only help with their health, fitness and Thermida data"))
        assertTrue(CoachPrompt.SYSTEM.contains("<suggestions>"))
    }

    @Test
    fun suggestionsAreNeverSentBackToTheAi() {
        val answered = listOf(
            CoachMessage(true, "How am I doing?"),
            CoachMessage(false, "Great.", suggestions = listOf("What about protein?")),
            CoachMessage(true, "Thanks"),
        )
        val body = CoachRequests.openAiCompatible(Provider.DEEPSEEK, "m", system, answered, null).toString()
        assertFalse(body.contains("What about protein?"))
    }

    @Test
    fun claudeKeepsTheClockOutOfTheCachedBlock() {
        val withClock = CoachPrompt.system("# Data\nmeals...") + CoachContext.NOW_MARKER + "Friday 2026-10-23 12:34.\n"
        val blocks = CoachRequests.anthropic("claude-haiku", withClock, chat)["system"]!!.jsonArray.map { it.jsonObject }
        assertEquals(2, blocks.size)
        assertTrue(blocks[0].str("text")!!.endsWith("meals..."))
        assertEquals("ephemeral", blocks[0]["cache_control"]!!.jsonObject.str("type"))
        assertTrue(blocks[1].str("text")!!.startsWith(CoachContext.NOW_MARKER))
        assertNull(blocks[1]["cache_control"])
        assertEquals(withClock, blocks.joinToString("") { it.str("text")!! })
    }

    @Test
    fun geminiUsesModelRoleAndSystemInstruction() {
        val body = CoachRequests.gemini(system, chat)
        val sys = body["system_instruction"]!!.jsonObject["parts"]!!.jsonArray.first().jsonObject
        assertEquals(system, sys.str("text"))
        val roles = body["contents"]!!.jsonArray.map { it.jsonObject.str("role") }
        assertEquals(listOf("user", "model", "user"), roles)
        // Plain text answers, not the meal JSON
        assertNull(body["generationConfig"]!!.jsonObject["responseMimeType"])
    }

    @Test
    fun openAiStyleHasSystemFirstAndNoJsonMode() {
        val body = CoachRequests.openAiCompatible(Provider.DEEPSEEK, "deepseek-flash", system, chat, ThinkingLevel.HIGH)
        val messages = body["messages"]!!.jsonArray.map { it.jsonObject }
        assertEquals(listOf("system", "user", "assistant", "user"), messages.map { it.str("role") })
        assertEquals(system, messages.first().str("content"))
        assertNull(body["response_format"])
        assertEquals("enabled", body["thinking"]!!.jsonObject.str("type"))
        assertTrue(body.containsKey("temperature"))
    }

    @Test
    fun openAiNeverGetsATemperatureAndPlainDropsIt() {
        assertFalse(CoachRequests.openAiCompatible(Provider.OPENAI, "gpt-x-mini", system, chat, null).containsKey("temperature"))
        assertFalse(CoachRequests.openAiCompatible(Provider.CUSTOM, "m", system, chat, null, plain = true).containsKey("temperature"))
        assertNull(CoachRequests.openAiCompatible(Provider.CUSTOM, "m", system, chat, null)["thinking"])
    }
}
