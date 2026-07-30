package com.iptvtv

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage

val Bg = Color(0xFF0B1120)
val Card = Color(0xFF16202E)
val Accent = Color(0xFF38BDF8)
val Muted = Color(0xFF8B98A8)

/** A focusable row/tile that highlights clearly when the D-pad lands on it. */
@Composable
fun TvItem(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable (focused: Boolean) -> Unit,
) {
    val interaction = remember { MutableInteractionSource() }
    val focused by interaction.collectIsFocusedAsState()
    Box(
        modifier
            .clip(RoundedCornerShape(10.dp))
            .background(if (focused) Accent.copy(alpha = .18f) else Card)
            .border(2.dp, if (focused) Accent else Color.Transparent, RoundedCornerShape(10.dp))
            .focusable(interactionSource = interaction)
            .clickable(interactionSource = interaction, indication = null) { onClick() }
            .padding(12.dp),
    ) { content(focused) }
}

@Composable
fun HomeScreen(vm: IptvViewModel, onOpen: (Source) -> Unit) {
    var showAdd by remember { mutableStateOf(false) }
    Column(Modifier.fillMaxSize().background(Bg).padding(28.dp)) {
        Text("📺 IPTV TV", color = Color.White, fontSize = 30.sp, fontWeight = FontWeight.Bold)
        Text("Free-to-air channels from iptv-org — plus your own playlists.",
            color = Muted, fontSize = 14.sp)
        Spacer(Modifier.height(20.dp))

        Text("Sources", color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(10.dp))
        LazyRow(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            items(vm.sources) { s ->
                Column {
                    TvItem(onClick = { onOpen(s) }, modifier = Modifier.width(210.dp).height(112.dp)) {
                        Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.Center) {
                            Text(s.emoji, fontSize = 30.sp)
                            Text(s.name, color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                            Text(if (s.builtIn) "iptv-org" else "custom playlist", color = Muted, fontSize = 11.sp)
                        }
                    }
                    if (!s.builtIn) {
                        Spacer(Modifier.height(6.dp))
                        TvItem(onClick = { vm.removeSource(s) }) {
                            Text("✕ Remove", color = Muted, fontSize = 12.sp)
                        }
                    }
                }
            }
            item {
                TvItem(onClick = { showAdd = true }, modifier = Modifier.width(210.dp).height(112.dp)) {
                    Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.Center) {
                        Text("➕", fontSize = 30.sp)
                        Text("Add playlist", color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                        Text("paste an M3U URL", color = Muted, fontSize = 11.sp)
                    }
                }
            }
        }

        Spacer(Modifier.height(24.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Options", color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.width(16.dp))
            TvItem(onClick = { vm.toggleHideGeo() }) {
                Text(
                    if (vm.hideGeo) "☑  Hide geo-blocked channels" else "☐  Hide geo-blocked channels",
                    color = Color.White, fontSize = 14.sp,
                )
            }
        }
        Spacer(Modifier.height(8.dp))
        Text("Geo-blocked channels only play from inside their own country — leave them shown if you use a VPN.",
            color = Muted, fontSize = 12.sp)

        if (showAdd) AddPlaylistDialog(onAdd = { n, u -> vm.addSource(n, u); showAdd = false }, onCancel = { showAdd = false })
    }
}

@Composable
private fun AddPlaylistDialog(onAdd: (String, String) -> Unit, onCancel: () -> Unit) {
    var name by remember { mutableStateOf("") }
    var url by remember { mutableStateOf("") }
    androidx.compose.material3.AlertDialog(
        onDismissRequest = onCancel,
        containerColor = Card,
        title = { Text("Add a playlist", color = Color.White) },
        text = {
            Column {
                Text("Paste the M3U/M3U8 URL from your provider.", color = Muted, fontSize = 13.sp)
                Spacer(Modifier.height(10.dp))
                OutlinedTextField(name, { name = it }, label = { Text("Name") }, singleLine = true,
                    modifier = Modifier.fillMaxWidth())
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(url, { url = it }, label = { Text("https://…/playlist.m3u") }, singleLine = true,
                    modifier = Modifier.fillMaxWidth())
            }
        },
        confirmButton = { Button(onClick = { if (url.isNotBlank()) onAdd(name, url) }) { Text("Add") } },
        dismissButton = { TextButton(onClick = onCancel) { Text("Cancel") } },
    )
}

@Composable
fun ChannelsScreen(vm: IptvViewModel, onPlay: (Channel) -> Unit, onBack: () -> Unit) {
    val list = vm.visible
    Column(Modifier.fillMaxSize().background(Bg).padding(20.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(vm.currentSource?.let { "${it.emoji}  ${it.name}" } ?: "Channels",
                color = Color.White, fontSize = 22.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.width(14.dp))
            Text("${list.size} channels", color = Muted, fontSize = 13.sp)
            Spacer(Modifier.weight(1f))
            TvItem(onClick = { vm.currentSource?.let { vm.open(it, refresh = true) } }) {
                Text("⟳ Refresh", color = Color.White, fontSize = 13.sp)
            }
            Spacer(Modifier.width(8.dp))
            TvItem(onClick = onBack) { Text("← Sources", color = Color.White, fontSize = 13.sp) }
        }

        Spacer(Modifier.height(12.dp))
        OutlinedTextField(
            value = vm.query, onValueChange = { vm.query = it },
            label = { Text("Search channels") }, singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )

        if (vm.groups.isNotEmpty()) {
            Spacer(Modifier.height(10.dp))
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                item {
                    TvItem(onClick = { vm.group = null }) {
                        Text(if (vm.group == null) "● All" else "All", color = Color.White, fontSize = 13.sp)
                    }
                }
                items(vm.groups) { g ->
                    TvItem(onClick = { vm.group = g }) {
                        Text(if (vm.group == g) "● $g" else g, color = Color.White, fontSize = 13.sp)
                    }
                }
            }
        }

        Spacer(Modifier.height(12.dp))
        when {
            vm.loading -> Box(Modifier.fillMaxSize(), Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    CircularProgressIndicator(color = Accent)
                    Spacer(Modifier.height(10.dp))
                    Text("Loading playlist…", color = Muted)
                }
            }
            vm.error != null -> Box(Modifier.fillMaxSize(), Alignment.Center) {
                Text(vm.error!!, color = Color(0xFFF87171))
            }
            list.isEmpty() -> Box(Modifier.fillMaxSize(), Alignment.Center) {
                Text("No channels match.", color = Muted)
            }
            else -> LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                items(list) { c ->
                    TvItem(onClick = { vm.play(c, list); onPlay(c) }, modifier = Modifier.fillMaxWidth()) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            if (c.logo != null) {
                                AsyncImage(model = c.logo, contentDescription = null,
                                    modifier = Modifier.size(44.dp).clip(RoundedCornerShape(6.dp)))
                            } else {
                                Box(Modifier.size(44.dp).clip(RoundedCornerShape(6.dp)).background(Bg),
                                    contentAlignment = Alignment.Center) { Text("📺", fontSize = 18.sp) }
                            }
                            Spacer(Modifier.width(14.dp))
                            Column(Modifier.weight(1f)) {
                                Text(c.clean, color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                                Row {
                                    Text(c.groups.joinToString(" · "), color = Muted, fontSize = 12.sp)
                                    c.quality?.let { Text("  ·  $it", color = Muted, fontSize = 12.sp) }
                                    if (c.geoBlocked) Text("  ·  🌍 geo-blocked", color = Color(0xFFFBBF24), fontSize = 12.sp)
                                    if (c.partTime) Text("  ·  ⏱ part-time", color = Muted, fontSize = 12.sp)
                                }
                            }
                            TvItem(onClick = { vm.toggleFav(c) }) {
                                Text(
                                    if (vm.isFav(c)) "★" else "☆",
                                    color = if (vm.isFav(c)) Accent else Muted, fontSize = 20.sp,
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
