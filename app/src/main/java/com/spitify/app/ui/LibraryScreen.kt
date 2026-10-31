package com.spitify.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.spitify.app.SpitifyApp
import com.spitify.app.data.Song

sealed interface Route {
    data object Liked : Route
    data class PlaylistPage(val id: String) : Route
}

@Composable
fun LibraryScreen(open: (Route) -> Unit) {
    val library = SpitifyApp.library
    val data by library.data.collectAsStateWithLifecycle()
    var creating by remember { mutableStateOf(false) }

    LazyColumn(Modifier.fillMaxSize()) {
        item {
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(start = 16.dp, top = 16.dp, end = 4.dp, bottom = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("Your Library", style = MaterialTheme.typography.headlineMedium, modifier = Modifier.weight(1f))
                IconButton(onClick = { creating = true }) { Icon(Icons.Default.Add, "New playlist") }
            }
        }
        item {
            LibraryEntry(
                title = "Liked Songs",
                subtitle = "${data.liked.size} songs",
                art = { LikedArt(56.dp) },
                onClick = { open(Route.Liked) },
            )
        }
        items(data.playlists, key = { it.id }) { p ->
            LibraryEntry(
                title = p.name,
                subtitle = "Playlist • ${p.songs.size} songs",
                art = { Artwork(p.songs.firstOrNull()?.thumb, 56.dp) },
                onClick = { open(Route.PlaylistPage(p.id)) },
            )
        }
        if (data.playlists.isEmpty()) {
            item { EmptyHint("Make a playlist with + or from any song's ⋮ menu.") }
        }

        item { SectionTitle("Settings") }
        item {
            Row(
                Modifier
                    .fillMaxWidth()
                    .clickable { library.setAutoplay(!data.autoplay) }
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f)) {
                    Text("Autoplay", style = MaterialTheme.typography.bodyLarge)
                    Text(
                        "Keep playing similar songs when the queue runs out",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Switch(checked = data.autoplay, onCheckedChange = library::setAutoplay)
            }
        }
    }

    if (creating) {
        NamePlaylistDialog("New playlist", onDismiss = { creating = false }) { library.createPlaylist(it) }
    }
}

@Composable
private fun LibraryEntry(title: String, subtitle: String, art: @Composable () -> Unit, onClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        art()
        Column(Modifier.padding(start = 12.dp)) {
            Text(title, style = MaterialTheme.typography.bodyLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun LikedArt(size: Dp) {
    Box(
        Modifier
            .size(size)
            .clip(RoundedCornerShape(4.dp))
            .background(Brush.linearGradient(listOf(Color(0xFF3B5BDB), Lime))),
        contentAlignment = Alignment.Center,
    ) {
        Icon(Icons.Default.Favorite, null, tint = Color.White, modifier = Modifier.size(size / 2.4f))
    }
}

/** Liked Songs or a playlist: header with play/shuffle, then the songs. */
@Composable
fun CollectionScreen(route: Route, onBack: () -> Unit) {
    val library = SpitifyApp.library
    val data by library.data.collectAsStateWithLifecycle()
    var renaming by remember { mutableStateOf(false) }

    val playlist = (route as? Route.PlaylistPage)?.let { r -> data.playlists.find { it.id == r.id } }
    if (route is Route.PlaylistPage && playlist == null) {
        // deleted while open
        LaunchedEffect(Unit) { onBack() }
        return
    }
    val title = playlist?.name ?: "Liked Songs"
    val songs: List<Song> = playlist?.songs ?: data.liked

    LazyColumn(Modifier.fillMaxSize()) {
        item {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 4.dp)) {
                IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") }
                Spacer(Modifier.weight(1f))
                if (playlist != null) {
                    IconButton(onClick = { renaming = true }) { Icon(Icons.Default.Edit, "Rename") }
                    IconButton(onClick = { library.deletePlaylist(playlist.id); onBack() }) {
                        Icon(Icons.Default.Delete, "Delete playlist")
                    }
                }
            }
        }
        item {
            Column(Modifier.padding(horizontal = 16.dp)) {
                if (playlist == null) LikedArt(160.dp) else Artwork(songs.firstOrNull()?.thumb, 160.dp, corner = 8.dp)
                Spacer(Modifier.height(16.dp))
                Text(title, style = MaterialTheme.typography.headlineMedium)
                Text(
                    "${songs.size} songs",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(16.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Button(
                        enabled = songs.isNotEmpty(),
                        onClick = { PlayerConnection.play(songs) },
                        colors = ButtonDefaults.buttonColors(),
                    ) {
                        Icon(Icons.Default.PlayArrow, null)
                        Spacer(Modifier.width(6.dp))
                        Text("Play")
                    }
                    OutlinedButton(
                        enabled = songs.isNotEmpty(),
                        onClick = { PlayerConnection.play(songs, songs.indices.random(), shuffled = true) },
                    ) {
                        Icon(AppIcons.Shuffle, null)
                        Spacer(Modifier.width(6.dp))
                        Text("Shuffle")
                    }
                }
                Spacer(Modifier.height(8.dp))
            }
        }
        if (songs.isEmpty()) {
            item {
                EmptyHint(
                    if (playlist == null) "Songs you like show up here."
                    else "Add songs from any song's ⋮ menu."
                )
            }
        }
        items(songs.size, key = { songs[it].id }) { i ->
            SongRow(songs[i], onClick = { PlayerConnection.play(songs, i) }, playlistId = playlist?.id)
        }
    }

    if (renaming && playlist != null) {
        NamePlaylistDialog("Rename playlist", initial = playlist.name, onDismiss = { renaming = false }) {
            library.renamePlaylist(playlist.id, it)
        }
    }
}
