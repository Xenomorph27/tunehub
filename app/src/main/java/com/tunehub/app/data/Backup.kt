package com.tunehub.app.data

import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.File
import java.io.IOException

/** The file format of a TuneHub backup: the library plus a little metadata. */
@Serializable
data class BackupFile(
    val app: String = "TuneHub",
    val backupVersion: Int = 1,
    val exportedAt: Long = 0,
    val library: LibraryData = LibraryData(),
)

/** Turns a library into backup JSON and back. Pure Kotlin, so it is unit-tested. */
object BackupCodec {
    private val json = Json { ignoreUnknownKeys = true; prettyPrint = true }

    /** Downloaded audio doesn't travel with a backup, so the download lists are left out. */
    fun encode(data: LibraryData, now: Long = System.currentTimeMillis()): String =
        json.encodeToString(
            BackupFile.serializer(),
            BackupFile(exportedAt = now, library = data.copy(downloads = emptyList(), pendingDownloads = emptyList())),
        )

    fun decode(text: String): LibraryData {
        val file = runCatching { json.decodeFromString(BackupFile.serializer(), text) }
            .getOrElse { throw IOException("This isn't a TuneHub backup file") }
        if (file.app != "TuneHub") throw IOException("This isn't a TuneHub backup file")
        return file.library
    }
}

/**
 * Combines a restored backup with what's already on the phone, without losing either:
 * songs, albums and artists are unioned; playlists are matched by id and the newer one wins;
 * settings come from the backup.
 */
fun mergeLibraries(current: LibraryData, incoming: LibraryData): LibraryData {
    fun <T, K> union(a: List<T>, b: List<T>, key: (T) -> K) = (a + b).distinctBy(key)
    val playlists = (current.playlists + incoming.playlists)
        .groupBy { it.id }
        .map { (_, versions) -> versions.maxBy { it.updatedAt } }
    return current.copy(
        liked = union(current.liked, incoming.liked) { it.id },
        playlists = playlists,
        albums = union(current.albums, incoming.albums) { it.url },
        artists = union(current.artists, incoming.artists) { it.url },
        recent = union(current.recent, incoming.recent) { it.id }.take(100),
        searches = union(current.searches, incoming.searches) { it.lowercase() }.take(15),
        autoplay = incoming.autoplay,
        dataSaver = incoming.dataSaver,
        autoLyrics = incoming.autoLyrics,
        autoBackup = incoming.autoBackup,
        eq = incoming.eq,
    )
}

fun LibraryData.isEmptyLibrary() =
    liked.isEmpty() && playlists.isEmpty() && albums.isEmpty() && artists.isEmpty() && recent.isEmpty()

/**
 * Keeps a copy of the library in Download/TuneHub/tunehub-backup.json, outside the app, so it
 * survives uninstalling. Also handles manual export and restore through the system file picker.
 */
@OptIn(FlowPreview::class)
class Backups(private val context: Context, private val library: Library) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val uriFile = File(context.filesDir, "backup_uri.txt")
    private val _lastBackupAt = MutableStateFlow(0L)

    /** When the automatic backup last succeeded (0 = not yet this session). */
    val lastBackupAt: StateFlow<Long> = _lastBackupAt

    /** Automatic backups need Android 10+ (writing to Downloads without a permission). */
    val autoSupported = Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q

    init {
        scope.launch {
            // skip the value loaded at startup; back up a few seconds after changes settle
            library.data.drop(1).debounce(3_000).collect { data ->
                // an empty library (fresh install) must never replace a real backup
                if (data.autoBackup && autoSupported && !data.isEmptyLibrary()) {
                    runCatching { writeToDownloads(BackupCodec.encode(data)) }.onSuccess {
                        _lastBackupAt.value = System.currentTimeMillis()
                    }
                }
            }
        }
    }

    fun exportTo(uri: Uri) {
        context.contentResolver.openOutputStream(uri, "wt")?.use {
            it.write(BackupCodec.encode(library.data.value).toByteArray())
        } ?: throw IOException("Couldn't open that file")
    }

    /** Reads a backup file and merges it into the library. Returns how many playlists it had. */
    fun restoreFrom(uri: Uri): Int {
        val text = context.contentResolver.openInputStream(uri)?.use { it.readBytes().decodeToString() }
            ?: throw IOException("Couldn't open that file")
        val incoming = BackupCodec.decode(text)
        library.restore(incoming)
        return incoming.playlists.size
    }

    private fun writeToDownloads(text: String) {
        val resolver = context.contentResolver
        // keep updating the same file; if it's gone (deleted, or made by an earlier install), make a new one
        val saved = runCatching { Uri.parse(uriFile.readText()) }.getOrNull()
        if (saved != null) {
            val ok = runCatching { resolver.openOutputStream(saved, "wt")?.use { it.write(text.toByteArray()) } != null }
                .getOrDefault(false)
            if (ok) return
        }
        val values = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, "tunehub-backup.json")
            put(MediaStore.MediaColumns.MIME_TYPE, "application/json")
            put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS + "/TuneHub")
        }
        val uri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values) ?: throw IOException("Can't create backup")
        resolver.openOutputStream(uri, "wt")?.use { it.write(text.toByteArray()) } ?: throw IOException("Can't write backup")
        uriFile.writeText(uri.toString())
    }
}
