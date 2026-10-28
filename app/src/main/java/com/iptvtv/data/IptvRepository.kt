package com.iptvtv.data

import com.iptvtv.model.Channel
import com.iptvtv.model.Settings
import com.iptvtv.model.Source
import kotlinx.coroutines.flow.Flow

/** What the channel list shows of a source. */
data class ChannelFilter(
    val query: String = "",
    val group: String? = null,
    val hideGeoBlocked: Boolean = false,
)

sealed interface RefreshResult {
    /** Channels were fetched and stored. */
    data object Updated : RefreshResult
    /** The stored channels are recent enough, or the source is a file. */
    data object Fresh : RefreshResult
    /** The fetch failed; whatever was stored before is still there. */
    data class Failed(val message: String) : RefreshResult
}

/**
 * The app's single data API. Everything is exposed as a Flow from the local database,
 * so a screen reacts to a download, an import or a favourite without being told.
 */
interface IptvRepository {
    val sources: Flow<List<Source>>
    fun source(id: String): Flow<Source?>

    /** Channels of one source, or of several shown as one list (a feed in two of them appears once). */
    fun channels(sourceIds: List<String>, filter: ChannelFilter): Flow<List<Channel>>
    /** Every group named in a source's playlist, most-populated first. */
    fun groups(sourceIds: List<String>): Flow<List<String>>
    fun channels(sourceId: String, filter: ChannelFilter): Flow<List<Channel>> = channels(listOf(sourceId), filter)
    fun groups(sourceId: String): Flow<List<String>> = groups(listOf(sourceId))

    /** Favourite keys ([Channel.favKey]). */
    val favourites: Flow<Set<String>>
    /** Favourites resolved to a channel each, for the Home row. */
    val favouriteChannels: Flow<List<Channel>>
    /** Most recently watched first. */
    val recentChannels: Flow<List<Channel>>

    val settings: Flow<Settings>

    /** Fetches a source's playlist if it is stale (or [force]) and stores its channels. */
    suspend fun refresh(source: Source, force: Boolean = false): RefreshResult

    suspend fun addUrlSource(name: String, url: String): Source
    /** Stores a handed-over playlist file's channels. Null when it holds no channels — nothing is stored then. */
    suspend fun importFile(name: String, text: String): Source?
    suspend fun renameSource(id: String, name: String)
    suspend fun removeSource(id: String)

    suspend fun toggleFavourite(channel: Channel)

    /**
     * Saves a freshly built Ace Stream playlist as a new version, named for the moment it was
     * built. Older versions are pruned when [Settings.aceKeepVersions] says so. Null when the
     * playlist held no channels — nothing is saved then.
     */
    suspend fun saveAceVersion(text: String): Source?
    suspend fun removeSources(ids: Collection<String>)
    suspend fun setAceKeepVersions(n: Int)
    suspend fun noteWatched(channel: Channel)

    suspend fun setHideGeoBlocked(on: Boolean)
    suspend fun setSubtitlesOn(on: Boolean)
    suspend fun setPrebufferMs(ms: Int)

    /** Bytes held by downloaded channel lists and logos — what [clearCache] frees. */
    suspend fun cacheBytes(): Long
    /** Drops downloaded channels and logos. Favourites, playlists and handed-over files are kept. */
    suspend fun clearCache()
}
