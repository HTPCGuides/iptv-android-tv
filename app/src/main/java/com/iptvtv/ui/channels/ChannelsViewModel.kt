package com.iptvtv.ui.channels

import kotlinx.coroutines.flow.Flow
import com.iptvtv.model.SourceKind
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.toRoute
import com.iptvtv.data.ChannelFilter
import com.iptvtv.data.IptvRepository
import com.iptvtv.data.PlaybackSession
import com.iptvtv.data.RefreshResult
import com.iptvtv.data.RemovedChannels
import com.iptvtv.data.M3uWriter
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
        /**
         * Channels can be taken out: every playlist shown is a saved Ace Stream version,
         * which nothing re-fetches. A web playlist would put them back on its next update.
         */
        val removable: Boolean = false,
        /** What the last removal took out, while it can still be undone. */
        val removed: String? = null,
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
    private val sourceIds = savedStateHandle.toRoute<ChannelsRoute>().sourceIds

    private val query = MutableStateFlow("")
    private val group = MutableStateFlow<String?>(null)
    private val refreshing = MutableStateFlow(false)
    private val error = MutableStateFlow<String?>(null)
    private val lastRemoval = MutableStateFlow<Pair<String, RemovedChannels>?>(null)
    private val removable = sourceIds.all { it.startsWith(Source.ACE_ID_PREFIX) }

    /**
     * The playlist being shown — or, for several opened together, one made up for the
     * header: their names joined, never stored, with nothing of its own to rename or reload.
     */
    private val source: Flow<Source> =
        if (sourceIds.size == 1) repo.source(sourceIds.single()).filterNotNull()
        else repo.sources.map { all ->
            val picked = all.filter { it.id in sourceIds }
            Source(
                id = "merged",
                name = if (picked.size <= 2) picked.joinToString(" + ") { it.name } else "${picked.size} playlists",
                url = "", kind = SourceKind.MERGED, emoji = "🗂",
            )
        }
    private val filter = combine(query, group, repo.settings) { q, g, s -> ChannelFilter(q, g, s.hideGeoBlocked) }
    private val channels = filter.flatMapLatest { repo.channels(sourceIds, it) }

    val uiState: StateFlow<ChannelsUiState> = combine(
        source, channels, repo.groups(sourceIds), repo.favourites, filter, refreshing, error, lastRemoval,
    ) { values ->
        val src = values[0] as Source
        @Suppress("UNCHECKED_CAST") val list = values[1] as List<Channel>
        @Suppress("UNCHECKED_CAST") val groups = values[2] as List<String>
        @Suppress("UNCHECKED_CAST") val favs = values[3] as Set<String>
        val f = values[4] as ChannelFilter
        val busy = values[5] as Boolean
        val err = values[6] as String?
        @Suppress("UNCHECKED_CAST") val removal = values[7] as Pair<String, RemovedChannels>?
        when {
            // Nothing stored yet: the first fetch decides between a list and an error.
            groups.isEmpty() && busy -> ChannelsUiState.Loading
            groups.isEmpty() && err != null -> ChannelsUiState.Error(src, err)
            else -> ChannelsUiState.Success(src, list, groups, favs, f.query, f.group, busy, removable, removal?.first)
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ChannelsUiState.Loading)

    init { refresh(force = false) }

    fun refresh(force: Boolean = true) {
        viewModelScope.launch {
            refreshing.value = true
            error.value = null
            // Each web playlist in the view is fetched; files and Ace versions are fixed.
            val all = repo.sources.first().filter { it.id in sourceIds }
            val failures = all.map { repo.refresh(it, force) }.filterIsInstance<RefreshResult.Failed>()
            if (failures.isNotEmpty()) error.value = failures.first().message
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

    /** Takes these feeds out of the version(s) shown, with Undo. */
    fun removeChannels(channels: List<Channel>) {
        if (!removable || channels.isEmpty()) return
        viewModelScope.launch {
            val removed = repo.removeChannels(sourceIds, channels.map { it.url })
            if (removed.count == 0) return@launch
            val what = if (removed.count == 1) "“${channels.first().clean}”" else "${removed.count} channels"
            lastRemoval.value = "Removed $what" to removed
        }
    }

    fun undoRemoval() {
        val (_, removed) = lastRemoval.value ?: return
        lastRemoval.value = null
        viewModelScope.launch { repo.restoreChannels(removed) }
    }

    fun dismissRemoval() { lastRemoval.value = null }

    /** The playlist shown, as an M3U file: its name and its text. */
    suspend fun export(): Pair<String, String> {
        val src = source.first()
        val name = if (src.isAce) src.url else M3uWriter.fileName(src.name)
        return name to repo.playlistText(sourceIds)
    }

    fun rename(name: String) {
        if (sourceIds.size != 1) return
        viewModelScope.launch { repo.renameSource(sourceIds.single(), name) }
    }
}
