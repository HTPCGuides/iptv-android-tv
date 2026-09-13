package com.iptvtv.ui.channels

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.toRoute
import com.iptvtv.data.ChannelFilter
import com.iptvtv.data.IptvRepository
import com.iptvtv.data.PlaybackSession
import com.iptvtv.data.RefreshResult
import com.iptvtv.model.Channel
import com.iptvtv.model.Source
import com.iptvtv.ui.navigation.ChannelsRoute
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

sealed interface ChannelsUiState {
    data object Loading : ChannelsUiState

    /** The source has no channels and the last fetch failed; [message] says why. */
    data class Error(val source: Source, val message: String) : ChannelsUiState

    data class Success(
        val source: Source,
        val channels: List<Channel>,
        val groups: List<String>,
        val favourites: Set<String>,
        val query: String,
        val group: String?,
        /** True while a fetch is in flight behind an already-shown list. */
        val refreshing: Boolean,
    ) : ChannelsUiState {
        val filtered get() = query.isNotBlank() || group != null
    }
}

@OptIn(ExperimentalCoroutinesApi::class)
class ChannelsViewModel(
    savedStateHandle: SavedStateHandle,
    private val repo: IptvRepository,
    private val playback: PlaybackSession,
) : ViewModel() {
    private val sourceId = savedStateHandle.toRoute<ChannelsRoute>().sourceId

    private val query = MutableStateFlow("")
    private val group = MutableStateFlow<String?>(null)
    private val refreshing = MutableStateFlow(false)
    private val error = MutableStateFlow<String?>(null)

    private val source = repo.source(sourceId).filterNotNull()
    private val filter = combine(query, group, repo.settings) { q, g, s -> ChannelFilter(q, g, s.hideGeoBlocked) }
    private val channels = filter.flatMapLatest { repo.channels(sourceId, it) }

    val uiState: StateFlow<ChannelsUiState> = combine(
        source, channels, repo.groups(sourceId), repo.favourites, filter, refreshing, error,
    ) { values ->
        val src = values[0] as Source
        @Suppress("UNCHECKED_CAST") val list = values[1] as List<Channel>
        @Suppress("UNCHECKED_CAST") val groups = values[2] as List<String>
        @Suppress("UNCHECKED_CAST") val favs = values[3] as Set<String>
        val f = values[4] as ChannelFilter
        val busy = values[5] as Boolean
        val err = values[6] as String?
        when {
            // Nothing stored yet: the first fetch decides between a list and an error.
            groups.isEmpty() && busy -> ChannelsUiState.Loading
            groups.isEmpty() && err != null -> ChannelsUiState.Error(src, err)
            else -> ChannelsUiState.Success(src, list, groups, favs, f.query, f.group, busy)
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ChannelsUiState.Loading)

    init { refresh(force = false) }

    fun refresh(force: Boolean = true) {
        viewModelScope.launch {
            refreshing.value = true
            error.value = null
            val result = repo.refresh(source.first(), force)
            if (result is RefreshResult.Failed) error.value = result.message
            refreshing.value = false
        }
    }

    fun setQuery(q: String) { query.value = q }
    fun setGroup(g: String?) { group.value = g }
    fun clearFilters() { query.value = ""; group.value = null }

    fun play(channel: Channel, queue: List<Channel>) {
        playback.play(channel, queue)
        viewModelScope.launch { repo.noteWatched(channel) }
    }

    fun toggleFavourite(channel: Channel) {
        viewModelScope.launch { repo.toggleFavourite(channel) }
    }

    fun rename(name: String) {
        viewModelScope.launch { repo.renameSource(sourceId, name) }
    }
}
