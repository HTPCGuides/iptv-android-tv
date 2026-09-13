package com.iptvtv.ui

import android.content.Context
import android.net.Uri
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import com.iptvtv.data.Handoff
import com.iptvtv.data.IptvRepository
import com.iptvtv.data.PlaybackSession
import com.iptvtv.data.readHandoff
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.first

/** State that belongs to the app shell rather than a screen: playlists handed over by other apps. */
class AppViewModel(
    private val repo: IptvRepository,
    private val playback: PlaybackSession,
    private val ready: Job,
) : ViewModel() {

    /** Why a handed-over playlist couldn't be opened; cleared once seen. */
    var importError by mutableStateOf<String?>(null)

    /**
     * Stores what [uri] points at and returns the source to open, or null (with
     * [importError] set) when it couldn't be. A file without channels is refused
     * rather than stored, so it can't leave an empty tile on Home.
     */
    suspend fun importPlaylist(ctx: Context, uri: Uri): String? {
        ready.join()
        val source = when (val h = readHandoff(ctx, uri)) {
            is Handoff.File -> repo.importFile(h.name, h.text)
                ?: run { importError = "“${h.name}” doesn't contain any channels."; return null }
            is Handoff.Link -> repo.sources.first().firstOrNull { it.url == h.url } ?: repo.addUrlSource(h.name, h.url)
            is Handoff.Failed -> { importError = h.message; return null }
        }
        playback.stop()   // the new list takes the screen, whatever was playing
        return source.id
    }
}
