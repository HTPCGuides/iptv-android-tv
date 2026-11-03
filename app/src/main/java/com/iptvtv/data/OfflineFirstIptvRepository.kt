package com.iptvtv.data

import com.iptvtv.data.db.ChannelEntity
import com.iptvtv.data.db.FavouriteEntity
import com.iptvtv.data.db.IptvDao
import com.iptvtv.data.db.RecentEntity
import com.iptvtv.data.db.SourceEntity
import com.iptvtv.model.Channel
import com.iptvtv.model.Settings
import com.iptvtv.model.Source
import com.iptvtv.model.SourceKind
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import java.io.File

/** How long a downloaded playlist is trusted before the next open re-fetches it. */
private const val FRESH_FOR_MS = 24L * 60 * 60 * 1000
private const val RECENTS_SHOWN = 10
private const val RECENTS_KEPT = 20

class OfflineFirstIptvRepository(
    private val dao: IptvDao,
    private val fetcher: PlaylistFetcher,
    private val settingsStore: SettingsDataSource,
    /** Where logos and other transient downloads land; swept by [clearCache]. */
    private val cacheDirs: List<File>,
    private val now: () -> Long = System::currentTimeMillis,
) : IptvRepository {

    /** The built-ins are rows too, so ordering and fetch times live in one place. Idempotent. */
    suspend fun seedBuiltIns() {
        Source.BUILT_IN.forEachIndexed { i, s ->
            if (dao.getSource(s.id) == null) dao.upsertSource(s.toEntity(position = i))
        }
    }

    override val sources: Flow<List<Source>> = dao.sources().map { rows -> rows.map { it.toModel() } }

    override fun source(id: String): Flow<Source?> = dao.source(id).map { it?.toModel() }

    override fun channels(sourceIds: List<String>, filter: ChannelFilter): Flow<List<Channel>> =
        dao.channels(sourceIds, filter.query.trim(), filter.group, filter.hideGeoBlocked)
            .map { rows ->
                val list = rows.map { it.toModel() }
                // Merged versions of one playlist carry the same feeds; show each once.
                if (sourceIds.size > 1) list.distinctBy { it.url } else list
            }

    override fun groups(sourceIds: List<String>): Flow<List<String>> =
        dao.channels(sourceIds, "", null, false).map { rows ->
            rows.flatMap { Channel.splitGroups(it.groupTitle) }
                .groupingBy { it }.eachCount().entries
                .sortedByDescending { it.value }.map { it.key }
        }

    override val favourites: Flow<Set<String>> =
        dao.favourites().map { rows -> rows.map { "${it.sourceId}|${it.cleanKey}" }.toSet() }

    override val favouriteChannels: Flow<List<Channel>> =
        dao.favouriteChannels().map { rows -> rows.map { it.toModel() } }

    override val recentChannels: Flow<List<Channel>> =
        dao.recentChannels(RECENTS_SHOWN).map { rows -> rows.map { it.toModel() } }

    override val settings: Flow<Settings> = settingsStore.settings

    override suspend fun refresh(source: Source, force: Boolean): RefreshResult {
        if (!source.refetchable) return RefreshResult.Fresh
        val row = dao.getSource(source.id) ?: return RefreshResult.Failed("This playlist was removed.")
        val stale = now() - row.fetchedAt > FRESH_FOR_MS || dao.channelCount(source.id) == 0
        if (!force && !stale) return RefreshResult.Fresh
        val text = try {
            fetcher.fetch(source.url)
        } catch (e: Exception) {
            return RefreshResult.Failed("Couldn't download this playlist. Check the connection and try Refresh.")
        }
        val channels = M3uParser.parse(text, source.id)
        if (channels.isEmpty()) return RefreshResult.Failed("The playlist downloaded but contained no channels.")
        dao.replaceChannels(source.id, channels.toEntities(), fetchedAt = now())
        return RefreshResult.Updated
    }

    override suspend fun addUrlSource(name: String, url: String): Source {
        val trimmed = url.trim()
        val existing = dao.getSource(Source.idFor(trimmed))
        if (existing != null) return existing.toModel()
        val source = Source(Source.idFor(trimmed), sourceName(name), trimmed, SourceKind.URL, "📃")
        dao.upsertSource(source.toEntity(position = dao.nextSourcePosition()))
        return source
    }

    override suspend fun importFile(name: String, text: String): Source? {
        // Keyed by file name, so the same file handed over again replaces its channels
        // but keeps the row — and with it a rename and any favourites made meanwhile.
        val id = Source.idFor("file:" + name.lowercase())
        val channels = M3uParser.parse(text, id)
        if (channels.isEmpty()) return null
        val existing = dao.getSource(id)
        val source = existing?.toModel()
            ?: Source(id, sourceName(name), name, SourceKind.FILE, "📃").also {
                dao.upsertSource(it.toEntity(position = dao.nextSourcePosition()))
            }
        dao.replaceChannels(id, channels.toEntities(), fetchedAt = now())
        return source
    }

    override suspend fun renameSource(id: String, name: String) = dao.renameSource(id, sourceName(name))

    override suspend fun removeSource(id: String) = dao.deleteSource(id)

    override suspend fun saveAceVersion(text: String): Source? {
        val at = now()
        val id = AceVersionName.id(at)
        val channels = M3uParser.parse(text, id)
        if (channels.isEmpty()) return null
        val source = Source(id, AceVersionName.title(at), AceVersionName.fileName(at), SourceKind.ACE, "🛰")
        dao.upsertSource(source.toEntity(position = dao.nextSourcePosition()))
        dao.replaceChannels(id, channels.toEntities(), fetchedAt = at)
        val keep = settingsStore.settings.first().aceKeepVersions
        if (keep > 0) dao.aceVersions().drop(keep).forEach { dao.deleteSource(it.id) }
        return source
    }

    override suspend fun removeSources(ids: Collection<String>) = ids.forEach { dao.deleteSource(it) }

    override suspend fun setAceKeepVersions(n: Int) {
        settingsStore.setAceKeepVersions(n)
        if (n > 0) dao.aceVersions().drop(n).forEach { dao.deleteSource(it.id) }
    }

    override suspend fun setAceEngineAddr(addr: String) = settingsStore.setAceEngineAddr(addr)

    override suspend fun toggleFavourite(channel: Channel) {
        val cleanKey = Channel.cleanKey(channel.name)
        val scope = Channel.favouriteScope(channel.sourceId)
        if (favourites.first().contains(channel.favKey)) dao.removeFavourite(scope, cleanKey)
        else dao.addFavourite(FavouriteEntity(scope, cleanKey, now()))
    }

    override suspend fun noteWatched(channel: Channel) {
        dao.upsertRecent(RecentEntity(channel.sourceId, channel.url, now()))
        dao.trimRecents(RECENTS_KEPT)
    }

    override suspend fun setHideGeoBlocked(on: Boolean) = settingsStore.setHideGeoBlocked(on)
    override suspend fun setSubtitlesOn(on: Boolean) = settingsStore.setSubtitlesOn(on)
    override suspend fun setPrebufferMs(ms: Int) = settingsStore.setPrebufferMs(ms)

    override suspend fun cacheBytes(): Long =
        cacheDirs.sumOf { dir -> dir.walkBottomUp().filter { it.isFile }.sumOf { it.length() } }

    override suspend fun clearCache() {
        dao.deleteDownloadedChannels()
        dao.forgetFetchTimes()
        cacheDirs.forEach { dir -> dir.listFiles()?.forEach { it.deleteRecursively() } }
    }

    private fun sourceName(name: String) = name.trim().ifBlank { "My playlist" }
}

// ---- mapping ----

fun SourceEntity.toModel() = Source(id, name, url, SourceKind.valueOf(kind), emoji)

fun Source.toEntity(position: Int, fetchedAt: Long = 0) =
    SourceEntity(id, name, url, kind.name, emoji, position, fetchedAt)

fun ChannelEntity.toModel() = Channel(name, url, logo, groupTitle, tvgId, sourceId)

fun List<Channel>.toEntities(): List<ChannelEntity> = mapIndexed { i, c ->
    ChannelEntity(
        sourceId = c.sourceId, position = i, name = c.name, cleanKey = Channel.cleanKey(c.name),
        url = c.url, logo = c.logo, groupTitle = c.group, tvgId = c.tvgId,
    )
}
