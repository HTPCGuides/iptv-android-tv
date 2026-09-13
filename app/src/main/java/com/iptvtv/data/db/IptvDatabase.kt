package com.iptvtv.data.db

import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.Index
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.RoomDatabase
import androidx.room.Transaction
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

/*
 * The local source of truth. Playlists are parsed into `channels` as they are
 * downloaded or handed over; every screen reads from here and never from the network.
 */

@Entity(tableName = "sources")
data class SourceEntity(
    @PrimaryKey val id: String,
    val name: String,
    val url: String,
    /** A [com.iptvtv.model.SourceKind] name. */
    val kind: String,
    val emoji: String,
    /** Display order: built-ins first, then user additions in the order they were added. */
    val position: Int,
    /** When the channels were last fetched, epoch ms; 0 for never. Meaningless for a file. */
    val fetchedAt: Long = 0,
)

@Entity(tableName = "channels", indices = [Index("sourceId")])
data class ChannelEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val sourceId: String,
    /** Order within the playlist. */
    val position: Int,
    val name: String,
    /** [com.iptvtv.model.Channel.cleanKey] of [name], stored so favourites can be joined in SQL. */
    val cleanKey: String,
    val url: String,
    val logo: String?,
    val groupTitle: String,
    val tvgId: String?,
)

/** A favourite is a channel by name within a source, not a feed — see Channel.favKey. */
@Entity(tableName = "favourites", primaryKeys = ["sourceId", "cleanKey"])
data class FavouriteEntity(val sourceId: String, val cleanKey: String, val addedAt: Long)

@Entity(tableName = "recents", primaryKeys = ["sourceId", "url"])
data class RecentEntity(val sourceId: String, val url: String, val watchedAt: Long)

@Dao
interface IptvDao {
    // ---- sources ----
    @Query("SELECT * FROM sources ORDER BY position")
    fun sources(): Flow<List<SourceEntity>>

    @Query("SELECT * FROM sources WHERE id = :id")
    fun source(id: String): Flow<SourceEntity?>

    @Query("SELECT * FROM sources WHERE id = :id")
    suspend fun getSource(id: String): SourceEntity?

    @Query("SELECT COALESCE(MAX(position), -1) + 1 FROM sources")
    suspend fun nextSourcePosition(): Int

    @Upsert
    suspend fun upsertSource(source: SourceEntity)

    @Query("UPDATE sources SET name = :name WHERE id = :id")
    suspend fun renameSource(id: String, name: String)

    @Query("UPDATE sources SET fetchedAt = :fetchedAt WHERE id = :id")
    suspend fun setFetchedAt(id: String, fetchedAt: Long)

    @Query("DELETE FROM sources WHERE id = :id")
    suspend fun deleteSourceRow(id: String)

    @Query("DELETE FROM favourites WHERE sourceId = :sourceId")
    suspend fun deleteFavouritesOf(sourceId: String)

    @Query("DELETE FROM recents WHERE sourceId = :sourceId")
    suspend fun deleteRecentsOf(sourceId: String)

    /** Removes a source and everything that referred to it. */
    @Transaction
    suspend fun deleteSource(id: String) {
        deleteChannelsOf(id)
        deleteFavouritesOf(id)
        deleteRecentsOf(id)
        deleteSourceRow(id)
    }

    // ---- channels ----
    /**
     * A source's channels, filtered in SQL. Search matches name or group; a group
     * matches one of the ';'-separated entries exactly; geo-blocked channels are
     * recognised by iptv-org's "[Geo-blocked]" annotation in the name.
     */
    @Query(
        """
        SELECT * FROM channels
        WHERE sourceId = :sourceId
          AND (:query = '' OR name LIKE '%' || :query || '%' OR groupTitle LIKE '%' || :query || '%')
          AND (:group IS NULL OR (';' || groupTitle || ';') LIKE '%;' || :group || ';%')
          AND (NOT :hideGeoBlocked OR name NOT LIKE '%[Geo-blocked]%')
        ORDER BY position
        """,
    )
    fun channels(sourceId: String, query: String, group: String?, hideGeoBlocked: Boolean): Flow<List<ChannelEntity>>

    @Query("SELECT DISTINCT groupTitle FROM channels WHERE sourceId = :sourceId")
    fun groupTitles(sourceId: String): Flow<List<String>>

    @Query("SELECT COUNT(*) FROM channels WHERE sourceId = :sourceId")
    suspend fun channelCount(sourceId: String): Int

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertChannels(channels: List<ChannelEntity>)

    @Query("DELETE FROM channels WHERE sourceId = :sourceId")
    suspend fun deleteChannelsOf(sourceId: String)

    /** Swaps a source's channels for a freshly parsed list, atomically. */
    @Transaction
    suspend fun replaceChannels(sourceId: String, channels: List<ChannelEntity>, fetchedAt: Long) {
        deleteChannelsOf(sourceId)
        insertChannels(channels)
        setFetchedAt(sourceId, fetchedAt)
    }

    // ---- favourites & recents ----
    @Query("SELECT * FROM favourites")
    fun favourites(): Flow<List<FavouriteEntity>>

    /** One channel per favourite: a favourite is by name, and a playlist may carry that name on several feeds. */
    @Query(
        """
        SELECT c.* FROM channels c
        JOIN favourites f ON f.sourceId = c.sourceId AND f.cleanKey = c.cleanKey
        GROUP BY c.sourceId, c.cleanKey
        ORDER BY c.cleanKey
        """,
    )
    fun favouriteChannels(): Flow<List<ChannelEntity>>

    @Upsert
    suspend fun addFavourite(favourite: FavouriteEntity)

    @Query("DELETE FROM favourites WHERE sourceId = :sourceId AND cleanKey = :cleanKey")
    suspend fun removeFavourite(sourceId: String, cleanKey: String)

    @Query(
        """
        SELECT c.* FROM channels c
        JOIN recents r ON r.sourceId = c.sourceId AND r.url = c.url
        ORDER BY r.watchedAt DESC
        LIMIT :limit
        """,
    )
    fun recentChannels(limit: Int): Flow<List<ChannelEntity>>

    @Upsert
    suspend fun upsertRecent(recent: RecentEntity)

    @Query(
        """
        DELETE FROM recents WHERE (sourceId, url) NOT IN (
            SELECT sourceId, url FROM recents ORDER BY watchedAt DESC LIMIT :keep
        )
        """,
    )
    suspend fun trimRecents(keep: Int)

    // ---- housekeeping ----
    /** Drops downloaded channels so they are fetched again; handed-over files have nowhere to come back from, so they stay. */
    @Query("DELETE FROM channels WHERE sourceId IN (SELECT id FROM sources WHERE kind != 'FILE')")
    suspend fun deleteDownloadedChannels()

    @Query("UPDATE sources SET fetchedAt = 0 WHERE kind != 'FILE'")
    suspend fun forgetFetchTimes()
}

@Database(
    entities = [SourceEntity::class, ChannelEntity::class, FavouriteEntity::class, RecentEntity::class],
    version = 1,
    exportSchema = true,
)
abstract class IptvDatabase : RoomDatabase() {
    abstract fun dao(): IptvDao
}
