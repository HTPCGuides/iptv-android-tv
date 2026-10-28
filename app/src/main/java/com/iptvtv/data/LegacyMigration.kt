package com.iptvtv.data

import android.content.Context
import com.iptvtv.data.db.FavouriteEntity
import com.iptvtv.data.db.IptvDao
import com.iptvtv.data.db.RecentEntity
import com.iptvtv.model.Channel
import com.iptvtv.model.Source
import com.iptvtv.model.SourceKind
import kotlinx.coroutines.flow.first
import java.io.File

/**
 * Carries the SharedPreferences-and-files era over into Room and DataStore, once.
 *
 * Before Room, favourites, recents, custom playlists and the settings lived in the
 * "iptv" preferences file, downloaded playlists as files/playlists/<id>.m3u and
 * handed-over files as files/imported/<id>.m3u. Installed phones have favourites in
 * there; losing them would be the first thing anyone noticed about the new build.
 */
class LegacyMigration(
    private val ctx: Context,
    private val dao: IptvDao,
    private val settings: SettingsDataSource,
) {
    private val sep = ""   // the old "name<SEP>url" row delimiter

    suspend fun runIfNeeded() {
        if (settings.legacyMigrated.first()) return
        val prefs = ctx.getSharedPreferences("iptv", Context.MODE_PRIVATE)
        val playlists = File(ctx.filesDir, "playlists")
        val imported = File(ctx.filesDir, "imported")

        // Custom sources: URL rows and "imported:<name>" rows.
        var position = dao.nextSourcePosition()
        (prefs.getString("custom", "") ?: "").split("\n").filter { it.isNotBlank() }.forEach { row ->
            val p = row.split(sep)
            if (p.size < 2) return@forEach
            val (name, url) = p[0] to p[1]
            val oldId = "c_" + url.hashCode()
            if (url.startsWith("imported:")) {
                val copy = File(imported, "$oldId.m3u")
                val fileName = url.removePrefix("imported:")
                val id = Source.idFor("file:$fileName")
                val channels = if (copy.exists()) M3uParser.parse(copy.readText(), id) else emptyList()
                if (channels.isNotEmpty() && dao.getSource(id) == null) {
                    dao.upsertSource(Source(id, name, fileName, SourceKind.FILE, "📃").toEntity(position++))
                    dao.replaceChannels(id, channels.toEntities(), fetchedAt = copy.lastModified())
                }
            } else if (dao.getSource(oldId) == null) {
                dao.upsertSource(Source(oldId, name, url, SourceKind.URL, "📃").toEntity(position++))
            }
        }

        // Downloaded playlists: keep what is on disk rather than re-downloading on first open.
        dao.sources().first().filter { it.kind != SourceKind.FILE.name }.forEach { s ->
            val f = File(playlists, "${s.id}.m3u")
            if (f.exists() && dao.channelCount(s.id) == 0) {
                val channels = M3uParser.parse(f.readText(), s.id)
                if (channels.isNotEmpty()) dao.replaceChannels(s.id, channels.toEntities(), fetchedAt = f.lastModified())
            }
        }

        // Favourites: "source|clean name" rows, plus older "source|url" ones resolved against what is now stored.
        val now = System.currentTimeMillis()
        (prefs.getStringSet("favs", emptySet()) ?: emptySet()).forEach { key ->
            val sourceId = key.substringBefore('|')
            val rest = key.substringAfter('|', "")
            if (rest.isBlank()) return@forEach
            val cleanKey = if (rest.contains("://")) {
                dao.channels(listOf(sourceId), "", null, false).first().firstOrNull { it.url == rest }?.cleanKey ?: return@forEach
            } else Channel.cleanKey(rest)
            dao.addFavourite(FavouriteEntity(sourceId, cleanKey, now))
        }

        // Recents: most recent first in the old string; give them descending timestamps.
        (prefs.getString("recents", "") ?: "").split("\n").filter { it.contains('|') }
            .forEachIndexed { i, key ->
                dao.upsertRecent(RecentEntity(key.substringBefore('|'), key.substringAfter('|'), now - i))
            }

        settings.setHideGeoBlocked(prefs.getBoolean("hideGeo", false))
        settings.setSubtitlesOn(prefs.getBoolean("subs", false))
        settings.setPrebufferMs(prefs.getInt("prebuffer", 2_500))
        settings.markLegacyMigrated()

        playlists.deleteRecursively()
        imported.deleteRecursively()
        prefs.edit().clear().apply()
    }
}
