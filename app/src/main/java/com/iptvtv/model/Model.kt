package com.iptvtv.model

/*
 * Domain models: pure Kotlin, no Android or Room in sight. The database entities
 * and the screens both map to and from these.
 */

/** One channel from an M3U playlist. */
data class Channel(
    val name: String,
    val url: String,
    val logo: String? = null,
    val group: String = "Other",
    val tvgId: String? = null,
    val sourceId: String = "",
) {
    // iptv-org annotates the display name, e.g. "BBC One (720p) [Geo-blocked]"
    val geoBlocked get() = name.contains("[Geo-blocked]", true)
    val partTime get() = name.contains("[Not 24/7]", true)
    /** Name without the quality/status annotations, for display. */
    val clean: String get() = cleanName(name)
    val quality: String? get() = Regex("""\((\d+p)\)""").find(name)?.groupValues?.get(1)
    /** Identity of this feed: the stream it plays. Recents are keyed by it. */
    val key get() = "$sourceId|$url"
    /**
     * Identity for favouriting. Playlists carry the same channel several times as
     * separate feeds — iptv-org's sports list has two entries both called
     * "Red Bull TV (1080p)" on different CDNs — and to a viewer those are one
     * channel, so the name is what a favourite is pinned to, not the stream URL.
     */
    val favKey get() = "$sourceId|${cleanKey(name)}"
    /** iptv-org packs several groups into one attribute ("Comedy;Movies;Sports"). */
    val groups: List<String> get() = splitGroups(group)

    companion object {
        fun cleanName(name: String): String =
            name.replace(Regex("""\s*\[(Geo-blocked|Not 24/7)]""", RegexOption.IGNORE_CASE), "")
                .replace(Regex("""\s*\(\d+p\)( \(HEVC\))?"""), "").trim()

        /** The part of [favKey] after the source: what the favourites table stores. */
        fun cleanKey(name: String): String = cleanName(name).lowercase()

        fun splitGroups(group: String): List<String> =
            group.split(';').map { it.trim() }.filter { it.isNotEmpty() }.ifEmpty { listOf("Other") }
    }
}

enum class SourceKind {
    /** Shipped with the app; refreshed from iptv-org. */
    BUILT_IN,
    /** A playlist URL the user pasted in. */
    URL,
    /** A file another app handed over. There is nothing to refresh from: the stored channels are the playlist. */
    FILE,
}

/** A playlist source — the four built-ins, plus anything the user adds or another app hands over. */
data class Source(
    val id: String,
    val name: String,
    /** The playlist address for [SourceKind.BUILT_IN] and [SourceKind.URL]; the file's name for [SourceKind.FILE]. */
    val url: String,
    val kind: SourceKind,
    val emoji: String = "📺",
) {
    val builtIn get() = kind == SourceKind.BUILT_IN
    val isFile get() = kind == SourceKind.FILE

    companion object {
        val BUILT_IN = listOf(
            Source("uk", "United Kingdom", "https://iptv-org.github.io/iptv/countries/uk.m3u", SourceKind.BUILT_IN, "🇬🇧"),
            Source("dk", "Denmark", "https://iptv-org.github.io/iptv/countries/dk.m3u", SourceKind.BUILT_IN, "🇩🇰"),
            Source("us", "United States", "https://iptv-org.github.io/iptv/countries/us.m3u", SourceKind.BUILT_IN, "🇺🇸"),
            Source("sports", "Sports", "https://iptv-org.github.io/iptv/categories/sports.m3u", SourceKind.BUILT_IN, "⚽"),
        )

        /** Ids for user-added sources, derived from the address so a re-added playlist keeps its favourites. */
        fun idFor(url: String) = "c_" + url.hashCode()
    }
}

/** Preferences, as one value so a screen can observe them together. */
data class Settings(
    val hideGeoBlocked: Boolean = false,   // kept visible by default — VPN users want them
    val subtitlesOn: Boolean = false,      // most live channels burn subtitles in or carry none at all
    val prebufferMs: Int = DEFAULT_PREBUFFER_MS,
)

/** Pre-buffer choices, in ms. 2.5 s is Media3's own default, so it stays the default here. */
val PREBUFFER_CHOICES = listOf(1_000, 2_500, 5_000, 10_000, 30_000, 60_000)
const val DEFAULT_PREBUFFER_MS = 2_500
