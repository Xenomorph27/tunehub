package com.spitify.app.ui

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.spitify.app.SpitifyApp
import com.spitify.app.data.AlbumRef
import com.spitify.app.data.ArtistRef
import com.spitify.app.data.Song
import com.spitify.app.data.YouTube
import com.spitify.app.data.totalDuration
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

private fun songMeta(songs: List<Song>): String =
    listOf("${songs.size} songs", totalDuration(songs)).filter { it.isNotBlank() }.joinToString(" • ")

private fun shuffle(songs: List<Song>) {
    if (songs.isNotEmpty()) PlayerConnection.play(songs, songs.indices.random(), shuffled = true)
}

// ------------------------------------------------------------------ YouTube albums & playlists

class AlbumViewModel : ViewModel() {
    var page by mutableStateOf<YouTube.AlbumPage?>(null)
        private set
    var error by mutableStateOf<String?>(null)
        private set
    private var loaded: String? = null

    fun load(album: AlbumRef, force: Boolean = false) {
        if (!force && loaded == album.url) return
        loaded = album.url
        error = null
        viewModelScope.launch {
            try {
                page = YouTube.albumPage(album.url, album)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                error = e.message ?: "Couldn't load"
            }
        }
    }
}

@Composable
fun AlbumScreen(album: AlbumRef, onBack: () -> Unit) {
    val vm: AlbumViewModel = viewModel(key = "album-" + album.url)
    LaunchedEffect(album.url) { vm.load(album) }
    val library = SpitifyApp.library
    val data by library.data.collectAsStateWithLifecycle()
    val nav = LocalNav.current
    val info = vm.page?.album ?: album
    val songs = vm.page?.songs.orEmpty()
    val saved = data.albums.any { it.url == album.url }
    val tint = rememberArtColor(info.thumb)
    // album pages list tracks by number; their thumbnails are all the same cover
    val isAlbum = info.isAlbum

    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 24.dp)) {
        item {
            CollectionHeader(
                title = info.title,
                subtitle = info.subtitle,
                meta = listOf(if (isAlbum) "Album" else "Playlist", songMeta(songs)).filter { it.isNotBlank() }.joinToString(" • "),
                tint = tint,
                onBack = onBack,
                art = { Artwork(info.thumb, 220.dp, corner = 6.dp, placeholder = AppIcons.Album) },
                playEnabled = songs.isNotEmpty(),
                onPlay = { PlayerConnection.play(songs) },
                onShuffle = { shuffle(songs) },
                onSubtitleClick = songs.firstOrNull()?.artistUrl?.takeIf { isAlbum }?.let { url ->
                    { nav.open(Route.Artist(ArtistRef(url, info.subtitle))) }
                },
                actions = {
                    IconButton(onClick = { library.toggleAlbum(info) }) {
                        Icon(
                            if (saved) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                            if (saved) "Remove from library" else "Save to library",
                            tint = if (saved) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                        )
                    }
                    DownloadAllButton(songs)
                },
            )
        }
        when {
            vm.page == null && vm.error != null -> item { ErrorBox(vm.error ?: "") { vm.load(album, force = true) } }
            vm.page == null -> item { LoadingBox() }
            songs.isEmpty() -> item { EmptyHint("This is empty.") }
            else -> itemsIndexed(songs, key = { _, s -> s.id }) { i, song ->
                SongRow(
                    song,
                    onClick = { PlayerConnection.play(songs, i) },
                    trackNumber = if (isAlbum) i + 1 else null,
                    showArt = !isAlbum,
                )
            }
        }
    }
}

// ------------------------------------------------------------------ local collections

