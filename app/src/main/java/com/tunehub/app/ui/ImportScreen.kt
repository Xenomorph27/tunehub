package com.tunehub.app.ui

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.tunehub.app.TuneHubApp
import com.tunehub.app.data.PlaylistImport
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class ImportViewModel : ViewModel() {
    sealed interface Step {
        data object Input : Step
        data class Working(val label: String, val done: Int = 0, val total: Int = 0) : Step
        data object Review : Step
    }

    var step by mutableStateOf<Step>(Step.Input)
        private set
    var error by mutableStateOf<String?>(null)
        private set
    var name by mutableStateOf("")
    val matches = mutableStateListOf<PlaylistImport.Match>()
    val selected = mutableStateListOf<Boolean>()

    fun importLink(link: String) = run("Reading the playlist…") {
        PlaylistImport.fromLink(TuneHubApp.http, link)
    }

    fun importText(text: String, fileName: String) = run("Reading the file…") {
        PlaylistImport.fromText(text, fileName.substringBeforeLast('.').ifBlank { "Imported playlist" })
    }

    private fun run(label: String, read: suspend () -> PlaylistImport.Source) {
        error = null
        step = Step.Working(label)
        viewModelScope.launch {
            try {
                val source = read()
                name = source.name
                val found = source.alreadyMatched?.let { songs ->
                    songs.map { PlaylistImport.Match(PlaylistImport.SourceTrack(it.title, it.artist), it, confident = true) }
                } ?: PlaylistImport.match(source.tracks) { done, total ->
                    step = Step.Working("Finding songs on YouTube Music…", done, total)
                }
                matches.clear()
                matches += found
                selected.clear()
                selected += found.map { it.song != null }
                step = Step.Review
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                error = e.message ?: "Import failed"
                step = Step.Input
            }
        }
    }

    /** Creates the playlist from the ticked matches; returns its id. */
    fun save(): String? {
        val songs = matches.indices.filter { selected[it] }.mapNotNull { matches[it].song }
        if (songs.isEmpty()) return null
        return TuneHubApp.library.importPlaylist(name.ifBlank { "Imported playlist" }, songs)
    }

    fun reset() {
        step = Step.Input
        matches.clear()
        selected.clear()
    }
}

@Composable
fun ImportScreen(onBack: () -> Unit) {
    val vm: ImportViewModel = viewModel(key = "import")
    val nav = LocalNav.current
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val clipboard = LocalClipboardManager.current
    var link by remember { mutableStateOf("") }

    val pickFile = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri ?: return@rememberLauncherForActivityResult
        scope.launch {
            val text = withContext(Dispatchers.IO) {
                runCatching { context.contentResolver.openInputStream(uri)?.use { it.readBytes().decodeToString() } }.getOrNull()
            }
            if (text == null) Toast.makeText(context, "Couldn't open that file", Toast.LENGTH_SHORT).show()
            else vm.importText(text, displayName(context, uri))
        }
    }

    Column(Modifier.fillMaxSize()) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(start = 12.dp, top = 12.dp)) {
            GlassIconButton(Icons.AutoMirrored.Filled.ArrowBack, "Back", onClick = onBack)
            Text("Import playlist", style = MaterialTheme.typography.headlineMedium, modifier = Modifier.padding(start = 14.dp))
        }

        when (val step = vm.step) {
            ImportViewModel.Step.Input -> Column(
                Modifier.padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                Text(
                    "Paste a Spotify or YouTube Music playlist link. TuneHub finds every song on YouTube Music " +
                        "and makes it your own playlist.",
                    style = MaterialTheme.typography.bodyLarge,
                    color = Aurora.Muted,
                )
                OutlinedTextField(
                    value = link,
                    onValueChange = { link = it },
                    placeholder = { Text("https://open.spotify.com/playlist/…") },
                    singleLine = true,
                    shape = RoundedCornerShape(18.dp),
                    modifier = Modifier.fillMaxWidth(),
                )
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    GlassChip("Paste") { clipboard.getText()?.text?.let { link = it } }
                    GlassChip("Import link", selected = true) { if (link.isNotBlank()) vm.importLink(link) }
                }
                vm.error?.let { Text(it, color = Aurora.Pink, style = MaterialTheme.typography.bodyMedium) }

                SectionTitle("From other apps", Modifier.padding(start = 0.dp))
                Text(
                    "Amazon Music, Apple Music and others don't share playlists publicly. Export the playlist as a " +
                        "CSV or text file (free tools like TuneMyMusic or Soundiiz do this), then pick the file here. " +
                        "A plain list with one \"Song - Artist\" per line works too.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = Aurora.Muted,
                )
                GlassChip("Choose a CSV or text file") { pickFile.launch(arrayOf("text/*", "application/csv", "application/octet-stream")) }
            }

            is ImportViewModel.Step.Working -> Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                Text(step.label, style = MaterialTheme.typography.titleMedium)
                if (step.total > 0) {
                    LinearProgressIndicator(
                        progress = { step.done.toFloat() / step.total },
                        color = Aurora.Cyan,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Text("${step.done} of ${step.total}", color = Aurora.Muted)
                } else {
                    LoadingBox()
                }
            }

            ImportViewModel.Step.Review -> ReviewList(vm) {
                val id = vm.save()
                if (id == null) {
                    Toast.makeText(context, "Tick at least one song", Toast.LENGTH_SHORT).show()
                } else {
                    vm.reset()
                    onBack()
                    nav.open(Route.LocalPlaylist(id))
                }
            }
        }
    }
}

