package com.iptvtv.data

/*
 * Ace Stream's engine serves a channel over its local HTTP API in two shapes:
 *
 *  - /ace/getstream?infohash=…   one endless MPEG-TS body. The engine ignores Range, and
 *    closes the body whenever its session changes, so a player that resumes by byte
 *    offset (Media3's default) ends up hunting through a fresh stream, and one that trusts
 *    end-of-stream sits on a frozen frame.
 *  - /ace/manifest.m3u8?infohash=…   the same channel as live HLS: small segments that can
 *    each be fetched again, a playlist that says where live is. Stalls become ordinary
 *    HLS buffering and a dropped connection is retried per segment.
 *
 * Playlists from Ace M3U carry the first form. The player plays the second.
 */
object AceStream {
    private val ID_KEYS = setOf("id", "infohash", "content_id")

    /** True for the engine's own HTTP API, whatever host it is reached on. */
    fun isEngineUrl(url: String) = url.contains("/ace/", ignoreCase = true) && url.contains('?')

    /** The acestream://<id> link behind an engine URL, or null for any other stream. */
    fun link(url: String): String? {
        if (!isEngineUrl(url)) return null
        val id = url.substringAfter('?').split('&').asSequence()
            .map { it.substringBefore('=') to it.substringAfter('=', "") }
            .firstOrNull { (k, v) -> k in ID_KEYS && v.isNotBlank() }
            ?.second ?: return null
        return "acestream://$id"
    }

    /** The HLS form of a getstream URL; anything else comes back unchanged. */
    fun hlsUrl(url: String): String =
        if (link(url) != null) url.replace(Regex("""/ace/getstream(?=\?)""", RegexOption.IGNORE_CASE), "/ace/manifest.m3u8")
        else url

    /** Whether [url] is a getstream URL that [hlsUrl] would rewrite. */
    fun isProgressive(url: String) = hlsUrl(url) != url
}
