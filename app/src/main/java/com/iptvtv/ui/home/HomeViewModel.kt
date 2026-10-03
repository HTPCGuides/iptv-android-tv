package com.iptvtv.ui.home

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
    ) : HomeUiState
}

/** What Home can say about the Ace Stream engine. */
enum class AceEngineState { NOT_INSTALLED, CHECKING, RUNNING, STOPPED }

class HomeViewModel(
    private val repo: IptvRepository,
    private val playback: PlaybackSession,
    private val ready: Job,
    private val aceEngine: AceEngine,
) : ViewModel() {

    private val cacheBytes = MutableStateFlow(0L)
    private val seeded = MutableStateFlow(false)
    private val engine = MutableStateFlow(if (aceEngine.installed) AceEngineState.CHECKING else AceEngineState.NOT_INSTALLED)

    val uiState: StateFlow<HomeUiState> = combine(
        seeded, repo.sources, repo.favouriteChannels, repo.recentChannels, repo.settings, cacheBytes, engine,
    ) { values ->
        if (values[0] != true) HomeUiState.Loading
        else @Suppress("UNCHECKED_CAST") HomeUiState.Ready(
            sources = values[1] as List<Source>,
            favourites = values[2] as List<Channel>,
            recents = values[3] as List<Channel>,
            settings = values[4] as Settings,
            cacheBytes = values[5] as Long,
            aceEngine = values[6] as AceEngineState,
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
