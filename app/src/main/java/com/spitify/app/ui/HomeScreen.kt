package com.spitify.app.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.spitify.app.SpitifyApp
import com.spitify.app.data.Song
import com.spitify.app.data.StreamResolver
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Calendar

class HomeViewModel : ViewModel() {
    var quickPicks by mutableStateOf<List<Song>>(emptyList())
        private set
    private var seedId: String? = null

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

@Composable
fun HomeScreen(vm: HomeViewModel = viewModel()) {
    val data by SpitifyApp.library.data.collectAsStateWithLifecycle()
    val recent = data.recent

    LaunchedEffect(recent.firstOrNull()?.id) { recent.firstOrNull()?.let(vm::loadFor) }

    LazyColumn(Modifier.fillMaxSize()) {
        item {
            Text(
                greeting(),
                style = MaterialTheme.typography.headlineMedium,
                modifier = Modifier.padding(start = 16.dp, top = 16.dp, bottom = 4.dp),
            )
        }

        if (recent.isEmpty()) {
            item { EmptyHint("Nothing played yet. Head to Search and pick a song — no ads, ever.") }
            return@LazyColumn
        }

        item { SectionTitle("Recently played") }
        item {
            LazyRow(
                contentPadding = PaddingValues(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                itemsIndexed(recent.take(15), key = { _, s -> s.id }) { i, song ->
                    Column(
                        Modifier
                            .width(136.dp)
                            .clickable { PlayerConnection.play(recent, i) },
                    ) {
                        Artwork(song.thumb, 136.dp, corner = 8.dp)
                        Text(
                            song.title,
                            style = MaterialTheme.typography.bodyMedium,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.padding(top = 8.dp),
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
            }
        }

        if (vm.quickPicks.isNotEmpty()) {
            item { SectionTitle("Quick picks") }
            items(vm.quickPicks, key = { "qp-" + it.id }) { song ->
                SongRow(song, onClick = { PlayerConnection.play(listOf(song)) })
            }
        }
    }
}

private fun greeting(): String = when (Calendar.getInstance().get(Calendar.HOUR_OF_DAY)) {
    in 5..11 -> "Good morning"
    in 12..16 -> "Good afternoon"
    else -> "Good evening"
}
