package com.tunehub.app.ui

import androidx.compose.foundation.background
import androidx.compose.material.icons.filled.Person
import androidx.compose.foundation.layout.size
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.shape.CircleShape
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import com.tunehub.app.TuneHubApp
import com.tunehub.app.data.ArtistRef
import com.tunehub.app.data.YouTube
import com.tunehub.app.data.formatCount
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

class ArtistViewModel : ViewModel() {
    var page by mutableStateOf<YouTube.ArtistPage?>(null)
        private set
    var error by mutableStateOf<String?>(null)
        private set
    private var loadedUrl: String? = null

    fun load(artist: ArtistRef, force: Boolean = false) {
        if (!force && loadedUrl == artist.url) return
        loadedUrl = artist.url
        error = null
        viewModelScope.launch {
            try {
                page = YouTube.artistPage(artist.url, artist.name)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                error = e.message ?: "Couldn't load artist"
            }
        }
    }
}

@Composable
fun ArtistScreen(artist: ArtistRef, onBack: () -> Unit) {
    val vm: ArtistViewModel = viewModel(key = "artist-" + artist.url)
    LaunchedEffect(artist.url) { vm.load(artist) }
    val nav = LocalNav.current
    val library = TuneHubApp.library
    val data by library.data.collectAsStateWithLifecycle()
    val page = vm.page
    val info = page?.artist ?: artist
    val following = data.artists.any { it.url == artist.url }
    val tint = rememberArtColor(info.thumb)

    BackPage(onBack) {
        item {
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(340.dp),
            ) {
                val banner = page?.banner
                if (banner != null) {
                    AsyncImage(
                        model = banner,
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(220.dp),
                    )
                }
                // the banner melts into the aurora; the artist's color glows behind the avatar
                Box(
                    Modifier
                        .fillMaxWidth()
                        .height(220.dp)
                        .background(Brush.verticalGradient(listOf(Color.Transparent, Aurora.Ink.copy(alpha = 0.6f), Aurora.Ink.copy(alpha = 0f)))),
                )
                Box(
                    Modifier
                        .align(Alignment.BottomCenter)
                        .size(320.dp)
                        .background(Brush.radialGradient(listOf(tint.copy(alpha = 0.45f), Color.Transparent))),
                )
                Column(
                    Modifier.align(Alignment.BottomCenter),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Box(
                        Modifier
                            .size(176.dp)
                            .clip(CircleShape)
                            .background(Aurora.accent)
                            .padding(4.dp),
                    ) {
                        Artwork(info.thumb, 168.dp, corner = 84.dp, placeholder = Icons.Default.Person)
                    }
                    Text(
                        info.name,
                        style = MaterialTheme.typography.displaySmall,
                        modifier = Modifier.padding(top = 12.dp),
                    )
                }
            }
        }
        item {
            Row(
                Modifier.padding(horizontal = 20.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                GlassChip(if (following) "Following" else "Follow", selected = following) { library.toggleFollow(info) }
                if (info.subscribers > 0) {
                    Text(
                        "${formatCount(info.subscribers)} subscribers",
                        style = MaterialTheme.typography.bodyMedium,
                        color = Aurora.Muted,
                    )
                }
                Spacer(Modifier.weight(1f))
                val popular = page?.popular.orEmpty()
                GlassIconButton(AppIcons.Shuffle, "Shuffle", size = 48.dp) {
                    if (popular.isNotEmpty()) PlayerConnection.play(popular, popular.indices.random(), shuffled = true)
                }
                PlayCircle(enabled = popular.isNotEmpty()) { PlayerConnection.play(popular) }
            }
        }

        when {
            page == null && vm.error != null -> item { ErrorBox(vm.error ?: "") { vm.load(artist, force = true) } }
            page == null -> item { LoadingBox() }
            else -> {
                if (page.popular.isNotEmpty()) {
                    item { SectionTitle("Popular") }
                    itemsIndexed(page.popular, key = { _, s -> "p-" + s.id }) { i, song ->
                        SongRow(song, onClick = { PlayerConnection.play(page.popular, i) }, trackNumber = i + 1)
                    }
                }
                if (page.albums.isNotEmpty()) {
                    item { SectionTitle("Discography") }
                    item {
                        LazyRow(contentPadding = PaddingValues(horizontal = 20.dp), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                            items(page.albums, key = { it.url }) { a -> AlbumCard(a, 150.dp) { nav.open(Route.Album(a)) } }
                        }
                    }
                }
                if (page.videos.isNotEmpty()) {
                    item { SectionTitle("Music videos") }
                    itemsIndexed(page.videos, key = { _, s -> "v-" + s.id }) { i, song ->
                        SongRow(song, onClick = { PlayerConnection.play(page.videos, i) })
                    }
                }
                if (page.popular.isEmpty() && page.albums.isEmpty() && page.videos.isEmpty()) {
                    item { EmptyHint("YouTube didn't return anything for this artist.") }
                }
            }
        }
    }
}

