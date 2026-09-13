package com.iptvtv.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
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
    ) : HomeUiState
}

class HomeViewModel(
    private val repo: IptvRepository,
    private val playback: PlaybackSession,
    private val ready: Job,
) : ViewModel() {

    private val cacheBytes = MutableStateFlow(0L)
    private val seeded = MutableStateFlow(false)

    val uiState: StateFlow<HomeUiState> = combine(
        seeded, repo.sources, repo.favouriteChannels, repo.recentChannels, repo.settings, cacheBytes,
    ) { values ->
        if (values[0] != true) HomeUiState.Loading
        else @Suppress("UNCHECKED_CAST") HomeUiState.Ready(
            sources = values[1] as List<Source>,
            favourites = values[2] as List<Channel>,
            recents = values[3] as List<Channel>,
            settings = values[4] as Settings,
            cacheBytes = values[5] as Long,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), HomeUiState.Loading)

    init {
        viewModelScope.launch { ready.join(); seeded.value = true; refreshCacheSize() }
    }

    fun refreshCacheSize() {
        viewModelScope.launch { cacheBytes.value = repo.cacheBytes() }
    }

    fun play(channel: Channel, queue: List<Channel>) {
        playback.play(channel, queue)
        viewModelScope.launch { repo.noteWatched(channel) }
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
        viewModelScope.launch { repo.clearCache(); refreshCacheSize() }
    }
}
