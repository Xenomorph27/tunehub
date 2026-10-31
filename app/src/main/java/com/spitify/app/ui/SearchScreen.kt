package com.spitify.app.ui

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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.spitify.app.SpitifyApp
import com.spitify.app.data.ArtistRef
import com.spitify.app.data.BrowseItem
import com.spitify.app.data.Song
import com.spitify.app.data.YouTube
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

enum class SearchFilter(val label: String, val ytFilter: String?) {
    All("All", null),
    Songs("Songs", YouTube.FILTER_SONGS),
    Artists("Artists", YouTube.FILTER_ARTISTS),
    Albums("Albums", YouTube.FILTER_ALBUMS),
    Playlists("Playlists", YouTube.FILTER_PLAYLISTS),
    Videos("Videos", YouTube.FILTER_VIDEOS),
}

/** Search state; also used (with a fixed query) by genre pages. */
class SearchViewModel : ViewModel() {
    var query by mutableStateOf("")
        private set
    var filter by mutableStateOf(SearchFilter.All)
        private set
    var suggestions by mutableStateOf<List<String>>(emptyList())
        private set
    var top by mutableStateOf<YouTube.TopResults?>(null)
        private set
    var items by mutableStateOf<List<BrowseItem>>(emptyList())
        private set
    var loading by mutableStateOf(false)
        private set
    var error by mutableStateOf<String?>(null)
        private set
    var showingResults by mutableStateOf(false)
        private set

    private var search: YouTube.Search? = null
    private var suggestJob: Job? = null
    private var searchJob: Job? = null

    fun onQueryChange(q: String) {
        query = q
        showingResults = false
        suggestJob?.cancel()
        if (q.isBlank()) {
            suggestions = emptyList()
            return
        }
        suggestJob = viewModelScope.launch {
            delay(250)
            suggestions = withContext(Dispatchers.IO) {
                runCatching { YouTube.suggestions(q) }.getOrDefault(emptyList())
            }
        }
    }

