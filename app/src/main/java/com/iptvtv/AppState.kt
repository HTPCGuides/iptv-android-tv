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

    /** All groups present in the loaded source, most-populated first. */
    val groups: List<String>
        get() = channels.groupingBy { it.group }.eachCount().entries
            .sortedByDescending { it.value }.map { it.key }

    /** Channels after the search / group / geo filters. */
    val visible: List<Channel>
        get() = channels.asSequence()
            .filter { !hideGeo || !it.geoBlocked }
            .filter { group == null || it.group == group }
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
            if (list.isEmpty()) error = "Couldn't load this playlist. Check the connection and try Refresh."
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

    fun isFav(c: Channel) = repo.isFavourite(c)
    fun toggleFav(c: Channel) { repo.toggleFavourite(c); channels = channels.toList() }

    fun toggleHideGeo() { hideGeo = !hideGeo; repo.hideGeoBlocked = hideGeo }

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
