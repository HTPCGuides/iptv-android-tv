package com.iptvtv.ui.home

import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.DeleteSweep
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.PlaylistPlay
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material.icons.rounded.Timer
import androidx.compose.material.icons.rounded.Tune
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.iptvtv.model.Channel
import com.iptvtv.model.PREBUFFER_CHOICES
import com.iptvtv.model.Settings
import com.iptvtv.model.Source
import com.iptvtv.model.SourceKind
import com.iptvtv.ui.ChannelLogo
import com.iptvtv.ui.SectionHeader
import com.iptvtv.ui.Space
import com.iptvtv.ui.TvActionButton
import com.iptvtv.ui.TvChip
import com.iptvtv.ui.TvSurface
import com.iptvtv.ui.containerViewModel
import com.iptvtv.ui.copyStreamUrl
import com.iptvtv.ui.formatBytes

@Composable
fun HomeRoute(
    onOpenSource: (Source) -> Unit,
    onPlay: () -> Unit,
    viewModel: HomeViewModel = containerViewModel { c, _ -> HomeViewModel(c.repository, c.playback, c.ready) },
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    // Logos and playlists downloaded while we were away change the size shown under Options.
    LaunchedEffect(Unit) { viewModel.refreshCacheSize() }
    HomeScreen(
        uiState = uiState,
        onOpenSource = onOpenSource,
        onPlay = { channel, queue -> viewModel.play(channel, queue); onPlay() },
        onAddSource = viewModel::addSource,
        onRemoveSource = viewModel::removeSource,
        onHideGeoBlocked = viewModel::setHideGeoBlocked,
        onChoosePrebuffer = viewModel::choosePrebuffer,
        onClearCache = viewModel::clearCache,
    )
}

@Composable
fun HomeScreen(
    uiState: HomeUiState,
    onOpenSource: (Source) -> Unit,
    onPlay: (Channel, List<Channel>) -> Unit,
    onAddSource: (String, String) -> Unit,
    onRemoveSource: (Source) -> Unit,
    onHideGeoBlocked: (Boolean) -> Unit,
    onChoosePrebuffer: (Int) -> Unit,
    onClearCache: () -> Unit,
) {
    val cs = MaterialTheme.colorScheme
    when (uiState) {
        HomeUiState.Loading -> Box(Modifier.fillMaxSize().background(cs.background), Alignment.Center) {
            CircularProgressIndicator(color = cs.primary)
        }
        is HomeUiState.Ready -> HomeContent(
            uiState, onOpenSource, onPlay, onAddSource, onRemoveSource, onHideGeoBlocked, onChoosePrebuffer, onClearCache,
        )
    }
}

