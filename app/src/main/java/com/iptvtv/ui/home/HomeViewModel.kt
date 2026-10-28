package com.iptvtv.ui.home

import android.content.Context
import android.net.Uri
import com.iptvtv.data.AceBuild
import com.iptvtv.data.AcePlaylistBuilder
import com.iptvtv.data.Handoff
import com.iptvtv.data.RefreshResult
import com.iptvtv.data.playlistName
import com.iptvtv.data.readHandoff
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.iptvtv.data.AceEngine
import com.iptvtv.data.IptvRepository
import com.iptvtv.data.PlaybackSession
import com.iptvtv.model.Channel
import com.iptvtv.model.Settings
import com.iptvtv.model.Source
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

sealed interface HomeUiState {
    /** The database is still being seeded (first run, or the pre-Room data being carried over). */
    data object Loading : HomeUiState

    data class Ready(
        val sources: List<Source>,
        val favourites: List<Channel>,
        val recents: List<Channel>,
        val settings: Settings,
        val cacheBytes: Long,
        val aceEngine: AceEngineState,
        /** Work on several playlists, or an Ace Stream build, in progress or just finished. */
        val activity: HomeActivity? = null,
    ) : HomeUiState
}

/** A line of status under Sources: what is being done to the playlists, or what just was. */
sealed interface HomeActivity {
    data class Working(val message: String) : HomeActivity
    data class Done(val message: String) : HomeActivity
    data class Failed(val message: String) : HomeActivity
}

/** What Home can say about the Ace Stream engine. */
enum class AceEngineState { NOT_INSTALLED, CHECKING, RUNNING, STOPPED }

