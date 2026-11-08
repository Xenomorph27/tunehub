package com.tunehub.app.ui

import android.app.Application
import android.content.Context
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.HorizontalDivider
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
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.tunehub.app.TuneHubApp
import com.tunehub.app.data.BrowseItem
import com.tunehub.app.data.Song
import com.tunehub.app.data.YouTube
import com.tunehub.app.data.homePlaylists
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import java.util.Locale
import java.util.concurrent.TimeUnit

class HomeViewModel(application: Application) : AndroidViewModel(application) {
    var quickPicks by mutableStateOf<List<Song>>(emptyList())
        private set
    var trending by mutableStateOf<List<Song>>(emptyList())
        private set
    private var historyIds = emptyList<String>()
    private var quickPicksJob: Job? = null

    init {
        viewModelScope.launch {
            val locale = Locale.getDefault().toLanguageTag()
            val cache = withContext(Dispatchers.IO) {
                application.getSharedPreferences("home_feed", Context.MODE_PRIVATE)
            }
            val (cached, fetchedAt) = withContext(Dispatchers.IO) {
                val snapshot = cache.all
                val songs = if (snapshot["locale"] == locale) {
                    (snapshot["songs"] as? String)?.let { encoded ->
                        runCatching { feedJson.decodeFromString(songList, encoded) }.getOrNull()
                    }.orEmpty()
                } else {
                    emptyList()
                }
                songs to (snapshot["fetched_at"] as? Long ?: 0L)
            }
            trending = cached
            val age = System.currentTimeMillis() - fetchedAt
            if (cached.isNotEmpty() && age in 0 until feedMaxAge) return@launch

            val latest = withContext(Dispatchers.IO) {
                val songs = runCatching { YouTube.trendingMusic().rankMusic().take(20) }.getOrDefault(emptyList())
                if (songs.isNotEmpty()) {
                    cache.edit()
                        .putString("locale", locale)
                        .putString("songs", feedJson.encodeToString(songList, songs))
                        .putLong("fetched_at", System.currentTimeMillis())
                        .apply()
                }
                songs
            }
            if (latest.isNotEmpty()) trending = latest
        }
    }

    /** Build Quick Picks from the YouTube Music song catalog of recently played artists. */
    fun loadFor(recent: List<Song>) {
        val ids = recent.map { it.id }
        if (ids == historyIds) return
        historyIds = ids
        quickPicksJob?.cancel()
        if (recent.isEmpty()) {
            quickPicks = emptyList()
            return
        }

        val played = ids.toSet()
        quickPicks = quickPicks.filterNot { it.id in played }
        val seeds = recent.asSequence()
            .filter { it.artist.isNotBlank() }
            .distinctBy { it.artist.trim().lowercase(Locale.ROOT) }
            .take(3)
            .toList()
        quickPicksJob = viewModelScope.launch {
            val groups = seeds.map { seed ->
                async(Dispatchers.IO) {
                    val artist = seed.artist.trim()
                    runCatching { YouTube.Search(artist, YouTube.FILTER_SONGS).loadNext() }
                        .getOrDefault(emptyList())
                        .filterIsInstance<BrowseItem.SongItem>()
                        .map { it.song }
                        .filter { song ->
                            song.id !in played && (song.artist.equals(artist, ignoreCase = true) ||
                                song.artist.startsWith("$artist and ", ignoreCase = true) ||
                                (seed.artistUrl != null && song.artistUrl == seed.artistUrl))
                        }
                }
            }.awaitAll()
            // Take results by rank across artists before moving deeper into any one catalog.
            quickPicks = buildList {
                repeat(groups.maxOfOrNull { it.size } ?: 0) { rank ->
                    groups.mapNotNullTo(this) { it.getOrNull(rank) }
                }
            }.distinctBy { it.id }.take(10)
        }
    }
}

private fun List<Song>.rankMusic() = distinctBy { it.id }.sortedByDescending { it.preferredMusicSource }

private val feedJson = Json { ignoreUnknownKeys = true }
private val songList = ListSerializer(Song.serializer())
private val feedMaxAge = TimeUnit.MINUTES.toMillis(30)

