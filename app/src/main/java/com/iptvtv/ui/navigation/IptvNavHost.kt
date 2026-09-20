package com.iptvtv.ui.navigation

import android.app.Activity
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
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
 * Immersive playback, decided by where the navigation is rather than by which composable
 * happens to be disposing: the player hides the status and navigation bars, every other
 * destination shows them. Keyed on the destination, so the bars come back the moment the
 * player is popped — including when it leaves through a hand-off from another app.
 */
@Composable
private fun SystemBarsForDestination(navController: NavHostController) {
    val entry by navController.currentBackStackEntryAsState()
    val immersive = entry?.destination?.hasRoute<PlayerRoute>() == true
    val view = LocalView.current
    DisposableEffect(immersive) {
        val window = (view.context as? Activity)?.window
        val bars = window?.let { WindowInsetsControllerCompat(it, view) }
        if (immersive) {
            bars?.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            bars?.hide(WindowInsetsCompat.Type.systemBars())
        } else {
            bars?.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_DEFAULT
            bars?.show(WindowInsetsCompat.Type.systemBars())
        }
        onDispose { bars?.show(WindowInsetsCompat.Type.systemBars()) }
    }
}
