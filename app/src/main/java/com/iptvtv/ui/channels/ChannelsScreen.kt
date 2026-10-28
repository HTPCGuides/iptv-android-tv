@file:OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)

package com.iptvtv.ui.channels

import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.RowScope
import androidx.compose.ui.platform.LocalConfiguration
import com.iptvtv.ui.screenPadding
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.background
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.Public
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.SearchOff
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material.icons.rounded.StarBorder
import androidx.compose.material.icons.rounded.WarningAmber
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
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
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.iptvtv.model.Channel
import com.iptvtv.model.Source
import com.iptvtv.model.SourceKind
import com.iptvtv.ui.AllGroupsIcon
import com.iptvtv.ui.Badge
import com.iptvtv.ui.groupIcon
import com.iptvtv.ui.CenteredState
import com.iptvtv.ui.ChannelLogo
import com.iptvtv.ui.Space
import com.iptvtv.ui.TvActionButton
import com.iptvtv.ui.TvChip
import com.iptvtv.ui.TvIconButton
import com.iptvtv.ui.TvSurface
import com.iptvtv.ui.containerViewModel
import com.iptvtv.ui.ChannelSheet

private fun channelCount(n: Int) = if (n == 1) "1 channel" else "$n channels"

@Composable
fun ChannelsRoute(
    onPlay: () -> Unit,
    onBack: () -> Unit,
    viewModel: ChannelsViewModel = containerViewModel { c, handle -> ChannelsViewModel(handle, c.repository, c.playback) },
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    ChannelsScreen(
        uiState = uiState,
        onBack = onBack,
        onPlay = { channel, queue -> viewModel.play(channel, queue); onPlay() },
        onToggleFavourite = viewModel::toggleFavourite,
        onQueryChange = viewModel::setQuery,
        onGroupChange = viewModel::setGroup,
        onClearFilters = viewModel::clearFilters,
        onRefresh = { viewModel.refresh() },
        onRename = viewModel::rename,
    )
}

