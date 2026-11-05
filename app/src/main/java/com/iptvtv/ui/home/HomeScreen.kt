@file:OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)

package com.iptvtv.ui.home

import androidx.compose.material.icons.rounded.Edit
import com.iptvtv.model.DEFAULT_ACE_ENGINE
import com.iptvtv.model.DEFAULT_ACE_PROFILE
import com.iptvtv.ace.EngineAddress
import com.iptvtv.ace.Profiles
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Checklist
import androidx.compose.material.icons.rounded.DoneAll
import androidx.compose.material.icons.rounded.FolderOpen
import androidx.compose.material.icons.rounded.RadioButtonUnchecked
import androidx.compose.material.icons.rounded.SatelliteAlt
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.selected
import com.iptvtv.ui.TvIconButton
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
    onOpenSources: (List<Source>) -> Unit = {},
    viewModel: HomeViewModel = containerViewModel { c, _ -> HomeViewModel(c.repository, c.playback, c.ready, c.aceEngine, c.aceBuilder) },
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val ctx = LocalContext.current
    // Any number of playlist files from this device's storage at once. The type filter is
    // loose on purpose: file managers label .m3u files every way imaginable.
    val pickFiles = rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris ->
        viewModel.importFiles(ctx, uris)
    }
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
        sourceActions = SourceActions(
            openTogether = onOpenSources,
            refresh = viewModel::refreshSources,
            remove = viewModel::removeSources,
            buildAce = viewModel::buildAceVersion,
            pickFiles = { pickFiles.launch(arrayOf("*/*")) },
            setAceKeep = viewModel::setAceKeepVersions,
            setAceEngine = viewModel::setAceEngineAddr,
            setAceProfile = viewModel::setAceProfile,
            dismissActivity = viewModel::dismissActivity,
        ),
    )
}

