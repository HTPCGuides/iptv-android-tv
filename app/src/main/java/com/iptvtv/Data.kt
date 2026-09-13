package com.iptvtv

import android.content.Context
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

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
    val clean: String
        get() = name.replace(Regex("""\s*\[(Geo-blocked|Not 24/7)]""", RegexOption.IGNORE_CASE), "")
            .replace(Regex("""\s*\(\d+p\)( \(HEVC\))?"""), "").trim()
    val quality: String? get() = Regex("""\((\d+p)\)""").find(name)?.groupValues?.get(1)
    val key get() = "$sourceId|$url"
    /**
     * Identity for favouriting. Playlists carry the same channel several times as
     * separate feeds — iptv-org's sports list has two entries both called
     * "Red Bull TV (1080p)" on different CDNs — and to a viewer those are one
     * channel, so the name is what a favourite is pinned to, not the stream URL.
     */
    val favKey get() = "$sourceId|${clean.lowercase()}"
    /** iptv-org packs several groups into one attribute ("Comedy;Movies;Sports"). */
    val groups: List<String>
        get() = group.split(';').map { it.trim() }.filter { it.isNotEmpty() }.ifEmpty { listOf("Other") }
}

/** A playlist source — the four built-ins, plus anything the user adds or another app hands over. */
data class Source(
    val id: String,
    val name: String,
    val url: String,
    val builtIn: Boolean = true,
    val emoji: String = "📺",
) {
    /**
     * Stored from a file another app handed over. Its channels come from our own copy: the
     * content:// URI it arrived on is only readable during the hand-off, so there is nothing
     * to fetch again later.
     */
    val isFile get() = url.startsWith(IMPORTED_SCHEME)

    companion object {
        /** Stands in for a URL on an imported file, followed by the file's name. */
        const val IMPORTED_SCHEME = "imported:"

        val BUILT_IN = listOf(
            Source("uk", "United Kingdom", "https://iptv-org.github.io/iptv/countries/uk.m3u", emoji = "🇬🇧"),
            Source("dk", "Denmark", "https://iptv-org.github.io/iptv/countries/dk.m3u", emoji = "🇩🇰"),
            Source("us", "United States", "https://iptv-org.github.io/iptv/countries/us.m3u", emoji = "🇺🇸"),
            Source("sports", "Sports", "https://iptv-org.github.io/iptv/categories/sports.m3u", emoji = "⚽"),
        )
    }
}

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

/** Downloads + caches playlists on disk, and stores favourites / custom sources. */
private const val SEP = "\u001F"   // delimiter for the stored "name<SEP>url" rows

class Repo(private val ctx: Context) {
    private val prefs = ctx.getSharedPreferences("iptv", Context.MODE_PRIVATE)
    private val cacheDir = File(ctx.filesDir, "playlists").apply { mkdirs() }
    // Imported files live apart from the download cache: "Clear cached data" empties that
    // folder and promises to keep your playlists, and for a file our copy is the playlist.
    private val importedDir = File(ctx.filesDir, "imported").apply { mkdirs() }

    // ---- sources ----
    fun sources(): List<Source> = Source.BUILT_IN + customSources()

    fun customSources(): List<Source> =
        (prefs.getString("custom", "") ?: "").split("\n").filter { it.isNotBlank() }.mapNotNull { row ->
            val p = row.split(SEP)
            if (p.size >= 2) Source(id = "c_" + p[1].hashCode(), name = p[0], url = p[1], builtIn = false, emoji = "📃")
            else null
        }

    /** Names are stored one row per line with SEP between fields, so neither may appear in one. */
    private fun rowName(name: String) =
        name.replace("\n", " ").replace(SEP, " ").trim().ifBlank { "My playlist" }

    fun addCustomSource(name: String, url: String) {
        val rows = (prefs.getString("custom", "") ?: "").split("\n").filter { it.isNotBlank() }.toMutableList()
        rows.add("${rowName(name)}$SEP${url.trim()}")
        prefs.edit().putString("custom", rows.joinToString("\n")).apply()
    }

    fun removeCustomSource(url: String) {
        customSources().firstOrNull { it.url == url && it.isFile }?.let { importedCopy(it).delete() }
        val rows = (prefs.getString("custom", "") ?: "").split("\n")
            .filter { it.isNotBlank() && !it.endsWith("$SEP$url") }
        prefs.edit().putString("custom", rows.joinToString("\n")).apply()
    }

    fun renameCustomSource(url: String, name: String) {
        val rows = (prefs.getString("custom", "") ?: "").split("\n").filter { it.isNotBlank() }
            .map { row -> if (row.endsWith("$SEP$url")) "${rowName(name)}$SEP$url" else row }
        prefs.edit().putString("custom", rows.joinToString("\n")).apply()
    }

