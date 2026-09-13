package com.iptvtv.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.net.URL

/** Where playlist text comes from. An interface so the repository can be tested without a network. */
fun interface PlaylistFetcher {
    /** The playlist at [url]. Throws on any failure; the caller decides what a failure means. */
    suspend fun fetch(url: String): String
}

class HttpPlaylistFetcher : PlaylistFetcher {
    override suspend fun fetch(url: String): String = withContext(Dispatchers.IO) {
        val conn = (URL(url).openConnection() as HttpURLConnection).apply {
            connectTimeout = 15_000
            readTimeout = 20_000
            instanceFollowRedirects = true
            setRequestProperty("User-Agent", "IPTVtv/1.0")
        }
        try {
            if (conn.responseCode !in 200..299) error("HTTP ${conn.responseCode}")
            conn.inputStream.bufferedReader().use { it.readText() }
        } finally {
            conn.disconnect()
        }
    }
}