/** What Home can do to several playlists at once, and the Ace Stream build. */
data class SourceActions(
    val openTogether: (List<Source>) -> Unit = {},
    val refresh: (List<Source>) -> Unit = {},
    val remove: (List<Source>) -> Unit = {},
    val buildAce: () -> Unit = {},
    val pickFiles: () -> Unit = {},
    val setAceKeep: (Int) -> Unit = {},
    val setAceEngine: (String) -> Unit = {},
    val setAceProfile: (String) -> Unit = {},
    val dismissActivity: () -> Unit = {},
)

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
    sourceActions: SourceActions = SourceActions(),
) {
    val cs = MaterialTheme.colorScheme
    when (uiState) {
        HomeUiState.Loading -> Box(Modifier.fillMaxSize().background(cs.background), Alignment.Center) {
            CircularProgressIndicator(color = cs.primary)
        }
        is HomeUiState.Ready -> HomeContent(
            uiState, onOpenSource, onPlay, onAddSource, onRemoveSource, onHideGeoBlocked, onChoosePrebuffer, onClearCache,
            onOpenAceStream, onAceStreamSettings, onCheckAceEngine, onToggleFavourite, sourceActions,
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
    actions: SourceActions,
) {
    var showAdd by remember { mutableStateOf(false) }
    // Selecting several sources: long-press a tile (or Select) to start, tap to add or drop.
    var selecting by rememberSaveable { mutableStateOf(false) }
    var selectedIds by rememberSaveable { mutableStateOf(listOf<String>()) }
    val selected = state.sources.filter { it.id in selectedIds }
    var removingMany by remember { mutableStateOf<List<Source>?>(null) }
    fun endSelection() { selecting = false; selectedIds = emptyList() }
    fun toggle(s: Source) { selectedIds = if (s.id in selectedIds) selectedIds - s.id else selectedIds + s.id }
    val aceInstalled = state.aceEngine != AceEngineState.NOT_INSTALLED
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
        SourcesHeader(
            selecting = selecting,
            selected = selected,
            onStart = { selecting = true },
            onOpenTogether = { actions.openTogether(selected); endSelection() },
            onRefresh = { actions.refresh(selected); endSelection() },
            onRemove = { removingMany = selected.filterNot { it.builtIn } },
            onSelectAll = { selectedIds = state.sources.map { it.id } },
            onDone = ::endSelection,
        )
        Spacer(Modifier.height(Space.md))
        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(Space.md),
            contentPadding = PaddingValues(horizontal = Space.xs, vertical = Space.sm),
        ) {
            items(state.sources, key = { it.id }) { s ->
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    SourceTile(
                        s,
                        selection = if (selecting) s.id in selectedIds else null,
                        onClick = { if (selecting) toggle(s) else onOpenSource(s) },
                        onLongClick = { selecting = true; toggle(s) },
                    )
                    if (!s.builtIn && !selecting) {
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
            if (aceInstalled && !selecting) {
                item(key = "ace-build") {
                    // Built here, from the engine's channel index as it is right now; each
                    // build is saved as a new version beside the old ones.
                    val working = state.activity is HomeActivity.Working
                    TvSurface(onClick = actions.buildAce, modifier = Modifier.width(210.dp).heightIn(min = 116.dp)) {
                        Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.Center) {
                            Icon(Icons.Rounded.SatelliteAlt, null, tint = cs.primary, modifier = Modifier.size(30.dp))
                            Spacer(Modifier.height(Space.xs))
                            Text("Fresh from Ace Stream", style = MaterialTheme.typography.titleSmall, color = cs.onSurface)
                            Text(
                                if (working) "Building…" else "Build a new version now",
                                style = MaterialTheme.typography.bodySmall, color = cs.onSurfaceVariant,
                            )
                        }
                    }
                }
            }
            if (!selecting) item {
                TvSurface(onClick = { showAdd = true }, modifier = Modifier.width(210.dp).height(116.dp)) {
                    Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.Center) {
                        Icon(Icons.Rounded.Add, null, tint = cs.primary, modifier = Modifier.size(30.dp))
                        Spacer(Modifier.height(Space.xs))
                        Text("Add playlist", style = MaterialTheme.typography.titleSmall, color = cs.onSurface)
                        Text(
                            "URLs or files", style = MaterialTheme.typography.bodySmall,
                            color = cs.onSurfaceVariant,
                        )
                    }
                }
            }
        }

        state.activity?.let { ActivityLine(it, actions.dismissActivity) }

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

        run {
            Spacer(Modifier.height(Space.xl))
            SectionHeader(Icons.Rounded.Memory, "Ace Stream engine")
            Spacer(Modifier.height(Space.md))
            Box(Modifier.widthIn(max = ReadableWidth)) {
                AceEngineCard(
                    state.aceEngine, onOpenAceStream, onAceStreamSettings, onCheckAceEngine,
                    keepVersions = state.settings.aceKeepVersions, onKeepVersions = actions.setAceKeep,
                    engineAddr = state.settings.aceEngineAddr, onEngineAddr = actions.setAceEngine,
                    profileId = state.settings.aceProfile, onProfile = actions.setAceProfile,
                )
            }
        }
        Spacer(Modifier.height(Space.xl))

        removingMany?.let { list ->
            AlertDialog(
                onDismissRequest = { removingMany = null },
                title = { Text(if (list.size == 1) "Remove “${list.single().name}”?" else "Remove ${list.size} playlists?") },
                text = {
                    Text(
                        "Their channels and favourites go with them. The built-in iptv-org sources stay." +
                            if (list.any { it.isFile }) " Imported files can only come back by opening them again." else "",
                    )
                },
                confirmButton = {
                    Button(onClick = { actions.remove(list); removingMany = null; endSelection() }, enabled = list.isNotEmpty()) {
                        Text("Remove")
                    }
                },
                dismissButton = { TextButton(onClick = { removingMany = null }) { Text("Cancel") } },
            )
        }
        removing?.let { s ->
            RemoveSourceDialog(s, onConfirm = { onRemoveSource(s); removing = null }, onCancel = { removing = null })
        }
        if (showAdd) {
            AddPlaylistDialog(
                onAdd = { n, u -> onAddSource(n, u); showAdd = false },
                onPickFiles = { showAdd = false; actions.pickFiles() },
                onCancel = { showAdd = false },
            )
        }
    }
}