class HomeViewModel(
    private val repo: IptvRepository,
    private val playback: PlaybackSession,
    private val ready: Job,
    private val aceEngine: AceEngine,
    private val aceBuilder: AcePlaylistBuilder,
) : ViewModel() {
    private val activity = MutableStateFlow<HomeActivity?>(null)
    private val busy get() = activity.value is HomeActivity.Working

    private val cacheBytes = MutableStateFlow(0L)
    private val seeded = MutableStateFlow(false)
    private val engine = MutableStateFlow(if (aceEngine.installed) AceEngineState.CHECKING else AceEngineState.NOT_INSTALLED)

    val uiState: StateFlow<HomeUiState> = combine(
        seeded, repo.sources, repo.favouriteChannels, repo.recentChannels, repo.settings, cacheBytes, engine, activity,
    ) { values ->
        if (values[0] != true) HomeUiState.Loading
        else @Suppress("UNCHECKED_CAST") HomeUiState.Ready(
            sources = values[1] as List<Source>,
            favourites = values[2] as List<Channel>,
            recents = values[3] as List<Channel>,
            settings = values[4] as Settings,
            cacheBytes = values[5] as Long,
            aceEngine = values[6] as AceEngineState,
            activity = values[7] as HomeActivity?,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), HomeUiState.Loading)

    init {
        viewModelScope.launch { ready.join(); seeded.value = true; refresh() }
    }

    /** Re-reads what changes while Home is away: cache size, and whether the engine is up. */
    fun refresh() {
        viewModelScope.launch { cacheBytes.value = repo.cacheBytes() }
        if (aceEngine.installed) {
            viewModelScope.launch {
                engine.value = if (aceEngine.running()) AceEngineState.RUNNING else AceEngineState.STOPPED
            }
        }
    }

    fun play(channel: Channel, queue: List<Channel>) {
        playback.play(channel, queue)
        viewModelScope.launch { repo.noteWatched(channel) }
    }

    fun toggleFavourite(channel: Channel) {
        viewModelScope.launch { repo.toggleFavourite(channel) }
    }

    fun addSource(name: String, url: String) {
        viewModelScope.launch { repo.addUrlSource(name, url) }
    }

    fun removeSource(source: Source) {
        viewModelScope.launch { repo.removeSource(source.id) }
    }

    fun dismissActivity() { if (!busy) activity.value = null }

    /**
     * Builds a fresh Ace Stream playlist from the engine's live channel index and saves it
     * as a new version, named for now. The previous versions stay (or are pruned to the
     * number the user chose).
     */
    fun buildAceVersion() {
        if (busy) return
        viewModelScope.launch { buildAce() }
    }

    private suspend fun buildAce(): Boolean {
        activity.value = HomeActivity.Working("Reading Ace Stream's channel list…")
        return when (val r = aceBuilder.build { n -> activity.value = HomeActivity.Working("Reading Ace Stream's channel list… $n channels") }) {
            is AceBuild.Failed -> { activity.value = HomeActivity.Failed(r.message); false }
            is AceBuild.Built -> {
                val saved = repo.saveAceVersion(r.text)
                activity.value = if (saved == null) HomeActivity.Failed("The engine returned no playable channels.")
                else HomeActivity.Done("Saved “${saved.name}” — ${count(r.sources, "channel")}")
                saved != null
            }
        }
    }

    /**
     * Fetches the selected playlists again. Web playlists are re-downloaded; if any Ace
     * Stream version is among them, one fresh version is built (a version itself never
     * changes). Handed-over files have nowhere to come from and are skipped.
     */
    fun refreshSources(sources: List<Source>) {
        if (busy || sources.isEmpty()) return
        viewModelScope.launch {
            val web = sources.filter { it.refetchable }
            var failed = 0
            web.forEachIndexed { i, s ->
                activity.value = HomeActivity.Working("Updating ${s.name} (${i + 1} of ${web.size})…")
                if (repo.refresh(s, force = true) is RefreshResult.Failed) failed++
            }
            val aceOk = if (sources.any { it.isAce }) buildAce() else true
            if (aceOk && web.isNotEmpty()) {
                val skipped = sources.count { it.isFile }
                activity.value = if (failed == 0) HomeActivity.Done(
                    "Updated ${count(web.size, "playlist")}" + if (skipped > 0) " · ${count(skipped, "file")} can't be updated" else "",
                ) else HomeActivity.Failed("${count(failed, "playlist")} couldn't be downloaded; the rest are up to date.")
            } else if (web.isEmpty() && !sources.any { it.isAce }) {
                activity.value = HomeActivity.Failed("Imported files can't be updated — open the file again from the app that made it.")
            }
        }
    }

    fun removeSources(sources: List<Source>) {
        viewModelScope.launch {
            repo.removeSources(sources.filterNot { it.builtIn }.map { it.id })
            activity.value = HomeActivity.Done("Removed ${count(sources.count { !it.builtIn }, "playlist")}")
        }
    }

    /** Several playlist URLs at once, one per line. A single URL takes the name given. */
    fun addSources(name: String, urls: String) {
        val list = urls.lines().map { it.trim() }.filter { it.startsWith("http", ignoreCase = true) }.distinct()
        if (list.isEmpty()) return
        viewModelScope.launch {
            list.forEach { url ->
                repo.addUrlSource(if (list.size == 1) name else playlistName(url.substringBefore('?')), url)
            }
            if (list.size > 1) activity.value = HomeActivity.Done("Added ${count(list.size, "playlist")}")
        }
    }

    /** Playlist files picked on this device, any number at once. */
    fun importFiles(ctx: Context, uris: List<Uri>) {
        if (uris.isEmpty() || busy) return
        viewModelScope.launch {
            var added = 0
            val problems = ArrayList<String>()
            uris.forEachIndexed { i, uri ->
                activity.value = HomeActivity.Working("Importing ${i + 1} of ${uris.size}…")
                when (val h = readHandoff(ctx, uri)) {
                    is Handoff.File -> if (repo.importFile(h.name, h.text) != null) added++ else problems += "“${h.name}” had no channels"
                    is Handoff.Link -> { repo.addUrlSource(h.name, h.url); added++ }
                    is Handoff.Failed -> problems += h.message
                }
            }
            activity.value = if (problems.isEmpty()) HomeActivity.Done("Imported ${count(added, "playlist")}")
            else HomeActivity.Failed("Imported ${count(added, "playlist")}. ${problems.joinToString(" ")}")
        }
    }

    fun setAceKeepVersions(n: Int) {
        viewModelScope.launch { repo.setAceKeepVersions(n) }
    }

    private fun count(n: Int, noun: String) = if (n == 1) "1 $noun" else "$n ${noun}s"

    fun setHideGeoBlocked(on: Boolean) {
        viewModelScope.launch { repo.setHideGeoBlocked(on) }
    }

    fun choosePrebuffer(ms: Int) {
        viewModelScope.launch { repo.setPrebufferMs(ms) }
    }

    fun clearCache() {
        viewModelScope.launch { repo.clearCache(); refresh() }
    }
}
