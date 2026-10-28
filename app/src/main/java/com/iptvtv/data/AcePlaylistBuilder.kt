package com.iptvtv.data

import com.iptvtv.ace.EngineAddress
import com.iptvtv.ace.EngineClient
import com.iptvtv.ace.EngineError
import com.iptvtv.ace.Generator
import com.iptvtv.ace.Profiles
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext

sealed interface AceBuild {
    data class Built(val text: String, val channels: Int, val sources: Int) : AceBuild
    data class Failed(val message: String) : AceBuild
}

/**
 * Builds the Ace Stream playlist live on this device, the way Ace M3U and the
 * m3u_gen_acestream tool do: read the engine's whole channel index, filter it with the
 * tool's default "everything" profile, and render one MPEG-TS entry per source. The player
 * plays those through the engine's HLS endpoint (see [AceStream]).
 */
class AcePlaylistBuilder(
    private val client: EngineClient,
    private val default: CoroutineDispatcher,
    private val engineAddr: String = EngineAddress.LOCAL,
) {
    suspend fun build(onProgress: (channels: Int) -> Unit = {}): AceBuild {
        try {
            client.info(engineAddr)
        } catch (e: EngineError) {
            return AceBuild.Failed("Ace Stream's engine isn't answering. Open Ace Stream, then try again.")
        }
        val read = client.searchAll(engineAddr) { _, channels, _ -> onProgress(channels) }
        if (read.channels.isEmpty()) {
            return AceBuild.Failed(read.error?.message ?: "The engine's channel index was empty.")
        }
        return withContext(default) {
            val profile = Generator.compile(Profiles.MPEGTS_ALL)
            val playlist = Generator.render(Generator.select(read.channels, profile), profile, engineAddr)
            AceBuild.Built(playlist.text, read.channels.size, playlist.entries.size)
        }
    }
}
