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
    /** True for the engine's own HTTP API, whatever host it is reached on. */
    fun isEngineUrl(url: String) = url.contains("/ace/", ignoreCase = true) && url.contains('?')

    /**
     * The shareable link behind an engine URL, or null for any other stream.
     *
     * Two kinds of identity reach the engine, and they are not interchangeable: a content
     * id (`id=` / `content_id=`) is an Ace Stream catalogue entry and is written
     * `acestream://<id>`; an `infohash=` is a torrent, and Ace Stream expects that as a
     * magnet link — given `acestream://<infohash>` it goes looking for a transport file
     * that doesn't exist and says so.
     */
    fun link(url: String): String? {
        if (!isEngineUrl(url)) return null
        val params = url.substringAfter('?').split('&').asSequence()
            .map { it.substringBefore('=') to it.substringAfter('=', "") }
            .filter { (_, v) -> v.isNotBlank() }
            .toMap()
        params["id"]?.let { return "acestream://$it" }
        params["content_id"]?.let { return "acestream://$it" }
        params["infohash"]?.let { return "magnet:?xt=urn:btih:$it" }
        return null
    }

    /** The HLS form of a getstream URL; anything else comes back unchanged. */
    fun hlsUrl(url: String): String =
        if (link(url) != null) url.replace(Regex("""/ace/getstream(?=\?)""", RegexOption.IGNORE_CASE), "/ace/manifest.m3u8")
        else url

    /** Whether [url] is a getstream URL that [hlsUrl] would rewrite. */
    fun isProgressive(url: String) = hlsUrl(url) != url
}
