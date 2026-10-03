package com.iptvtv.ui.player

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Cast
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Smartphone
import androidx.compose.material.icons.rounded.WarningAmber
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.iptvtv.data.AceStream
import com.iptvtv.ui.ReadableWidth
import com.iptvtv.ui.Space
import com.iptvtv.ui.TvActionButton
import kotlinx.coroutines.delay

/** How long a TV may sit loading before we say so. Live HLS on a TV normally starts in a few seconds. */
private const val SLOW_START_MS = 20_000L

/**
 * What the phone shows while casting: whatever the TV is actually doing, not just that a
 * session exists. Each state that isn't "playing" offers a way out — "Watch here" ends the
 * cast and plays the channel on this phone.
 */
@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
fun CastOverlay(
    cast: CastController,
    target: CastTarget,
    channelName: String,
    channelUrl: String,
    onRetry: () -> Unit,
    onWatchHere: () -> Unit,
) {
    val cs = MaterialTheme.colorScheme
    val tvName = cast.deviceName ?: "the TV"
    var slow by remember { mutableStateOf(false) }
    LaunchedEffect(cast.receiver, target) {
        slow = false
        if (target is CastTarget.Send && cast.receiver == CastController.Receiver.LOADING) {
            delay(SLOW_START_MS)
            slow = true
        }
    }

    val playing = target is CastTarget.Send && cast.receiver == CastController.Receiver.PLAYING
    val loading = target is CastTarget.Send && !playing && cast.receiver != CastController.Receiver.FAILED
    val problem: String? = when {
        target == CastTarget.OnThisDevice && AceStream.isEngineUrl(channelUrl) ->
            "This channel plays through the Ace Stream engine on this phone, and $tvName can't reach it."
        target == CastTarget.OnThisDevice -> "This channel is served from this phone, and $tvName can't reach it."
        target == CastTarget.UnsupportedFormat -> "$tvName can't play this channel's format (MPEG-TS). It plays on this phone."
        cast.receiver == CastController.Receiver.FAILED ->
            "$tvName couldn't play this channel. ${cast.receiverError.orEmpty()}".trim()
        else -> null
    }

    // Scrim, as on the error screen: the paused last frame stays behind this text.
    Box(Modifier.fillMaxSize().background(cs.scrim.copy(alpha = .78f)), Alignment.Center) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier
                .widthIn(max = ReadableWidth)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = Space.xxl, vertical = Space.lg),
        ) {
            when {
                problem != null -> Icon(Icons.Rounded.WarningAmber, null, tint = cs.tertiary, modifier = Modifier.size(44.dp))
                loading -> CircularProgressIndicator(color = cs.primary)
                else -> Icon(Icons.Filled.Cast, null, tint = cs.primary, modifier = Modifier.size(48.dp))
            }
            Spacer(Modifier.height(Space.md))
            Text(channelName, style = MaterialTheme.typography.titleLarge, color = Color.White, textAlign = TextAlign.Center)
            Spacer(Modifier.height(Space.sm))
            Text(
                when {
                    problem != null -> problem
                    loading -> "Starting on $tvName…"
                    else -> "Playing on $tvName"
                },
                style = MaterialTheme.typography.bodyLarge,
                color = if (problem != null) cs.tertiary else cs.primary,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(Space.xs))
            Text(
                when {
                    problem != null -> "Pick another channel, or watch this one here."
                    slow -> "This is taking a while. Some streams can't be cast — pick another channel, or watch it here."
                    else -> "Previous and next change the channel on the TV"
                },
                style = MaterialTheme.typography.bodyMedium, color = cs.onSurfaceVariant, textAlign = TextAlign.Center,
            )
            if (problem != null || slow) {
                Spacer(Modifier.height(Space.lg))
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(Space.sm, Alignment.CenterHorizontally),
                    verticalArrangement = Arrangement.spacedBy(Space.sm),
                ) {
                    if (target is CastTarget.Send) TvActionButton("Try again", onRetry, icon = Icons.Rounded.Refresh)
                    TvActionButton("Watch here", onWatchHere, icon = Icons.Rounded.Smartphone)
                }
            }
        }
    }
}
