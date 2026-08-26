package com.iptvtv

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Cast
import androidx.compose.material.icons.filled.CastConnected
import androidx.compose.material.icons.rounded.ClosedCaption
import androidx.compose.material.icons.rounded.ClosedCaptionOff
import androidx.compose.material.icons.rounded.Public
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material.icons.rounded.Stop
import androidx.compose.material.icons.rounded.WarningAmber
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.style.TextAlign
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.Tracks
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
    var showPicker by remember { mutableStateOf(false) }
    var reloads by remember { mutableStateOf(0) }
    var hasSubtitles by remember { mutableStateOf(false) }
    val focus = remember { FocusRequester() }
    val stopFocus = remember { FocusRequester() }
    val overlayFocus = remember { FocusRequester() }
    val cast = rememberCastController()
    val cs = MaterialTheme.colorScheme
    val reducedMotion = LocalReducedMotion.current

    val player = remember {
        ExoPlayer.Builder(ctx).build().apply {
            playWhenReady = true
            addListener(object : Player.Listener {
                override fun onPlaybackStateChanged(state: Int) {
                    buffering = state == Player.STATE_BUFFERING
                    if (state == Player.STATE_READY) error = null
                }
                override fun onTracksChanged(tracks: Tracks) {
                    // Only offer the control when the stream actually carries text —
                    // a subtitle button that does nothing is worse than none at all.
                    hasSubtitles = tracks.groups.any { it.type == C.TRACK_TYPE_TEXT }
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

    // (re)load whenever the selected channel changes — also covers channel up/down and retry
    LaunchedEffect(channel.key, cast.casting, reloads) {
        error = null; showInfo = true
        if (cast.casting) {
            // The TV is the screen now. Stop decoding locally — a phone quietly
            // burning battery on a stream nobody is watching is the bug every
            // cast implementation ships first.
            player.pause()
            buffering = false
            if (CastController.castable(channel.url)) cast.play(channel.url, channel.clean)
            else error = "This channel is raw MPEG-TS, which a Chromecast can't play. " +
                "It works locally — disconnect to watch it here."
        } else {
            buffering = true
            player.setMediaItem(MediaItem.fromUri(channel.url))
            player.prepare()
            player.play()
        }
        delay(4000)
        showInfo = false
    }
    // Apply the remembered subtitle choice to every stream, including after a zap.
    LaunchedEffect(vm.subtitlesOn, channel.key, reloads) {
        player.trackSelectionParameters = player.trackSelectionParameters.buildUpon()
            .setTrackTypeDisabled(C.TRACK_TYPE_TEXT, !vm.subtitlesOn)
            .build()
    }

    // The overlay carries focusable controls; when it fades out, focus has to come
    // back to the player itself or the D-pad is left with nothing to talk to.
    LaunchedEffect(showInfo) {
        if (!showInfo) runCatching { focus.requestFocus() }
    }

    // Immersive playback: hide status/nav bars, restore them when leaving the player.
    val view = LocalView.current
    DisposableEffect(Unit) {
        focus.requestFocus()
        val window = (view.context as? android.app.Activity)?.window
        val bars = window?.let { WindowInsetsControllerCompat(it, view) }
        bars?.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        bars?.hide(WindowInsetsCompat.Type.systemBars())
        onDispose {
            bars?.show(WindowInsetsCompat.Type.systemBars())
            player.release()
        }
    }

    Box(
        Modifier.fillMaxSize().background(Color.Black)
            // Tap to reveal the overlay. The D-pad centre already did this; on a
            // phone or tablet there was no way in at all, which is where the
            // cast button now lives.
            .pointerInput(Unit) { detectTapGestures { showInfo = !showInfo } }
            .focusRequester(focus).focusable()
            .onKeyEvent { ev ->
                if (ev.type != KeyEventType.KeyDown) return@onKeyEvent false
                when (ev.key) {
                    Key.DirectionUp, Key.ChannelUp -> { vm.step(-1); true }
                    Key.DirectionDown, Key.ChannelDown -> { vm.step(1); true }
                    // Centre retries a failed stream; otherwise it toggles the info bar.
                    Key.DirectionCenter, Key.Enter -> {
                        if (error != null) reloads++ else showInfo = !showInfo
                        true
                    }
                    // The player itself holds focus so it can hear these keys, which
                    // means the D-pad can't wander into the overlay on its own. With
                    // the bar up, left and right hand focus over deliberately.
                    Key.DirectionLeft -> {
                        if (showInfo) { runCatching { stopFocus.requestFocus() }; true } else false
                    }
                    Key.DirectionRight -> {
                        if (showInfo) { runCatching { overlayFocus.requestFocus() }; true } else false
                    }
                    Key.Back -> { onExit(); true }
                    else -> false
                }
            },
    ) {
        AndroidView(
            modifier = Modifier.fillMaxSize(),
            factory = { c ->
                PlayerView(c).apply {
                    useController = false
                    keepScreenOn = true   // live TV: never let the screen sleep mid-stream
                    this.player = player
                }
            },
        )

        if (cast.casting) {
            Box(Modifier.fillMaxSize(), Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(Icons.Filled.Cast, null, Modifier.size(48.dp), tint = cs.primary)
                    Spacer(Modifier.height(Space.md))
                    Text(channel.clean, style = MaterialTheme.typography.titleLarge, color = Color.White)
                    Spacer(Modifier.height(Space.sm))
                    Text(
                        "Playing on ${cast.deviceName}",
                        style = MaterialTheme.typography.bodyLarge, color = cs.primary,
                    )
                    Spacer(Modifier.height(Space.xs))
                    Text(
                        "↑↓ changes the channel on the TV",
                        style = MaterialTheme.typography.bodyMedium, color = cs.onSurfaceVariant,
                    )
                }
            }
        }

        if (buffering && error == null && !cast.casting) {
            Box(Modifier.fillMaxSize(), Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    CircularProgressIndicator(color = cs.primary)
                    Spacer(Modifier.height(Space.md))
                    Text("Connecting…", style = MaterialTheme.typography.bodyLarge, color = Color.White)
                }
            }
        }

        error?.let {
            // Scrim: a failed stream can leave the last decoded frame on screen behind this.
            Box(
                Modifier.fillMaxSize().background(cs.scrim.copy(alpha = .78f)),
                contentAlignment = Alignment.Center,
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.padding(horizontal = Space.xxl),
                ) {
                    Icon(Icons.Rounded.WarningAmber, null, tint = cs.tertiary, modifier = Modifier.size(44.dp))
                    Spacer(Modifier.height(Space.md))
                    Text(channel.clean, style = MaterialTheme.typography.titleLarge, color = Color.White)
                    Spacer(Modifier.height(Space.sm))
                    Text(
                        it, style = MaterialTheme.typography.bodyLarge, color = cs.tertiary,
                        textAlign = TextAlign.Center,
                    )
                    Spacer(Modifier.height(Space.lg))
                    Text(
                        "OK to try again   ·   ↑↓ for another channel   ·   Back to the list",
                        style = MaterialTheme.typography.bodyMedium, color = cs.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                    )
                }
            }
        }

        // Leaving the player was Back-only, which is invisible on a phone and easy
        // to miss on a remote. Sits opposite the cast button, on the same overlay.
        AnimatedVisibility(
            visible = showInfo,
            enter = fadeIn(tween(if (reducedMotion) 0 else 180)),
            exit = fadeOut(tween(if (reducedMotion) 0 else 260)),
            modifier = Modifier.align(Alignment.TopStart),
        ) {
            Row(Modifier.padding(Space.xl)) {
                TvActionButton(
                    "Stop", onExit,
                    modifier = Modifier.focusRequester(stopFocus),
                    icon = Icons.Rounded.Stop,
                )
            }
        }

        if (showInfo) {
            Row(
                Modifier.align(Alignment.TopEnd).padding(Space.xl),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                // Always offered, so captions are a control you can find rather than
                // one that appears only on channels that happen to carry them. When
                // this stream has none, say so instead of leaving a dead switch.
                if (vm.subtitlesOn && !hasSubtitles) {
                    Text(
                        "None on this channel",
                        style = MaterialTheme.typography.labelMedium, color = cs.onSurfaceVariant,
                    )
                    Spacer(Modifier.width(Space.sm))
                }
                TvIconButton(
                    icon = if (vm.subtitlesOn) Icons.Rounded.ClosedCaption
                    else Icons.Rounded.ClosedCaptionOff,
                    contentDescription = when {
                        vm.subtitlesOn && !hasSubtitles -> "Subtitles on, but this channel has none"
                        vm.subtitlesOn -> "Turn subtitles off"
                        else -> "Turn subtitles on"
                    },
                    onClick = { vm.toggleSubtitles() },
                    modifier = Modifier.focusRequester(overlayFocus),
                    tint = when {
                        vm.subtitlesOn && !hasSubtitles -> cs.onSurfaceVariant
                        vm.subtitlesOn -> cs.primary
                        else -> Color.White
                    },
                )
                Spacer(Modifier.width(Space.sm))
                // Casting only where it can actually happen. An Android TV box without
                // Play services gets no button rather than a button that fails.
                if (cast.available) {
                    if (cast.connecting) {
                        Text("Connecting…", style = MaterialTheme.typography.labelLarge, color = cs.primary)
                        Spacer(Modifier.width(Space.md))
                    }
                    IconButton(onClick = { if (cast.casting) cast.disconnect() else showPicker = true }) {
                        Icon(
                            if (cast.casting) Icons.Filled.CastConnected else Icons.Filled.Cast,
                            contentDescription = if (cast.casting)
                                "Stop casting to ${cast.deviceName}" else "Cast to a device",
                            tint = if (cast.casting) cs.primary else Color.White,
                            modifier = Modifier.size(28.dp),
                        )
                    }
                }
            }
        }

        if (showPicker) {
            CastPicker(
                cast = cast,
                onPick = { route -> cast.connect(route, channel.url, channel.clean); showPicker = false },
                onDismiss = { showPicker = false },
            )
        }

        AnimatedVisibility(
            visible = showInfo,
            enter = fadeIn(tween(if (reducedMotion) 0 else 180)),
            exit = fadeOut(tween(if (reducedMotion) 0 else 260)),
            modifier = Modifier.align(Alignment.BottomStart),
        ) {
            Column(
                Modifier
                    .fillMaxWidth()
                    // Gradient rather than a hard band: the bar has to sit on moving
                    // picture without cutting a visible edge across it.
                    .background(Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(alpha = .9f))))
                    .padding(horizontal = Space.xl, vertical = Space.xl),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(channel.clean, style = MaterialTheme.typography.headlineSmall, color = Color.White)
                    channel.quality?.let {
                        Spacer(Modifier.width(Space.md))
                        Text(it, style = MaterialTheme.typography.labelLarge, color = cs.primary)
                    }
                    if (channel.geoBlocked) {
                        Spacer(Modifier.width(Space.md))
                        Icon(Icons.Rounded.Public, null, tint = cs.tertiary, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(Space.xs))
                        Text("Geo-blocked", style = MaterialTheme.typography.labelMedium, color = cs.tertiary)
                    }
                    if (vm.isFav(channel)) {
                        Spacer(Modifier.width(Space.md))
                        Icon(Icons.Rounded.Star, null, tint = cs.primary, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(Space.xs))
                        Text("Favourite", style = MaterialTheme.typography.labelMedium, color = cs.primary)
                    }
                }
                Spacer(Modifier.height(Space.xs))
                Text(
                    "${channel.groups.joinToString(" · ")}   ·   ↑↓ change channel   ·   OK info   ·   Back to list",
                    style = MaterialTheme.typography.bodyMedium, color = cs.onSurfaceVariant,
                )
            }
        }
    }
}
