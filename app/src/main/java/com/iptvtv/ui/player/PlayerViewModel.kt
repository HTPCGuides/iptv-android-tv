package com.iptvtv.ui.player

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.iptvtv.data.IptvRepository
import com.iptvtv.data.PlaybackSession
import com.iptvtv.model.Channel
import com.iptvtv.model.Settings
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

sealed interface PlayerUiState {
    /** Nothing is playing — the screen was reached without a channel, or playback was stopped. */
    data object Idle : PlayerUiState

    data class Playing(
        val channel: Channel,
        val isFavourite: Boolean,
        val settings: Settings,
    ) : PlayerUiState
}

class PlayerViewModel(
    private val repo: IptvRepository,
    private val playback: PlaybackSession,
) : ViewModel() {

    val uiState: StateFlow<PlayerUiState> = combine(
        playback.current, repo.favourites, repo.settings,
    ) { current, favs, settings ->
        if (current == null) PlayerUiState.Idle
        else PlayerUiState.Playing(current.channel, current.channel.favKey in favs, settings)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), PlayerUiState.Idle)

    /** Channel up/down within the list playback started from. */
    fun step(delta: Int) {
        val next = playback.step(delta) ?: return
        viewModelScope.launch { repo.noteWatched(next) }
    }

    fun toggleSubtitles() {
        viewModelScope.launch { repo.setSubtitlesOn(!repo.settings.first().subtitlesOn) }
    }

    fun stop() = playback.stop()
}
