package com.iptvtv.ui.channels

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
import androidx.compose.foundation.lazy.LazyColumn
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
import com.iptvtv.ui.Badge
import com.iptvtv.ui.CenteredState
import com.iptvtv.ui.ChannelLogo
import com.iptvtv.ui.Space
import com.iptvtv.ui.TvActionButton
import com.iptvtv.ui.TvChip
import com.iptvtv.ui.TvIconButton
import com.iptvtv.ui.TvSurface
import com.iptvtv.ui.containerViewModel
import com.iptvtv.ui.copyStreamUrl

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
            .padding(horizontal = Space.xl, vertical = Space.lg),
    ) {
        Header(uiState, onBack, onRefresh, onRename)

        Spacer(Modifier.height(Space.lg))
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

        if (success != null && success.groups.isNotEmpty()) {
            Spacer(Modifier.height(Space.md))
            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(Space.sm),
                contentPadding = PaddingValues(horizontal = Space.xs, vertical = Space.xs),
            ) {
                item { TvChip("All", success.group == null) { onGroupChange(null) } }
                items(success.groups, key = { it }) { g ->
                    TvChip(g, success.group == g) { onGroupChange(g) }
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
                if (!uiState.source.isFile) {
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
private fun Header(uiState: ChannelsUiState, onBack: () -> Unit, onRefresh: () -> Unit, onRename: (String) -> Unit) {
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
        Column(Modifier.weight(1f)) {
            Text(
                source?.name ?: "Channels",
                style = MaterialTheme.typography.headlineSmall, color = cs.onSurface,
                maxLines = 1, overflow = TextOverflow.Ellipsis,
            )
            if (uiState is ChannelsUiState.Success) {
                Text(
                    if (uiState.refreshing) "Updating…" else channelCount(uiState.channels.size),
                    style = MaterialTheme.typography.bodyMedium, color = cs.onSurfaceVariant,
                )
            }
        }
        Spacer(Modifier.width(Space.md))
        if (source != null && !source.builtIn) {
            TvIconButton(Icons.Rounded.Edit, "Rename this playlist", { renaming = true }, tint = cs.onSurface)
        }
        if (source?.isFile != true) {
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
    val ctx = LocalContext.current
    LazyColumn(
        modifier = Modifier.focusRequester(listFocus).focusGroup(),
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
                                    meta, style = MaterialTheme.typography.bodySmall, color = cs.onSurfaceVariant,
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
                val fav = c.favKey in state.favourites
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
