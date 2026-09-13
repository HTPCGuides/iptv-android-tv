package com.iptvtv

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.platform.LocalContext
import androidx.navigation.compose.rememberNavController
import com.iptvtv.data.handedUri
import com.iptvtv.ui.AppViewModel
import com.iptvtv.ui.IptvTheme
import com.iptvtv.ui.containerViewModel
import com.iptvtv.ui.navigation.ChannelsRoute
import com.iptvtv.ui.navigation.HomeRoute
import com.iptvtv.ui.navigation.IptvNavHost

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
    val ctx = LocalContext.current
    val navController = rememberNavController()
    val appViewModel: AppViewModel = containerViewModel { c, _ -> AppViewModel(c.repository, c.playback, c.ready) }

    // A playlist handed over by another app opens on its own channel list, from wherever
    // the user was, the player included.
    val incoming = handoff.value
    LaunchedEffect(incoming) {
        val uri = handedUri(incoming) ?: return@LaunchedEffect
        val sourceId = appViewModel.importPlaylist(ctx, uri) ?: return@LaunchedEffect
        navController.navigate(ChannelsRoute(sourceId)) { popUpTo(HomeRoute) }
    }

    IptvNavHost(navController)

    appViewModel.importError?.let { message ->
        AlertDialog(
            onDismissRequest = { appViewModel.importError = null },
            title = { Text("Couldn't open that playlist") },
            text = { Text(message) },
            confirmButton = { TextButton(onClick = { appViewModel.importError = null }) { Text("OK") } },
        )
    }
}
