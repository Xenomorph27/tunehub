package com.tunehub.app.ui

import android.content.ActivityNotFoundException
import android.content.Intent
import android.media.audiofx.AudioEffect
import android.net.Uri
import android.widget.Toast
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.tunehub.app.TuneHubApp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import com.tunehub.app.playback.PlaybackService

@Composable
fun SettingsScreen(onBack: () -> Unit) {
    val library = TuneHubApp.library
    val data by library.data.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val nav = LocalNav.current
    var confirm by remember { mutableStateOf<ConfirmRequest?>(null) }
    ConfirmDialog(confirm) { confirm = null }
    // measuring files is disk work: do it off the main thread
    val downloadsMb by produceState(0L, data.downloads.size) {
        value = withContext(Dispatchers.IO) { TuneHubApp.downloads.usedBytes() / (1024 * 1024) }
    }
    val version = remember {
        runCatching { context.packageManager.getPackageInfo(context.packageName, 0).versionName }.getOrNull() ?: "?"
    }

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState()),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(start = 12.dp, top = 12.dp)) {
            GlassIconButton(Icons.AutoMirrored.Filled.ArrowBack, "Back", onClick = onBack)
            Text("Settings", style = MaterialTheme.typography.headlineMedium, modifier = Modifier.padding(start = 14.dp))
        }

        Header("Playback")
        ToggleRow("Autoplay", "Keep playing similar songs when your queue ends", data.autoplay, library::setAutoplay)
        ToggleRow("Hi-Res audio", "Stream the best quality YouTube offers. Off saves mobile data", !data.dataSaver) { PlayerConnection.setHiRes(it) }
        ToggleRow(
            "Look up lyrics automatically",
            "Sends the song title and artist to lrclib.net when Now Playing opens. Off: only when you tap Lyrics",
            data.autoLyrics,
            library::setAutoLyrics,
        )
        ActionRow("Equalizer & UltraTune", "Bass, treble, vocals, 3D sound and loudness") { nav.open(Route.Equalizer) }

        Header("Backup & restore")
        val lastBackup by TuneHubApp.backups.lastBackupAt.collectAsStateWithLifecycle()
        val exportBackup = rememberExportAction()
        val restoreBackup = rememberRestoreAction()
        ToggleRow(
            "Back up automatically",
            if (!TuneHubApp.backups.autoSupported) "Needs Android 10 or newer. Use Export below instead"
            else "Keeps a copy in Download/TuneHub that survives uninstalling" +
                if (lastBackup > 0) " · last saved just now" else "",
            data.autoBackup && TuneHubApp.backups.autoSupported,
            library::setAutoBackup,
        )
        ActionRow("Export backup", "Save your playlists, likes and settings to a file you choose") { exportBackup() }
        ActionRow("Restore from backup", "Pick a TuneHub backup file; it's merged with what's here") { restoreBackup() }
        ActionRow("Import a playlist", "From Spotify, YouTube Music, or a CSV / text file") { nav.open(Route.Import) }

        Header("Storage")
        ActionRow("Downloads", "${data.downloads.size} songs • $downloadsMb MB") {}
        ActionRow("Streaming cache", "Up to 512 MB of recently played audio, cleared automatically") {}
        ActionRow("Clear listening history", "${data.recent.size} songs in Recently played") {
            confirm = ConfirmRequest("Clear listening history?", "Recently played and Quick picks will start fresh.", "Clear") {
                library.clearRecent()
            }
        }
        ActionRow("Clear search history", "${data.searches.size} recent searches") {
            confirm = ConfirmRequest("Clear search history?", "Your recent searches will be removed.", "Clear") {
                library.clearSearches()
            }
        }

        Header("About")
        ActionRow("Version", version) {}
        ActionRow("Get the latest build", "Opens the GitHub releases page") {
            try {
                context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(RELEASES_URL)))
            } catch (_: ActivityNotFoundException) {
                Toast.makeText(context, "No browser found. Visit $RELEASES_URL", Toast.LENGTH_LONG).show()
            }
        }
        Text(
            "TuneHub plays music from YouTube Music. Lyrics come from LRCLIB. Your library stays on this " +
                "phone (Android may include it in your device backup; downloaded audio is excluded). Personal use only.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(16.dp),
        )
    }
}

@Composable
private fun Header(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.titleMedium,
        color = Aurora.Lilac,
        modifier = Modifier.padding(start = 20.dp, top = 26.dp, bottom = 6.dp),
    )
}

@Composable
private fun ToggleRow(title: String, subtitle: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clickable { onChange(!checked) }
            .padding(horizontal = 20.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Switch(checked = checked, onCheckedChange = onChange)
    }
}

@Composable
private fun ActionRow(title: String, subtitle: String, onClick: () -> Unit) {
    Column(
        Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 20.dp, vertical = 12.dp),
    ) {
        Text(title, style = MaterialTheme.typography.bodyLarge)
        Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

private const val RELEASES_URL = "https://github.com/Xenomorph27/tunehub/releases/latest"
