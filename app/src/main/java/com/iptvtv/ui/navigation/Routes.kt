package com.iptvtv.ui.navigation

import kotlinx.serialization.Serializable

@Serializable
object HomeRoute

@Serializable
data class ChannelsRoute(
    /** One playlist, or several opened together as one channel list. */
    val sourceIds: List<String>,
) {
    constructor(sourceId: String) : this(listOf(sourceId))
}

/** What is playing lives in the app-wide [com.iptvtv.data.PlaybackSession], so the route needs no argument. */
@Serializable
object PlayerRoute
