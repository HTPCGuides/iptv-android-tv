package com.iptvtv

import android.app.Application
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class IptvApp : Application()

class IptvViewModel(app: Application) : AndroidViewModel(app) {
    private val repo = Repo(app)

    var sources by mutableStateOf(repo.sources()); private set
    var channels by mutableStateOf<List<Channel>>(emptyList()); private set
    var loading by mutableStateOf(false); private set
    var error by mutableStateOf<String?>(null); private set
    var currentSource by mutableStateOf<Source?>(null); private set

    /** The channel being played, plus the list it came from (for channel up/down). */
    var playing by mutableStateOf<Channel?>(null); private set
    var playQueue by mutableStateOf<List<Channel>>(emptyList()); private set

    var query by mutableStateOf("")
    var group by mutableStateOf<String?>(null)
    var hideGeo by mutableStateOf(repo.hideGeoBlocked)

    /** Observable mirror of the persisted favourites — prefs alone can't drive recomposition. */
    private var favs by mutableStateOf(repo.favourites().toSet())

    /** Favourites / recents resolved to real channels for the Home rows. */
    var favChannels by mutableStateOf<List<Channel>>(emptyList()); private set
    var recentChannels by mutableStateOf<List<Channel>>(emptyList()); private set

    init { refreshHomeRows() }

    /**
     * Resolve the stored "sourceId|url" keys against the (cached) playlists they
     * came from. Sources that were removed are skipped silently.
     */
    fun refreshHomeRows() {
        val favKeys = repo.favourites()
        val recentKeys = repo.recents()
        if (favKeys.isEmpty() && recentKeys.isEmpty()) {
            favChannels = emptyList(); recentChannels = emptyList(); return
        }
        val wanted = (favKeys + recentKeys).map { it.substringBefore('|') }.toSet()
        val have = sources.filter { it.id in wanted }
        viewModelScope.launch {
            val loaded = withContext(Dispatchers.IO) {
                have.flatMap { s -> runCatching { repo.load(s) }.getOrDefault(emptyList()) }
            }
            // Look a channel up by either identity: the stream URL (what older
            // favourites and all recents are keyed by) or the channel itself.
            val byKey = HashMap<String, Channel>(loaded.size * 2)
            loaded.forEach { c ->
                byKey[c.key] = c
                byKey.putIfAbsent(c.favKey, c)
            }

            // Migrate URL-keyed favourites to channel keys as their playlists load.
            // Two feeds of one channel collapse to a single entry here; anything we
            // could not resolve is left untouched rather than dropped.
            val migrated = favKeys.map { byKey[it]?.favKey ?: it }.toSet()
            if (migrated != favKeys) {
                repo.replaceFavourites(migrated)
                favs = migrated
            }

            favChannels = migrated.mapNotNull { byKey[it] }
                .distinctBy { it.favKey }
                .sortedBy { it.clean.lowercase() }
            recentChannels = recentKeys.mapNotNull { byKey[it] }.distinctBy { it.favKey }.take(10)
        }
    }

    /** All groups present in the loaded source, most-populated first. */
    val groups: List<String>
        get() = channels.flatMap { it.groups }.groupingBy { it }.eachCount().entries
            .sortedByDescending { it.value }.map { it.key }

    /** Channels after the search / group / geo filters. */
    val visible: List<Channel>
        get() = channels.asSequence()
            .filter { !hideGeo || !it.geoBlocked }
            .filter { group == null || it.groups.contains(group) }
            .filter { query.isBlank() || it.name.contains(query, true) || it.group.contains(query, true) }
            .toList()

    fun open(source: Source, refresh: Boolean = false) {
        currentSource = source
        group = null; query = ""
        loading = true; error = null
        viewModelScope.launch {
            val list = withContext(Dispatchers.IO) { repo.load(source, refresh) }
            channels = list
            loading = false
            if (list.isEmpty()) {
                error = if (source.isFile) "This playlist's stored copy is missing. Open the file again from the app that made it."
                else "Couldn't load this playlist. Check the connection and try Refresh."
            }
        }
    }

