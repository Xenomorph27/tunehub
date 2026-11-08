package com.tunehub.app.ui

import android.annotation.SuppressLint
import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.Icon
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.tunehub.app.BuildConfig
import com.tunehub.app.TuneHubApp
import com.tunehub.app.data.ReleaseCheck
import com.tunehub.app.data.UPSTREAM_RELEASES_URL
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

// Compose lint misses the downloads size producer's value assignment.
@SuppressLint("ProduceStateDoesNotAssignValue")
@Composable
fun SettingsScreen(onBack: () -> Unit) {
    val library = TuneHubApp.library
    val data by library.data.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val nav = LocalNav.current
    val scope = rememberCoroutineScope()
    var checkingVersion by remember { mutableStateOf(false) }
    var confirm by remember { mutableStateOf<ConfirmRequest?>(null) }
    HistoryDialog(confirm) { confirm = null }
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
        SettingsHeading("Settings", onBack)

        SettingsGroup("Playback") {
            ToggleRow("Autoplay", "Keep playing similar songs when your queue ends", data.autoplay, library::setAutoplay)
            ToggleRow("Hi-Res audio", "Stream the best quality YouTube offers. Off saves mobile data", !data.dataSaver) { PlayerConnection.setHiRes(it) }
            ToggleRow(
                "Look up lyrics automatically",
                "Sends the song title and artist to lrclib.net when Now Playing opens. Off: only when you tap Lyrics",
                data.autoLyrics,
                library::setAutoLyrics,
            )
            ActionRow("Equalizer & UltraTune", "Bass, treble, vocals, 3D sound and loudness") { nav.open(Route.Equalizer) }
        }

        val lastBackup by TuneHubApp.backups.lastBackupAt.collectAsStateWithLifecycle()
        val exportBackup = rememberExportAction()
        val restoreBackup = rememberRestoreAction()
        SettingsGroup("Backup & restore") {
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
        }

        SettingsGroup("Storage") {
            ActionRow("Downloads", "${data.downloads.size} songs • $downloadsMb MB") { nav.open(Route.Downloads) }
            ActionRow("Streaming cache", "Up to 512 MB of recently played audio, cleared automatically")
            ActionRow("Clear listening history", "${data.recent.size} songs in Recently played") {
                confirm = ConfirmRequest("Clear listening history?", "Recently played and Quick picks will start fresh.", "Clear history") {
                    library.clearRecent()
                }
            }
            ActionRow("Clear search history", "${data.searches.size} recent searches") {
                confirm = ConfirmRequest("Clear search history?", "Your recent searches will be removed.", "Clear history") {
                    library.clearSearches()
                }
            }
        }

        SettingsGroup("About") {
            ActionRow(
                title = "Version",
                subtitle = if (checkingVersion) "Checking GitHub…" else "Check for a newer release",
                readout = version,
                enabled = !checkingVersion,
            ) {
                if (!checkingVersion) {
                    checkingVersion = true
                    scope.launch {
                        try {
                            val upstreamBuild = if (BuildConfig.APPLICATION_ID == "com.spitify.app") {
                                BuildConfig.VERSION_CODE
                            } else {
                                BuildConfig.UPSTREAM_BUILD_NUMBER
                            }
                            val newer = withContext(Dispatchers.IO) { ReleaseCheck.hasNewerBuild(TuneHubApp.http, upstreamBuild) }
                            if (newer) openReleases(context, UPSTREAM_RELEASES_URL)
                            else Toast.makeText(context, "Already on the latest version", Toast.LENGTH_SHORT).show()
                        } catch (e: CancellationException) {
                            throw e
                        } catch (_: Exception) {
                            Toast.makeText(context, "Couldn't check for updates. Try again.", Toast.LENGTH_SHORT).show()
                        } finally {
                            checkingVersion = false
                        }
                    }
                }
            }
            val releasesUrl = BuildConfig.RELEASES_URL
            if (releasesUrl.isNotBlank() && releasesUrl != UPSTREAM_RELEASES_URL && releasesUrl != "$UPSTREAM_RELEASES_URL/latest") {
                ActionRow("Project releases", "Opens the GitHub releases page") {
                    openReleases(context, releasesUrl)
                }
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

private fun openReleases(context: android.content.Context, url: String) {
    try {
        context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
    } catch (_: ActivityNotFoundException) {
        Toast.makeText(context, "No browser found. Visit $url", Toast.LENGTH_LONG).show()
    }
}

@Composable
private fun SettingsGroup(text: String, content: @Composable ColumnScope.() -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(start = 20.dp, end = 20.dp, top = 22.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(text, style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(end = 10.dp))
        HorizontalDivider(Modifier.weight(1f), color = tuneColors.rule)
    }
    Column(
        Modifier.padding(horizontal = 20.dp).fillMaxWidth().panel(RoundedCornerShape(14.dp)).padding(vertical = 4.dp),
        content = content,
    )
}

@Composable
private fun ToggleRow(title: String, subtitle: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .toggleable(value = checked, role = Role.Switch, onValueChange = onChange)
            .padding(horizontal = 14.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f).padding(end = 12.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Switch(
            checked = checked,
            onCheckedChange = null,
            colors = SwitchDefaults.colors(checkedTrackColor = tuneColors.accent, checkedThumbColor = tuneColors.onAccent),
        )
    }
}

@Composable
private fun ActionRow(
    title: String,
    subtitle: String,
    readout: String? = null,
    enabled: Boolean = true,
    onClick: (() -> Unit)? = null,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .then(if (onClick != null) Modifier.clickable(enabled = enabled, role = Role.Button, onClick = onClick) else Modifier)
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = tuneColors.muted)
        }
        if (readout != null) ConsoleReadout(readout, Modifier.padding(start = 8.dp))
        if (onClick != null) {
            Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, null, tint = tuneColors.muted)
        }
    }
}

