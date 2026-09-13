package com.iptvtv.data

import com.iptvtv.model.Channel

/** Parses the M3U/M3U8 playlist format (#EXTINF attributes + the URL on the following line). */
object M3uParser {
    private val ATTR = Regex("""([A-Za-z0-9-]+)="([^"]*)"""")

    fun parse(text: String, sourceId: String): List<Channel> {
        val out = ArrayList<Channel>()
        var pending: Channel? = null
        text.lineSequence().forEach { raw ->
            val line = raw.trim()
            when {
                line.startsWith("#EXTINF", true) -> {
                    // The title comma must come after the last quoted attribute — attribute
                    // values themselves may contain commas (group-title="News, Politics").
                    val comma = line.indexOf(',', startIndex = line.lastIndexOf('"') + 1)
                    val head = if (comma >= 0) line.substring(0, comma) else line
                    val title = if (comma >= 0) line.substring(comma + 1).trim() else ""
                    val attrs = ATTR.findAll(head).associate { it.groupValues[1].lowercase() to it.groupValues[2] }
                    pending = Channel(
                        name = title.ifBlank { attrs["tvg-name"] ?: "Unnamed" },
                        url = "",
                        logo = attrs["tvg-logo"]?.takeIf { it.isNotBlank() },
                        group = attrs["group-title"]?.takeIf { it.isNotBlank() } ?: "Other",
                        tvgId = attrs["tvg-id"]?.takeIf { it.isNotBlank() },
                        sourceId = sourceId,
                    )
                }
                line.isEmpty() || line.startsWith("#") -> Unit   // other directives ignored
                else -> {
                    pending?.let { out.add(it.copy(url = line)) }
                    pending = null
                }
            }
        }
        return out
    }
}
