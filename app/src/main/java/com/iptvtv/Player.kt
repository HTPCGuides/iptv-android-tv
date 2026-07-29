package com.iptvtv

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import androidx.compose.foundation.focusable
import kotlinx.coroutines.delay

/**
 * Full-screen player. D-pad: up/down = previous/next channel, centre = toggle the
 * info bar, back = exit. Errors are surfaced instead of leaving a black screen —
 * dead streams are common in public IPTV lists.
 */
@androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
@Composable
fun PlayerScreen(vm: IptvViewModel, onExit: () -> Unit) {
    val ctx = LocalContext.current
    val channel = vm.playing ?: return
    var error by remember { mutableStateOf<String?>(null) }
    var buffering by remember { mutableStateOf(true) }
    var showInfo by remember { mutableStateOf(true) }
    val focus = remember { FocusRequester() }

    val player = remember {
        ExoPlayer.Builder(ctx).build().apply {
            playWhenReady = true
            addListener(object : Player.Listener {
                override fun onPlaybackStateChanged(state: Int) {
                    buffering = state == Player.STATE_BUFFERING
                    if (state == Player.STATE_READY) error = null
                }
                override fun onPlayerError(e: PlaybackException) {
                    error = when (e.errorCode) {
                        PlaybackException.ERROR_CODE_IO_BAD_HTTP_STATUS,
                        PlaybackException.ERROR_CODE_IO_INVALID_HTTP_CONTENT_TYPE ->
                            "Stream refused the connection. It may be offline, or geo-blocked from your location."
                        PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_FAILED,
                        PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_TIMEOUT ->
                            "Couldn't reach the stream — check your connection (or VPN)."
                        else -> "This stream didn't play (${e.errorCodeName})."
                    }
                    buffering = false
                }
            })
        }
    }

    // (re)load whenever the selected channel changes — also covers channel up/down
    LaunchedEffect(channel.key) {
        error = null; buffering = true; showInfo = true
        player.setMediaItem(MediaItem.fromUri(channel.url))
        player.prepare()
        delay(4000)
        showInfo = false
    }
    DisposableEffect(Unit) {
        focus.requestFocus()
        onDispose { player.release() }
    }

    Box(
        Modifier.fillMaxSize().background(Color.Black)
            .focusRequester(focus).focusable()
            .onKeyEvent { ev ->
                if (ev.type != KeyEventType.KeyDown) return@onKeyEvent false
                when (ev.key) {
                    Key.DirectionUp, Key.ChannelUp -> { vm.step(-1); true }
                    Key.DirectionDown, Key.ChannelDown -> { vm.step(1); true }
                    Key.DirectionCenter, Key.Enter -> { showInfo = !showInfo; true }
                    Key.Back -> { onExit(); true }
                    else -> false
                }
            },
    ) {
        AndroidView(
            modifier = Modifier.fillMaxSize(),
            factory = { c -> PlayerView(c).apply { useController = false; this.player = player } },
        )

        if (buffering && error == null) {
            Box(Modifier.fillMaxSize(), Alignment.Center) {
                Text("Connecting…", color = Color.White, fontSize = 18.sp)
            }
        }

        error?.let {
            Box(Modifier.fillMaxSize(), Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("⚠", fontSize = 40.sp)
                    Spacer(Modifier.height(8.dp))
                    Text(channel.clean, color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(6.dp))
                    Text(it, color = Color(0xFFFBBF24), fontSize = 14.sp)
                    Spacer(Modifier.height(12.dp))
                    Text("Press ↑ / ↓ for another channel, or Back to the list.", color = Muted, fontSize = 13.sp)
                }
            }
        }

        if (showInfo) {
            Column(
                Modifier.align(Alignment.BottomStart).fillMaxWidth()
                    .background(Color(0xCC000000)).padding(20.dp),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(channel.clean, color = Color.White, fontSize = 24.sp, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.width(12.dp))
                    channel.quality?.let { Text(it, color = Accent, fontSize = 14.sp) }
                    if (channel.geoBlocked) {
                        Spacer(Modifier.width(10.dp))
                        Text("🌍 geo-blocked", color = Color(0xFFFBBF24), fontSize = 13.sp)
                    }
                    Spacer(Modifier.width(12.dp))
                    Text(if (vm.isFav(channel)) "★ favourite" else "", color = Accent, fontSize = 13.sp)
                }
                Spacer(Modifier.height(4.dp))
                Text("${channel.group}   ·   ↑↓ change channel   ·   OK info   ·   Back to list",
                    color = Muted, fontSize = 13.sp)
            }
        }
    }
}
