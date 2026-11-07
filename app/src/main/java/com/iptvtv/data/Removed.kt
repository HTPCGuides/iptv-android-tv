package com.iptvtv.data

import com.iptvtv.data.db.ChannelEntity
import com.iptvtv.data.db.FavouriteEntity
import com.iptvtv.data.db.RecentEntity
import com.iptvtv.data.db.SourceEntity

/**
 * What a removal took out, row for row, so Undo can put it back exactly — the same
 * position in Sources, the same channel order, the favourites and recents with it.
 * Opaque outside the data layer: screens only hold it and hand it back.
 */
class RemovedPlaylists internal constructor(internal val rows: List<Snapshot>) {
    val count get() = rows.size

    internal class Snapshot(
        val source: SourceEntity,
        val channels: List<ChannelEntity>,
        val favourites: List<FavouriteEntity>,
        val recents: List<RecentEntity>,
    )
}

/** Channels taken out of fixed playlists (Ace Stream versions), for Undo. */
class RemovedChannels internal constructor(internal val rows: List<ChannelEntity>) {
    /** Feeds removed, counting a feed once however many versions held it. */
    val count get() = rows.distinctBy { it.url }.size
}