@Composable
fun ChannelsScreen(
    uiState: ChannelsUiState,
    onBack: () -> Unit,
    onPlay: (Channel, List<Channel>) -> Unit,
    onToggleFavourite: (Channel) -> Unit,
    onQueryChange: (String) -> Unit,
    onGroupChange: (String?) -> Unit,
    onClearFilters: () -> Unit,
    onRefresh: () -> Unit,
    onRename: (String) -> Unit,
) {
    val cs = MaterialTheme.colorScheme
    val focusManager = LocalFocusManager.current
    val listFocus = remember { FocusRequester() }
    val success = uiState as? ChannelsUiState.Success

    // Land on the channels, not the search box: a remote reaching this screen should
    // be able to pick something without first dismissing a keyboard it never asked for.
    LaunchedEffect(success?.source?.id, success?.channels?.isNotEmpty()) {
        if (success?.channels?.isNotEmpty() == true) runCatching { listFocus.requestFocus() }
    }

    Column(
        Modifier
            .fillMaxSize()
            .background(cs.background)
            .safeDrawingPadding()
            .screenPadding(),
    ) {
        // On a phone held sideways the window is ~384dp tall, and title, search and
        // chips stacked took 60% of it — two rows of channels left. Below 480dp the
        // search field joins the title row. (A TV is 540dp; it keeps the stack.)
        val compact = LocalConfiguration.current.screenHeightDp < 480
        val searchField = @Composable { m: Modifier ->
            OutlinedTextField(
                value = success?.query ?: "",
                onValueChange = onQueryChange,
                label = { Text("Search channels") },
                leadingIcon = { Icon(Icons.Rounded.Search, null) },
                trailingIcon = {
                    if (!success?.query.isNullOrEmpty()) {
                        TvIconButton(Icons.Rounded.Close, "Clear search", { onQueryChange("") })
                    }
                },
                singleLine = true,
                enabled = success != null,
                shape = MaterialTheme.shapes.medium,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(onSearch = { focusManager.clearFocus() }),
                // A text field swallows the D-pad, which strands a remote inside the box.
                // Take the vertical keys back and hand focus to the neighbouring row.
                modifier = m
                    .onPreviewKeyEvent { ev ->
                        if (ev.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                        when (ev.key) {
                            Key.DirectionDown -> { focusManager.moveFocus(FocusDirection.Down); true }
                            Key.DirectionUp -> { focusManager.moveFocus(FocusDirection.Up); true }
                            else -> false
                        }
                    },
            )
        }
        if (compact) {
            Header(uiState, onBack, onRefresh, onRename) { searchField(Modifier.weight(1f)) }
        } else {
            Header(uiState, onBack, onRefresh, onRename)
            Spacer(Modifier.height(Space.lg))
            searchField(Modifier.fillMaxWidth())
        }

        if (success != null && success.groups.isNotEmpty()) {
            Spacer(Modifier.height(Space.md))
            LazyRow(
                Modifier.selectableGroup(),   // one set of choices to a screen reader, not twelve buttons
                horizontalArrangement = Arrangement.spacedBy(Space.sm),
                contentPadding = PaddingValues(horizontal = Space.xs, vertical = Space.xs),
            ) {
                item { TvChip("All", success.group == null, AllGroupsIcon) { onGroupChange(null) } }
                items(success.groups, key = { it }) { g ->
                    TvChip(g, success.group == g, groupIcon(g)) { onGroupChange(g) }
                }
            }
        }

        Spacer(Modifier.height(Space.md))
        when (uiState) {
            ChannelsUiState.Loading -> CenteredState {
                CircularProgressIndicator(color = cs.primary)
                Spacer(Modifier.height(Space.md))
                Text("Loading playlist…", style = MaterialTheme.typography.bodyLarge, color = cs.onSurfaceVariant)
            }

            is ChannelsUiState.Error -> CenteredState {
                Icon(Icons.Rounded.WarningAmber, null, tint = cs.error, modifier = Modifier.size(40.dp))
                Spacer(Modifier.height(Space.md))
                Text("Couldn't load this playlist", style = MaterialTheme.typography.titleMedium, color = cs.onSurface)
                Spacer(Modifier.height(Space.xs))
                Text(
                    uiState.message, style = MaterialTheme.typography.bodyMedium, color = cs.error,
                    textAlign = TextAlign.Center,
                )
                if (uiState.source.refetchable || uiState.source.kind == SourceKind.MERGED) {
                    Spacer(Modifier.height(Space.lg))
                    TvActionButton("Try again", onRefresh, icon = Icons.Rounded.Refresh)
                }
            }

            is ChannelsUiState.Success -> if (uiState.channels.isEmpty()) {
                CenteredState {
                    Icon(Icons.Rounded.SearchOff, null, tint = cs.onSurfaceVariant, modifier = Modifier.size(40.dp))
                    Spacer(Modifier.height(Space.md))
                    Text(
                        if (uiState.filtered) "No channels match" else "This playlist is empty",
                        style = MaterialTheme.typography.titleMedium, color = cs.onSurface,
                    )
                    Spacer(Modifier.height(Space.xs))
                    Text(
                        if (uiState.filtered) "Try a different search or group." else "The playlist loaded but contained no channels.",
                        style = MaterialTheme.typography.bodyMedium, color = cs.onSurfaceVariant,
                    )
                    if (uiState.filtered) {
                        Spacer(Modifier.height(Space.lg))
                        TvActionButton("Clear filters", onClearFilters, icon = Icons.Rounded.Close)
                    }
                }
            } else {
                ChannelList(uiState, listFocus, onPlay, onToggleFavourite)
            }
        }
    }
}

/**
 * Laid out like a top app bar: navigation at the start, title, actions at the end.
 * Playlists you added or were handed can be renamed; a file has nowhere to reload
 * from — our stored channels are the playlist — so it gets no Reload.
 */
@Composable
private fun Header(
    uiState: ChannelsUiState,
    onBack: () -> Unit,
    onRefresh: () -> Unit,
    onRename: (String) -> Unit,
    /** Shares the row with the title when the window is short; it takes the spare width. */
    middle: (@Composable RowScope.() -> Unit)? = null,
) {
    val cs = MaterialTheme.colorScheme
    val source: Source? = when (uiState) {
        is ChannelsUiState.Success -> uiState.source
        is ChannelsUiState.Error -> uiState.source
        ChannelsUiState.Loading -> null
    }
    var renaming by remember { mutableStateOf(false) }
    Row(verticalAlignment = Alignment.CenterVertically) {
        TvIconButton(Icons.AutoMirrored.Rounded.ArrowBack, "Back to sources", onBack, tint = cs.onSurface)
        Spacer(Modifier.width(Space.md))
        source?.takeIf { it.builtIn }?.let {
            Text(it.emoji, fontSize = 24.sp)
            Spacer(Modifier.width(Space.md))
        }
        Column(if (middle == null) Modifier.weight(1f) else Modifier.widthIn(max = 280.dp)) {
            // Two lines before it gives up: at 1.3x text on a phone "United Kingdom"
            // was "United Kingd…", and the title is what says where you are.
            Text(
                source?.name ?: "Channels",
                style = MaterialTheme.typography.headlineSmall, color = cs.onSurface,
                maxLines = 2, overflow = TextOverflow.Ellipsis,
            )
            if (uiState is ChannelsUiState.Success) {
                Text(
                    if (uiState.refreshing) "Updating…" else channelCount(uiState.channels.size),
                    style = MaterialTheme.typography.bodyMedium, color = cs.onSurfaceVariant,
                )
            }
        }
        if (middle != null) {
            Spacer(Modifier.width(Space.lg))
            middle()
        }
        Spacer(Modifier.width(Space.md))
        // Playlists you added or were handed can be renamed; a merged view is not a playlist.
        if (source != null && !source.builtIn && source.kind != SourceKind.MERGED) {
            TvIconButton(Icons.Rounded.Edit, "Rename this playlist", { renaming = true }, tint = cs.onSurface)
        }
        // Only an address can be fetched again; a file or an Ace Stream version is what it is
        // (a fresh Ace Stream version is built from Home).
        if (source != null && (source.refetchable || source.kind == SourceKind.MERGED)) {
            TvIconButton(Icons.Rounded.Refresh, "Reload this playlist", onRefresh, tint = cs.onSurface)
        }
    }
    if (renaming && source != null) {
        RenamePlaylistDialog(
            current = source.name,
            onRename = { onRename(it); renaming = false },
            onCancel = { renaming = false },
        )
    }
}

@Composable
private fun ChannelList(
    state: ChannelsUiState.Success,
    listFocus: FocusRequester,
    onPlay: (Channel, List<Channel>) -> Unit,
    onToggleFavourite: (Channel) -> Unit,
) {
    val cs = MaterialTheme.colorScheme
    var held by remember { mutableStateOf<Channel?>(null) }
    held?.let { c ->
        ChannelSheet(
            c, isFavourite = c.favKey in state.favourites,
            onPlay = { onPlay(c, state.channels) }, onToggleFavourite = { onToggleFavourite(c) }, onDismiss = { held = null },
        )
    }
    // One column on a phone; on a tablet, a TV or a landscape phone, as many as fit.
    // A single full-width list put every name at the far left of a tablet and its star
    // more than a thousand pixels away at the far right.
    val narrow = LocalConfiguration.current.screenWidthDp < 600
    LazyVerticalGrid(
        columns = GridCells.Adaptive(minSize = 340.dp),
        modifier = Modifier.focusRequester(listFocus).focusGroup(),
        horizontalArrangement = Arrangement.spacedBy(Space.lg),
        verticalArrangement = Arrangement.spacedBy(Space.sm),
        contentPadding = PaddingValues(vertical = Space.xs),
    ) {
        items(state.channels, key = { it.key }) { c ->
            // Play and favourite are siblings, not nested: a focusable inside a
            // focusable is skipped by the D-pad, which would put the star out of
            // reach of a remote entirely.
            Row(Modifier.fillMaxWidth().focusGroup(), verticalAlignment = Alignment.CenterVertically) {
                TvSurface(
                    onClick = { onPlay(c, state.channels) },
                    onLongClick = { held = c },
                    modifier = Modifier.weight(1f),
                    peakScale = 1.02f,
                    contentPadding = PaddingValues(horizontal = Space.md, vertical = Space.sm),
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        ChannelLogo(c.logo, 48.dp, c.groups.first())
                        Spacer(Modifier.width(Space.md))
                        Column(Modifier.weight(1f)) {
                            Text(
                                c.clean, style = MaterialTheme.typography.titleSmall, color = cs.onSurface,
                                maxLines = 1, overflow = TextOverflow.Ellipsis,
                            )
                            Spacer(Modifier.height(Space.xs))
                            // Wraps, so a badge drops to its own line instead of taking the
                            // room the groups and quality need: in portrait "ITV2 HD" read
                            // "Entertainment…" with its 1080p gone, beside "Geo-blocked".
                            FlowRow(
                                horizontalArrangement = Arrangement.spacedBy(Space.md),
                                verticalArrangement = Arrangement.spacedBy(Space.xs),
                            ) {
                                // Groups and quality are separate pieces, so at large text the
                                // quality wraps onto the next line instead of being the part an
                                // ellipsis eats ("Outdoor · Sports …").
                                Text(
                                    c.groups.joinToString(" · "),
                                    style = MaterialTheme.typography.bodySmall, color = cs.onSurfaceVariant,
                                    maxLines = 1, overflow = TextOverflow.Ellipsis,
                                )
                                c.quality?.let {
                                    Text(it, style = MaterialTheme.typography.bodySmall, color = cs.onSurfaceVariant, maxLines = 1)
                                }
                                if (c.geoBlocked) Badge(Icons.Rounded.Public, "Geo-blocked", cs.tertiary)
                                if (c.partTime) Badge(Icons.Rounded.Schedule, "Part-time", cs.onSurfaceVariant)
                            }
                        }
                    }
                }
                Spacer(Modifier.width(Space.sm))
                val fav = c.favKey in state.favourites
                val actions = @Composable {
                    // The row's own action is the one that carries the channel name; the
                    // star is a short label with its state, so a screen reader says
                    // "Favourite, on" rather than repeating the name for every row.
                    TvIconButton(
                        icon = if (fav) Icons.Rounded.Star else Icons.Rounded.StarBorder,
                        contentDescription = "Favourite",
                        onClick = { onToggleFavourite(c) },
                        modifier = Modifier.semantics { stateDescription = if (fav) "On" else "Off" },
                        tint = if (fav) cs.primary else cs.onSurfaceVariant,
                    )
                    // The visible way to the channel's other actions — press-and-hold does the
                    // same, but nothing on screen says so.
                    TvIconButton(Icons.Rounded.MoreVert, "More for ${c.clean}", { held = c }, tint = cs.onSurfaceVariant)
                }
                // Side by side they cost a narrow phone ~100dp of name ("Red Bull …" at 1.3x
                // text); stacked, they fit the height a row has anyway.
                if (narrow) Column { actions() } else Row { actions() }
            }
        }
    }
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
