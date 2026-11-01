package com.tunehub.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
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
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.tunehub.app.TuneHubApp
import com.tunehub.app.data.Song
import com.tunehub.app.data.StreamResolver
import com.tunehub.app.data.YouTube
import com.tunehub.app.data.isEmptyLibrary
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
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
                runCatching { StreamResolver.related(seed.id) }.getOrDefault(emptyList())
            }
            if (related.isNotEmpty()) quickPicks = related.distinctBy { it.id }.take(10)
        }
    }
}

private val moods = listOf(
    "Chill" to Aurora.Cyan,
    "Romance" to Aurora.Pink,
    "Workout" to Aurora.Amber,
    "Bollywood" to Color(0xFFFF7A3D),
    "Lo-fi" to Aurora.Lilac,
    "Party" to Color(0xFF9CFF57),
)

/** A tile in the top shortcuts grid. */
private class Shortcut(val title: String, val art: @Composable () -> Unit, val open: () -> Unit)

@Composable
fun HomeScreen(vm: HomeViewModel) {
    val nav = LocalNav.current
    val data by TuneHubApp.library.data.collectAsStateWithLifecycle()
    val recent = data.recent

    LaunchedEffect(recent.firstOrNull()?.id) { recent.firstOrNull()?.let(vm::loadFor) }

    val shortcuts = buildList {
        add(Shortcut("Liked Songs", { LikedCover(58.dp, corner = 16.dp) }) { nav.open(Route.Liked) })
        data.playlists.take(3).forEach { p ->
            add(Shortcut(p.name, { PlaylistCover(p.songs, 58.dp, corner = 16.dp) }) { nav.open(Route.LocalPlaylist(p.id)) })
        }
        data.albums.take(2).forEach { a ->
            add(Shortcut(a.title, { Artwork(a.thumb, 58.dp, corner = 16.dp) }) { nav.open(Route.Album(a)) })
        }
        data.artists.take(2).forEach { a ->
            add(Shortcut(a.name, { Artwork(a.thumb, 58.dp, corner = 29.dp) }) { nav.open(Route.Artist(a)) })
        }
        if (data.downloads.isNotEmpty()) add(Shortcut("Downloads", { DownloadsCover(58.dp, corner = 16.dp) }) { nav.open(Route.Downloads) })
    }.take(6)

    // one radio per artist you actually listen to, each shown with that artist's song cover
    val mixes = (recent + data.liked).distinctBy { it.artist.lowercase() }.take(8)

    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 12.dp)) {
        item {
            Row(
                Modifier.padding(start = 20.dp, top = 14.dp, end = 16.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f)) {
                    GradientText("TuneHub", MaterialTheme.typography.displaySmall)
                    Text(greeting(), style = MaterialTheme.typography.bodyLarge, color = Aurora.Muted)
                }
                GlassIconButton(Icons.Default.Settings, "Settings") { nav.open(Route.Settings) }
            }
        }

        item {
            Row(
                Modifier
                    .padding(horizontal = 20.dp, vertical = 16.dp)
                    .fillMaxWidth()
                    .height(52.dp)
                    .glass(RoundedCornerShape(18.dp))
                    .clickable { nav.selectTab(1) }
                    .padding(horizontal = 16.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(Icons.Default.Search, null, tint = Aurora.Muted)
                Text(
                    "Search songs, artists, albums",
                    style = MaterialTheme.typography.bodyLarge,
                    color = Aurora.Muted,
                    modifier = Modifier.padding(start = 12.dp),
                )
            }
        }

        if (data.isEmptyLibrary()) item { RestoreCard() }

        val hero = PlayerConnection.current ?: recent.firstOrNull()
        if (hero != null) item { ContinueCard(hero, recent) }

        item {
            Row(
                Modifier
                    .horizontalScroll(rememberScrollState())
                    .padding(horizontal = 20.dp, vertical = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                moods.forEach { (name, color) -> GlassChip(name, dot = color) { nav.open(Route.Genre(name)) } }
            }
        }

        item {
            Column(
                Modifier.padding(horizontal = 20.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                shortcuts.chunked(2).forEach { pair ->
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        pair.forEach { s -> ShortcutTile(s, Modifier.weight(1f)) }
                        if (pair.size == 1) Spacer(Modifier.weight(1f))
                    }
                }
            }
        }

        if (mixes.isNotEmpty()) {
            item { SectionTitle("Made for you") }
            item {
                LazyRow(contentPadding = PaddingValues(horizontal = 20.dp), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                    items(mixes, key = { "mix-" + it.id }) { seed ->
                        MixCard("${seed.artist} Radio", "Mix based on ${seed.title}", seed.thumb) {
                            PlayerConnection.startRadio(seed)
                        }
                    }
                }
            }
        }

        if (recent.isEmpty()) {
            item { EmptyHint("Nothing played yet. Search for a song to get started. No ads, ever.") }
        } else {
            item { SectionTitle("Recently played") }
            item {
                LazyRow(contentPadding = PaddingValues(horizontal = 20.dp), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
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
                LazyRow(contentPadding = PaddingValues(horizontal = 20.dp), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    items(data.artists, key = { it.url }) { a -> ArtistCircle(a, 112.dp) { nav.open(Route.Artist(a)) } }
                }
            }
        }

        if (vm.trending.isNotEmpty()) {
            item { SectionTitle("Trending now") }
            item {
                LazyRow(contentPadding = PaddingValues(horizontal = 20.dp), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                    itemsIndexed(vm.trending, key = { _, s -> "tr-" + s.id }) { i, song ->
                        SongCard(song) { PlayerConnection.play(vm.trending, i) }
                    }
                }
            }
        }
        item { CreditFooter() }
    }
}

/** The big glass card at the top: what's playing, or what you played last. */
@Composable
private fun ContinueCard(song: Song, recent: List<Song>) {
    val isCurrent = PlayerConnection.current?.id == song.id
    var position by remember { mutableLongStateOf(0L) }
    LaunchedEffect(isCurrent) {
        while (isCurrent) {
            position = PlayerConnection.positionMs()
            delay(1000)
        }
    }
    val duration = PlayerConnection.durationMs
    val progress = if (isCurrent && duration > 0) (position.toFloat() / duration).coerceIn(0f, 1f) else 0f
    val glow = rememberArtColor(song.thumb)

    Column(
        Modifier
            .padding(horizontal = 20.dp)
            .fillMaxWidth()
            .glass(RoundedCornerShape(30.dp), fill = Aurora.GlassStrong)
            .background(Brush.horizontalGradient(listOf(glow.copy(alpha = 0.22f), Color.Transparent)))
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(
            if (isCurrent) "NOW PLAYING" else "CONTINUE LISTENING",
            style = MaterialTheme.typography.labelSmall,
            color = Aurora.Lilac,
        )
        Row(verticalAlignment = Alignment.CenterVertically) {
            Artwork(song.thumb, 88.dp, corner = 22.dp)
            Column(
                Modifier
                    .weight(1f)
                    .padding(horizontal = 14.dp),
            ) {
                Text(song.title, style = MaterialTheme.typography.titleLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(song.artist, style = MaterialTheme.typography.bodyMedium, color = Aurora.Muted, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Box(
                    Modifier
                        .padding(top = 10.dp)
                        .fillMaxWidth()
                        .height(5.dp)
                        .clip(RoundedCornerShape(3.dp))
                        .background(Color.White.copy(alpha = 0.14f)),
                ) {
                    Box(
                        Modifier
                            .fillMaxWidth(progress)
                            .height(5.dp)
                            .clip(RoundedCornerShape(3.dp))
                            .background(Aurora.accent),
                    )
                }
            }
            GradientCircleButton(
                if (isCurrent && PlayerConnection.isPlaying) AppIcons.Pause else Icons.Default.PlayArrow,
                if (isCurrent && PlayerConnection.isPlaying) "Pause" else "Play",
                size = 56.dp,
            ) {
                if (isCurrent) PlayerConnection.togglePlay()
                else PlayerConnection.play(recent.take(50), recent.indexOfFirst { it.id == song.id }.coerceAtLeast(0))
            }
        }
    }
}

@Composable
private fun ShortcutTile(s: Shortcut, modifier: Modifier) {
    Row(
        modifier
            .height(70.dp)
            .glass(RoundedCornerShape(20.dp))
            .clickable(onClick = s.open)
            .padding(6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        s.art()
        Text(
            s.title,
            style = MaterialTheme.typography.titleSmall,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(horizontal = 10.dp),
        )
    }
}

/** A radio mix: the seed song's cover with the mix name over a dark fade. */
@Composable
private fun MixCard(title: String, subtitle: String, thumb: String?, onClick: () -> Unit) {
    Column(
        Modifier
            .width(156.dp)
            .clickable(onClick = onClick),
    ) {
        Box(Modifier.size(156.dp)) {
            Artwork(thumb, 156.dp, corner = 28.dp)
            Box(
                Modifier
                    .matchParentSize()
                    .clip(RoundedCornerShape(28.dp))
                    .background(Brush.verticalGradient(listOf(Color.Transparent, Aurora.Ink.copy(alpha = 0.85f)))),
            )
            Row(
                Modifier
                    .align(Alignment.TopEnd)
                    .padding(10.dp)
                    .glass(RoundedCornerShape(50), fill = Aurora.Ink.copy(alpha = 0.45f))
                    .padding(horizontal = 8.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(AppIcons.Radio, null, tint = Aurora.Cyan, modifier = Modifier.size(14.dp))
                Text("RADIO", style = MaterialTheme.typography.labelSmall, color = Aurora.Text, modifier = Modifier.padding(start = 4.dp))
            }
            Text(
                title,
                style = MaterialTheme.typography.titleMedium,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(12.dp),
            )
        }
        Text(
            subtitle,
            style = MaterialTheme.typography.bodySmall,
            color = Aurora.Muted,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(top = 8.dp),
        )
    }
}

@Composable
private fun SongCard(song: Song, onClick: () -> Unit) {
    Column(
        Modifier
            .width(144.dp)
            .prefetchOnTouch(song.id)
            .clickable(onClick = onClick),
    ) {
        Artwork(song.thumb, 144.dp, corner = 26.dp)
        Text(
            song.title,
            style = MaterialTheme.typography.titleSmall,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 8.dp),
        )
        Text(song.artist, style = MaterialTheme.typography.bodySmall, color = Aurora.Muted, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

private fun greeting(): String = when (Calendar.getInstance().get(Calendar.HOUR_OF_DAY)) {
    in 5..11 -> "Good morning"
    in 12..16 -> "Good afternoon"
    else -> "Good evening"
}

/** Shown while the library is empty, e.g. right after a reinstall: bring everything back. */
@Composable
private fun RestoreCard() {
    val nav = LocalNav.current
    val restore = rememberRestoreAction()
    Column(
        Modifier
            .padding(horizontal = 20.dp, vertical = 4.dp)
            .fillMaxWidth()
            .glass(RoundedCornerShape(28.dp), fill = Aurora.GlassStrong)
            .padding(18.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Text("Reinstalled TuneHub?", style = MaterialTheme.typography.titleLarge)
        Text(
            "Restore your playlists and likes from your backup in Download/TuneHub, or import a playlist from Spotify.",
            style = MaterialTheme.typography.bodyMedium,
            color = Aurora.Muted,
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            GlassChip("Restore backup", selected = true, onClick = restore)
            GlassChip("Import playlist") { nav.open(Route.Import) }
        }
    }
}
