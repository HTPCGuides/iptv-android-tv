package com.iptvtv

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.viewmodel.compose.viewModel

private enum class Route { HOME, CHANNELS, PLAYER }

class MainActivity : ComponentActivity() {
    /**
     * The intent another app last opened us with. The activity is singleTask, so a playlist
     * handed over while the app is already running arrives here through onNewIntent.
     */
    private val handoff = mutableStateOf<Intent?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // A recreated activity still holds the intent it was first started with; importing
        // that again would pull the user back to a list they had already left.
        if (savedInstanceState == null) handoff.value = intent
        setContent {
            IptvTheme {
                Surface { App(handoff) }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handoff.value = intent
    }
}

@Composable
private fun App(handoff: State<Intent?>) {
    val vm: IptvViewModel = viewModel()
    val ctx = LocalContext.current
    var route by remember { mutableStateOf(Route.HOME) }
    // Where leaving the player goes back to — Home rows start playback too.
    var playerReturn by remember { mutableStateOf(Route.CHANNELS) }

    // A playlist handed over by another app opens on its own channel list, from wherever
    // the user was, the player included.
    val incoming = handoff.value
    LaunchedEffect(incoming) {
        val uri = handedUri(incoming) ?: return@LaunchedEffect
        val opened = when (val h = readHandoff(ctx, uri)) {
            is Handoff.File -> vm.importPlaylist(h.name, h.text)
            is Handoff.Link -> { vm.followPlaylistLink(h.name, h.url); true }
            is Handoff.Failed -> { vm.importError = h.message; false }
        }
        if (opened) {
            vm.stop()
            route = Route.CHANNELS
        }
    }

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

    vm.importError?.let { message ->
        AlertDialog(
            onDismissRequest = { vm.importError = null },
            title = { Text("Couldn't open that playlist") },
            text = { Text(message) },
            confirmButton = { TextButton(onClick = { vm.importError = null }) { Text("OK") } },
        )
    }
}
