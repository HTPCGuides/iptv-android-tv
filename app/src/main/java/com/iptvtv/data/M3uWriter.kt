package com.iptvtv.data

import com.iptvtv.model.Channel

/**
 * Writes channels back out as an M3U playlist — what Share and Save hand to other apps.
 * Only what [M3uParser] keeps survives the round trip: name, URL, logo, group, tvg-id.
 */
object M3uWriter {
    fun write(channels: List<Channel>): String = buildString {
        append("#EXTM3U\n")
        channels.forEach { c ->
            append("#EXTINF:-1")
            c.tvgId?.takeIf { it.isNotBlank() }?.let { append(" tvg-id=\"").append(attr(it)).append('"') }
            c.logo?.takeIf { it.isNotBlank() }?.let { append(" tvg-logo=\"").append(attr(it)).append('"') }
            append(" group-title=\"").append(attr(c.group)).append("\",")
            append(c.name.replace('\n', ' ')).append('\n')
            append(c.url).append('\n')
        }
    }

    /** A double quote would end the attribute early; M3U has no escape for it. */
    private fun attr(v: String) = v.replace('"', '\'').replace('\n', ' ')

    /** "Ace Stream · Portugal · 5 Nov" → "ace-stream-portugal-5-nov.m3u8". */
    fun fileName(name: String): String =
        name.lowercase().replace(Regex("[^\\p{L}\\p{N}]+"), "-").trim('-').take(60).ifEmpty { "playlist" } + ".m3u8"
}
