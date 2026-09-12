package com.iptvtv

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.lifecycle.viewmodel.compose.viewModel

private enum class Route { HOME, CHANNELS, PLAYER }

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            IptvTheme {
                Surface { App() }
            }
        }
    }
}

@Composable
private fun App() {
    val vm: IptvViewModel = viewModel()
    var route by remember { mutableStateOf(Route.HOME) }
    // Where leaving the player goes back to — Home rows start playback too.
    var playerReturn by remember { mutableStateOf(Route.CHANNELS) }

    BackHandler(enabled = route != Route.HOME) {
        route = when (route) {
            Route.PLAYER -> { vm.stop(); playerReturn }
            else -> Route.HOME
        }
    }

    when (route) {
        Route.HOME -> HomeScreen(
            vm,
            onOpen = { source ->
                vm.open(source)
                route = Route.CHANNELS
            },
            onPlay = { playerReturn = Route.HOME; route = Route.PLAYER },
        )
        Route.CHANNELS -> ChannelsScreen(
            vm,
            onPlay = { playerReturn = Route.CHANNELS; route = Route.PLAYER },
            onBack = { route = Route.HOME },
        )
        Route.PLAYER -> PlayerScreen(
            vm,
            exitLabel = if (playerReturn == Route.HOME) "Back to home" else "Back to channels",
        ) { vm.stop(); route = playerReturn }
    }
}
