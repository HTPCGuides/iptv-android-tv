package com.iptvtv.ui.player

import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.FocusRequester
import com.iptvtv.ui.Space

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Cast
import androidx.compose.material.icons.filled.Tv
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.mediarouter.media.MediaRouter

/**
 * The device picker.
 *
 * Deliberately the same shape as the one in YouTube and Plex — a short list of
 * names, tap one, done — because this is a dialog people have already learned.
 * The interesting states are the two empty ones: still looking, and looked and
 * found nothing. The second needs to say *why*, since the usual cause is the
 * phone being on a different network from the television and no amount of
 * waiting fixes that.
 */
@Composable
fun CastPicker(
    cast: CastController,
    onPick: (MediaRouter.RouteInfo) -> Unit,
    onDismiss: () -> Unit,
) {
    val cs = MaterialTheme.colorScheme
    val firstRow = remember { FocusRequester() }
    // A remote lands on the first device — or on Cancel while still searching — rather than
    // on nothing, which cost an extra press before anything was focused.
    LaunchedEffect(cast.routes.isEmpty()) { runCatching { firstRow.requestFocus() } }
    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = MaterialTheme.shapes.large,
            color = cs.surfaceContainer,
            modifier = Modifier.widthIn(max = 420.dp),
        ) {
            Column(Modifier.padding(Space.xl)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Filled.Cast, null, Modifier.size(22.dp), tint = cs.primary)
                    Spacer(Modifier.width(Space.md))
                    Text("Cast to", style = MaterialTheme.typography.titleLarge, color = cs.onSurface)
                }
                Spacer(Modifier.height(Space.lg))

                if (cast.routes.isEmpty()) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        CircularProgressIndicator(color = cs.primary, strokeWidth = 2.dp, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(Space.md))
                        Text(
                            "Looking for devices…",
                            style = MaterialTheme.typography.bodyLarge, color = cs.onSurface,
                        )
                    }
                    Spacer(Modifier.height(Space.sm))
                    Text(
                        "A Chromecast or Google TV has to be on the same Wi-Fi as this " +
                            "device. If nothing appears, that's usually why.",
                        style = MaterialTheme.typography.bodyMedium, color = cs.onSurfaceVariant,
                    )
                } else {
                    Column(
                        Modifier.heightIn(max = 280.dp).verticalScroll(rememberScrollState()),
                        verticalArrangement = Arrangement.spacedBy(Space.xs),
                    ) {
                        cast.routes.forEachIndexed { i, route ->
                            PickerRow(
                                modifier = if (i == 0) Modifier.focusRequester(firstRow) else Modifier,
                                name = route.name,
                                detail = route.description?.takeIf { it.isNotBlank() },
                                onClick = { onPick(route) },
                            )
                        }
                    }
                }

                // A way out that a remote can reach: the dialog otherwise only closes on Back
                // or a tap outside, and while it is still searching there is nothing to focus.
                // (Stopping a cast lives on the player's Cast button, not here.)
                Spacer(Modifier.height(Space.md))
                PickerRow(
                    modifier = if (cast.routes.isEmpty()) Modifier.focusRequester(firstRow) else Modifier,
                    name = "Cancel", detail = null, tint = cs.onSurfaceVariant, icon = false, onClick = onDismiss)
            }
        }
    }
}

/** Selectable row with a visible focus state — the picker is reachable by D-pad too. */
@Composable
private fun PickerRow(
    name: String,
    detail: String?,
    onClick: () -> Unit,
    tint: Color? = null,
    icon: Boolean = true,
    modifier: Modifier = Modifier,
) {
    var focused by remember { mutableStateOf(false) }
    val cs = MaterialTheme.colorScheme
    val shape = MaterialTheme.shapes.small
    Row(
        modifier
            .fillMaxWidth()
            .defaultMinSize(minHeight = 48.dp)
            .clip(shape)
            .background(if (focused) cs.surfaceContainerHighest else Color.Transparent)
            .onFocusChanged { focused = it.isFocused }
            .clickable(onClick = onClick, role = Role.Button)
            .padding(horizontal = Space.md, vertical = Space.md),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (icon) {
            Icon(
                Icons.Filled.Tv, null, Modifier.size(20.dp),
                tint = if (focused) cs.primary else cs.onSurfaceVariant,
            )
            Spacer(Modifier.width(Space.md))
        }
        Column(Modifier.weight(1f)) {
            Text(
                name,
                style = MaterialTheme.typography.bodyLarge,
                color = tint ?: cs.onSurface,
                maxLines = 1, overflow = TextOverflow.Ellipsis,
            )
            detail?.let {
                Text(
                    it, style = MaterialTheme.typography.bodySmall, color = cs.onSurfaceVariant,
                    maxLines = 1, overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}
