package com.iptvtv.ui

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import android.content.ActivityNotFoundException
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.material.icons.automirrored.rounded.OpenInNew
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.TextButton
import androidx.compose.ui.platform.LocalContext
import android.os.Build
import android.widget.Toast
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Tv
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import coil.compose.AsyncImage
import com.iptvtv.IptvApp
import com.iptvtv.data.AceStream
import com.iptvtv.model.Channel

/**
 * The app's single focus response, shared by every focusable surface: lift, warm the
 * fill, ring it in the accent, and settle with a slight overshoot. On a ten-foot
 * screen focus has to be readable across the room, so it moves as well as recolours —
 * unless the user has switched animations off, in which case it cuts.
 */
@Composable
private fun focusMotion(focused: Boolean, peak: Float): Float {
    val reduced = LocalReducedMotion.current
    val scale by animateFloatAsState(
        targetValue = if (focused) peak else 1f,
        animationSpec = if (reduced) snap() else spring(dampingRatio = 0.7f, stiffness = Spring.StiffnessMediumLow),
        label = "focus-scale",
    )
    return scale
}

/** A focusable card. `peakScale` is trimmed for full-width rows so they don't clip at the edges. */
@Composable
@OptIn(ExperimentalFoundationApi::class)
fun TvSurface(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    shape: Shape = MaterialTheme.shapes.medium,
    peakScale: Float = 1.05f,
    contentPadding: PaddingValues = PaddingValues(horizontal = Space.lg, vertical = Space.md),
    contentAlignment: Alignment = Alignment.TopStart,
    onLongClick: (() -> Unit)? = null,
    content: @Composable BoxScope.(focused: Boolean) -> Unit,
) {
    var focused by remember { mutableStateOf(false) }
    val scale = focusMotion(focused, peakScale)
    val cs = MaterialTheme.colorScheme
    Box(
        contentAlignment = contentAlignment,
        modifier =
        modifier
            .defaultMinSize(minWidth = 48.dp, minHeight = 48.dp)
            .graphicsLayer { scaleX = scale; scaleY = scale }
            .shadow(
                elevation = if (focused) 14.dp else 0.dp,
                shape = shape,
                clip = false,
                ambientColor = cs.primary,
                spotColor = cs.primary,
            )
            .clip(shape)
            .background(if (focused) cs.surfaceContainerHighest else cs.surfaceContainer)
            .border(if (focused) 2.dp else 1.dp, if (focused) cs.primary else cs.outlineVariant, shape)
            .onFocusChanged { focused = it.isFocused }
            .combinedClickable(onClick = onClick, onLongClick = onLongClick)
            .padding(contentPadding),
    ) { content(focused) }
}

/**
 * Icon-only control. Stays transparent at rest so it never reads as a card inside a
 * card; the accent ring only appears on focus.
 */
@Composable
fun TvIconButton(
    icon: ImageVector,
    contentDescription: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    tint: androidx.compose.ui.graphics.Color? = null,
) {
    var focused by remember { mutableStateOf(false) }
    val scale = focusMotion(focused, 1.12f)
    val cs = MaterialTheme.colorScheme
    Box(
        modifier
            .size(48.dp)
            .graphicsLayer { scaleX = scale; scaleY = scale }
            .clip(CircleShape)
            .background(if (focused) cs.primary.copy(alpha = .16f) else androidx.compose.ui.graphics.Color.Transparent)
            .border(if (focused) 2.dp else 0.dp, if (focused) cs.primary else androidx.compose.ui.graphics.Color.Transparent, CircleShape)
            .onFocusChanged { focused = it.isFocused }
            .clickable(onClick = onClick, role = Role.Button),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, contentDescription, tint = tint ?: cs.onSurfaceVariant, modifier = Modifier.size(24.dp))
    }
}

/** Labelled action, optionally with a leading icon. Used for screen-level actions. */
@Composable
fun TvActionButton(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
) {
    TvSurface(
        onClick = onClick,
        modifier = modifier,
        shape = RoundedCornerShape(50),
        peakScale = 1.08f,
        contentPadding = PaddingValues(horizontal = Space.lg, vertical = Space.sm),
        // The label row is shorter than the 48dp touch minimum; without this it
        // hugs the top of the pill and the whole button looks misaligned.
        contentAlignment = Alignment.Center,
    ) { focused ->
        Row(verticalAlignment = Alignment.CenterVertically) {
            val fg = if (focused) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
            if (icon != null) {
                Icon(icon, null, tint = fg, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(Space.sm))
            }
            Text(label, style = MaterialTheme.typography.labelLarge, color = fg)
        }
    }
}

/** Filter pill. Selection is carried by the fill, not by a character prefix. */
@Composable
fun TvChip(label: String, selected: Boolean, icon: ImageVector? = null, onClick: () -> Unit) {
    var focused by remember { mutableStateOf(false) }
    val scale = focusMotion(focused, 1.08f)
    val cs = MaterialTheme.colorScheme
    val shape = RoundedCornerShape(50)
    Box(
        Modifier
            .defaultMinSize(minHeight = 48.dp)
            .graphicsLayer { scaleX = scale; scaleY = scale }
            .clip(shape)
            .background(
                when {
                    selected -> cs.primaryContainer
                    focused -> cs.surfaceContainerHighest
                    else -> cs.surfaceContainer
                },
            )
            .border(
                width = if (focused) 2.dp else 1.dp,
                color = if (focused) cs.primary else if (selected) cs.primary.copy(alpha = .4f) else cs.outlineVariant,
                shape = shape,
            )
            .onFocusChanged { focused = it.isFocused }
            .clickable(onClick = onClick, role = Role.RadioButton)
            .padding(horizontal = Space.lg, vertical = Space.sm),
        contentAlignment = Alignment.Center,
    ) {
        val fg = if (selected) cs.onPrimaryContainer else cs.onSurface
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (icon != null) {
                // Quieter than the label at rest; it identifies the chip, the text names it.
                Icon(icon, null, tint = if (selected) fg else cs.onSurfaceVariant, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(Space.sm))
            }
            Text(label, style = MaterialTheme.typography.labelLarge, color = fg)
        }
    }
}