    fun play(c: Channel, queue: List<Channel>) {
        playing = c
        playQueue = queue
        repo.noteWatched(c)
    }
    fun stop() { playing = null }

    /** Channel up/down within the current list (wraps around). */
    fun step(delta: Int) {
        val q = playQueue
        val i = q.indexOfFirst { it.key == playing?.key }
        if (q.isEmpty() || i < 0) return
        val next = q[((i + delta) % q.size + q.size) % q.size]
        playing = next
        repo.noteWatched(next)
    }

    var cacheBytes by mutableStateOf(0L); private set

    fun refreshCacheSize() {
        viewModelScope.launch { cacheBytes = withContext(Dispatchers.IO) { repo.cacheBytes() } }
    }

    fun clearCache() {
        viewModelScope.launch {
            withContext(Dispatchers.IO) {
                // Let Coil close its disk cache handles cleanly before we sweep the dir.
                val loader = coil.Coil.imageLoader(getApplication())
                loader.memoryCache?.clear()
                runCatching { loader.diskCache?.clear() }
                repo.clearCache()
            }
            cacheBytes = withContext(Dispatchers.IO) { repo.cacheBytes() }
        }
    }

    fun isFav(c: Channel) = favs.contains(c.favKey) || favs.contains(c.key)
    fun toggleFav(c: Channel) {
        repo.toggleFavourite(c)
        favs = repo.favourites().toSet()
        refreshHomeRows()   // the Home row has to lose or gain the tile straight away
    }

    fun toggleHideGeo() { hideGeo = !hideGeo; repo.hideGeoBlocked = hideGeo }

    /** Subtitle preference is remembered across channels and sessions, like a TV does. */
    var subtitlesOn by mutableStateOf(repo.subtitlesOn)
        private set

    fun toggleSubtitles() { subtitlesOn = !subtitlesOn; repo.subtitlesOn = subtitlesOn }

    /** Read when the player opens, so a change takes effect from the next channel started. */
    var prebufferMs by mutableStateOf(repo.prebufferMs)
        private set

    fun choosePrebuffer(ms: Int) { prebufferMs = ms; repo.prebufferMs = ms }

    /** Why a playlist another app handed over couldn't be opened; cleared once seen. */
    var importError by mutableStateOf<String?>(null)

    /**
     * Stores a handed-over playlist file and opens it. A file without channels is refused
     * rather than stored, so it can't leave an empty tile on Home. Returns whether it opened.
     */
    suspend fun importPlaylist(name: String, text: String): Boolean {
        val source = withContext(Dispatchers.IO) {
            if (M3uParser.parse(text, "").isEmpty()) null else repo.importFile(name, text)
        }
        if (source == null) {
            importError = "“$name” doesn't contain any channels."
            return false
        }
        sources = repo.sources()
        open(source)
        return true
    }

    /** A playlist handed over as a web address is kept like one typed into Add playlist. */
    fun followPlaylistLink(name: String, url: String) {
        if (sources.none { it.url == url }) {
            repo.addCustomSource(name, url)
            sources = repo.sources()
        }
        sources.firstOrNull { it.url == url }?.let { open(it) }
    }

    fun renameSource(s: Source, name: String) {
        repo.renameCustomSource(s.url, name)
        sources = repo.sources()
        if (currentSource?.url == s.url) currentSource = sources.firstOrNull { it.url == s.url }
    }

    fun addSource(name: String, url: String) {
        repo.addCustomSource(name, url)
        sources = repo.sources()
    }
    fun removeSource(s: Source) {
        repo.removeCustomSource(s.url)
        sources = repo.sources()
    }

    /** Favourites resolved across whatever is currently loaded. */
    fun favouritesIn(list: List<Channel>) = list.filter { repo.isFavourite(it) }
}
