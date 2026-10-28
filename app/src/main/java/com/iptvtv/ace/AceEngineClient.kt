package com.iptvtv.ace

import android.util.Log
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import kotlin.coroutines.coroutineContext

class EngineError(message: String, cause: Throwable? = null) : Exception(message, cause)

data class EngineInfo(val version: String, val platform: String, val code: Int)

/** A finished index fetch. On a mid-way failure the pages already read are kept, as the tool does. */
data class SearchOutcome(val channels: List<Channel>, val error: EngineError?)

/** The two calls m3u_gen_acestream makes to an engine (docs/ENGINE_API.md §1). [addr] is `host:port`. */
interface EngineClient {
    /** `get_version`. Up only when `result.code != 0` and `error` is null. */
    suspend fun info(addr: String): EngineInfo

    /** Every page of `/search`, from page 0, until a short page. [onPage] gets running totals. */
    suspend fun searchAll(addr: String, onPage: (page: Int, channels: Int, sources: Int) -> Unit): SearchOutcome
}

class HttpEngineClient(
    private val io: CoroutineDispatcher,
    private val connectTimeoutMs: Int = 5_000,
    // The tool allows 5 s for a whole request; a phone engine answering a 200-channel
    // page over Wi-Fi can take longer, and a timeout here silently truncates the index.
    private val readTimeoutMs: Int = 15_000,
) : EngineClient {

    override suspend fun info(addr: String): EngineInfo = withContext(io) {
        val body = get(addr, "/webui/api/service?method=get_version")
        val json = runCatching { JSONObject(body) }.getOrElse {
            throw EngineError("$addr answered, but not like an Ace Stream engine")
        }
        val result = json.optJSONObject("result")
        val code = result?.optInt("code") ?: 0
        if (code == 0 || !json.isNull("error")) {
            throw EngineError("Engine at $addr is not ready (${json.opt("error") ?: "no version"})")
        }
        EngineInfo(version = result!!.optString("version"), platform = result.optString("platform"), code = code)
    }

    override suspend fun searchAll(addr: String, onPage: (Int, Int, Int) -> Unit): SearchOutcome = withContext(io) {
        val all = ArrayList<Channel>()
        var page = 0
        // The tool has no upper bound; an engine that ignored `page` would loop forever.
        while (page < MAX_PAGES) {
            coroutineContext.ensureActive()
            val results = try {
                SearchJson.parsePage(get(addr, "/search?page_size=${EngineAddress.PAGE_SIZE}&page=$page"))
            } catch (e: EngineError) {
                return@withContext SearchOutcome(all, EngineError("Page $page: ${e.message}", e))
            } catch (e: Exception) {
                return@withContext SearchOutcome(all, EngineError("Page $page: unreadable engine response", e))
            }
            all += results
            onPage(page, all.size, all.sourceCount)
            if (results.size < EngineAddress.PAGE_SIZE) break
            page++
        }
        SearchOutcome(all, null)
    }

    private fun get(addr: String, pathAndQuery: String): String {
        val started = System.nanoTime()
        Log.i(TAG, "GET http://$addr$pathAndQuery")
        return try {
            request(addr, pathAndQuery).also {
                Log.i(TAG, "  ${it.length} chars in ${(System.nanoTime() - started) / 1_000_000} ms")
            }
        } catch (e: Exception) {
            Log.w(TAG, "  failed after ${(System.nanoTime() - started) / 1_000_000} ms: $e")
            throw e
        }
    }

    private fun request(addr: String, pathAndQuery: String): String {
        val conn = try {
            (URL("http://$addr$pathAndQuery").openConnection() as HttpURLConnection).apply {
                connectTimeout = connectTimeoutMs
                readTimeout = readTimeoutMs
                useCaches = false
            }
        } catch (e: Exception) {
            throw EngineError("\"$addr\" is not a usable engine address", e)
        }
        return try {
            // Like the tool, the status code is not checked: the body decides.
            val stream = if (conn.responseCode >= 400) conn.errorStream else conn.inputStream
            stream?.bufferedReader()?.use { it.readText() }.orEmpty()
        } catch (e: java.net.SocketTimeoutException) {
            throw EngineError("Engine at $addr timed out", e)
        } catch (e: Exception) {
            throw EngineError("Can't reach an engine at $addr", e)
        } finally {
            conn.disconnect()
        }
    }

    private companion object {
        const val TAG = "AceM3U.Engine"
        const val MAX_PAGES = 5_000
    }
}

/**
 * The tool's checker: alive when the link answers below HTTP 400 and the first read returns
 * bytes — and, with the analyzer, when those bytes hold an MPEG-TS sync point. Connect and
 * first read each get the timeout; the tool bounds both together.
 */