@Composable
private fun SourceTile(
    s: Source,
    onClick: () -> Unit,
    onLongClick: (() -> Unit)? = null,
    /** Null outside selection; otherwise whether this tile is picked. */
    selection: Boolean? = null,
) {
    val cs = MaterialTheme.colorScheme
    TvSurface(
        onClick = onClick, onLongClick = onLongClick,
        // A dated Ace Stream title takes two lines; the tile grows rather than clipping it.
        modifier = Modifier.width(210.dp).heightIn(min = 116.dp)
            .then(if (selection == true) Modifier.border(2.dp, cs.primary, MaterialTheme.shapes.medium) else Modifier)
            .semantics { if (selection != null) selected = selection },
        role = if (selection != null) Role.Checkbox else Role.Button,
        contentAlignment = Alignment.CenterStart,   // tiles of differing height still centre alike
    ) {
        Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.Center) {
            // One fixed-height slot for the flag or icon, so a tall emoji (⚽) doesn't push its
            // tile's name lower than its neighbours'.
            Row(Modifier.height(34.dp), verticalAlignment = Alignment.CenterVertically) {
                when {
                    s.builtIn -> Text(s.emoji, fontSize = 28.sp, lineHeight = 34.sp)   // the flag is the source's identity
                    s.isAce -> Icon(Icons.Rounded.SatelliteAlt, null, tint = cs.primary, modifier = Modifier.size(30.dp))
                    else -> Icon(Icons.Rounded.PlaylistPlay, null, tint = cs.primary, modifier = Modifier.size(30.dp))
                }
                Spacer(Modifier.weight(1f))
                if (selection != null) {
                    Icon(
                        if (selection) Icons.Rounded.CheckCircle else Icons.Rounded.RadioButtonUnchecked, null,
                        tint = if (selection) cs.primary else cs.onSurfaceVariant, modifier = Modifier.size(24.dp),
                    )
                }
            }
            Spacer(Modifier.height(Space.xs))
            Text(
                s.name, style = MaterialTheme.typography.titleSmall, color = cs.onSurface,
                maxLines = if (s.isAce) 2 else 1, overflow = TextOverflow.Ellipsis,
            )
            Text(
                when (s.kind) {
                    SourceKind.BUILT_IN -> "iptv-org"
                    SourceKind.FILE -> "Imported file"
                    SourceKind.URL -> "Custom playlist"
                    SourceKind.ACE -> "Saved Ace Stream version"
                    SourceKind.MERGED -> "Several playlists"
                },
                style = MaterialTheme.typography.bodySmall, color = cs.onSurfaceVariant,
            )
        }
    }
}

/**
 * The Sources heading, which becomes the selection bar: how many are picked and what can
 * be done to them together. Actions that can't apply to the pick are left out rather than
 * shown disabled — built-ins can't be removed, files can't be refetched.
 */
@Composable
private fun SourcesHeader(
    selecting: Boolean,
    selected: List<Source>,
    onStart: () -> Unit,
    onOpenTogether: () -> Unit,
    onRefresh: () -> Unit,
    onRemove: () -> Unit,
    onSelectAll: () -> Unit,
    onDone: () -> Unit,
) {
    val cs = MaterialTheme.colorScheme
    if (!selecting) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.weight(1f)) { SectionHeader(Icons.Rounded.PlaylistPlay, "Sources") }
            TvActionButton("Select", onStart, icon = Icons.Rounded.Checklist)
        }
        return
    }
    Column {
        Text(
            when (selected.size) { 0 -> "Pick playlists"; 1 -> "1 playlist selected"; else -> "${selected.size} playlists selected" },
            style = MaterialTheme.typography.titleMedium, color = cs.onSurface,
        )
        Spacer(Modifier.height(Space.sm))
        FlowRow(horizontalArrangement = Arrangement.spacedBy(Space.sm), verticalArrangement = Arrangement.spacedBy(Space.sm)) {
            if (selected.isNotEmpty()) {
                TvActionButton(if (selected.size > 1) "Watch together" else "Open", onOpenTogether, icon = Icons.Rounded.PlaylistPlay)
                if (selected.any { it.refetchable || it.isAce }) TvActionButton("Update", onRefresh, icon = Icons.Rounded.Refresh)
                if (selected.any { !it.builtIn }) TvActionButton("Remove", onRemove, icon = Icons.Rounded.Close)
            }
            TvActionButton("Select all", onSelectAll, icon = Icons.Rounded.DoneAll)
            TvActionButton("Done", onDone, icon = Icons.Rounded.Check)
        }
    }
}

