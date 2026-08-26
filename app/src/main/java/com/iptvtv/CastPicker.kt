package com.iptvtv

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
                        cast.routes.forEach { route ->
                            PickerRow(
                                name = route.name,
                                detail = route.description?.takeIf { it.isNotBlank() },
                                onClick = { onPick(route) },
                            )
                        }
                    }
                }

                if (cast.casting) {
                    Spacer(Modifier.height(Space.md))
                    PickerRow(
                        name = "Stop casting",
                        detail = null,
                        tint = cs.error,
                        icon = false,
                        onClick = { cast.disconnect(); onDismiss() },
                    )
                }
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
) {
    var focused by remember { mutableStateOf(false) }
    val cs = MaterialTheme.colorScheme
    val shape = MaterialTheme.shapes.small
    Row(
        Modifier
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
