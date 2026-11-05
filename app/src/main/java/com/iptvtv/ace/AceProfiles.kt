package com.iptvtv.ace


/** The tool's default config (`config.newDefCfg`), as built-in profiles. Values are copied verbatim. */
object Profiles {
    const val HEADER = "#EXTM3U url-tvg=\"http://epg.one/epg2.xml.gz\" tvg-shift=0 deinterlace=1 m3uautoload=1\n"
    const val EXTINF = "#EXTINF:-1 group-title=\"{{.Categories}}\",{{.Name}}\n"
    const val LINK_MPEGTS = "http://{{.EngineAddr}}/ace/getstream?infohash={{.Infohash}}"
    const val LINK_HLS = "http://{{.EngineAddr}}/ace/manifest.m3u8?infohash={{.Infohash}}"
    const val LINK_HTTPACEPROXY = "http://127.0.0.1:8000/infohash/{{.Infohash}}/stream.mp4"

    private const val NON_DEFAULT_CATEGORY =
        "^(?!.*(informational|entertaining|educational|movies|documentaries|sport|fashion|music|" +
            "regional|ethnic|religion|teleshop|erotic_18_plus|other_18_plus|cyber_games|amateur|webcam)).*"

    val MPEGTS_ALL = Profile(
        id = "mpegts_all",
        title = "MPEG-TS · everything",
        description = "Every available source. Categories outside Ace Stream's standard list become “other”.",
        outputPath = "./out/playlist_mpegts_all.m3u8",
        headerTemplate = HEADER,
        entryTemplate = EXTINF + LINK_MPEGTS + "\n",
        categoryRxToCategoryMap = listOf(NON_DEFAULT_CATEGORY to "other"),
    )

    val HLS_TV_MUSIC = Profile(
        id = "hls_tv_music_no_category",
        title = "HLS · TV, music, uncategorised",
        description = "Music and uncategorised sources (as “unknown”). Like the tool, it renames “tv” before filtering, so TV drops out.",
        outputPath = "./out/playlist_hls_tv_+_music_+_no_category.m3u8",
        headerTemplate = HEADER,
        entryTemplate = EXTINF + LINK_HLS + "\n",
        categoryRxToCategoryMap = listOf("(?i)^tv$" to "television", "^$" to "unknown"),
        categories = ListFilter(include = listOf("tv", "music", "unknown")),
    )

    val HTTPACEPROXY_NO_ADULT = Profile(
        id = "httpaceproxy_all_but_porn",
        title = "HTTPAceProxy · all but adult",
        description = "Everything except adult channels, linked through an HTTPAceProxy on 127.0.0.1:8000.",
        outputPath = "./out/playlist_httpaceproxy_all_but_porn.m3u8",
        headerTemplate = HEADER,
        entryTemplate = EXTINF + LINK_HTTPACEPROXY + "\n",
        nameRxBlacklist = listOf("(?i).*erotic.*", "(?i).*porn.*", "(?i).*18\\+.*"),
        categories = ListFilter(exclude = listOf("erotic_18_plus", "18+")),
    )

    val BUILT_IN = listOf(MPEGTS_ALL, HLS_TV_MUSIC, HTTPACEPROXY_NO_ADULT)

    /**
     * Portuguese channels: the usual names, plus anything tagged Portugal (`pt`) or
     * Portuguese (`por`). Brazil (`br`) is left out, since nearly every `por` source in the
     * index is Brazilian. The app's own profile, not one of the tool's.
     */
    val PORTUGAL = Profile(
        id = "portugal",
        title = "Portugal",
        description = "Sport TV, Benfica TV, Eleven, DAZN, Canal 11, RTP, SIC, TVI, CMTV and Porto Canal, " +
            "plus anything tagged Portugal or Portuguese. Brazil is left out.",
        outputPath = "./out/playlist_portugal.m3u8",
        headerTemplate = HEADER,
        entryTemplate = "#EXTINF:-1 group-title=\"Portugal\",{{.Name}}\n" + LINK_MPEGTS + "\n",
        countries = ListFilter(exclude = listOf("br")),
        matchAny = MatchAny(
            nameRx = listOf(
                "(?i)\\bsport\\s*tv\\s*([1-7]|\\+)",              // Sport TV 1–7, Sport TV+
                "(?i)\\bbenfica\\b|^btv\\b",                  // BTV / Benfica TV
                "(?i)\\b(eleven|dazn)\\b.*\\b(pt|portugal)\\b", // Eleven, DAZN: only the Portuguese feeds
                "(?i)\\bcanal\\s*11\\b",
                "(?i)\\b(rtp|sic|tvi|cmtv)(\\d|\\b)",          // RTP1, SIC Notícias, TVI24 — not "Sicilia"
                "(?i)\\bporto\\s*canal\\b",
            ),
            countries = listOf("pt"),
            languages = listOf("por"),
        ),
        emptyMessage = "No Portuguese channels are on Ace Stream right now. Sport TV, RTP and SIC come and go " +
            "from its list; try again later, or switch to Everything.",
    )

    /** What "Fresh from Ace Stream" can build, first is the default. */
    val CHOICES = listOf(MPEGTS_ALL, PORTUGAL)

    fun choice(id: String): Profile = CHOICES.firstOrNull { it.id == id } ?: MPEGTS_ALL
}