@Composable
fun LikedScreen(onBack: () -> Unit) {
    val data by SpitifyApp.library.data.collectAsStateWithLifecycle()
    val songs = data.liked
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 24.dp)) {
        item {
            CollectionHeader(
                title = "Liked Songs",
                subtitle = "",
                meta = songMeta(songs),
                tint = androidx.compose.ui.graphics.Color(0xFF3A2D8C),
                onBack = onBack,
                art = { LikedCover(220.dp, corner = 6.dp) },
                playEnabled = songs.isNotEmpty(),
                onPlay = { PlayerConnection.play(songs) },
                onShuffle = { shuffle(songs) },
                actions = { DownloadAllButton(songs) },
            )
        }
        if (songs.isEmpty()) item { EmptyHint("Songs you like will appear here. Tap ♡ on any song.") }
        itemsIndexed(songs, key = { _, s -> s.id }) { i, song ->
            SongRow(song, onClick = { PlayerConnection.play(songs, i) })
        }
    }
}

@Composable
fun DownloadsScreen(onBack: () -> Unit) {
    val data by SpitifyApp.library.data.collectAsStateWithLifecycle()
    val progress by SpitifyApp.downloads.progress.collectAsStateWithLifecycle()
    val songs = data.downloads
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 24.dp)) {
        item {
            CollectionHeader(
                title = "Downloads",
                subtitle = "",
                meta = songMeta(songs) + if (progress.isNotEmpty()) " • ${progress.size} downloading" else "",
                tint = androidx.compose.ui.graphics.Color(0xFF0C5A41),
                onBack = onBack,
                art = { DownloadsCover(220.dp, corner = 6.dp) },
                playEnabled = songs.isNotEmpty(),
                onPlay = { PlayerConnection.play(songs) },
                onShuffle = { shuffle(songs) },
            )
        }
        if (progress.isNotEmpty()) {
            item {
                LinearProgressIndicator(
                    progress = { progress.values.average().toFloat() },
                    modifier = Modifier
                        .fillParentMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                )
            }
        }
        if (songs.isEmpty() && progress.isEmpty()) {
            item { EmptyHint("Download songs, albums or playlists to play them without internet.") }
        }
        itemsIndexed(songs, key = { _, s -> s.id }) { i, song ->
            SongRow(song, onClick = { PlayerConnection.play(songs, i) })
        }
    }
}

@Composable
fun LocalPlaylistScreen(id: String, onBack: () -> Unit) {
    val library = SpitifyApp.library
    val data by library.data.collectAsStateWithLifecycle()
    var renaming by remember { mutableStateOf(false) }
    val playlist = data.playlists.find { it.id == id }
    if (playlist == null) {
        // deleted while open
        LaunchedEffect(Unit) { onBack() }
        return
    }
    val songs = playlist.songs

    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 24.dp)) {
        item {
            CollectionHeader(
                title = playlist.name,
                subtitle = "",
                meta = "Playlist • " + songMeta(songs),
                tint = rememberArtColor(songs.firstOrNull()?.thumb),
                onBack = onBack,
                art = { PlaylistCover(songs, 220.dp, corner = 6.dp) },
                playEnabled = songs.isNotEmpty(),
                onPlay = { PlayerConnection.play(songs) },
                onShuffle = { shuffle(songs) },
                topActions = {
                    IconButton(onClick = { renaming = true }) { Icon(Icons.Default.Edit, "Rename") }
                    IconButton(onClick = { library.deletePlaylist(playlist.id) }) { Icon(Icons.Default.Delete, "Delete playlist") }
                },
                actions = { DownloadAllButton(songs) },
            )
        }
        if (songs.isEmpty()) {
            item { EmptyHint("Find songs in Search, then use ⋮ → Add to playlist.") }
        }
        itemsIndexed(songs, key = { _, s -> s.id }) { i, song ->
            SongRow(song, onClick = { PlayerConnection.play(songs, i) }, playlistId = playlist.id)
        }
        if (songs.isNotEmpty()) {
            item {
                Text(
                    "Long-press a song for more options.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(16.dp),
                )
            }
        }
    }

    if (renaming) {
        NamePlaylistDialog("Rename playlist", initial = playlist.name, onDismiss = { renaming = false }) {
            library.renamePlaylist(playlist.id, it)
        }
    }
}
