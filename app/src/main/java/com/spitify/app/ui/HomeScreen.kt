package com.spitify.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.spitify.app.SpitifyApp
import com.spitify.app.data.Song
import com.spitify.app.data.StreamResolver
import com.spitify.app.data.YouTube
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Calendar

class HomeViewModel : ViewModel() {
    var quickPicks by mutableStateOf<List<Song>>(emptyList())
        private set
    var trending by mutableStateOf<List<Song>>(emptyList())
        private set
    private var seedId: String? = null

    init {
        viewModelScope.launch {
            trending = withContext(Dispatchers.IO) {
                runCatching { YouTube.trendingMusic().take(20) }.getOrDefault(emptyList())
            }
        }
    }

    /** Quick picks = songs YouTube relates to whatever you played last. */
    fun loadFor(seed: Song) {
        if (seed.id == seedId) return
        seedId = seed.id
        viewModelScope.launch {
            val related = withContext(Dispatchers.IO) {
                runCatching { StreamResolver.resolve(seed.id).related }.getOrDefault(emptyList())
            }
            if (related.isNotEmpty()) quickPicks = related.distinctBy { it.id }.take(12)
        }
    }
}

/** A tile in the top shortcuts grid. */
private class Shortcut(val title: String, val art: @Composable () -> Unit, val open: () -> Unit)

@Composable
fun HomeScreen(vm: HomeViewModel) {
    val nav = LocalNav.current
    val data by SpitifyApp.library.data.collectAsStateWithLifecycle()
    val recent = data.recent

    LaunchedEffect(recent.firstOrNull()?.id) { recent.firstOrNull()?.let(vm::loadFor) }

    val shortcuts = buildList {
        add(Shortcut("Liked Songs", { LikedCover(56.dp, corner = 0.dp) }) { nav.open(Route.Liked) })
        data.playlists.take(3).forEach { p ->
            add(Shortcut(p.name, { PlaylistCover(p.songs, 56.dp, corner = 0.dp) }) { nav.open(Route.LocalPlaylist(p.id)) })
        }
        data.albums.take(2).forEach { a ->
            add(Shortcut(a.title, { Artwork(a.thumb, 56.dp, corner = 0.dp) }) { nav.open(Route.Album(a)) })
        }
        data.artists.take(2).forEach { a ->
            add(Shortcut(a.name, { Artwork(a.thumb, 56.dp, corner = 0.dp) }) { nav.open(Route.Artist(a)) })
        }
        if (data.downloads.isNotEmpty()) add(Shortcut("Downloads", { DownloadsCover(56.dp, corner = 0.dp) }) { nav.open(Route.Downloads) })
    }.take(6)

    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 24.dp)) {
        item {
            Row(
                Modifier.padding(start = 16.dp, top = 12.dp, end = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(greeting(), style = MaterialTheme.typography.headlineMedium, modifier = Modifier.weight(1f))
                IconButton(onClick = { nav.open(Route.Settings) }) { Icon(Icons.Default.Settings, "Settings") }
            }
        }

        item {
            Column(
                Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                shortcuts.chunked(2).forEach { pair ->
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        pair.forEach { s -> ShortcutTile(s, Modifier.weight(1f)) }
                        if (pair.size == 1) Spacer(Modifier.weight(1f))
                    }
                }
            }
        }

        if (recent.isEmpty()) {
            item { EmptyHint("Nothing played yet. Head to Search and pick something: no ads, ever.") }
        } else {
            item { SectionTitle("Recently played") }
            item {
                LazyRow(
                    contentPadding = PaddingValues(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(14.dp),
                ) {
                    itemsIndexed(recent.take(20), key = { _, s -> s.id }) { i, song ->
                        SongCard(song) { PlayerConnection.play(recent.take(50), i) }
                    }
                }
            }
        }

        if (vm.quickPicks.isNotEmpty()) {
            item { SectionTitle("Quick picks") }
            items(vm.quickPicks, key = { "qp-" + it.id }) { song ->
                SongRow(song, onClick = { PlayerConnection.startRadio(song) })
            }
        }

        if (data.artists.isNotEmpty()) {
            item { SectionTitle("Your artists") }
            item {
                LazyRow(
                    contentPadding = PaddingValues(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(14.dp),
                ) {
                    items(data.artists, key = { it.url }) { a -> ArtistCircle(a) { nav.open(Route.Artist(a)) } }
                }
            }
        }

        if (vm.trending.isNotEmpty()) {
            item { SectionTitle("Trending now") }
            item {
                LazyRow(
                    contentPadding = PaddingValues(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(14.dp),
                ) {
                    itemsIndexed(vm.trending, key = { _, s -> "tr-" + s.id }) { i, song ->
                        SongCard(song) { PlayerConnection.play(vm.trending, i) }
                    }
                }
            }
        }
    }
}

@Composable
private fun ShortcutTile(s: Shortcut, modifier: Modifier) {
    Row(
        modifier
            .height(56.dp)
            .clip(RoundedCornerShape(6.dp))
            .background(MaterialTheme.colorScheme.surfaceContainerHigh)
            .clickable(onClick = s.open),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        s.art()
        Text(
            s.title,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Bold,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(horizontal = 10.dp),
        )
    }
}

@Composable
private fun SongCard(song: Song, onClick: () -> Unit) {
    Column(
        Modifier
            .width(140.dp)
            .clickable(onClick = onClick),
    ) {
        Artwork(song.thumb, 140.dp, corner = 6.dp)
        Text(
            song.title,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 8.dp),
        )
        Text(
            song.artist,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

private fun greeting(): String = when (Calendar.getInstance().get(Calendar.HOUR_OF_DAY)) {
    in 5..11 -> "Good morning"
    in 12..16 -> "Good afternoon"
    else -> "Good evening"
}
