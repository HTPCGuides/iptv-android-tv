package com.iptvtv.ui.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import com.iptvtv.ui.SystemBars
import com.iptvtv.ui.channels.ChannelsRoute as ChannelsScreenRoute
import com.iptvtv.ui.home.HomeRoute as HomeScreenRoute
import com.iptvtv.ui.player.PlayerRoute as PlayerScreenRoute

@Composable
fun IptvNavHost(navController: NavHostController) {
    SystemBarsForDestination(navController)
    NavHost(navController = navController, startDestination = HomeRoute) {
        composable<HomeRoute> {
            HomeScreenRoute(
                onOpenSource = { navController.navigate(ChannelsRoute(it.id)) },
                onPlay = { navController.navigate(PlayerRoute) },
            )
        }
        composable<ChannelsRoute> {
            ChannelsScreenRoute(
                onPlay = { navController.navigate(PlayerRoute) },
                onBack = { navController.popBackStack() },
            )
        }
        composable<PlayerRoute> {
            // The back control names where it goes: the player is reached from Home's
            // rows as well as from a channel list.
            val fromChannels = navController.previousBackStackEntry?.destination?.hasRoute<ChannelsRoute>() == true
            PlayerScreenRoute(
                exitLabel = if (fromChannels) "Back to channels" else "Back to home",
                onExit = { navController.popBackStack() },
            )
        }
    }
}

/**
 * Every destination but the player shows Android's bars. The player decides for itself,
 * following its own overlay (see PlayerScreen); this is the backstop that brings the bars
 * back the moment it is popped, whichever way it leaves — including through a playlist
 * hand-off from another app.
 */
@Composable
private fun SystemBarsForDestination(navController: NavHostController) {
    val entry by navController.currentBackStackEntryAsState()
    val onPlayer = entry?.destination?.hasRoute<PlayerRoute>() == true
    if (!onPlayer) SystemBars(hidden = false)
}
