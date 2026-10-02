@file:OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)

package com.iptvtv.ui.player

import android.app.Activity
import com.iptvtv.ui.Badge
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.rememberScrollState
import android.content.pm.ActivityInfo
import android.content.res.Configuration
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.material.icons.automirrored.rounded.OpenInNew
import androidx.compose.material.icons.rounded.BatterySaver
import com.iptvtv.ui.appContainer
import android.content.pm.PackageManager
import android.os.SystemClock
import android.util.Log
import androidx.compose.material.icons.rounded.ScreenRotation
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.focusable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.filled.Cast
import androidx.compose.material.icons.filled.CastConnected
import androidx.compose.material.icons.rounded.ClosedCaption
import androidx.compose.material.icons.rounded.ClosedCaptionOff
import androidx.compose.material.icons.rounded.Public
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.SkipNext
import androidx.compose.material.icons.rounded.SkipPrevious
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material.icons.rounded.WarningAmber
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
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
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.DefaultHttpDataSource
import com.iptvtv.data.AceStream
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.Tracks
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.exoplayer.upstream.DefaultLoadErrorHandlingPolicy
import androidx.media3.exoplayer.upstream.LoadErrorHandlingPolicy
import androidx.media3.ui.PlayerView
import com.iptvtv.model.DEFAULT_PREBUFFER_MS
import com.iptvtv.ui.LocalReducedMotion
import com.iptvtv.ui.Space
import com.iptvtv.ui.SystemBars
import com.iptvtv.ui.TvActionButton
import com.iptvtv.ui.TvIconButton
import com.iptvtv.ui.containerViewModel
import com.iptvtv.ui.openInAceStream
import kotlinx.coroutines.delay

/** At most this many silent restarts of a dropped stream per [RECOVERY_WINDOW_MS]. */
private const val MAX_RECOVERIES = 3
private const val RECOVERY_WINDOW_MS = 60_000L

/** Errors that mean "the connection went, start over", as opposed to "this can't play". */
private val DROP_OUT_CODES = setOf(
    PlaybackException.ERROR_CODE_IO_READ_POSITION_OUT_OF_RANGE,
    PlaybackException.ERROR_CODE_BEHIND_LIVE_WINDOW,
    PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_FAILED,
    PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_TIMEOUT,
    PlaybackException.ERROR_CODE_IO_UNSPECIFIED,
)

/** Tag on an Ace Stream media item: the getstream URL the playlist named, kept for the fallback. */
private data class AceProgressive(val url: String)

/** On an engine URL these mean the engine itself is not there, rather than the stream. */
private val ENGINE_DOWN_CODES = setOf(
    PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_FAILED,
    PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_TIMEOUT,
    PlaybackException.ERROR_CODE_PARSING_CONTAINER_UNSUPPORTED,
    PlaybackException.ERROR_CODE_PARSING_MANIFEST_MALFORMED,
    PlaybackException.ERROR_CODE_IO_BAD_HTTP_STATUS,
)

@Composable
fun PlayerRoute(
    exitLabel: String,
    onExit: () -> Unit,
    viewModel: PlayerViewModel = containerViewModel { c, _ -> PlayerViewModel(c.repository, c.playback) },
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    // Leaving the screen, by any route, ends playback: there is no background audio. An
    // activity being rebuilt for a configuration change is not leaving — on a foldable,
    // stopping here made the stream vanish at the fold — so playback is kept for the
    // rebuilt screen to pick up.
    val activity = LocalContext.current as? Activity
    DisposableEffect(Unit) {
        onDispose { if (activity?.isChangingConfigurations != true) viewModel.stop() }
    }
    PlayerScreen(
        uiState = uiState,
        exitLabel = exitLabel,
        onExit = onExit,
        onStep = viewModel::step,
        onToggleSubtitles = viewModel::toggleSubtitles,
    )
}

/**
 * Full-screen player. D-pad: up/down = previous/next channel, centre = toggle the
 * info bar, back = exit. Errors are surfaced instead of leaving a black screen —
 * dead streams are common in public IPTV lists.
 *
 * The ExoPlayer lives here rather than in the ViewModel: it is bound to a PlayerView
 * and to this Activity's window, and this screen is the only place it plays.
 */
@androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
@Composable
fun PlayerScreen(
    uiState: PlayerUiState,
    exitLabel: String,
    onExit: () -> Unit,
    onStep: (Int) -> Unit,
    onToggleSubtitles: () -> Unit,
) {
    val playing = uiState as? PlayerUiState.Playing ?: return
    val channel = playing.channel
    val settings = playing.settings
    val ctx = LocalContext.current
    var error by remember { mutableStateOf<String?>(null) }
    // Set with [error] when the failure is the Ace Stream engine being gone: the error
    // screen then offers to open it.
    var engineDown by remember { mutableStateOf(false) }
    var buffering by remember { mutableStateOf(true) }
    var showInfo by remember { mutableStateOf(true) }
    var showPicker by remember { mutableStateOf(false) }
    var reloads by remember { mutableStateOf(0) }
    var hasSubtitles by remember { mutableStateOf(false) }
    // Bumped by every key press and control tap while the bar is up; restarts its
    // hide timer, so the bar never fades out from under someone using it.
    var pokes by remember { mutableStateOf(0) }
    val focus = remember { FocusRequester() }
    val controlsFocus = remember { FocusRequester() }
    val backFocus = remember { FocusRequester() }
    var controlsFocused by remember { mutableStateOf(false) }
    var playerFocused by remember { mutableStateOf(false) }
    // Where a remote lands when a stream fails: on Try again, not on a picture that can't play.
    val retryFocus = remember { FocusRequester() }
    val cast = rememberCastController()
    val cs = MaterialTheme.colorScheme
    val reducedMotion = LocalReducedMotion.current

    // Live streams drop out mid-play. After a network blip Media3 resumes the load with a
    // byte-range request, a live server has nothing at that offset to give back (HTTP 416),
    // and Media3 treats that — ERROR_CODE_IO_READ_POSITION_OUT_OF_RANGE — as fatal. Starting
    // the stream over from the live edge is the fix, so do it quietly; but only a few times a
    // minute, so a stream that is genuinely broken still reaches the error screen.
    val recoveries = remember { ArrayDeque<Long>() }

    // The pre-buffer is read once, when the player is built: a change takes effect from
    // the next channel started, which is what the setting says.
    val player = remember {
        // Progressive live streams — raw MPEG-TS, the Ace Stream engine's getstream — are one
        // endless HTTP body. Media3's default policy retries a failed load from the byte it
        // had reached, with a Range header; a server that ignores Range (the engine does)
        // answers with a fresh stream, and the player then skips through it looking for an
        // offset that never comes, which looks like a stall. So for those streams: no
        // retry. The error surfaces at once and the listener below restarts from live.
        val noRetryForProgressiveLive = object : DefaultLoadErrorHandlingPolicy() {
            override fun getRetryDelayMsFor(loadErrorInfo: LoadErrorHandlingPolicy.LoadErrorInfo): Long =
                if (loadErrorInfo.mediaLoadData.dataType == C.DATA_TYPE_MEDIA_PROGRESSIVE_LIVE) C.TIME_UNSET
                else super.getRetryDelayMsFor(loadErrorInfo)
        }
        // A peer-to-peer engine can go quiet for a while without being gone. Media3's 8 s
        // read timeout turned every such pause into a dropped connection; 30 s waits it out.
        val http = DefaultHttpDataSource.Factory()
            .setConnectTimeoutMs(15_000)
            .setReadTimeoutMs(30_000)
            .setAllowCrossProtocolRedirects(true)
        val sources = DefaultMediaSourceFactory(DefaultDataSource.Factory(ctx, http))
            .setLoadErrorHandlingPolicy(noRetryForProgressiveLive)
        ExoPlayer.Builder(ctx, sources)
            .setLoadControl(loadControlFor(settings.prebufferMs))
            .build().apply {
            playWhenReady = true

            /** Starts the current item over from the live edge, a few times a minute at most. */
            fun restart(why: String): Boolean {
                val now = SystemClock.elapsedRealtime()
                while (recoveries.isNotEmpty() && now - recoveries.first() > RECOVERY_WINDOW_MS) recoveries.removeFirst()
                val item = currentMediaItem ?: return false
                if (recoveries.size >= MAX_RECOVERIES) return false
                recoveries.addLast(now)
                // Nothing on screen marks a silent restart; the log is how to see them.
                Log.i("iptv", "stream $why, restarting from live: ${recoveries.size}/$MAX_RECOVERIES this minute")
                buffering = true
                // Setting the item again is a fresh load: no byte range, live edge.
                setMediaItem(item)
                prepare()
                return true
            }

            addListener(object : Player.Listener {
                override fun onPlaybackStateChanged(state: Int) {
                    buffering = state == Player.STATE_BUFFERING
                    if (state == Player.STATE_READY) error = null
                    // Live TV has no end. A stream that "ends" is a server that closed the
                    // connection — the engine does this between sessions — so start over
                    // rather than sit on a frozen last frame.
                    if (state == Player.STATE_ENDED) {
                        error = when {
                            !isCurrentMediaItemLive -> "This stream has ended."
                            restart("ended") -> null
                            else -> "This stream keeps stopping. Try again, or pick a longer pre-buffer in Options."
                        }
                    }
                }
                override fun onTracksChanged(tracks: Tracks) {
                    // Only offer the control when the stream actually carries text —
                    // a subtitle button that does nothing is worse than none at all.
                    hasSubtitles = tracks.groups.any { it.type == C.TRACK_TYPE_TEXT }
                }
                override fun onPlayerError(e: PlaybackException) {
                    // The Ace Stream engine is a background app, and the phone reclaims it
                    // when it feels like it — a connection refused on 127.0.0.1, or a reply
                    // that isn't media, means the engine is gone, not the stream. No amount
                    // of retrying brings it back; opening the app does.
                    val uri = currentMediaItem?.localConfiguration?.uri?.toString().orEmpty()
                    if (AceStream.isEngineUrl(uri) && e.errorCode in ENGINE_DOWN_CODES) {
                        Log.i("iptv", "ace engine not answering (${e.errorCodeName})")
                        engineDown = true
                        error = "Ace Stream's engine isn't running — the phone stopped it in the background. " +
                            "Open Ace Stream, then try again. To stop this happening, set Ace Stream's battery use to Unrestricted."
                        buffering = false
                        return
                    }
                    // An Ace Stream channel is played over the engine's HLS endpoint. If the
                    // engine on this phone turns out not to serve it — an old engine, a bad
                    // manifest — fall back once to the progressive stream the playlist named.
                    val original = currentMediaItem?.localConfiguration?.tag as? AceProgressive
                    if (original != null && e.errorCode !in DROP_OUT_CODES) {
                        Log.i("iptv", "engine HLS failed (${e.errorCodeName}), falling back to getstream")
                        buffering = true
                        setMediaItem(MediaItem.fromUri(original.url))
                        prepare()
                        return
                    }
                    if (e.errorCode in DROP_OUT_CODES && restart("dropped out (${e.errorCodeName})")) return
                    error = when (e.errorCode) {
                        PlaybackException.ERROR_CODE_IO_BAD_HTTP_STATUS,
                        PlaybackException.ERROR_CODE_IO_INVALID_HTTP_CONTENT_TYPE ->
                            "Stream refused the connection. It may be offline, or geo-blocked from your location."
                        PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_FAILED,
                        PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_TIMEOUT ->
                            "Couldn't reach the stream — check your connection (or VPN)."
                        PlaybackException.ERROR_CODE_IO_READ_POSITION_OUT_OF_RANGE,
                        PlaybackException.ERROR_CODE_BEHIND_LIVE_WINDOW,
                        PlaybackException.ERROR_CODE_IO_UNSPECIFIED ->
                            "This stream keeps dropping out. Try again, or pick a longer pre-buffer in Options."
                        else -> "This stream didn't play (${e.errorCodeName})."
                    }
                    buffering = false
                }
            })
        }
    }

    // (re)load whenever the selected channel changes — also covers channel up/down and retry
    LaunchedEffect(channel.key, cast.casting, reloads) {
        error = null; engineDown = false; showInfo = true; pokes++
        recoveries.clear()   // a new channel, or a deliberate retry, gets the full allowance
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
            player.setMediaItem(mediaItemFor(channel.url, settings.prebufferMs))
            player.prepare()
            player.play()
        }
    }
    // Fade the bar after a quiet spell. A remote resting on a control gets longer:
    // someone is mid-task there, not watching.
    LaunchedEffect(showInfo, pokes, showPicker) {
        if (!showInfo || showPicker) return@LaunchedEffect
        delay(if (controlsFocused) 8000 else 4000)
        showInfo = false
    }
    // Apply the remembered subtitle choice to every stream, including after a zap.
    LaunchedEffect(settings.subtitlesOn, channel.key, reloads) {
        player.trackSelectionParameters = player.trackSelectionParameters.buildUpon()
            .setTrackTypeDisabled(C.TRACK_TYPE_TEXT, !settings.subtitlesOn)
            .build()
    }

    // The overlay carries focusable controls; when it fades out, focus has to come
    // back to the player itself or the D-pad is left with nothing to talk to.
    LaunchedEffect(showInfo) {
        if (!showInfo) runCatching { focus.requestFocus() }
    }

    // Android's bars come and go with the overlay: a tap on the picture shows the channel
    // bar and the navigation buttons together, and both fade together. This is the only
    // place that touches the bars — leaving the screen shows them (SystemBars' dispose).
    // A second writer elsewhere, racing this one during the exit transition, left the bars
    // visible but transient, with the next screen laid out underneath the status bar.
    SystemBars(hidden = !showInfo)

    DisposableEffect(Unit) {
        focus.requestFocus()
        onDispose { player.release() }
    }

    // The player starts the way the device is being held and then holds that orientation
    // — a stream shouldn't flip because the sofa shifted — and a control switches between
    // portrait and landscape. The activity declares orientation in configChanges, so the
    // switch re-lays out rather than restarting playback. Leaving the player hands
    // orientation back to the device. Only offered where the device can be turned — a TV
    // box gets no rotate button.
    val activity = ctx as? Activity
    val canRotate = remember { ctx.packageManager.hasSystemFeature(PackageManager.FEATURE_TOUCHSCREEN) }
    val startedPortrait = LocalConfiguration.current.orientation == Configuration.ORIENTATION_PORTRAIT
    var portrait by remember { mutableStateOf(startedPortrait) }
    DisposableEffect(portrait) {
        activity?.requestedOrientation =
            if (portrait) ActivityInfo.SCREEN_ORIENTATION_SENSOR_PORTRAIT else ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
        onDispose { activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_FULL_USER }
    }

    Box(
        Modifier.fillMaxSize().background(Color.Black)
            // Tap to reveal the overlay. The D-pad centre already did this; on a
            // phone or tablet there was no way in at all, which is where the
            // cast button now lives.
            .pointerInput(Unit) { detectTapGestures { showInfo = !showInfo } }
            // Sees every key before the focused control does, so walking the bar
            // with a remote keeps it on screen.
            .onPreviewKeyEvent { ev ->
                if (ev.type == KeyEventType.KeyDown && showInfo) pokes++
                false
            }
            .focusRequester(focus)
            .onFocusChanged { playerFocused = it.isFocused }
            .focusable()
            .onKeyEvent { ev ->
                if (ev.type != KeyEventType.KeyDown) return@onKeyEvent false
                // Keys bubble up here from whichever control has focus. The arrows only
                // zap when the picture itself holds focus — from a button they move focus,
                // so the remote can walk the bar's two rows and the error screen's buttons.
                // The remote's own channel keys zap from anywhere.
                when (ev.key) {
                    Key.ChannelUp -> { onStep(-1); true }
                    Key.ChannelDown -> { onStep(1); true }
                    Key.DirectionUp -> if (playerFocused) { onStep(-1); true } else false
                    Key.DirectionDown -> if (playerFocused) { onStep(1); true } else false
                    // Centre retries a failed stream; otherwise it toggles the info bar.
                    Key.DirectionCenter, Key.Enter -> if (!playerFocused) false else {
                        if (error != null) reloads++ else showInfo = !showInfo
                        true
                    }
                    // The player itself holds focus so it can hear these keys, which
                    // means the D-pad can't wander into the control bar on its own.
                    // Hand focus over on the first sideways press — left lands on back
                    // at the start of the bar, right on the actions at its end — then
                    // step aside so left/right can walk the whole row.
                    Key.DirectionLeft, Key.DirectionRight -> when {
                        !showInfo -> false
                        controlsFocused -> false
                        else -> {
                            val target = if (ev.key == Key.DirectionRight) controlsFocus else backFocus
                            runCatching { target.requestFocus() }
                            true
                        }
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
            // Scrim, as on the error screen: the paused last frame stays behind this text.
            Box(Modifier.fillMaxSize().background(cs.scrim.copy(alpha = .78f)), Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(Icons.Filled.Cast, null, Modifier.size(48.dp), tint = cs.primary)
                    Spacer(Modifier.height(Space.md))
                    Text(channel.clean, style = MaterialTheme.typography.titleLarge, color = Color.White)
                    Spacer(Modifier.height(Space.sm))
                    Text("Playing on ${cast.deviceName}", style = MaterialTheme.typography.bodyLarge, color = cs.primary)
                    Spacer(Modifier.height(Space.xs))
                    Text(
                        "Previous and next change the channel on the TV",
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
                // A remote lands on Try again as soon as a stream fails; the arrows then walk
                // the error's buttons. Scrolls, for a landscape phone at large font sizes.
                LaunchedEffect(Unit) { runCatching { retryFocus.requestFocus() } }
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.verticalScroll(rememberScrollState()).safeDrawingPadding().padding(horizontal = Space.xxl, vertical = Space.lg),
                ) {
                    Icon(Icons.Rounded.WarningAmber, null, tint = cs.tertiary, modifier = Modifier.size(44.dp))
                    Spacer(Modifier.height(Space.md))
                    Text(channel.clean, style = MaterialTheme.typography.titleLarge, color = Color.White)
                    Spacer(Modifier.height(Space.sm))
                    Text(it, style = MaterialTheme.typography.bodyLarge, color = cs.tertiary, textAlign = TextAlign.Center)
                    Spacer(Modifier.height(Space.lg))
                    // A real button: the old "OK to try again" line was a remote-only
                    // instruction, so on a touchscreen a dead stream had no retry at all.
                    // OK on a remote still retries straight from the player.
                    val aceEngine = appContainer().aceEngine
                    if (engineDown && aceEngine.installed) {
                        // Handing the channel's own link to the Ace Stream app brings the app
                        // to the front — which restarts the engine — with this channel already
                        // loaded, so it can be played there or here (Try again) as preferred.
                        // The settings page is where the phone is told to stop killing it.
                        val hasLink = AceStream.link(channel.url) != null
                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(Space.sm, Alignment.CenterHorizontally),
                            verticalArrangement = Arrangement.spacedBy(Space.sm),
                        ) {
                            TvActionButton(
                                if (hasLink) "Open in Ace Stream" else "Open Ace Stream",
                                { if (hasLink) openInAceStream(ctx, channel) else aceEngine.open()?.let(ctx::startActivity) },
                                icon = Icons.AutoMirrored.Rounded.OpenInNew,
                            )
                            TvActionButton("Allow in background", { aceEngine.appSettings()?.let(ctx::startActivity) }, icon = Icons.Rounded.BatterySaver)
                        }
                        Spacer(Modifier.height(Space.sm))
                    }
                    TvActionButton("Try again", { reloads++ }, modifier = Modifier.focusRequester(retryFocus), icon = Icons.Rounded.Refresh)
                    Spacer(Modifier.height(Space.md))
                    Text(
                        "Or skip to the previous or next channel.",
                        style = MaterialTheme.typography.bodyMedium, color = cs.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                    )
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
            modifier = Modifier.align(Alignment.TopStart),
        ) {
            // Laid out like the channel list's app bar — back at the start, what you're
            // watching, actions at the end — and at the top, where Android puts screen
            // navigation and where it stays clear of the subtitles.
            BoxWithConstraints(
                Modifier
                    .fillMaxWidth()
                    // Held dark through the text before it fades: on a bright, full-frame
                    // picture a straight fade left the second line sitting on sky.
                    .background(
                        Brush.verticalGradient(
                            0f to Color.Black.copy(alpha = .9f),
                            .6f to Color.Black.copy(alpha = .7f),
                            1f to Color.Transparent,
                        ),
                    )
                    .safeDrawingPadding()   // clear of the camera cutout in landscape
                    .padding(start = Space.xl, end = Space.xl, top = Space.lg, bottom = Space.xxl)
                    .onFocusChanged { controlsFocused = it.hasFocus }
                    .focusGroup(),
            ) {
                // Back, what you're watching, and six 48dp controls need ~620dp on one line.
                // A portrait phone has ~410, which left the channel name no width at all —
                // so a narrow bar puts the controls on a line of their own, under the title.
                val narrow = maxWidth < 600.dp
                val backButton = @Composable {
                    TvIconButton(
                        Icons.AutoMirrored.Rounded.ArrowBack, exitLabel, onExit,
                        modifier = Modifier.focusRequester(backFocus),
                        tint = Color.White,
                    )
                }
                val identity = @Composable { m: Modifier ->
                    Column(m) {
                        Text(
                            channel.clean, style = MaterialTheme.typography.headlineSmall, color = Color.White,
                            maxLines = 1, overflow = TextOverflow.Ellipsis,
                        )
                        Spacer(Modifier.height(Space.xs))
                        // Wraps rather than squeezing: on a portrait phone the group, quality and
                        // the labels did not fit one line, and the group text went to nothing.
                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(Space.md),
                            verticalArrangement = Arrangement.spacedBy(Space.xs),
                        ) {
                            // Secondary text over picture is dimmed white, not the UI's
                            // blue-grey: that grey is tuned for the app's own ink surfaces
                            // and turns muddy over footage.
                            val dim = Color.White.copy(alpha = .8f)
                            Text(
                                channel.groups.joinToString(" · "),
                                style = MaterialTheme.typography.bodyMedium, color = dim,
                                maxLines = 1, overflow = TextOverflow.Ellipsis,
                            )
                            channel.quality?.let {
                                Text(it, style = MaterialTheme.typography.labelMedium, color = cs.primary, modifier = Modifier.align(Alignment.CenterVertically))
                            }
                            if (channel.geoBlocked) Badge(Icons.Rounded.Public, "Geo-blocked", cs.tertiary, Modifier.align(Alignment.CenterVertically))
                            if (playing.isFavourite) Badge(Icons.Rounded.Star, "Favourite", cs.primary, Modifier.align(Alignment.CenterVertically))
                            if (settings.subtitlesOn && !hasSubtitles) {
                                Text(
                                    "No subtitles on this channel",
                                    style = MaterialTheme.typography.labelMedium, color = dim,
                                    maxLines = 1, modifier = Modifier.align(Alignment.CenterVertically),
                                )
                            }
                        }
                    }

                }
                val controls = @Composable {
                    TvIconButton(
                        Icons.Rounded.SkipPrevious, "Previous channel", { onStep(-1) },
                        modifier = Modifier.focusRequester(controlsFocus),
                        tint = Color.White,
                    )
                    TvIconButton(Icons.Rounded.SkipNext, "Next channel", { onStep(1) }, tint = Color.White)
                    // A fresh load of the same stream, for a picture that has frozen or fallen
                    // behind without the player noticing — the same thing the error screen's
                    // Try again does, reachable while the stream is nominally still playing.
                    TvIconButton(Icons.Rounded.Refresh, "Reload stream", { reloads++; pokes++ }, tint = Color.White)
                    if (canRotate) {
                        TvIconButton(
                            Icons.Rounded.ScreenRotation,
                            if (portrait) "Rotate to landscape" else "Rotate to portrait",
                            { portrait = !portrait; pokes++ },
                            tint = Color.White,
                        )
                    }
                    TvIconButton(
                        icon = if (settings.subtitlesOn) Icons.Rounded.ClosedCaption else Icons.Rounded.ClosedCaptionOff,
                        contentDescription = when {
                            settings.subtitlesOn && !hasSubtitles -> "Subtitles on, but this channel has none"
                            settings.subtitlesOn -> "Turn subtitles off"
                            else -> "Turn subtitles on"
                        },
                        onClick = { onToggleSubtitles(); pokes++ },
                        tint = when {
                            settings.subtitlesOn && !hasSubtitles -> cs.onSurfaceVariant
                            settings.subtitlesOn -> cs.primary
                            else -> Color.White
                        },
                    )
                    // Casting only where it can actually happen. An Android TV box
                    // without Play services gets no button rather than one that fails.
                    if (cast.available) {
                        // Connecting is shown on the button itself, not as a label beside it: on a
                        // portrait phone the label pushed the button — the way to cancel — off the row.
                        TvIconButton(
                            icon = if (cast.casting) Icons.Filled.CastConnected else Icons.Filled.Cast,
                            contentDescription = when {
                                cast.casting -> "Stop casting to ${cast.deviceName}"
                                cast.connecting -> "Connecting to a device. Tap to cancel"
                                else -> "Cast to a device"
                            },
                            onClick = { if (cast.casting || cast.connecting) cast.disconnect() else showPicker = true },
                            tint = if (cast.casting || cast.connecting) cs.primary else Color.White,
                        )
                    }
                }
                if (narrow) {
                    Column {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            backButton()
                            Spacer(Modifier.width(Space.md))
                            identity(Modifier.weight(1f))
                        }
                        Spacer(Modifier.height(Space.sm))
                        Row(Modifier.align(Alignment.End), verticalAlignment = Alignment.CenterVertically) {
                            controls()
                        }
                    }
                } else {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        backButton()
                        Spacer(Modifier.width(Space.md))
                        identity(Modifier.weight(1f))
                        Spacer(Modifier.width(Space.lg))
                        controls()
                    }
                }
            }
        }
    }
}