@Composable
private fun ReviewList(vm: ImportViewModel, onSave: () -> Unit) {
    val found = vm.matches.count { it.song != null }
    val unsure = vm.matches.count { it.song != null && !it.confident }
    LazyColumn(contentPadding = PaddingValues(bottom = 24.dp)) {
        item {
            Column(Modifier.padding(horizontal = 20.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(
                    value = vm.name,
                    onValueChange = { vm.name = it },
                    label = { Text("Playlist name") },
                    singleLine = true,
                    shape = RoundedCornerShape(18.dp),
                    modifier = Modifier.fillMaxWidth(),
                )
                Text(
                    "Found $found of ${vm.matches.size} songs" + if (unsure > 0) " · $unsure marked \"check\" may be a different version" else "",
                    style = MaterialTheme.typography.bodyMedium,
                    color = Aurora.Muted,
                )
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    GlassChip("Save playlist", selected = true, onClick = onSave)
                    GlassChip("Start over") { vm.reset() }
                }
            }
        }
        itemsIndexed(vm.matches) { i, match ->
            val song = match.song
            Row(
                Modifier
                    .fillMaxWidth()
                    .clickable(enabled = song != null) { vm.selected[i] = !vm.selected[i] }
                    .padding(horizontal = 12.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Checkbox(
                    checked = vm.selected.getOrElse(i) { false },
                    onCheckedChange = { vm.selected[i] = it },
                    enabled = song != null,
                    colors = CheckboxDefaults.colors(checkedColor = Aurora.Cyan, checkmarkColor = Aurora.Ink),
                )
                Box(Modifier.padding(end = 10.dp)) { Artwork(song?.thumb, 46.dp) }
                Column(Modifier.weight(1f)) {
                    Text(
                        song?.title ?: match.source.title,
                        style = MaterialTheme.typography.titleSmall,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        color = if (song == null) Aurora.Muted else Aurora.Text,
                    )
                    Text(
                        when {
                            song == null -> "Not found · ${match.source.artist}"
                            match.confident -> song.artist
                            else -> "Check: ${song.artist} (wanted ${match.source.title} – ${match.source.artist})"
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = if (song != null && !match.confident) Aurora.Amber else Aurora.Muted,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
        item { CreditFooter() }
    }
}

private fun displayName(context: Context, uri: Uri): String = runCatching {
    context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { c ->
        if (c.moveToFirst()) c.getString(0) else null
    }
}.getOrNull() ?: "Imported playlist"