@Composable
private fun HomeContent(
    state: HomeUiState.Ready,
    onOpenSource: (Source) -> Unit,
    onPlay: (Channel, List<Channel>) -> Unit,
    onAddSource: (String, String) -> Unit,
    onRemoveSource: (Source) -> Unit,
    onHideGeoBlocked: (Boolean) -> Unit,
    onChoosePrebuffer: (Int) -> Unit,
    onClearCache: () -> Unit,
) {
    var showAdd by remember { mutableStateOf(false) }
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
            items(state.sources, key = { it.id }) { s ->
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    SourceTile(s, onClick = { onOpenSource(s) })
                    if (!s.builtIn) {
                        Spacer(Modifier.height(Space.sm))
                        TvActionButton("Remove", { onRemoveSource(s) }, icon = Icons.Rounded.Close)
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

        if (state.favourites.isNotEmpty()) {
            Spacer(Modifier.height(Space.xl))
            SectionHeader(Icons.Rounded.Star, "Favourites")
            Spacer(Modifier.height(Space.md))
            ChannelRow(state.favourites, onPlay)
        }
        if (state.recents.isNotEmpty()) {
            Spacer(Modifier.height(Space.xl))
            SectionHeader(Icons.Rounded.History, "Recently watched")
            Spacer(Modifier.height(Space.md))
            ChannelRow(state.recents, onPlay)
        }

        Spacer(Modifier.height(Space.xl))
        SectionHeader(Icons.Rounded.Tune, "Options")
        Spacer(Modifier.height(Space.md))
        OptionsSection(state.settings, state.cacheBytes, onHideGeoBlocked, onChoosePrebuffer, onClearCache)
        Spacer(Modifier.height(Space.xl))

        if (showAdd) {
            AddPlaylistDialog(
                onAdd = { n, u -> onAddSource(n, u); showAdd = false },
                onCancel = { showAdd = false },
            )
        }
    }
}

@Composable
private fun SourceTile(s: Source, onClick: () -> Unit) {
    val cs = MaterialTheme.colorScheme
    TvSurface(onClick = onClick, modifier = Modifier.width(210.dp).height(116.dp)) {
        Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.Center) {
            if (s.builtIn) {
                Text(s.emoji, fontSize = 28.sp)   // the flag is the source's identity
            } else {
                Icon(Icons.Rounded.PlaylistPlay, null, tint = cs.primary, modifier = Modifier.size(30.dp))
            }
            Spacer(Modifier.height(Space.xs))
            Text(
                s.name, style = MaterialTheme.typography.titleSmall, color = cs.onSurface,
                maxLines = 1, overflow = TextOverflow.Ellipsis,
            )
            Text(
                when (s.kind) {
                    SourceKind.BUILT_IN -> "iptv-org"
                    SourceKind.FILE -> "Imported file"
                    SourceKind.URL -> "Custom playlist"
                },
                style = MaterialTheme.typography.bodySmall, color = cs.onSurfaceVariant,
            )
        }
    }
}

/** Compact horizontal channel tiles for the Home favourites / recents rows. */
@Composable
private fun ChannelRow(list: List<Channel>, onPlay: (Channel, List<Channel>) -> Unit) {
    val ctx = LocalContext.current
    LazyRow(
        horizontalArrangement = Arrangement.spacedBy(Space.md),
        contentPadding = PaddingValues(horizontal = Space.xs, vertical = Space.sm),
    ) {
        items(list, key = { it.key }) { c ->
            TvSurface(
                onClick = { onPlay(c, list) },
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
private fun OptionsSection(
    settings: Settings,
    cacheBytes: Long,
    onHideGeoBlocked: (Boolean) -> Unit,
    onChoosePrebuffer: (Int) -> Unit,
    onClearCache: () -> Unit,
) {
    val cs = MaterialTheme.colorScheme
    TvSurface(onClick = { onHideGeoBlocked(!settings.hideGeoBlocked) }, peakScale = 1.02f) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Checkbox(checked = settings.hideGeoBlocked, onCheckedChange = null)
            Spacer(Modifier.width(Space.md))
            Column {
                Text("Hide geo-blocked channels", style = MaterialTheme.typography.bodyLarge, color = cs.onSurface)
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
                TvChip(prebufferLabel(ms), settings.prebufferMs == ms) { onChoosePrebuffer(ms) }
            }
        }
    }

    Spacer(Modifier.height(Space.md))
    TvSurface(onClick = onClearCache, peakScale = 1.02f) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Rounded.DeleteSweep, null, tint = cs.onSurfaceVariant, modifier = Modifier.size(24.dp))
            Spacer(Modifier.width(Space.md))
            Column(Modifier.weight(1f)) {
                Text("Clear cached data", style = MaterialTheme.typography.bodyLarge, color = cs.onSurface)
                Text(
                    "Downloaded channel lists and logos. Favourites, your playlists and imported files are kept.",
                    style = MaterialTheme.typography.bodySmall, color = cs.onSurfaceVariant,
                )
            }
            Spacer(Modifier.width(Space.md))
            Text(formatBytes(cacheBytes), style = MaterialTheme.typography.labelLarge, color = cs.primary)
        }
    }
}

/** "1 s", "2.5 s" — whole seconds without a stray ".0", decimals in the user's own format. */
private fun prebufferLabel(ms: Int) =
    if (ms % 1000 == 0) "${ms / 1000} s" else "%.1f s".format(ms / 1000f)

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