/**
 * Buffer rules for the chosen pre-buffer: playback starts once [prebufferMs] is loaded, and
 * after a stall once the larger of that and Media3's usual 5 s is. How far ahead the player
 * keeps loading stays at Media3's 50 s.
 */
private fun loadControlFor(prebufferMs: Int): DefaultLoadControl {
    val afterStall = maxOf(prebufferMs, DefaultLoadControl.DEFAULT_BUFFER_FOR_PLAYBACK_AFTER_REBUFFER_MS)
    val minBuffer = maxOf(DefaultLoadControl.DEFAULT_MIN_BUFFER_MS, afterStall)
    return DefaultLoadControl.Builder()
        .setBufferDurationsMs(minBuffer, maxOf(DefaultLoadControl.DEFAULT_MAX_BUFFER_MS, minBuffer), prebufferMs, afterStall)
        .build()
}

/**
 * On a live stream Media3 only waits for half the distance to the live edge, whatever the
 * pre-buffer says, and HLS playlists usually sit just three segments back — so a long
 * pre-buffer would quietly be cut short. Longer settings therefore also play further behind
 * live: twice the pre-buffer, and never closer than 30 s, which is behind nearly every
 * playlist's own choice. The default and shorter keep the playlist's.
 *
 * Media3 clamps that offset to what the playlist actually keeps, so a 60 s pre-buffer on a
 * channel holding 40 s gets 20 s. Raw MPEG-TS has no live window and gets the full amount.
 */
private fun mediaItemFor(url: String, prebufferMs: Int): MediaItem =
    MediaItem.Builder().setUri(AceStream.hlsUrl(url)).apply {
        // Ace Stream: play the engine's HLS form (see AceStream), remembering the original
        // so a failure can fall back to it.
        if (AceStream.isProgressive(url)) {
            setMimeType(MimeTypes.APPLICATION_M3U8)
            setTag(AceProgressive(url))
        }
        if (prebufferMs > DEFAULT_PREBUFFER_MS) {
            setLiveConfiguration(
                MediaItem.LiveConfiguration.Builder()
                    .setTargetOffsetMs(maxOf(2L * prebufferMs + 2_000, 30_000L))
                    .build(),
            )
        }
    }.build()
