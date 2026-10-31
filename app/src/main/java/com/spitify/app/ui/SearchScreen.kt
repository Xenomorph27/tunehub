package com.spitify.app.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.spitify.app.data.Song
import com.spitify.app.data.YouTube
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class SearchViewModel : ViewModel() {
    var query by mutableStateOf("")
        private set
    var filter by mutableStateOf(YouTube.FILTER_SONGS)
        private set
    var suggestions by mutableStateOf<List<String>>(emptyList())
        private set
    var results by mutableStateOf<List<Song>>(emptyList())
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

    fun submit(q: String = query) {
        if (q.isBlank()) return
        query = q
        showingResults = true
        suggestJob?.cancel()
        suggestions = emptyList()
        results = emptyList()
        error = null
        search = null
        searchJob?.cancel()
        searchJob = viewModelScope.launch {
            loading = true
            try {
                val s = withContext(Dispatchers.IO) { YouTube.Search(q, filter) }
                search = s
                results = withContext(Dispatchers.IO) { s.loadNext() }.distinctBy { it.id }
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
                val seen = results.map { it.id }.toSet()
                results = results + more.filter { it.id !in seen }.distinctBy { it.id }
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                // keep what we have; scrolling again retries
            } finally {
                if (isActive) loading = false
            }
        }
    }

    fun selectFilter(f: String) {
        if (f == filter) return
        filter = f
        if (showingResults) submit()
    }
}

@Composable
fun SearchScreen(vm: SearchViewModel) {
    val focus = LocalFocusManager.current
    val listState = rememberLazyListState()

    LaunchedEffect(listState, vm.showingResults) {
        snapshotFlow { listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0 }
            .collect { last -> if (vm.showingResults && last >= vm.results.size - 5) vm.loadMore() }
    }

    Column(Modifier.fillMaxSize()) {
        Text(
            "Search",
            style = MaterialTheme.typography.headlineMedium,
            modifier = Modifier.padding(start = 16.dp, top = 16.dp, bottom = 12.dp),
        )
        TextField(
            value = vm.query,
            onValueChange = vm::onQueryChange,
            placeholder = { Text("Songs, artists, anything") },
            leadingIcon = { Icon(Icons.Default.Search, null) },
            trailingIcon = {
                if (vm.query.isNotEmpty()) {
                    IconButton(onClick = { vm.onQueryChange("") }) { Icon(Icons.Default.Clear, "Clear") }
                }
            },
            singleLine = true,
            shape = RoundedCornerShape(10.dp),
            colors = TextFieldDefaults.colors(
                focusedIndicatorColor = Color.Transparent,
                unfocusedIndicatorColor = Color.Transparent,
                focusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
            ),
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
            keyboardActions = KeyboardActions(onSearch = { focus.clearFocus(); vm.submit() }),
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
        )
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
        ) {
            FilterChip(
                selected = vm.filter == YouTube.FILTER_SONGS,
                onClick = { vm.selectFilter(YouTube.FILTER_SONGS) },
                label = { Text("Songs") },
            )
            FilterChip(
                selected = vm.filter == YouTube.FILTER_VIDEOS,
                onClick = { vm.selectFilter(YouTube.FILTER_VIDEOS) },
                label = { Text("Videos") },
            )
        }

        when {
            !vm.showingResults && vm.suggestions.isNotEmpty() -> LazyColumn {
                items(vm.suggestions) { s ->
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .clickable { focus.clearFocus(); vm.submit(s) }
                            .padding(horizontal = 16.dp, vertical = 14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(
                            Icons.Default.Search, null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(18.dp),
                        )
                        Text(s, modifier = Modifier.padding(start = 16.dp))
                    }
                }
            }

            !vm.showingResults -> EmptyHint("Find any song on YouTube Music. Tap one to start a radio from it.")

            vm.error != null && vm.results.isEmpty() -> EmptyHint("Search failed: ${vm.error}")

            else -> LazyColumn(state = listState) {
                // single tap = play it and let autoplay build a radio around it
                items(vm.results, key = { it.id }) { song ->
                    SongRow(song, onClick = { PlayerConnection.play(listOf(song)) })
                }
                if (vm.loading) {
                    item {
                        Box(Modifier.fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center) {
                            CircularProgressIndicator(Modifier.size(28.dp))
                        }
                    }
                } else if (vm.results.isEmpty()) {
                    item { EmptyHint("No results") }
                }
            }
        }
    }
}
