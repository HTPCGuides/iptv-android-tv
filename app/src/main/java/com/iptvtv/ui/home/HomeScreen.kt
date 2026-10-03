@file:OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)

package com.iptvtv.ui.home

import com.iptvtv.ui.screenPadding
import com.iptvtv.ui.ReadableWidth
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.state.ToggleableState
import androidx.compose.ui.semantics.toggleableState
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.Role
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.OpenInNew
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.BatterySaver
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Memory
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.WarningAmber
import com.iptvtv.ui.appContainer
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
import com.iptvtv.ui.ChannelSheet
import com.iptvtv.ui.formatBytes

@Composable
fun HomeRoute(
    onOpenSource: (Source) -> Unit,
    onPlay: () -> Unit,
    viewModel: HomeViewModel = containerViewModel { c, _ -> HomeViewModel(c.repository, c.playback, c.ready, c.aceEngine) },
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val ctx = LocalContext.current
    val aceEngine = appContainer().aceEngine
    // Cache size and the engine's state both change while Home is away; re-read on return.
    LaunchedEffect(Unit) { viewModel.refresh() }
    HomeScreen(
        uiState = uiState,
        onOpenSource = onOpenSource,
        onPlay = { channel, queue -> viewModel.play(channel, queue); onPlay() },
        onAddSource = viewModel::addSource,
        onRemoveSource = viewModel::removeSource,
        onHideGeoBlocked = viewModel::setHideGeoBlocked,
        onChoosePrebuffer = viewModel::choosePrebuffer,
        onClearCache = viewModel::clearCache,
        onOpenAceStream = { aceEngine.open()?.let(ctx::startActivity) },
        onAceStreamSettings = { aceEngine.appSettings()?.let(ctx::startActivity) },
        onCheckAceEngine = viewModel::refresh,
        onToggleFavourite = viewModel::toggleFavourite,
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
    onOpenAceStream: () -> Unit = {},
    onAceStreamSettings: () -> Unit = {},
    onCheckAceEngine: () -> Unit = {},
    onToggleFavourite: (Channel) -> Unit = {},
) {
    val cs = MaterialTheme.colorScheme
    when (uiState) {
        HomeUiState.Loading -> Box(Modifier.fillMaxSize().background(cs.background), Alignment.Center) {
            CircularProgressIndicator(color = cs.primary)
        }
        is HomeUiState.Ready -> HomeContent(
            uiState, onOpenSource, onPlay, onAddSource, onRemoveSource, onHideGeoBlocked, onChoosePrebuffer, onClearCache,
            onOpenAceStream, onAceStreamSettings, onCheckAceEngine, onToggleFavourite,
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
    onOpenAceStream: () -> Unit,
    onAceStreamSettings: () -> Unit,
    onCheckAceEngine: () -> Unit,
    onToggleFavourite: (Channel) -> Unit,
) {
    var showAdd by remember { mutableStateOf(false) }
    val favourites = remember(state.favourites) { state.favourites.map { it.favKey }.toSet() }
    var removing by remember { mutableStateOf<Source?>(null) }
    val cs = MaterialTheme.colorScheme
    Column(
        Modifier
            .fillMaxSize()
            .background(cs.background)
            .safeDrawingPadding()          // Android 15 draws edge-to-edge; keep clear of the bars
            .verticalScroll(rememberScrollState())
            .screenPadding(),
    ) {
        Text("HTPCGuides IPTV", style = MaterialTheme.typography.displaySmall, color = cs.onSurface)
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
                        // Named for TalkBack — with two custom sources, two buttons both
                        // called "Remove" — and confirmed, since it takes a whole source away.
                        TvActionButton(
                            "Remove", { removing = s },
                            modifier = Modifier.semantics { contentDescription = "Remove ${s.name}" },
                            icon = Icons.Rounded.Close,
                        )
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
            ChannelRow(state.favourites, favourites, onPlay, onToggleFavourite)
        }
        if (state.recents.isNotEmpty()) {
            Spacer(Modifier.height(Space.xl))
            SectionHeader(Icons.Rounded.History, "Recently watched")
            Spacer(Modifier.height(Space.md))
            ChannelRow(state.recents, favourites, onPlay, onToggleFavourite)
        }

        Spacer(Modifier.height(Space.xl))
        SectionHeader(Icons.Rounded.Tune, "Options")
        Spacer(Modifier.height(Space.md))
        Column(Modifier.widthIn(max = ReadableWidth)) {
            OptionsSection(state.settings, state.cacheBytes, onHideGeoBlocked, onChoosePrebuffer, onClearCache)
        }

        if (state.aceEngine != AceEngineState.NOT_INSTALLED) {
            Spacer(Modifier.height(Space.xl))
            SectionHeader(Icons.Rounded.Memory, "Ace Stream engine")
            Spacer(Modifier.height(Space.md))
            Box(Modifier.widthIn(max = ReadableWidth)) {
                AceEngineCard(state.aceEngine, onOpenAceStream, onAceStreamSettings, onCheckAceEngine)
            }
        }
        Spacer(Modifier.height(Space.xl))

        removing?.let { s ->
            RemoveSourceDialog(s, onConfirm = { onRemoveSource(s); removing = null }, onCancel = { removing = null })
        }
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
private fun ChannelRow(
    list: List<Channel>,
    favourites: Set<String>,
    onPlay: (Channel, List<Channel>) -> Unit,
    onToggleFavourite: (Channel) -> Unit,
) {
    var held by remember { mutableStateOf<Channel?>(null) }
    held?.let { c ->
        ChannelSheet(
            c, isFavourite = c.favKey in favourites,
            onPlay = { onPlay(c, list) }, onToggleFavourite = { onToggleFavourite(c) }, onDismiss = { held = null },
        )
    }
    LazyRow(
        horizontalArrangement = Arrangement.spacedBy(Space.md),
        contentPadding = PaddingValues(horizontal = Space.xs, vertical = Space.sm),
    ) {
        items(list, key = { it.key }) { c ->
            TvSurface(
                onClick = { onPlay(c, list) },
                onLongClick = { held = c },
                // A floor, not a fixed height: a two-line name plus its quality line
                // needs more than 76dp, and the quality line was being cut in half.
                modifier = Modifier.width(212.dp).heightIn(min = 76.dp),
                contentPadding = PaddingValues(Space.md),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    ChannelLogo(c.logo, 40.dp, c.groups.first())
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
    TvSurface(
        onClick = { onHideGeoBlocked(!settings.hideGeoBlocked) },
        // Full width like the cards under it — it ended a third of the way across a
        // tablet — and a checkbox to a screen reader, which now hears on or off.
        modifier = Modifier.fillMaxWidth().semantics { toggleableState = ToggleableState(settings.hideGeoBlocked) },
        role = Role.Checkbox,
        peakScale = 1.02f,
    ) {
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
                    "How much to load before a channel plays. More rides out a patchy connection but " +
                        "starts slower and runs further behind live; some channels hold under a minute, so " +
                        "the longest may not fill. 2.5 s is the usual.",
                    style = MaterialTheme.typography.bodySmall, color = cs.onSurfaceVariant,
                )
            }
        }
        Spacer(Modifier.height(Space.md))
        // Indented to the text column, so the choices read as answers to the label above.
        // Wraps rather than squeezes: five chips fit a TV or a landscape phone, not a
        // portrait one, where a fixed Row crushed the last chip into a vertical "3 / 0 / s".
        FlowRow(
            Modifier.padding(start = 24.dp + Space.md).selectableGroup(),
            horizontalArrangement = Arrangement.spacedBy(Space.sm),
            verticalArrangement = Arrangement.spacedBy(Space.sm),
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

/**
 * Ace Stream channels play through the engine app on this phone, and OxygenOS kills that
 * app in the background — which is what "the stream keeps stopping" was. This says whether
 * the engine is up, starts it, and takes you to the one setting that stops the killing.
 */
@Composable
private fun AceEngineCard(
    engine: AceEngineState,
    onOpen: () -> Unit,
    onSettings: () -> Unit,
    onCheck: () -> Unit,
) {
    val cs = MaterialTheme.colorScheme
    Column(
        Modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.medium)
            .background(cs.surfaceContainer)
            .border(1.dp, cs.outlineVariant, MaterialTheme.shapes.medium)
            .padding(horizontal = Space.lg, vertical = Space.md),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            val (label, tint) = when (engine) {
                AceEngineState.RUNNING -> "Engine running" to cs.primary
                AceEngineState.STOPPED -> "Engine not running" to cs.tertiary
                else -> "Checking the engine…" to cs.onSurfaceVariant
            }
            Icon(
                if (engine == AceEngineState.RUNNING) Icons.Rounded.CheckCircle else Icons.Rounded.WarningAmber,
                null, tint = tint, modifier = Modifier.size(24.dp),
            )
            Spacer(Modifier.width(Space.md))
            Column(Modifier.weight(1f)) {
                Text(label, style = MaterialTheme.typography.bodyLarge, color = cs.onSurface)
                Text(
                    "Ace Stream channels play through the Ace Stream app's engine. The phone stops it in " +
                        "the background to save battery, taking the stream with it — set its battery use to " +
                        "Unrestricted and it stays up.",
                    style = MaterialTheme.typography.bodySmall, color = cs.onSurfaceVariant,
                )
            }
        }
        Spacer(Modifier.height(Space.md))
        FlowRow(
            Modifier.padding(start = 24.dp + Space.md),
            horizontalArrangement = Arrangement.spacedBy(Space.sm),
            verticalArrangement = Arrangement.spacedBy(Space.sm),
        ) {
            TvActionButton("Allow in background", onSettings, icon = Icons.Rounded.BatterySaver)
            if (engine == AceEngineState.STOPPED) {
                TvActionButton("Start engine", onOpen, icon = Icons.AutoMirrored.Rounded.OpenInNew)
            }
            TvActionButton("Check again", onCheck, icon = Icons.Rounded.Refresh)
        }
    }
}

/** "1 s", "2.5 s" — whole seconds without a stray ".0", decimals in the user's own format. */
private fun prebufferLabel(ms: Int) =
    if (ms % 1000 == 0) "${ms / 1000} s" else "%.1f s".format(ms / 1000f)

@Composable
private fun RemoveSourceDialog(source: Source, onConfirm: () -> Unit, onCancel: () -> Unit) {
    AlertDialog(
        onDismissRequest = onCancel,
        title = { Text("Remove ${source.name}?") },
        text = {
            Text(
                if (source.isFile) {
                    "It was imported from a file, so its channels only come back by importing that file again."
                } else {
                    "It comes off your sources. You can add the playlist URL again later."
                },
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        },
        confirmButton = { Button(onClick = onConfirm) { Text("Remove") } },
        dismissButton = { TextButton(onClick = onCancel) { Text("Cancel") } },
    )
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
                    url, { url = it },
                    label = { Text("Playlist URL") },
                    placeholder = { Text("https://…/playlist.m3u") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        },
        confirmButton = { Button(onClick = { onAdd(name, url) }, enabled = url.isNotBlank()) { Text("Add") } },
        dismissButton = { TextButton(onClick = onCancel) { Text("Cancel") } },
    )
}
