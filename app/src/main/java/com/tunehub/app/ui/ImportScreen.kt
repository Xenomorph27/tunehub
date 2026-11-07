package com.tunehub.app.ui

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.HorizontalDivider
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
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.semantics
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
    val focus = LocalFocusManager.current
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
        SettingsHeading("Import playlist", onBack)

        when (val step = vm.step) {
            ImportViewModel.Step.Input -> Column(
                Modifier.verticalScroll(rememberScrollState()).padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                ImportPanel("From a playlist link") {
                    Text("Paste a Spotify or YouTube Music playlist link to find matches.", style = MaterialTheme.typography.bodyMedium, color = tuneColors.muted)
                    OutlinedTextField(
                        value = link,
                        onValueChange = { link = it },
                        label = { Text("Playlist URL") },
                        placeholder = { Text("https://…") },
                        singleLine = true,
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        ConsoleKey("Paste", Modifier.weight(1f)) { clipboard.getText()?.text?.let { link = it } }
                        ConsoleKey("Import link", Modifier.weight(1.5f), primary = true, enabled = link.isNotBlank()) {
                            focus.clearFocus()
                            vm.importLink(link.trim())
                        }
                    }
                }
                vm.error?.let { Text(it, color = tuneColors.danger, style = MaterialTheme.typography.bodyMedium) }

                ImportPanel("From a file") {
                    Text(
                        "Choose a CSV exported from another music app, or a text file with one “Song - Artist” per line.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = tuneColors.muted,
                    )
                    ConsoleKey("Choose a CSV or text file", Modifier.fillMaxWidth()) {
                        pickFile.launch(arrayOf("text/*", "application/csv", "application/octet-stream"))
                    }
                }
            }

            is ImportViewModel.Step.Working -> Column(Modifier.padding(20.dp)) {
                ImportPanel(step.label) {
                    if (step.total > 0) {
                        val progress = (step.done.toFloat() / step.total).coerceIn(0f, 1f)
                        ConsoleMeter(progress, Modifier.fillMaxWidth().semantics { progressBarRangeInfo = ProgressBarRangeInfo(progress, 0f..1f) })
                        ConsoleReadout("${step.done} / ${step.total}", Modifier.align(Alignment.End))
                    } else {
                        LoadingBox()
                    }
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
private fun ImportPanel(title: String, content: @Composable ColumnScope.() -> Unit) {
    Column(
        Modifier.fillMaxWidth().panel(RoundedCornerShape(16.dp)).padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Text(title, style = MaterialTheme.typography.titleLarge)
        content()
    }
}

@Composable
private fun ReviewList(vm: ImportViewModel, onSave: () -> Unit) {
    val found = vm.matches.count { it.song != null }
    val unsure = vm.matches.count { it.song != null && !it.confident }
    val selected = vm.selected.count { it }
    Column(Modifier.fillMaxSize()) {
        LazyColumn(Modifier.weight(1f), contentPadding = PaddingValues(horizontal = 20.dp, vertical = 12.dp)) {
            item {
                ImportPanel("$found of ${vm.matches.size} songs found") {
                    OutlinedTextField(
                        value = vm.name,
                        onValueChange = { vm.name = it },
                        label = { Text("Playlist name") },
                        singleLine = true,
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.fillMaxWidth(),
                    )
                    if (unsure > 0) {
                        Text("$unsure marked “check” may be a different version.", style = MaterialTheme.typography.bodyMedium, color = tuneColors.muted)
                    }
                }
            }
            itemsIndexed(vm.matches) { i, match ->
                val song = match.song
                Row(
                    Modifier
                        .fillMaxWidth()
                        .toggleable(value = vm.selected.getOrElse(i) { false }, enabled = song != null, role = Role.Checkbox) { vm.selected[i] = it }
                        .padding(vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Artwork(song?.thumb, 46.dp)
                    Column(Modifier.weight(1f)) {
                        Text(
                            song?.title ?: match.source.title,
                            style = MaterialTheme.typography.titleSmall,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            color = if (song == null) tuneColors.muted else tuneColors.text,
                        )
                        Text(
                            when {
                                song == null -> "Not found · ${match.source.artist}"
                                match.confident -> song.artist
                                else -> "Check: ${song.artist} (wanted ${match.source.title} – ${match.source.artist})"
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = if (song != null && !match.confident) tuneColors.accentText else tuneColors.muted,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                    Checkbox(
                        checked = vm.selected.getOrElse(i) { false },
                        onCheckedChange = null,
                        enabled = song != null,
                        colors = CheckboxDefaults.colors(checkedColor = tuneColors.accent, checkmarkColor = tuneColors.onAccent),
                    )
                }
            }
        }
        HorizontalDivider(Modifier.padding(horizontal = 20.dp), color = tuneColors.rule)
        Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            ConsoleKey("Start over", Modifier.weight(1f)) { vm.reset() }
            ConsoleKey("Save playlist ($selected)", Modifier.weight(1.6f), primary = true, enabled = selected > 0, onClick = onSave)
        }
    }
}

private fun displayName(context: Context, uri: Uri): String = runCatching {
    context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { c ->
        if (c.moveToFirst()) c.getString(0) else null
    }
}.getOrNull() ?: "Imported playlist"
