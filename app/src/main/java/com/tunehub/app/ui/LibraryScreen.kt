package com.tunehub.app.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.tunehub.app.TuneHubApp

private enum class LibFilter(val label: String) { Playlists("Playlists"), Albums("Albums"), Artists("Artists"), Downloaded("Downloaded") }

private enum class LibSort(val label: String) { Recent("Recently added"), Alpha("Alphabetical") }

/** One entry in the library list, whatever its kind. */
private class LibEntry(
    val key: String,
    val title: String,
    val subtitle: String,
    val kind: LibFilter?,
    /** When it was added to the library (0 for entries saved before this was recorded). */
    val addedAt: Long,
    val art: @Composable () -> Unit,
    val open: () -> Unit,
)

@Composable
fun LibraryScreen() {
    val library = TuneHubApp.library
    val nav = LocalNav.current
    val data by library.data.collectAsStateWithLifecycle()
    var filter by rememberSaveable { mutableStateOf<LibFilter?>(null) }
    var sort by rememberSaveable { mutableStateOf(LibSort.Recent) }
    var creating by remember { mutableStateOf(false) }

    val pinned = buildList {
        if (filter == null || filter == LibFilter.Playlists) {
            add(LibEntry("liked", "Liked Songs", "Playlist • ${data.liked.size} songs", null, Long.MAX_VALUE, { LikedCover(58.dp) }) { nav.open(Route.Liked) })
        }
        if ((filter == null || filter == LibFilter.Downloaded) && (data.downloads.isNotEmpty() || filter == LibFilter.Downloaded)) {
            add(LibEntry("dl", "Downloads", "${data.downloads.size} songs", null, Long.MAX_VALUE, { DownloadsCover(58.dp) }) { nav.open(Route.Downloads) })
        }
    }
    val entries = buildList {
        data.playlists.forEach { p ->
            add(LibEntry("p-" + p.id, p.name, "Playlist • ${p.songs.size} songs", LibFilter.Playlists, p.addedAt, { PlaylistCover(p.songs, 58.dp) }) {
                nav.open(Route.LocalPlaylist(p.id))
            })
        }
        data.albums.forEach { a ->
            add(LibEntry("a-" + a.url, a.title, (if (a.isAlbum) "Album" else "Playlist") + " • " + a.subtitle, LibFilter.Albums, a.addedAt,
                { Artwork(a.thumb, 58.dp, placeholder = AppIcons.Album) }) { nav.open(Route.Album(a)) })
        }
        data.artists.forEach { a ->
            add(LibEntry("r-" + a.url, a.name, "Artist", LibFilter.Artists, a.addedAt,
                { Artwork(a.thumb, 58.dp, corner = 29.dp, placeholder = Icons.Default.Person) }) { nav.open(Route.Artist(a)) })
        }
    }.filter { filter == null || it.kind == filter }
        // newest first; the sort is stable, so older entries without a date keep their order
        .let { list -> if (sort == LibSort.Alpha) list.sortedBy { it.title.lowercase() } else list.sortedByDescending { it.addedAt } }

    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 24.dp)) {
        item {
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(start = 20.dp, top = 14.dp, end = 16.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("Your Library", style = MaterialTheme.typography.headlineMedium, modifier = Modifier.weight(1f))
                GlassIconButton(AppIcons.Download, "Import playlist") { nav.open(Route.Import) }
                Spacer(Modifier.width(8.dp))
                GlassIconButton(Icons.Default.Add, "Create playlist") { creating = true }
            }
        }
        item {
            Row(
                Modifier
                    .horizontalScroll(rememberScrollState())
                    .padding(horizontal = 20.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                LibFilter.entries.forEach { f ->
                    GlassChip(f.label, selected = filter == f) { filter = if (filter == f) null else f }
                }
            }
        }
        item {
            Row(
                Modifier
                    .clickable { sort = if (sort == LibSort.Recent) LibSort.Alpha else LibSort.Recent }
                    .padding(horizontal = 20.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(AppIcons.Sort, null, modifier = Modifier.size(16.dp))
                Text(sort.label, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(start = 8.dp))
            }
        }
        items(pinned + entries, key = { it.key }) { e ->
            BrowseRow(title = e.title, subtitle = e.subtitle, thumb = null, art = e.art, onClick = e.open)
        }
        if (entries.isEmpty() && filter != null && filter != LibFilter.Downloaded) {
            item {
                EmptyHint(
                    when (filter) {
                        LibFilter.Playlists -> "Create a playlist with + above."
                        LibFilter.Albums -> "Tap ♡ on an album or playlist page to save it here."
                        else -> "Follow artists from their pages to see them here."
                    }
                )
            }
        }
    }

    if (creating) {
        NamePlaylistDialog("Give your playlist a name", onDismiss = { creating = false }) { name ->
            nav.open(Route.LocalPlaylist(library.createPlaylist(name)))
        }
    }
}
