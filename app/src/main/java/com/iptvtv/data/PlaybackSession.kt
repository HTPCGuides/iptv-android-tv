package com.iptvtv.data

import com.iptvtv.model.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update

/** The channel being played, plus the list it came from (for channel up/down). */
data class Playback(val channel: Channel, val queue: List<Channel>)

/**
 * What is playing, app-wide. It outlives any one screen: Home and the channel list
 * both start playback, and the player reads it rather than being handed a channel.
 */
class PlaybackSession {
    private val _current = MutableStateFlow<Playback?>(null)
    val current: StateFlow<Playback?> = _current

    fun play(channel: Channel, queue: List<Channel>) {
        _current.value = Playback(channel, queue)
    }

    /** Channel up/down within the current list (wraps around). Returns the new channel, if any. */
    fun step(delta: Int): Channel? {
        var stepped: Channel? = null
        _current.update { p ->
            val q = p?.queue ?: return@update p
            val i = q.indexOfFirst { it.key == p.channel.key }
            if (q.isEmpty() || i < 0) return@update p
            stepped = q[((i + delta) % q.size + q.size) % q.size]
            p.copy(channel = stepped!!)
        }
        return stepped
    }

    fun stop() { _current.value = null }
}