    private fun importedCopy(source: Source) = File(importedDir, source.id + ".m3u")

    /**
     * Stores a playlist file another app handed over. The same file name handed over again
     * replaces the copy but keeps the row — and with it the id, so a rename and any
     * favourites made in the meantime survive a regenerated playlist.
     */
    fun importFile(name: String, text: String): Source {
        val url = Source.IMPORTED_SCHEME + name.lowercase()
        if (customSources().none { it.url == url }) addCustomSource(name, url)
        val source = customSources().first { it.url == url }
        importedCopy(source).writeText(text)
        return source
    }

    // ---- playlists ----
    /** Cached copy if fresh (24h) or offline; otherwise download. BLOCKING — call off the main thread. */
    fun load(source: Source, forceRefresh: Boolean = false): List<Channel> {
        // A file has no address to refresh from; re-reading our copy is the refresh.
        if (source.isFile) {
            val copy = importedCopy(source)
            return if (copy.exists()) M3uParser.parse(copy.readText(), source.id) else emptyList()
        }
        val f = File(cacheDir, source.id + ".m3u")
        val fresh = f.exists() && System.currentTimeMillis() - f.lastModified() < 24 * 60 * 60 * 1000
        if (!forceRefresh && fresh) return M3uParser.parse(f.readText(), source.id)
        return try {
            val text = fetch(source.url)
            f.writeText(text)
            M3uParser.parse(text, source.id)
        } catch (e: Exception) {
            if (f.exists()) M3uParser.parse(f.readText(), source.id) else emptyList()
        }
    }

    private fun fetch(url: String): String {
        val conn = (URL(url).openConnection() as HttpURLConnection).apply {
            connectTimeout = 15000
            readTimeout = 20000
            instanceFollowRedirects = true
            setRequestProperty("User-Agent", "IPTVtv/1.0")
        }
        return conn.inputStream.bufferedReader().use { it.readText() }.also { conn.disconnect() }
    }

    // ---- favourites & recents ----
    fun favourites(): Set<String> = prefs.getStringSet("favs", emptySet()) ?: emptySet()
    /** `c.key` is accepted too so favourites saved before the switch still count. */
    fun isFavourite(c: Channel) = favourites().let { it.contains(c.favKey) || it.contains(c.key) }
    fun toggleFavourite(c: Channel) {
        val set = favourites().toMutableSet()
        if (isFavourite(c)) { set.remove(c.favKey); set.remove(c.key) } else set.add(c.favKey)
        prefs.edit().putStringSet("favs", set).apply()
    }
    fun replaceFavourites(keys: Set<String>) = prefs.edit().putStringSet("favs", keys).apply()

    fun noteWatched(c: Channel) {
        val list = recents().toMutableList()
        list.remove(c.key); list.add(0, c.key)
        prefs.edit().putString("recents", list.take(20).joinToString("\n")).apply()
    }
    fun recents(): List<String> =
        (prefs.getString("recents", "") ?: "").split("\n").filter { it.isNotBlank() }

    // ---- cache housekeeping ----
    /** Bytes used by cached playlists + the app cache dir (logo images etc.). */
    fun cacheBytes(): Long =
        (cacheDir.walkBottomUp() + ctx.cacheDir.walkBottomUp())
            .filter { it.isFile }.sumOf { it.length() }

    fun clearCache() {
        cacheDir.listFiles()?.forEach { it.deleteRecursively() }
        ctx.cacheDir.listFiles()?.forEach { it.deleteRecursively() }
    }

    // ---- prefs ----
    var hideGeoBlocked: Boolean
        get() = prefs.getBoolean("hideGeo", false)   // kept visible by default — VPN users want them
        set(v) = prefs.edit().putBoolean("hideGeo", v).apply()

    /** Off by default: most live channels burn subtitles in or carry none at all. */
    var subtitlesOn: Boolean
        get() = prefs.getBoolean("subs", false)
        set(v) = prefs.edit().putBoolean("subs", v).apply()

    /** How much of a stream to load before playback starts, in ms. Anything unknown reads as the default. */
    var prebufferMs: Int
        get() = prefs.getInt("prebuffer", DEFAULT_PREBUFFER_MS).takeIf { it in PREBUFFER_CHOICES } ?: DEFAULT_PREBUFFER_MS
        set(v) = prefs.edit().putInt("prebuffer", v).apply()
}

/** Pre-buffer choices, in ms. 2.5 s is Media3's own default, so it stays the default here. */
val PREBUFFER_CHOICES = listOf(1_000, 2_500, 5_000, 10_000, 30_000, 60_000)
const val DEFAULT_PREBUFFER_MS = 2_500
