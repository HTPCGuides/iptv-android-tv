package com.iptvtv

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.lifecycle.viewmodel.compose.viewModel

private val scheme = darkColorScheme(
    primary = Accent,
    background = Bg,
    surface = Card,
)

private enum class Route { HOME, CHANNELS, PLAYER }

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme(colorScheme = scheme) {
                Surface { App() }
            }
        }
    }
}

@Composable
private fun App() {
    val vm: IptvViewModel = viewModel()
    var route by remember { mutableStateOf(Route.HOME) }

    BackHandler(enabled = route != Route.HOME) {
        route = when (route) {
            Route.PLAYER -> { vm.stop(); Route.CHANNELS }
            else -> Route.HOME
        }
    }

    when (route) {
        Route.HOME -> HomeScreen(vm) { source ->
            vm.open(source)
            route = Route.CHANNELS
        }
        Route.CHANNELS -> ChannelsScreen(
            vm,
            onPlay = { route = Route.PLAYER },
            onBack = { route = Route.HOME },
        )
        Route.PLAYER -> PlayerScreen(vm) { vm.stop(); route = Route.CHANNELS }
    }
}
