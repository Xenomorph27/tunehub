package com.tunehub.app.ui

import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.platform.LocalContext
import com.tunehub.app.TuneHubApp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Opens the file picker for a TuneHub backup and merges it into the library. */
@Composable
fun rememberRestoreAction(): () -> Unit {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri ?: return@rememberLauncherForActivityResult
        scope.launch {
            val result = withContext(Dispatchers.IO) { runCatching { TuneHubApp.backups.restoreFrom(uri) } }
            val message = result.fold(
                { playlists -> "Library restored ($playlists playlists)" },
                { e -> e.message ?: "Couldn't restore that file" },
            )
            Toast.makeText(context, message, Toast.LENGTH_LONG).show()
        }
    }
    // backups are .json; some file managers label them oddly, so allow any file and validate it
    return { launcher.launch(arrayOf("application/json", "application/octet-stream", "text/*", "*/*")) }
}

/** Opens a "save as" picker and writes a backup of the library there. */
@Composable
fun rememberExportAction(): () -> Unit {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        uri ?: return@rememberLauncherForActivityResult
        scope.launch {
            val result = withContext(Dispatchers.IO) { runCatching { TuneHubApp.backups.exportTo(uri) } }
            Toast.makeText(
                context,
                if (result.isSuccess) "Backup saved" else "Couldn't save the backup",
                Toast.LENGTH_SHORT,
            ).show()
        }
    }
    return {
        val date = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date())
        launcher.launch("tunehub-backup-$date.json")
    }
}
