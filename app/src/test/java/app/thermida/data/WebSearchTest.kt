package app.thermida.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class WebSearchTest {
    private val official = """
        {"results":[{"rerank_score":2.4,"title":"DeepSeek V4 model released","url":"https://example.com/deepseek-v4",
        "snippet":"DeepSeek V4 is now available.","summary":"DeepSeek has released the V4 model.","content":"",
        "site_name":"Example News","site_icon":"https://example.com/favicon.ico","date":"2026-06-01T00:00:00+00:00","authority":3}]}
    """.trimIndent()

    @Test
    fun readsTheOfficialAnswer() {
        val results = WebSearchParser.parse(official)
        assertEquals(1, results.size)
        val r = results[0]
        assertEquals("DeepSeek V4 model released", r.title)
        assertEquals("https://example.com/deepseek-v4", r.url)
        assertEquals("Example News", r.site)
        assertEquals("2026-06-01", r.date)
        assertEquals("DeepSeek has released the V4 model.", r.summary)
        assertEquals(2.4, r.score, 1e-9)
    }

    @Test
    fun anEmptyAnswerGivesNoResults() {
        assertTrue(WebSearchParser.parse("""{"results":[]}""").isEmpty())
    }

    @Test
    fun missingOptionalFieldsFallBackToTheAddress() {
        val body = """{"results":[{"url":"https://www.goodys.com/menu","title":"Menu","site_name":null,"date":null,"summary":"Classic burger 540 kcal"}]}"""
        val r = WebSearchParser.parse(body).single()
        assertEquals("goodys.com", r.site)
        assertNull(r.date)
        assertEquals(0.0, r.score, 1e-9)
    }

    @Test
    fun resultsWithoutAnAddressAreSkipped() {
        assertTrue(WebSearchParser.parse("""{"results":[{"title":"no link"}]}""").isEmpty())
    }

    @Test
    fun badAnswersNeverCrash() {
        assertTrue(WebSearchParser.parse("<html>502 Bad Gateway</html>").isEmpty())
        assertTrue(WebSearchParser.parse("").isEmpty())
    }

    @Test
    fun resultsAreSortedByScore() {
        val body = """{"results":[{"url":"https://a.com","rerank_score":0.5},{"url":"https://b.com","rerank_score":2.0}]}"""
        assertEquals(listOf("https://b.com", "https://a.com"), WebSearchParser.parse(body).map { it.url })
    }

    @Test
    fun failuresSayWhy() {
        assertEquals("Web search skipped (DeepSeek balance is empty)", WebSearchParser.failureNote(402))
        assertEquals("Web search skipped (DeepSeek rejected the key)", WebSearchParser.failureNote(401))
        assertEquals("Web search skipped (busy right now)", WebSearchParser.failureNote(429))
        assertEquals("Web search skipped (DeepSeek is busy)", WebSearchParser.failureNote(503))
    }

    @Test
    fun hostsLoseTheWww() {
        assertEquals("efood.gr", WebSearchParser.hostOf("https://www.efood.gr/menu"))
    }
}
