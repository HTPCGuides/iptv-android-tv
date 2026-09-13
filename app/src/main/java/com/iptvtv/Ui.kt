package com.iptvtv

import androidx.compose.foundation.background
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.DeleteSweep
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.PlaylistPlay
import androidx.compose.material.icons.rounded.Public
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.SearchOff
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material.icons.rounded.StarBorder
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material.icons.rounded.Tv
import androidx.compose.material.icons.rounded.WarningAmber
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.os.Build
import android.widget.Toast
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.foundation.border
import androidx.compose.material.icons.rounded.Timer
import androidx.compose.material.icons.rounded.Edit
import kotlinx.coroutines.delay

/**
 * Press-and-hold on a channel puts its stream address on the clipboard, for pasting into
 * another player — the acestream:// ids in particular are unreadable to retype. Android 13+
 * shows its own "Copied" confirmation, so the toast is only for older versions.
 */
private fun copyStreamUrl(ctx: Context, c: Channel) {
    val clipboard = ctx.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    clipboard.setPrimaryClip(ClipData.newPlainText(c.clean, c.url))
    if (Build.VERSION.SDK_INT < 33) Toast.makeText(ctx, "Stream address copied", Toast.LENGTH_SHORT).show()
}

/** "1 s", "2.5 s" — whole seconds without a stray ".0", decimals in the user's own format. */
private fun prebufferLabel(ms: Int) =
    if (ms % 1000 == 0) "${ms / 1000} s" else "%.1f s".format(ms / 1000f)

fun formatBytes(b: Long): String = when {
    b >= 1_000_000_000 -> "%.1f GB".format(b / 1e9)
    b >= 1_000_000 -> "%.1f MB".format(b / 1e6)
    b >= 1_000 -> "%.0f kB".format(b / 1e3)
    else -> "$b B"
}

private fun channelCount(n: Int) = if (n == 1) "1 channel" else "$n channels"

/** Section heading: icon and label share one baseline across every row on Home. */
@Composable
private fun SectionHeader(icon: ImageVector, label: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
        Spacer(Modifier.width(Space.sm))
        Text(label, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurface)
    }
}

/** Channel logo, falling back to a drawn mark rather than leaving a hole. */
@Composable
private fun ChannelLogo(logo: String?, size: androidx.compose.ui.unit.Dp) {
    val shape = MaterialTheme.shapes.small
    if (logo != null) {
        AsyncImage(
            model = logo,
            contentDescription = null, // decorative: the channel name sits beside it
            modifier = Modifier.size(size).clip(shape),
        )
    } else {
        Box(
            Modifier.size(size).clip(shape).background(MaterialTheme.colorScheme.surfaceContainerLowest),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                Icons.Rounded.Tv, null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(size * 0.5f),
            )
        }
    }
}

/** Small advisory marker — geo-blocked, part-time. */
@Composable
private fun Badge(icon: ImageVector, label: String, tint: Color) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, null, tint = tint, modifier = Modifier.size(14.dp))
        Spacer(Modifier.width(Space.xs))
        Text(label, style = MaterialTheme.typography.labelMedium, color = tint, maxLines = 1)
    }
}