/** A tile in the top shortcuts grid. */
private class Shortcut(val title: String, val art: @Composable () -> Unit, val open: () -> Unit)

@Composable
fun HomeScreen(vm: HomeViewModel) {
    val nav = LocalNav.current
    val data by TuneHubApp.library.data.collectAsStateWithLifecycle()
    val recent = data.recent
    val quickPicks = vm.quickPicks
    val trending = vm.trending

    LaunchedEffect(recent) { vm.loadFor(recent) }

    val shortcuts = buildList {
        add(Shortcut("Liked Songs", { LikedCover(58.dp, corner = 16.dp) }) { nav.open(Route.Liked) })
        add(Shortcut("Recently played", { RecentCover(58.dp, corner = 16.dp) }) { nav.open(Route.Recent) })
        data.playlists.homePlaylists().forEach { p ->
            add(Shortcut(p.name, { PlaylistCover(p.songs, 58.dp, corner = 16.dp) }) { nav.open(Route.LocalPlaylist(p.id)) })
        }
    }

    // one radio per artist you actually listen to, each shown with that artist's song cover
    val mixes = (recent + data.liked).distinctBy { it.artist.lowercase() }.take(8)

    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 12.dp)) {
        item(key = "library-heading") {
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(start = 20.dp, end = 20.dp, top = 14.dp, bottom = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                BrowseTitle("TuneHub", Modifier.weight(1f))
                PanelIconButton(Icons.Default.Settings, "Settings") { nav.open(Route.Settings) }
            }
        }
        item {
            Column(
                Modifier.padding(horizontal = 20.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                shortcuts.chunked(2).forEach { pair ->
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        pair.forEach { s -> ShortcutTile(s, Modifier.weight(1f)) }
                    }
                }
            }
        }

        val featured = recent.isEmpty() && PlayerConnection.current == null
        val spotlightQueue = if (featured) trending else recent
        val hero = PlayerConnection.current ?: spotlightQueue.firstOrNull()
        if (hero != null) {
            item(key = "spotlight") {
                Box(Modifier.animateItem(fadeInSpec = tween(180)).padding(top = 16.dp)) {
                    SpotlightCard(hero, spotlightQueue, featured)
                }
            }
        }

        if (mixes.isNotEmpty()) {
            item { BrowseSection("Made for you") }
            item {
                LazyRow(contentPadding = PaddingValues(horizontal = 20.dp), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                    items(mixes, key = { "mix-" + it.id }) { seed ->
                        MixCard("${seed.artist} Radio", seed.thumb) {
                            PlayerConnection.startRadio(seed)
                        }
                    }
                }
            }
        }

        if (quickPicks.isNotEmpty()) {
            item {
                QuickPicksRail(quickPicks) { index ->
                    PlayerConnection.startRadio(quickPicks[index])
                }
            }
        }

        if (trending.isNotEmpty()) {
            item {
                TrendingRail(trending)
            }
        }

        if (data.artists.isNotEmpty()) {
            item { BrowseSection("Your artists") }
            item {
                LazyRow(contentPadding = PaddingValues(horizontal = 20.dp), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    items(data.artists, key = { it.url }) { a -> ArtistCircle(a, 112.dp) { nav.open(Route.Artist(a)) } }
                }
            }
        }
    }
}