/** Section heading: icon and label share one baseline across every row on Home. */
@Composable
fun SectionHeader(icon: ImageVector, label: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
        Spacer(Modifier.width(Space.sm))
        Text(label, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurface)
    }
}

/**
 * Channel logo. Without one, the channel's group stands in — its glyph in the group's
 * tint on a faint wash of the same — so a list of logo-less channels still reads at a
 * glance instead of showing a column of identical television icons.
 */
@Composable
fun ChannelLogo(logo: String?, size: Dp, group: String = "Other") {
    val shape = MaterialTheme.shapes.small
    if (logo != null) {
        AsyncImage(
            model = logo,
            contentDescription = null, // decorative: the channel name sits beside it
            modifier = Modifier.size(size).clip(shape),
        )
    } else {
        val tint = groupTint(group)
        Box(
            Modifier.size(size).clip(shape).background(tint.copy(alpha = .14f)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(groupIcon(group), null, tint = tint, modifier = Modifier.size(size * 0.5f))
        }
    }
}

/** Small advisory marker — geo-blocked, part-time. */
@Composable
fun Badge(icon: ImageVector, label: String, tint: Color) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, null, tint = tint, modifier = Modifier.size(14.dp))
        Spacer(Modifier.width(Space.xs))
        Text(label, style = MaterialTheme.typography.labelMedium, color = tint, maxLines = 1)
    }
}

/** Centred full-height state block — loading, error and empty share one layout. */
@Composable
fun CenteredState(content: @Composable ColumnScope.() -> Unit) {
    Box(Modifier.fillMaxSize(), Alignment.Center) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.padding(horizontal = Space.xl),
            content = content,
        )
    }
}

/**
 * Press-and-hold on a channel puts its stream address on the clipboard, for pasting into
 * another player — the acestream:// ids in particular are unreadable to retype. Android 13+
 * shows its own "Copied" confirmation, so the toast is only for older versions.
 */
fun copyStreamUrl(ctx: Context, channel: Channel) {
    // An Ace Stream channel arrives as an engine URL (http://127.0.0.1:6878/ace/getstream?
    // infohash=…), which is only good on the phone that made it. The acestream:// link is
    // what other players and other people can use, so that is what gets copied.
    val ace = AceStream.link(channel.url)
    val clipboard = ctx.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    clipboard.setPrimaryClip(ClipData.newPlainText(channel.clean, ace ?: channel.url))
    if (Build.VERSION.SDK_INT < 33) {
        Toast.makeText(ctx, if (ace != null) "Ace Stream link copied" else "Stream address copied", Toast.LENGTH_SHORT).show()
    }
}

/**
 * Hands an Ace Stream channel's acestream:// link to the Ace Stream app — that app
 * specifically, not whichever apps claim the scheme (Ace M3U does too), so there is no
 * chooser in the way.
 */
fun openInAceStream(ctx: Context, channel: Channel) {
    val link = AceStream.link(channel.url) ?: return
    val pkg = (ctx.applicationContext as IptvApp).container.aceEngine.installedPackage
    if (pkg == null) {
        Toast.makeText(ctx, "Ace Stream isn't installed", Toast.LENGTH_SHORT).show()
        return
    }
    try {
        ctx.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(link)).setPackage(pkg).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    } catch (e: ActivityNotFoundException) {
        Toast.makeText(ctx, "Ace Stream can't open this link", Toast.LENGTH_SHORT).show()
    }
}

/**
 * What press-and-hold on a channel offers. An ordinary stream has one thing worth doing —
 * copy its address — so it just does it. An Ace Stream channel has two: copy the
 * acestream:// link, or open it in the Ace Stream app itself.
 */
@Composable
fun ChannelActions(channel: Channel, onDismiss: () -> Unit) {
    val ctx = LocalContext.current
    val ace = AceStream.link(channel.url)
    if (ace == null) {
        copyStreamUrl(ctx, channel); onDismiss()
        return
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(channel.clean) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(Space.sm)) {
                Text(ace, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                TvActionButton("Open in Ace Stream", { openInAceStream(ctx, channel); onDismiss() }, icon = Icons.AutoMirrored.Rounded.OpenInNew)
                TvActionButton("Copy Ace Stream link", { copyStreamUrl(ctx, channel); onDismiss() }, icon = Icons.Rounded.ContentCopy)
            }
        },
        confirmButton = {},
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

fun formatBytes(b: Long): String = when {
    b >= 1_000_000_000 -> "%.1f GB".format(b / 1e9)
    b >= 1_000_000 -> "%.1f MB".format(b / 1e6)
    b >= 1_000 -> "%.0f kB".format(b / 1e3)
    else -> "$b B"
}