@Composable
fun HomeScreen(vm: IptvViewModel, onOpen: (Source) -> Unit, onPlay: (Channel) -> Unit) {
    var showAdd by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { vm.refreshHomeRows(); vm.refreshCacheSize() }   // both change while we're away
    val cs = MaterialTheme.colorScheme
    Column(
        Modifier
            .fillMaxSize()
            .background(cs.background)
            .safeDrawingPadding()          // Android 15 draws edge-to-edge; keep clear of the bars
            .verticalScroll(rememberScrollState())
            .padding(horizontal = Space.xl, vertical = Space.lg),
    ) {
        Text("IPTV TV", style = MaterialTheme.typography.displaySmall, color = cs.onSurface)
        Spacer(Modifier.height(Space.xs))
        Text(
            "Free-to-air channels from iptv-org — plus your own playlists.",
            style = MaterialTheme.typography.bodyMedium, color = cs.onSurfaceVariant,
        )

        Spacer(Modifier.height(Space.xl))
        SectionHeader(Icons.Rounded.PlaylistPlay, "Sources")
        Spacer(Modifier.height(Space.md))
        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(Space.md),
            contentPadding = PaddingValues(horizontal = Space.xs, vertical = Space.sm),
        ) {
            items(vm.sources, key = { it.id }) { s ->
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    TvSurface(
                        onClick = { onOpen(s) },
                        modifier = Modifier.width(210.dp).height(116.dp),
                    ) {
                        Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.Center) {
                            if (s.builtIn) {
                                Text(s.emoji, fontSize = 28.sp)   // the flag is the source's identity
                            } else {
                                Icon(
                                    Icons.Rounded.PlaylistPlay, null,
                                    tint = cs.primary, modifier = Modifier.size(30.dp),
                                )
                            }
                            Spacer(Modifier.height(Space.xs))
                            Text(
                                s.name, style = MaterialTheme.typography.titleSmall, color = cs.onSurface,
                                maxLines = 1, overflow = TextOverflow.Ellipsis,
                            )
                            Text(
                                when {
                                    s.builtIn -> "iptv-org"
                                    s.isFile -> "Imported file"
                                    else -> "Custom playlist"
                                },
                                style = MaterialTheme.typography.bodySmall, color = cs.onSurfaceVariant,
                            )
                        }
                    }
                    if (!s.builtIn) {
                        Spacer(Modifier.height(Space.sm))
                        TvActionButton("Remove", { vm.removeSource(s) }, icon = Icons.Rounded.Close)
                    }
                }
            }
            item {
                TvSurface(onClick = { showAdd = true }, modifier = Modifier.width(210.dp).height(116.dp)) {
                    Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.Center) {
                        Icon(Icons.Rounded.Add, null, tint = cs.primary, modifier = Modifier.size(30.dp))
                        Spacer(Modifier.height(Space.xs))
                        Text("Add playlist", style = MaterialTheme.typography.titleSmall, color = cs.onSurface)
                        Text(
                            "Paste an M3U URL", style = MaterialTheme.typography.bodySmall,
                            color = cs.onSurfaceVariant,
                        )
                    }
                }
            }
        }

        if (vm.favChannels.isNotEmpty()) {
            Spacer(Modifier.height(Space.xl))
            SectionHeader(Icons.Rounded.Star, "Favourites")
            Spacer(Modifier.height(Space.md))
            ChannelRow(vm.favChannels, vm, onPlay)
        }
        if (vm.recentChannels.isNotEmpty()) {
            Spacer(Modifier.height(Space.xl))
            SectionHeader(Icons.Rounded.History, "Recently watched")
            Spacer(Modifier.height(Space.md))
            ChannelRow(vm.recentChannels, vm, onPlay)
        }

        Spacer(Modifier.height(Space.xl))
        SectionHeader(Icons.Rounded.Tune, "Options")
        Spacer(Modifier.height(Space.md))
        TvSurface(onClick = { vm.toggleHideGeo() }, peakScale = 1.02f) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Checkbox(checked = vm.hideGeo, onCheckedChange = null)
                Spacer(Modifier.width(Space.md))
                Column {
                    Text(
                        "Hide geo-blocked channels",
                        style = MaterialTheme.typography.bodyLarge, color = cs.onSurface,
                    )
                    Text(
                        "They only play from inside their own country — leave them shown if you use a VPN.",
                        style = MaterialTheme.typography.bodySmall, color = cs.onSurfaceVariant,
                    )
                }
            }
        }

        Spacer(Modifier.height(Space.md))
        // A plain card holding sibling chips, not one clickable row: a focusable nested in
        // a focusable is skipped by the D-pad, which would put the choices out of reach.
        Column(
            Modifier
                .fillMaxWidth()
                .clip(MaterialTheme.shapes.medium)
                .background(cs.surfaceContainer)
                .border(1.dp, cs.outlineVariant, MaterialTheme.shapes.medium)
                .padding(horizontal = Space.lg, vertical = Space.md),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Rounded.Timer, null, tint = cs.onSurfaceVariant, modifier = Modifier.size(24.dp))
                Spacer(Modifier.width(Space.md))
                Column {
                    Text("Pre-buffer", style = MaterialTheme.typography.bodyLarge, color = cs.onSurface)
                    Text(
                        "How much of a channel to load before it plays. More rides out a patchy connection, " +
                            "but channels start slower and play further behind live. Some live channels keep " +
                            "less than a minute, so the longest settings may not fill. 2.5 s is the usual.",
                        style = MaterialTheme.typography.bodySmall, color = cs.onSurfaceVariant,
                    )
                }
            }
            Spacer(Modifier.height(Space.md))
            // Indented to the text column, so the choices read as answers to the label above.
            Row(
                Modifier.padding(start = 24.dp + Space.md),
                horizontalArrangement = Arrangement.spacedBy(Space.sm),
            ) {
                PREBUFFER_CHOICES.forEach { ms ->
                    TvChip(prebufferLabel(ms), vm.prebufferMs == ms) { vm.choosePrebuffer(ms) }
                }
            }
        }

        Spacer(Modifier.height(Space.md))
        TvSurface(onClick = { vm.clearCache() }, peakScale = 1.02f) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Rounded.DeleteSweep, null, tint = cs.onSurfaceVariant, modifier = Modifier.size(24.dp))
                Spacer(Modifier.width(Space.md))
                Column(Modifier.weight(1f)) {
                    Text(
                        "Clear cached data",
                        style = MaterialTheme.typography.bodyLarge, color = cs.onSurface,
                    )
                    Text(
                        "Downloaded playlists and channel logos. Favourites and your playlists are kept.",
                        style = MaterialTheme.typography.bodySmall, color = cs.onSurfaceVariant,
                    )
                }
                Spacer(Modifier.width(Space.md))
                Text(
                    formatBytes(vm.cacheBytes),
                    style = MaterialTheme.typography.labelLarge, color = cs.primary,
                )
            }
        }

        Spacer(Modifier.height(Space.xl))

        if (showAdd) {
            AddPlaylistDialog(
                onAdd = { n, u -> vm.addSource(n, u); showAdd = false },
                onCancel = { showAdd = false },
            )
        }
    }
}

