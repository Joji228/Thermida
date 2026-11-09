package app.thermida.data

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import okhttp3.Call
import okhttp3.Callback
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import java.io.IOException
import java.net.URI
import java.util.concurrent.TimeUnit

/** One web page found by DeepSeek's search: its title and address, and a summary written for an AI to read. */
data class WebResult(
    val title: String,
    val url: String,
    val site: String,
    val date: String?,
    val summary: String,
    /** DeepSeek's relevance score, 0 to 3; 1.5 and above is high quality. */
    val score: Double,
)

/** A failed search. [note] is the short reason shown to the user, e.g. "Web search skipped (DeepSeek balance is empty)". */
class WebSearchException(val code: Int?, val note: String) : Exception(note)

/**
 * DeepSeek's web search (POST /v1/tools/search), with the same key as the AI engine. It works with
 * any engine, as long as a DeepSeek key is saved. It costs $0.75 per 1,000 successful searches from
 * the DeepSeek balance; empty results are not charged.
 */
class WebSearch(client: OkHttpClient) {
    private val http = client.newBuilder()
        .connectTimeout(8, TimeUnit.SECONDS)
        .writeTimeout(8, TimeUnit.SECONDS)
        .readTimeout(8, TimeUnit.SECONDS)
        .followRedirects(false)
        .followSslRedirects(false)
        .build()
    private val jsonMedia = "application/json".toMediaType()

    /** Searches the web for [query]. [question] is what the user asked, which helps DeepSeek rank results. */
    suspend fun search(key: String, query: String, question: String? = null): List<WebResult> {
        if (key.isBlank()) throw WebSearchException(null, "Web search skipped (no DeepSeek key)")
        val body = buildJsonObject {
            put("query", query.take(128))
            put("count", 5)
            put("need_content", false)
            question?.trim()?.takeIf { it.isNotEmpty() }?.let { put("ori_question", it.take(128)) }
        }
        val request = Request.Builder()
            .url("https://api.deepseek.com/v1/tools/search")
            .header("Authorization", "Bearer $key")
            .post(body.toString().toRequestBody(jsonMedia))
            .build()
        return withContext(Dispatchers.IO) {
            val call = http.newCall(request)
            suspendCancellableCoroutine { cont ->
                cont.invokeOnCancellation { call.cancel() }
                call.enqueue(object : Callback {
                    override fun onFailure(call: Call, e: IOException) {
                        cont.resumeWith(Result.failure(WebSearchException(null, "Web search skipped (no connection or timeout)")))
                    }

                    override fun onResponse(call: Call, response: Response) {
                        response.use { r ->
                            val answer = runCatching { r.body.string() }.getOrNull()
                            when {
                                !r.isSuccessful -> cont.resumeWith(Result.failure(WebSearchException(r.code, WebSearchParser.failureNote(r.code))))
                                answer == null -> cont.resumeWith(Result.failure(WebSearchException(null, "Web search skipped (unreadable answer)")))
                                else -> cont.resumeWith(Result.success(WebSearchParser.parse(answer)))
                            }
                        }
                    }
                })
            }
        }
    }
}

/**
 * The same search, but a failure becomes [FoodSearch.Outcome.Failed] with the reason to show, so a
 * flaky network or an empty balance never stops an estimate or a coach answer.
 */
suspend fun WebSearch.outcome(key: String, query: String, question: String? = null): FoodSearch.Outcome =
    try {
        FoodSearch.Outcome.Found(search(key, query, question))
    } catch (e: CancellationException) {
        throw e
    } catch (e: WebSearchException) {
        FoodSearch.Outcome.Failed(e.note)
    } catch (e: Exception) {
        FoodSearch.Outcome.Failed("Web search skipped (${e.message ?: "error"})")
    }

/** Reads DeepSeek's search answer. Kept free of networking so it can be unit tested. */
object WebSearchParser {
    private val json = Json { ignoreUnknownKeys = true; isLenient = true }

    /** The results, best first. A bad or empty answer gives an empty list, never a crash. */
    fun parse(body: String): List<WebResult> {
        val root = runCatching { json.parseToJsonElement(body).jsonObject }.getOrNull() ?: return emptyList()
        return root["results"]?.jsonArray.orEmpty().mapNotNull { element ->
            val item = element.jsonObject
            val url = item.str("url") ?: return@mapNotNull null
            WebResult(
                title = item.str("title").orEmpty(),
                url = url,
                site = item.str("site_name") ?: hostOf(url),
                date = item.str("date")?.take(10),
                summary = item.str("summary") ?: item.str("snippet").orEmpty(),
                score = (item["rerank_score"] as? JsonPrimitive)?.doubleOrNull ?: 0.0,
            )
        }.sortedByDescending { it.score }
    }

    /** What the user sees when a search doesn't work, from the HTTP status. */
    fun failureNote(code: Int): String = when (code) {
        401, 403 -> "Web search skipped (DeepSeek rejected the key)"
        402 -> "Web search skipped (DeepSeek balance is empty)"
        429 -> "Web search skipped (busy right now)"
        in 500..599 -> "Web search skipped (DeepSeek is busy)"
        else -> "Web search skipped (HTTP $code)"
    }

    /** The site a page belongs to, from its address, without "www.". */
    fun hostOf(url: String): String =
        runCatching { URI(url).host?.removePrefix("www.").orEmpty() }.getOrDefault("")

    private fun JsonObject.str(key: String): String? =
        (this[key] as? JsonPrimitive)?.takeUnless { it is JsonNull }?.contentOrNull?.takeIf { it.isNotBlank() }
}
