package com.iptvtv.ui.navigation

import androidx.compose.runtime.Composable
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import com.iptvtv.ui.channels.ChannelsRoute as ChannelsScreenRoute
import com.iptvtv.ui.home.HomeRoute as HomeScreenRoute
import com.iptvtv.ui.player.PlayerRoute as PlayerScreenRoute

@Composable
fun IptvNavHost(navController: NavHostController) {
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
