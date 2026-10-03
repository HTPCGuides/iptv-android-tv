package com.iptvtv.ui

import android.content.ActivityNotFoundException
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.OpenInNew
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material.icons.rounded.SmartDisplay
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material.icons.rounded.StarBorder
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import com.iptvtv.data.AceStream
import com.iptvtv.model.Channel

/**
 * Everything you can do with one channel, the same set the Ace M3U app offers for an
 * entry: play it, favourite it, share it, copy its links, or hand it to another app.
 * Tapping a channel still plays it straight away — this is the ⋮ on a channel row, and
 * press-and-hold on a row or a Home tile.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChannelSheet(
    channel: Channel,
    isFavourite: Boolean,
    onPlay: () -> Unit,
    onToggleFavourite: () -> Unit,
    onDismiss: () -> Unit,
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = MaterialTheme.colorScheme.surfaceContainer,
    ) {
        ChannelSheetContent(
            channel = channel,
            isFavourite = isFavourite,
            aceStreamInstalled = runCatching { appContainer().aceEngine.installed }.getOrDefault(false),
            onPlay = { onDismiss(); onPlay() },
            onToggleFavourite = onToggleFavourite,
        )
    }
}

/** The sheet's contents, apart from the sheet: what the screenshot tests render. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ChannelSheetContent(
    channel: Channel,
    isFavourite: Boolean,
    aceStreamInstalled: Boolean,
    onPlay: () -> Unit,
    onToggleFavourite: () -> Unit,
) {
    val ctx = LocalContext.current
    val cs = MaterialTheme.colorScheme
    val aceLink = AceStream.link(channel.url)
    // An engine URL (http://127.0.0.1:6878/…) only works on this phone; the Ace link is the
    // one worth sharing. Other apps on this phone can still play the engine URL.
    val shareable = aceLink ?: channel.url
    Column(
        Modifier
            .widthIn(max = ReadableWidth)
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = Space.xl)
            .padding(bottom = Space.xl)
            .navigationBarsPadding(),
    ) {
        Text(channel.clean, style = MaterialTheme.typography.headlineSmall, color = cs.onSurface)
        val meta = listOfNotNull(
            channel.groups.joinToString(" · "),
            channel.quality,
            "Geo-blocked".takeIf { channel.geoBlocked },
            "Part-time".takeIf { channel.partTime },
        ).joinToString("  ·  ")
        Text(meta, style = MaterialTheme.typography.bodyMedium, color = cs.onSurfaceVariant)

        Spacer(Modifier.height(Space.lg))
        FlowRow(horizontalArrangement = Arrangement.spacedBy(Space.sm), verticalArrangement = Arrangement.spacedBy(Space.sm)) {
            TvActionButton("Play", onPlay, icon = Icons.Rounded.PlayArrow)
            TvActionButton(
                if (isFavourite) "Remove from favourites" else "Add to favourites",
                onToggleFavourite,
                icon = if (isFavourite) Icons.Rounded.Star else Icons.Rounded.StarBorder,
            )
            TvActionButton("Share", { share(ctx, channel.clean, shareable) }, icon = Icons.Rounded.Share)
        }

        if (aceLink != null) {
            LinkBlock(
                if (aceLink.startsWith("magnet:")) "Ace Stream link (magnet)" else "Ace Stream link",
                aceLink,
            ) { copy(ctx, channel.clean, aceLink, "Ace Stream link") }
        } else {
            LinkBlock("Stream link", channel.url) { copy(ctx, channel.clean, channel.url, "Stream link") }
        }

        Spacer(Modifier.height(Space.lg))
        Text("In other apps", style = MaterialTheme.typography.labelLarge, color = cs.onSurfaceVariant)
        Spacer(Modifier.height(Space.sm))
        FlowRow(horizontalArrangement = Arrangement.spacedBy(Space.sm), verticalArrangement = Arrangement.spacedBy(Space.sm)) {
            if (aceLink != null && aceStreamInstalled) {
                TvActionButton("Open in Ace Stream", { openInAceStream(ctx, channel) }, icon = Icons.AutoMirrored.Rounded.OpenInNew)
            }
            TvActionButton("Play in another app", { playElsewhere(ctx, channel.url) }, icon = Icons.Rounded.SmartDisplay)
        }
    }
}

/** A link, readable, with its own Copy — copying lives on the thing it copies. */
@Composable
private fun LinkBlock(label: String, value: String, onCopy: () -> Unit) {
    val cs = MaterialTheme.colorScheme
    Spacer(Modifier.height(Space.lg))
    Text(label, style = MaterialTheme.typography.labelLarge, color = cs.onSurfaceVariant)
    Spacer(Modifier.height(Space.xs))
    Row(
        Modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.medium)
            .background(cs.surfaceContainerHigh)
            .padding(start = Space.md),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            value, style = MaterialTheme.typography.bodySmall, color = cs.onSurface,
            maxLines = 2, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f).padding(vertical = Space.sm),
        )
        TvIconButton(Icons.Rounded.ContentCopy, "Copy $label", onCopy, tint = cs.onSurface)
    }
}

private fun copy(ctx: Context, name: String, text: String, what: String) {
    val clipboard = ctx.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    clipboard.setPrimaryClip(ClipData.newPlainText(name, text))
    // Android 13+ shows its own confirmation.
    if (Build.VERSION.SDK_INT < 33) Toast.makeText(ctx, "$what copied", Toast.LENGTH_SHORT).show()
}

private fun share(ctx: Context, name: String, link: String) {
    val send = Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, "$name\n$link")
    try {
        ctx.startActivity(Intent.createChooser(send, name).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    } catch (e: ActivityNotFoundException) {
        Toast.makeText(ctx, "Nothing on this device can receive a share", Toast.LENGTH_SHORT).show()
    }
}

private fun playElsewhere(ctx: Context, url: String) {
    val view = Intent(Intent.ACTION_VIEW).setDataAndType(Uri.parse(url), "video/*")
    try {
        ctx.startActivity(Intent.createChooser(view, "Play with").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    } catch (e: ActivityNotFoundException) {
        Toast.makeText(ctx, "No other app on this device plays stream links", Toast.LENGTH_SHORT).show()
    }
}
