package com.iptvtv.ui.navigation

import kotlinx.serialization.Serializable

@Serializable
object HomeRoute

@Serializable
data class ChannelsRoute(val sourceId: String)

/** What is playing lives in the app-wide [com.iptvtv.data.PlaybackSession], so the route needs no argument. */
@Serializable
object PlayerRoute