/** Compact horizontal channel tiles for the Home favourites / recents rows. */
@Composable
private fun ChannelRow(list: List<Channel>, vm: IptvViewModel, onPlay: (Channel) -> Unit) {
    val ctx = LocalContext.current
    LazyRow(
        horizontalArrangement = Arrangement.spacedBy(Space.md),
        contentPadding = PaddingValues(horizontal = Space.xs, vertical = Space.sm),
    ) {
        items(list, key = { it.key }) { c ->
            TvSurface(
                onClick = { vm.play(c, list); onPlay(c) },
                onLongClick = { copyStreamUrl(ctx, c) },
                modifier = Modifier.width(212.dp).height(76.dp),
                contentPadding = PaddingValues(Space.md),
            ) {
                Row(Modifier.fillMaxSize(), verticalAlignment = Alignment.CenterVertically) {
                    ChannelLogo(c.logo, 40.dp)
                    Spacer(Modifier.width(Space.md))
                    Column {
                        Text(
                            c.clean,
                            style = MaterialTheme.typography.titleSmall,
                            color = MaterialTheme.colorScheme.onSurface,
                            maxLines = 2, overflow = TextOverflow.Ellipsis,
                        )
                        c.quality?.let {
                            Text(
                                it, style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun AddPlaylistDialog(onAdd: (String, String) -> Unit, onCancel: () -> Unit) {
    var name by remember { mutableStateOf("") }
    var url by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onCancel,
        title = { Text("Add a playlist") },
        text = {
            Column {
                Text(
                    "Paste the M3U or M3U8 URL from your provider.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(Space.md))
                OutlinedTextField(
                    name, { name = it }, label = { Text("Name") }, singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(Space.sm))
                OutlinedTextField(
                    url, { url = it }, label = { Text("https://…/playlist.m3u") }, singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        },
        confirmButton = { Button(onClick = { if (url.isNotBlank()) onAdd(name, url) }) { Text("Add") } },
        dismissButton = { TextButton(onClick = onCancel) { Text("Cancel") } },
    )
}

@Composable
private fun RenamePlaylistDialog(current: String, onRename: (String) -> Unit, onCancel: () -> Unit) {
    var name by remember { mutableStateOf(current) }
    AlertDialog(
        onDismissRequest = onCancel,
        title = { Text("Rename playlist") },
        text = {
            OutlinedTextField(
                name, { name = it }, label = { Text("Name") }, singleLine = true,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = { if (name.isNotBlank()) onRename(name.trim()) }),
                modifier = Modifier.fillMaxWidth(),
            )
        },
        confirmButton = {
            Button(onClick = { onRename(name.trim()) }, enabled = name.isNotBlank()) { Text("Rename") }
        },
        dismissButton = { TextButton(onClick = onCancel) { Text("Cancel") } },
    )
}

@Composable
fun ChannelsScreen(vm: IptvViewModel, onPlay: (Channel) -> Unit, onBack: () -> Unit) {
    val list = vm.visible
    val cs = MaterialTheme.colorScheme
    val ctx = LocalContext.current
    val filtered = vm.query.isNotBlank() || vm.group != null
    val focusManager = LocalFocusManager.current
    val listFocus = remember { FocusRequester() }

    // Land on the channels, not the search box: a remote reaching this screen should
    // be able to pick something without first dismissing a keyboard it never asked for.
    LaunchedEffect(vm.currentSource?.id) {
        repeat(20) {
            if (vm.visible.isNotEmpty()) {
                runCatching { listFocus.requestFocus() }
                return@LaunchedEffect
            }
            delay(150)
        }
    }
    Column(
        Modifier
            .fillMaxSize()
            .background(cs.background)
            .safeDrawingPadding()
            .padding(horizontal = Space.xl, vertical = Space.lg),
    ) {
        // Laid out like a top app bar: navigation at the start, title, actions at the
        // end. Going back was sitting on the right as a labelled pill heavy enough to
        // out-shout the screen's own title.
        Row(verticalAlignment = Alignment.CenterVertically) {
            TvIconButton(
                Icons.AutoMirrored.Rounded.ArrowBack, "Back to sources", onBack,
                tint = cs.onSurface,
            )
            Spacer(Modifier.width(Space.md))
            vm.currentSource?.takeIf { it.builtIn }?.let {
                Text(it.emoji, fontSize = 24.sp)
                Spacer(Modifier.width(Space.md))
            }
            Column(Modifier.weight(1f)) {
                Text(
                    vm.currentSource?.name ?: "Channels",
                    style = MaterialTheme.typography.headlineSmall, color = cs.onSurface,
                    maxLines = 1, overflow = TextOverflow.Ellipsis,
                )
                if (!vm.loading && vm.error == null) {
                    Text(
                        channelCount(list.size),
                        style = MaterialTheme.typography.bodyMedium, color = cs.onSurfaceVariant,
                    )
                }
            }
            Spacer(Modifier.width(Space.md))
            val source = vm.currentSource
            // Playlists you added or were handed can be renamed; a file arrives named after
            // whatever its sender called it.
            if (source != null && !source.builtIn) {
                var renaming by remember { mutableStateOf(false) }
                TvIconButton(Icons.Rounded.Edit, "Rename this playlist", { renaming = true }, tint = cs.onSurface)
                if (renaming) {
                    RenamePlaylistDialog(
                        current = source.name,
                        onRename = { vm.renameSource(source, it); renaming = false },
                        onCancel = { renaming = false },
                    )
                }
            }
            // A file has nowhere to reload from — our stored copy is the playlist — and a
            // button that does nothing is worse than none.
            if (source?.isFile != true) {
                TvIconButton(
                    Icons.Rounded.Refresh, "Reload this playlist",
                    { source?.let { vm.open(it, refresh = true) } },
                    tint = cs.onSurface,
                )
            }
        }

        Spacer(Modifier.height(Space.lg))
        OutlinedTextField(
            value = vm.query,
            onValueChange = { vm.query = it },
            label = { Text("Search channels") },
            leadingIcon = { Icon(Icons.Rounded.Search, null) },
            trailingIcon = {
                if (vm.query.isNotEmpty()) {
                    TvIconButton(Icons.Rounded.Close, "Clear search", { vm.query = "" })
                }
            },
            singleLine = true,
            shape = MaterialTheme.shapes.medium,
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
            keyboardActions = KeyboardActions(onSearch = { focusManager.clearFocus() }),
            // A text field swallows the D-pad, which strands a remote inside the box.
            // Take the vertical keys back and hand focus to the neighbouring row.
            modifier = Modifier
                .fillMaxWidth()
                .onPreviewKeyEvent { ev ->
                    if (ev.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                    when (ev.key) {
                        Key.DirectionDown -> { focusManager.moveFocus(FocusDirection.Down); true }
                        Key.DirectionUp -> { focusManager.moveFocus(FocusDirection.Up); true }
                        else -> false
                    }
                },
        )

        if (vm.groups.isNotEmpty()) {
            Spacer(Modifier.height(Space.md))
            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(Space.sm),
                contentPadding = PaddingValues(horizontal = Space.xs, vertical = Space.xs),
            ) {
                item { TvChip("All", vm.group == null) { vm.group = null } }
                items(vm.groups, key = { it }) { g ->
                    TvChip(g, vm.group == g) { vm.group = g }
                }
            }
        }

        Spacer(Modifier.height(Space.md))
        when {
            vm.loading -> CenteredState {
                CircularProgressIndicator(color = cs.primary)
                Spacer(Modifier.height(Space.md))
                Text("Loading playlist…", style = MaterialTheme.typography.bodyLarge, color = cs.onSurfaceVariant)
            }

            vm.error != null -> CenteredState {
                Icon(Icons.Rounded.WarningAmber, null, tint = cs.error, modifier = Modifier.size(40.dp))
                Spacer(Modifier.height(Space.md))
                Text("Couldn't load this playlist", style = MaterialTheme.typography.titleMedium, color = cs.onSurface)
                Spacer(Modifier.height(Space.xs))
                Text(
                    vm.error!!, style = MaterialTheme.typography.bodyMedium, color = cs.error,
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                )
                Spacer(Modifier.height(Space.lg))
                TvActionButton(
                    "Try again",
                    { vm.currentSource?.let { vm.open(it, refresh = true) } },
                    icon = Icons.Rounded.Refresh,
                )
            }

            list.isEmpty() -> CenteredState {
                Icon(Icons.Rounded.SearchOff, null, tint = cs.onSurfaceVariant, modifier = Modifier.size(40.dp))
                Spacer(Modifier.height(Space.md))
                Text(
                    if (filtered) "No channels match" else "This playlist is empty",
                    style = MaterialTheme.typography.titleMedium, color = cs.onSurface,
                )
                Spacer(Modifier.height(Space.xs))
                Text(
                    if (filtered) "Try a different search or group." else "The playlist loaded but contained no channels.",
                    style = MaterialTheme.typography.bodyMedium, color = cs.onSurfaceVariant,
                )
                if (filtered) {
                    Spacer(Modifier.height(Space.lg))
                    TvActionButton("Clear filters", { vm.query = ""; vm.group = null }, icon = Icons.Rounded.Close)
                }
            }

            else -> LazyColumn(
                modifier = Modifier.focusRequester(listFocus).focusGroup(),
                verticalArrangement = Arrangement.spacedBy(Space.sm),
                contentPadding = PaddingValues(vertical = Space.xs),
            ) {
                items(list, key = { it.key }) { c ->
                    // Play and favourite are siblings, not nested: a focusable inside a
                    // focusable is skipped by the D-pad, which would put the star out of
                    // reach of a remote entirely.
                    Row(
                        Modifier.fillMaxWidth().focusGroup(),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                      TvSurface(
                        onClick = { vm.play(c, list); onPlay(c) },
                        onLongClick = { copyStreamUrl(ctx, c) },
                        modifier = Modifier.weight(1f),
                        peakScale = 1.02f,
                        contentPadding = PaddingValues(horizontal = Space.md, vertical = Space.sm),
                      ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            ChannelLogo(c.logo, 48.dp)
                            Spacer(Modifier.width(Space.md))
                            Column(Modifier.weight(1f)) {
                                Text(
                                    c.clean, style = MaterialTheme.typography.titleSmall, color = cs.onSurface,
                                    maxLines = 1, overflow = TextOverflow.Ellipsis,
                                )
                                Spacer(Modifier.height(2.dp))
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    val meta = buildString {
                                        append(c.groups.joinToString(" · "))
                                        c.quality?.let { append("  ·  $it") }
                                    }
                                    Text(
                                        meta, style = MaterialTheme.typography.bodySmall,
                                        color = cs.onSurfaceVariant,
                                        maxLines = 1, overflow = TextOverflow.Ellipsis,
                                        modifier = Modifier.weight(1f, fill = false),
                                    )
                                    if (c.geoBlocked) {
                                        Spacer(Modifier.width(Space.md))
                                        Badge(Icons.Rounded.Public, "Geo-blocked", cs.tertiary)
                                    }
                                    if (c.partTime) {
                                        Spacer(Modifier.width(Space.md))
                                        Badge(Icons.Rounded.Schedule, "Part-time", cs.onSurfaceVariant)
                                    }
                                }
                            }
                        }
                      }
                      Spacer(Modifier.width(Space.sm))
                      val fav = vm.isFav(c)
                      // The row's own action is the one that carries the channel name; the
                      // star is a short label with its state, so a screen reader says
                      // "Favourite, on" rather than repeating the name for every row.
                      TvIconButton(
                          icon = if (fav) Icons.Rounded.Star else Icons.Rounded.StarBorder,
                          contentDescription = "Favourite",
                          onClick = { vm.toggleFav(c) },
                          modifier = Modifier.semantics { stateDescription = if (fav) "On" else "Off" },
                          tint = if (fav) cs.primary else cs.onSurfaceVariant,
                      )
                    }
                }
            }
        }
    }
}

/** Centred full-height state block — loading, error and empty share one layout. */
@Composable
private fun CenteredState(content: @Composable androidx.compose.foundation.layout.ColumnScope.() -> Unit) {
    Box(Modifier.fillMaxSize(), Alignment.Center) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.padding(horizontal = Space.xl),
            content = content,
        )
    }
}
