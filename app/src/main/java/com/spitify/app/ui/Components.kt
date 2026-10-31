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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.spitify.app.SpitifyApp
import com.spitify.app.data.Song
import com.spitify.app.data.formatDuration

@Composable
fun Artwork(url: String?, size: Dp, modifier: Modifier = Modifier, corner: Dp = 4.dp) {
    Box(
        modifier
            .size(size)
            .clip(RoundedCornerShape(corner))
            .background(MaterialTheme.colorScheme.surfaceContainerHigh),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            AppIcons.MusicNote, null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.fillMaxSize(0.4f),
        )
        if (url != null) {
            AsyncImage(
                model = url,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
        }
    }
}

@Composable
fun SongRow(
    song: Song,
    onClick: () -> Unit,
    playlistId: String? = null,
    active: Boolean = PlayerConnection.current?.id == song.id,
) {
    var menuOpen by remember { mutableStateOf(false) }
    var pickPlaylist by remember { mutableStateOf(false) }

    Row(
        Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(start = 16.dp, top = 6.dp, bottom = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Artwork(song.thumb, 52.dp)
        Column(
            Modifier
                .weight(1f)
                .padding(horizontal = 12.dp),
        ) {
            Text(
                song.title,
                style = MaterialTheme.typography.bodyLarge,
                color = if (active) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            val duration = formatDuration(song.durationSec)
            Text(
                if (duration.isEmpty()) song.artist else "${song.artist} • $duration",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Box {
            IconButton(onClick = { menuOpen = true }) {
                Icon(Icons.Default.MoreVert, "More", tint = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            SongMenu(
                song = song,
                expanded = menuOpen,
                onDismiss = { menuOpen = false },
                playlistId = playlistId,
                onAddToPlaylist = { pickPlaylist = true },
            )
        }
    }

    if (pickPlaylist) AddToPlaylistDialog(song) { pickPlaylist = false }
}

@Composable
private fun SongMenu(
    song: Song,
    expanded: Boolean,
    onDismiss: () -> Unit,
    playlistId: String?,
    onAddToPlaylist: () -> Unit,
) {
    val library = SpitifyApp.library
    val data by library.data.collectAsStateWithLifecycle()
    val liked = data.liked.any { it.id == song.id }

    DropdownMenu(expanded = expanded, onDismissRequest = onDismiss) {
        DropdownMenuItem(
            text = { Text("Play next") },
            leadingIcon = { Icon(AppIcons.SkipNext, null) },
            onClick = { PlayerConnection.playNext(song); onDismiss() },
        )
        DropdownMenuItem(
            text = { Text("Add to queue") },
            leadingIcon = { Icon(AppIcons.QueueMusic, null) },
            onClick = { PlayerConnection.enqueue(song); onDismiss() },
        )
        DropdownMenuItem(
            text = { Text(if (liked) "Remove from Liked" else "Like") },
            leadingIcon = { Icon(if (liked) Icons.Default.Favorite else Icons.Default.FavoriteBorder, null) },
            onClick = { library.toggleLike(song); onDismiss() },
        )
        DropdownMenuItem(
            text = { Text("Add to playlist") },
            leadingIcon = { Icon(AppIcons.PlaylistAdd, null) },
            onClick = { onDismiss(); onAddToPlaylist() },
        )
        if (playlistId != null) {
            DropdownMenuItem(
                text = { Text("Remove from this playlist") },
                leadingIcon = { Icon(Icons.Default.Delete, null) },
                onClick = { library.removeFromPlaylist(playlistId, song.id); onDismiss() },
            )
        }
    }
}

@Composable
fun AddToPlaylistDialog(song: Song, onDismiss: () -> Unit) {
    val library = SpitifyApp.library
    val data by library.data.collectAsStateWithLifecycle()
    var newName by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Add to playlist") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                LazyColumn(Modifier.heightIn(max = 260.dp)) {
                    items(data.playlists, key = { it.id }) { playlist ->
                        Text(
                            playlist.name,
                            style = MaterialTheme.typography.bodyLarge,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    library.addToPlaylist(playlist.id, song)
                                    onDismiss()
                                }
                                .padding(vertical = 12.dp),
                        )
                    }
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    OutlinedTextField(
                        value = newName,
                        onValueChange = { newName = it },
                        placeholder = { Text("New playlist") },
                        singleLine = true,
                        modifier = Modifier.weight(1f),
                    )
                    Spacer(Modifier.width(8.dp))
                    IconButton(
                        enabled = newName.isNotBlank(),
                        onClick = {
                            val id = library.createPlaylist(newName)
                            library.addToPlaylist(id, song)
                            onDismiss()
                        },
                    ) { Icon(Icons.Default.Add, "Create") }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Close") } },
    )
}

@Composable
fun NamePlaylistDialog(
    title: String,
    initial: String = "",
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit,
) {
    var name by remember { mutableStateOf(initial) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                placeholder = { Text("Playlist name") },
                singleLine = true,
            )
        },
        confirmButton = {
            TextButton(enabled = name.isNotBlank(), onClick = { onConfirm(name); onDismiss() }) { Text("Save") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

@Composable
fun SectionTitle(text: String, modifier: Modifier = Modifier) {
    Text(
        text,
        style = MaterialTheme.typography.titleLarge,
        modifier = modifier.padding(start = 16.dp, end = 16.dp, top = 20.dp, bottom = 8.dp),
    )
}

@Composable
fun EmptyHint(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 24.dp),
    )
}