    fun submit(q: String = query, remember: Boolean = true) {
        if (q.isBlank()) return
        query = q
        showingResults = true
        if (remember) SpitifyApp.library.addSearch(q)
        suggestJob?.cancel()
        suggestions = emptyList()
        top = null
        items = emptyList()
        error = null
        search = null
        searchJob?.cancel()
        val f = filter
        searchJob = viewModelScope.launch {
            loading = true
            try {
                if (f.ytFilter == null) {
                    val results = YouTube.searchAll(q)
                    top = results
                    if (results.songs.isEmpty() && results.artists.isEmpty() && results.albums.isEmpty()) {
                        error = "Nothing found, or YouTube didn't answer. Check your connection."
                    }
                } else {
                    val s = withContext(Dispatchers.IO) { YouTube.Search(q, f.ytFilter) }
                    search = s
                    items = withContext(Dispatchers.IO) { s.loadNext() }.distinctBy(::itemKey)
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                error = e.message ?: "Search failed"
            } finally {
                if (isActive) loading = false
            }
        }
    }

    fun loadMore() {
        val s = search ?: return
        if (loading || !s.hasMore) return
        searchJob = viewModelScope.launch {
            loading = true
            try {
                val more = withContext(Dispatchers.IO) { s.loadNext() }
                val seen = items.map(::itemKey).toSet()
                items = items + more.filter { itemKey(it) !in seen }.distinctBy(::itemKey)
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                // keep what we have; scrolling again retries
            } finally {
                if (isActive) loading = false
            }
        }
    }

    fun selectFilter(f: SearchFilter) {
        if (f == filter) return
        filter = f
        if (showingResults) submit(remember = false)
    }

    fun clear() {
        onQueryChange("")
        top = null
        items = emptyList()
    }
}

fun itemKey(item: BrowseItem): String = when (item) {
    is BrowseItem.SongItem -> "s-" + item.song.id
    is BrowseItem.ArtistItem -> "a-" + item.artist.url
    is BrowseItem.AlbumItem -> "l-" + item.album.url
}

private val genres = listOf(
    "Bollywood" to 0xFFE13300, "Punjabi" to 0xFF8D67AB, "Lo-fi" to 0xFF27856A, "Pop" to 0xFF1E3264,
    "Hip-Hop" to 0xFFBA5D07, "Chill" to 0xFF477D95, "Workout" to 0xFF777777, "Romance" to 0xFFDC148C,
    "Party" to 0xFFAF2896, "Rock" to 0xFFE91429, "Indie" to 0xFF608108, "K-Pop" to 0xFF148A08,
    "Focus" to 0xFF503750, "Sad songs" to 0xFF0D73EC, "EDM" to 0xFF8C1932, "Retro" to 0xFFB06239,
)

@Composable
fun SearchScreen(vm: SearchViewModel) {
    val focusManager = LocalFocusManager.current
    val keyboard = LocalSoftwareKeyboardController.current
    // hide the keyboard so results aren't half covered
    val dismissKeyboard = { keyboard?.hide(); focusManager.clearFocus() }
    val nav = LocalNav.current

    Column(Modifier.fillMaxSize()) {
        Text(
            "Search",
            style = MaterialTheme.typography.headlineMedium,
            modifier = Modifier.padding(start = 16.dp, top = 12.dp, bottom = 12.dp),
        )
        TextField(
            value = vm.query,
            onValueChange = vm::onQueryChange,
            placeholder = { Text("What do you want to listen to?") },
            leadingIcon = { Icon(Icons.Default.Search, null) },
            trailingIcon = {
                if (vm.query.isNotEmpty()) {
                    IconButton(onClick = vm::clear) { Icon(Icons.Default.Clear, "Clear") }
                }
            },
            singleLine = true,
            shape = RoundedCornerShape(10.dp),
            colors = TextFieldDefaults.colors(
                focusedIndicatorColor = Color.Transparent,
                unfocusedIndicatorColor = Color.Transparent,
                focusedContainerColor = Color.White,
                unfocusedContainerColor = Color.White,
                focusedTextColor = Ink,
                unfocusedTextColor = Ink,
                focusedPlaceholderColor = Color(0xFF6A6A6A),
                unfocusedPlaceholderColor = Color(0xFF6A6A6A),
                focusedLeadingIconColor = Ink,
                unfocusedLeadingIconColor = Ink,
                focusedTrailingIconColor = Ink,
                unfocusedTrailingIconColor = Ink,
                cursorColor = Ink,
            ),
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
            keyboardActions = KeyboardActions(onSearch = { dismissKeyboard(); vm.submit() }),
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
        )

        when {
            !vm.showingResults && vm.suggestions.isNotEmpty() -> Suggestions(vm.suggestions) { s ->
                dismissKeyboard()
                vm.submit(s)
            }
            !vm.showingResults -> SearchHome(
                onSearch = { q -> dismissKeyboard(); vm.submit(q) },
                onGenre = { nav.open(Route.Genre(it)) },
            )
            else -> {
                FilterChips(vm.filter, vm::selectFilter)
                SearchResults(vm)
            }
        }
    }
}

@Composable
private fun FilterChips(selected: SearchFilter, onSelect: (SearchFilter) -> Unit) {
    Row(
        Modifier
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 10.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        SearchFilter.entries.forEach { f ->
            FilterChip(
                selected = selected == f,
                onClick = { onSelect(f) },
                label = { Text(f.label) },
                shape = RoundedCornerShape(50),
                colors = FilterChipDefaults.filterChipColors(
                    selectedContainerColor = MaterialTheme.colorScheme.primary,
                    selectedLabelColor = Ink,
                ),
            )
        }
    }
}

@Composable
private fun Suggestions(suggestions: List<String>, onPick: (String) -> Unit) {
    LazyColumn {
        items(suggestions) { s ->
            Row(
                Modifier
                    .fillMaxWidth()
                    .clickable { onPick(s) }
                    .padding(horizontal = 16.dp, vertical = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(Icons.Default.Search, null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(18.dp))
                Text(s, modifier = Modifier.padding(start = 16.dp))
            }
        }
    }
}

/** What the Search tab shows before you type: recent searches and genre tiles. */
@Composable
private fun SearchHome(onSearch: (String) -> Unit, onGenre: (String) -> Unit) {
    val library = SpitifyApp.library
    val data by library.data.collectAsStateWithLifecycle()

    LazyColumn(contentPadding = PaddingValues(bottom = 24.dp)) {
        if (data.searches.isNotEmpty()) {
            item { SectionTitle("Recent searches", action = "Clear", onAction = library::clearSearches) }
            items(data.searches, key = { "q-$it" }) { q ->
                Row(
                    Modifier
                        .fillMaxWidth()
                        .clickable { onSearch(q) }
                        .padding(start = 16.dp, top = 4.dp, bottom = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(AppIcons.History, null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(20.dp))
                    Text(q, modifier = Modifier
                        .weight(1f)
                        .padding(start = 16.dp))
                    IconButton(onClick = { library.removeSearch(q) }) {
                        Icon(Icons.Default.Close, "Remove", tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(18.dp))
                    }
                }
            }
        }
        item { SectionTitle("Browse all") }
        items(genres.chunked(2)) { pair ->
            Row(
                Modifier.padding(horizontal = 16.dp, vertical = 5.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                pair.forEach { (name, color) ->
                    Box(
                        Modifier
                            .weight(1f)
                            .height(92.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(Color(color))
                            .clickable { onGenre(name) }
                            .padding(12.dp),
                    ) {
                        Text(name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = Color.White)
                    }
                }
            }
        }
    }
}

@Composable
private fun SearchResults(vm: SearchViewModel) {
    val listState = rememberLazyListState()

    LaunchedEffect(listState, vm.filter, vm.query) {
        snapshotFlow { listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0 }
            .collect { last -> if (vm.filter != SearchFilter.All && last >= vm.items.size - 5) vm.loadMore() }
    }

    LazyColumn(state = listState, contentPadding = PaddingValues(bottom = 24.dp)) {
        val top = vm.top
        when {
            vm.error != null && top == null && vm.items.isEmpty() -> item { ErrorBox(vm.error ?: "") { vm.submit(remember = false) } }
            vm.filter == SearchFilter.All && top != null -> topResults(top, vm.query, onMore = vm::selectFilter)
            else -> {
                items(vm.items, key = ::itemKey) { item -> BrowseItemRow(item) }
                if (!vm.loading && vm.items.isEmpty() && vm.error == null) item { EmptyHint("No results") }
            }
        }
        if (vm.loading) item { LoadingBox() }
    }
}

@Composable
fun BrowseItemRow(item: BrowseItem) {
    val nav = LocalNav.current
    when (item) {
        // a single tap plays the song and lets autoplay build a radio around it
        is BrowseItem.SongItem -> SongRow(item.song, onClick = { PlayerConnection.startRadio(item.song) })
        is BrowseItem.ArtistItem -> BrowseRow(
            title = item.artist.name,
            subtitle = artistSubtitle(item.artist),
            thumb = item.artist.thumb,
            round = true,
            placeholder = Icons.Default.Person,
        ) { nav.open(Route.Artist(item.artist)) }
        is BrowseItem.AlbumItem -> BrowseRow(
            title = item.album.title,
            subtitle = listOf(if (item.album.isAlbum) "Album" else "Playlist", item.album.subtitle)
                .filter { it.isNotBlank() }.joinToString(" • "),
            thumb = item.album.thumb,
        ) { nav.open(Route.Album(item.album)) }
    }
}

/** The "All" tab: a top result, then a few of each kind. */
fun LazyListScope.topResults(top: YouTube.TopResults, query: String, onMore: ((SearchFilter) -> Unit)?) {
    val seeAll = if (onMore != null) "See all" else null
    // the best artist match wins top result if its name matches the query; otherwise the first song
    val topArtist = top.artists.firstOrNull()?.takeIf { it.name.equals(query.trim(), ignoreCase = true) }
    val topSong = top.songs.firstOrNull()

    if (topArtist != null || topSong != null) {
        item { SectionTitle("Top result") }
        item { TopResultCard(topArtist, if (topArtist == null) topSong else null) }
    }
    if (top.songs.isNotEmpty()) {
        item { SectionTitle("Songs", action = seeAll) { onMore?.invoke(SearchFilter.Songs) } }
        items(top.songs.take(5), key = { "ts-" + it.id }) { song ->
            SongRow(song, onClick = { PlayerConnection.startRadio(song) })
        }
    }
    if (top.artists.isNotEmpty()) {
        item { SectionTitle("Artists", action = seeAll) { onMore?.invoke(SearchFilter.Artists) } }
        item {
            val nav = LocalNav.current
            LazyRow(contentPadding = PaddingValues(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                items(top.artists.take(10), key = { it.url }) { a -> ArtistCircle(a, 120.dp) { nav.open(Route.Artist(a)) } }
            }
        }
    }
    if (top.albums.isNotEmpty()) {
        item { SectionTitle("Albums", action = seeAll) { onMore?.invoke(SearchFilter.Albums) } }
        item {
            val nav = LocalNav.current
            LazyRow(contentPadding = PaddingValues(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                items(top.albums.take(10), key = { it.url }) { a -> AlbumCard(a, 140.dp) { nav.open(Route.Album(a)) } }
            }
        }
    }
    if (top.playlists.isNotEmpty()) {
        item { SectionTitle("Playlists", action = seeAll) { onMore?.invoke(SearchFilter.Playlists) } }
        item {
            val nav = LocalNav.current
            LazyRow(contentPadding = PaddingValues(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                items(top.playlists.take(10), key = { it.url }) { a -> AlbumCard(a, 140.dp) { nav.open(Route.Album(a)) } }
            }
        }
    }
}

@Composable
private fun TopResultCard(artist: ArtistRef?, song: Song?) {
    val nav = LocalNav.current
    val title = artist?.name ?: song?.title ?: return
    val subtitle = if (artist != null) "Artist" else "Song • ${song?.artist}"
    val thumb = artist?.thumb ?: song?.thumb
    Row(
        Modifier
            .padding(horizontal = 16.dp)
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(MaterialTheme.colorScheme.surfaceContainerHigh)
            .clickable {
                if (artist != null) nav.open(Route.Artist(artist)) else song?.let(PlayerConnection::startRadio)
            }
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Artwork(
            thumb, 92.dp,
            corner = if (artist != null) 46.dp else 6.dp,
            placeholder = if (artist != null) Icons.Default.Person else AppIcons.MusicNote,
        )
        Column(Modifier.padding(start = 16.dp)) {
            Text(title, style = MaterialTheme.typography.titleLarge, maxLines = 2, overflow = TextOverflow.Ellipsis)
            Spacer(Modifier.height(4.dp))
            Text(subtitle, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

/** A genre tile's page: the "All" search for that genre. */
@Composable
fun GenreScreen(name: String, onBack: () -> Unit) {
    val vm: SearchViewModel = viewModel(key = "genre-$name")
    LaunchedEffect(name) { if (!vm.showingResults) vm.submit("$name songs", remember = false) }
    Column(Modifier.fillMaxSize()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") }
            Text(name, style = MaterialTheme.typography.headlineMedium)
        }
        LazyColumn(contentPadding = PaddingValues(bottom = 24.dp)) {
            val top = vm.top
            if (top != null) topResults(top, "", onMore = null) else if (vm.loading) item { LoadingBox() }
            vm.error?.let { e -> if (top == null) item { ErrorBox(e) { vm.submit("$name songs", remember = false) } } }
        }
    }
}