/** What was just done to the playlists — or is being done — with a way to put it away. */
@Composable
private fun ActivityLine(activity: HomeActivity, onDismiss: () -> Unit) {
    val cs = MaterialTheme.colorScheme
    val (text, tint) = when (activity) {
        is HomeActivity.Working -> activity.message to cs.onSurfaceVariant
        is HomeActivity.Done -> activity.message to cs.primary
        is HomeActivity.Failed -> activity.message to cs.tertiary
    }
    Row(
        Modifier.padding(top = Space.sm).widthIn(max = ReadableWidth).semantics { liveRegion = LiveRegionMode.Polite },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (activity is HomeActivity.Working) {
            CircularProgressIndicator(color = cs.primary, strokeWidth = 2.dp, modifier = Modifier.size(18.dp))
        } else {
            Icon(
                if (activity is HomeActivity.Done) Icons.Rounded.CheckCircle else Icons.Rounded.WarningAmber,
                null, tint = tint, modifier = Modifier.size(20.dp),
            )
        }
        Spacer(Modifier.width(Space.md))
        Text(text, style = MaterialTheme.typography.bodyMedium, color = tint, modifier = Modifier.weight(1f, fill = false))
        if (activity !is HomeActivity.Working) {
            Spacer(Modifier.width(Space.sm))
            TvIconButton(Icons.Rounded.Close, "Dismiss", onDismiss, tint = cs.onSurfaceVariant)
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
    keepVersions: Int = 0,
    onKeepVersions: (Int) -> Unit = {},
    engineAddr: String = DEFAULT_ACE_ENGINE,
    onEngineAddr: (String) -> Unit = {},
    profileId: String = DEFAULT_ACE_PROFILE,
    onProfile: (String) -> Unit = {},
) {
    val cs = MaterialTheme.colorScheme
    var editingAddr by remember { mutableStateOf(false) }
    val onThisDevice = EngineAddress.isLocal(engineAddr)
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
                AceEngineState.NOT_INSTALLED -> "Ace Stream isn't installed here" to cs.onSurfaceVariant
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
                    when {
                        engine == AceEngineState.NOT_INSTALLED ->
                            "Point this at an Ace Stream engine on another device — a TV box or a PC — and build its playlist here."
                        !onThisDevice -> "Reading the engine at $engineAddr."
                        else -> "Ace Stream channels play through the Ace Stream app's engine. The phone stops it in " +
                            "the background to save battery, taking the stream with it — set its battery use to " +
                            "Unrestricted and it stays up."
                    },
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
            if (onThisDevice && engine != AceEngineState.NOT_INSTALLED) {
                TvActionButton("Allow in background", onSettings, icon = Icons.Rounded.BatterySaver)
            }
            if (onThisDevice && engine == AceEngineState.STOPPED) {
                TvActionButton("Start engine", onOpen, icon = Icons.AutoMirrored.Rounded.OpenInNew)
            }
            TvActionButton("Check again", onCheck, icon = Icons.Rounded.Refresh)
        }
        Spacer(Modifier.height(Space.lg))
        // Ace M3U's default is the engine on this device; any engine on the network works.
        val indent = Modifier.padding(start = 24.dp + Space.md)   // under the status text, not the icon
        Row(indent, verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("Engine address", style = MaterialTheme.typography.bodyLarge, color = cs.onSurface)
                Text(
                    engineAddr + if (onThisDevice) " · this device" else "",
                    style = MaterialTheme.typography.bodySmall, color = cs.onSurfaceVariant,
                )
            }
            TvActionButton("Change", { editingAddr = true }, icon = Icons.Rounded.Edit)
        }
        if (editingAddr) {
            EngineAddressDialog(engineAddr, onSave = { onEngineAddr(it); editingAddr = false }, onCancel = { editingAddr = false })
        }
        Spacer(Modifier.height(Space.lg))
        val profile = Profiles.choice(profileId)
        Text("Channels", style = MaterialTheme.typography.bodyLarge, color = cs.onSurface, modifier = indent)
        Text(
            if (profile == Profiles.MPEGTS_ALL) "Every channel on Ace Stream's list." else profile.description,
            style = MaterialTheme.typography.bodySmall, color = cs.onSurfaceVariant, modifier = indent,
        )
        Spacer(Modifier.height(Space.sm))
        Row(indent.selectableGroup(), horizontalArrangement = Arrangement.spacedBy(Space.sm)) {
            Profiles.CHOICES.forEach { p ->
                TvChip(if (p == Profiles.MPEGTS_ALL) "Everything" else p.title, p == profile) { onProfile(p.id) }
            }
        }
        Spacer(Modifier.height(Space.lg))
        Text("Saved versions", style = MaterialTheme.typography.bodyLarge, color = cs.onSurface, modifier = indent)
        Text(
            "Each “Fresh from Ace Stream” build is saved as its own playlist, named for when it was built.",
            style = MaterialTheme.typography.bodySmall, color = cs.onSurfaceVariant, modifier = indent,
        )
        Spacer(Modifier.height(Space.sm))
        Row(indent.selectableGroup(), horizontalArrangement = Arrangement.spacedBy(Space.sm)) {
            TvChip("Keep all", keepVersions == 0) { onKeepVersions(0) }
            TvChip("Keep newest 10", keepVersions == 10) { onKeepVersions(10) }
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
private fun EngineAddressDialog(current: String, onSave: (String) -> Unit, onCancel: () -> Unit) {
    var text by remember { mutableStateOf(current) }
    AlertDialog(
        onDismissRequest = onCancel,
        title = { Text("Ace Stream engine") },
        text = {
            Column {
                Text(
                    "Where “Fresh from Ace Stream” reads its channel list. $DEFAULT_ACE_ENGINE is the Ace Stream " +
                        "app on this device; for another device, enter its address, like 192.168.1.20.",
                    style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(Space.md))
                OutlinedTextField(
                    text, { text = it }, label = { Text("Host or host:port") }, singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        },
        confirmButton = { Button(onClick = { onSave(text) }) { Text("Save") } },
        dismissButton = {
            Row {
                TextButton(onClick = { onSave(DEFAULT_ACE_ENGINE) }) { Text("Use this device") }
                TextButton(onClick = onCancel) { Text("Cancel") }
            }
        },
    )
}

@Composable
private fun AddPlaylistDialog(onAdd: (String, String) -> Unit, onPickFiles: () -> Unit, onCancel: () -> Unit) {
    var name by remember { mutableStateOf("") }
    var url by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onCancel,
        title = { Text("Add a playlist") },
        text = {
            Column {
                Text(
                    "Paste an M3U or M3U8 URL — or several, one per line.",
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
                    minLines = 2, maxLines = 6,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
                    modifier = Modifier.fillMaxWidth(),
                )
                val several = url.lines().count { it.isNotBlank() } > 1
                if (several) {
                    Text(
                        "Each one is added as its own playlist, named from its address.",
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Spacer(Modifier.height(Space.md))
                TvActionButton("Choose files on this device…", onPickFiles, icon = Icons.Rounded.FolderOpen)
            }
        },
        confirmButton = { Button(onClick = { onAdd(name, url) }, enabled = url.isNotBlank()) { Text("Add") } },
        dismissButton = { TextButton(onClick = onCancel) { Text("Cancel") } },
    )
}