/** The first card shows a real track from playback, history or the trending feed. */
@Composable
private fun SpotlightCard(song: Song, queue: List<Song>, featured: Boolean) {
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
    Column(
        Modifier
            .padding(horizontal = 20.dp)
            .fillMaxWidth()
            .panel(RoundedCornerShape(16.dp), fill = tuneColors.surface)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(
            when {
                isCurrent -> "NOW PLAYING"
                featured -> "FROM TRENDING"
                else -> "CONTINUE LISTENING"
            },
            style = MaterialTheme.typography.labelSmall,
            color = tuneColors.accentText,
        )
        Row(verticalAlignment = Alignment.CenterVertically) {
            Artwork(song.thumb, 84.dp, corner = 10.dp)
            Column(
                Modifier
                    .weight(1f)
                    .padding(horizontal = 14.dp),
            ) {
                Text(song.title, style = MaterialTheme.typography.headlineSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(song.artist, style = MaterialTheme.typography.bodyMedium, color = tuneColors.muted, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Box(
                    Modifier
                        .padding(top = 10.dp)
                        .fillMaxWidth()
                        .height(5.dp)
                        .clip(RoundedCornerShape(3.dp))
                        .background(tuneColors.rule),
                ) {
                    Box(
                        Modifier
                            .fillMaxWidth(progress)
                            .height(5.dp)
                            .clip(RoundedCornerShape(3.dp))
                            .background(tuneColors.accent),
                    )
                }
            }
            AccentCircleButton(
                if (isCurrent && PlayerConnection.isPlaying) AppIcons.Pause else AppIcons.Play,
                if (isCurrent && PlayerConnection.isPlaying) "Pause" else "Play",
                size = 56.dp,
            ) {
                if (isCurrent) PlayerConnection.togglePlay()
                else PlayerConnection.play(queue.take(50), queue.indexOfFirst { it.id == song.id }.coerceAtLeast(0))
            }
        }
    }
}

@Composable
private fun ShortcutTile(s: Shortcut, modifier: Modifier) {
    Row(
        modifier
            .height(70.dp)
            .panel(RoundedCornerShape(20.dp))
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
private fun MixCard(title: String, thumb: String?, onClick: () -> Unit) {
    Box(
        Modifier.width(204.dp).height(132.dp)
            .clip(RoundedCornerShape(12.dp))
            .clickable(role = Role.Button, onClick = onClick),
    ) {
        Artwork(thumb, 204.dp, Modifier.matchParentSize(), corner = 12.dp)
        Box(Modifier.matchParentSize().background(Brush.verticalGradient(listOf(Color.Transparent, TuneHubInk.copy(alpha = 0.95f)))))
        Row(
            Modifier.align(Alignment.BottomStart).padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Icon(AppIcons.Radio, null, tint = TuneHubAmber, modifier = Modifier.size(16.dp))
            Text(title, style = MaterialTheme.typography.titleMedium, color = TuneHubLightText, maxLines = 2, overflow = TextOverflow.Ellipsis)
        }
    }
}

@Composable
private fun QuickPicksRail(songs: List<Song>, onPlay: (Int) -> Unit) {
    BrowseSection("Quick picks")
    LazyRow(contentPadding = PaddingValues(horizontal = 20.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        itemsIndexed(songs, key = { _, song -> song.id }) { index, song ->
            SongCard(song) { onPlay(index) }
        }
    }
}

@Composable
private fun SongCard(song: Song, onClick: () -> Unit) {
    Column(
        Modifier
            .width(144.dp)
            .prefetchOnTouch(song.id)
            .clickable(role = Role.Button, onClick = onClick),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Artwork(song.thumb, 144.dp, corner = 10.dp)
        Text(
            song.title,
            style = MaterialTheme.typography.titleSmall,
            minLines = 2,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
        Text(song.artist, style = MaterialTheme.typography.bodySmall, color = tuneColors.muted, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
private fun TrendingRail(songs: List<Song>) {
    BrowseSection("Trending now")
    LazyRow(contentPadding = PaddingValues(horizontal = 20.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        itemsIndexed(songs.chunked(3), key = { _, group -> group.first().id }) { page, group ->
            Column(Modifier.width(280.dp).panel(RoundedCornerShape(12.dp))) {
                group.forEachIndexed { offset, song ->
                    val index = page * 3 + offset
                    if (offset > 0) HorizontalDivider(Modifier.padding(start = 64.dp, end = 12.dp), color = tuneColors.rule)
                    Row(
                        Modifier.fillMaxWidth().prefetchOnTouch(song.id)
                            .clickable(role = Role.Button) { PlayerConnection.play(songs, index) }
                            .padding(10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        Artwork(song.thumb, 44.dp, corner = 7.dp)
                        Column(Modifier.weight(1f)) {
                            Text(song.title, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text(song.artist, style = MaterialTheme.typography.bodySmall, color = tuneColors.muted, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                        Icon(AppIcons.Play, null, tint = tuneColors.muted, modifier = Modifier.size(18.dp))
                    }
                }
            }
        }
    }
}
