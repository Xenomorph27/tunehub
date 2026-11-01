package com.tunehub.app.data

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.updateAndGet
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.util.UUID

/** Sound settings: equalizer band levels in millibels, effect strengths 0..1000, loudness gain in millibels. */
@Serializable
data class EqSettings(
    val enabled: Boolean = false,
    val preset: String = "Flat",
    val bands: List<Int> = emptyList(),
    val bass: Int = 0,
    val virtualizer: Int = 0,
    val loudness: Int = 0,
    val ultra: Boolean = false,
)

/**
 * Everything saved on the device. Every field has a default, so older files load into newer
 * versions; bump [version] and migrate in [LibraryStore.load] when a field changes meaning.
 */
@Serializable
data class LibraryData(
    val version: Int = CURRENT_VERSION,
    val liked: List<Song> = emptyList(),
    val playlists: List<Playlist> = emptyList(),
    val albums: List<AlbumRef> = emptyList(),
    val artists: List<ArtistRef> = emptyList(),
    val downloads: List<Song> = emptyList(),
    /** Downloads requested but not finished; resumed on the next start if the app was killed. */
    val pendingDownloads: List<Song> = emptyList(),
    val recent: List<Song> = emptyList(),
    val searches: List<String> = emptyList(),
    val autoplay: Boolean = true,
    val dataSaver: Boolean = false,
    /** Look lyrics up on LRCLIB automatically (sends title and artist to lrclib.net). */
    val autoLyrics: Boolean = true,
    /** Keep a copy of the library in Download/TuneHub so it survives uninstalling. */
    val autoBackup: Boolean = true,
    val eq: EqSettings = EqSettings(),
) {
    companion object {
        const val CURRENT_VERSION = 2
    }
}

/** How the library file was found at startup. */
sealed interface LoadResult {
    /** No file yet: first run. */
    data object Missing : LoadResult

    /** Read fine. [fromBackup] when the main file was damaged and the last good copy was used. */
    data class Loaded(val data: LibraryData, val fromBackup: Boolean) : LoadResult

    /** Unreadable, with no usable backup. The damaged file was kept as [quarantined]. */
    data class Failed(val quarantined: File?, val reason: String) : LoadResult
}

/**
 * Reads and writes `library.json` safely: a damaged file is never treated as an empty library,
 * it is copied aside and the last successfully loaded copy (`library.json.bak`) is used instead.
 * Writes go to a temp file that is synced to disk before it replaces the real one.
 */
class LibraryStore(private val dir: File) {
    private val file = File(dir, "library.json")
    private val backup = File(dir, "library.json.bak")
    private val json = Json { ignoreUnknownKeys = true }

    fun load(): LoadResult {
        if (!file.exists()) {
            // a crash between deleting and renaming could leave only the backup
            return parse(backup)?.let { LoadResult.Loaded(it, fromBackup = true) } ?: LoadResult.Missing
        }
        val main = parse(file)
        if (main != null) {
            // remember this as the last known good copy
            runCatching { file.copyTo(backup, overwrite = true) }
            return LoadResult.Loaded(main, fromBackup = false)
        }
        val quarantined = runCatching {
            File(dir, "library.json.corrupt-${System.currentTimeMillis()}").also { file.copyTo(it) }
        }.getOrNull()
        val fromBackup = parse(backup)
        return if (fromBackup != null) LoadResult.Loaded(fromBackup, fromBackup = true)
        else LoadResult.Failed(quarantined, "library.json could not be read")
    }

    /** Writes [data]; throws on failure so the caller can retry and report. */
    fun save(data: LibraryData) {
        val tmp = File(dir, "library.json.tmp")
        FileOutputStream(tmp).use { out ->
            out.write(json.encodeToString(LibraryData.serializer(), data).toByteArray())
            out.fd.sync()
        }
        if (!tmp.renameTo(file)) {
            // some filesystems won't rename over an existing file
            file.delete()
            if (!tmp.renameTo(file)) throw IOException("Couldn't replace library.json")
        }
    }

    private fun parse(f: File): LibraryData? = runCatching {
        if (!f.exists()) return null
        migrate(json.decodeFromString<LibraryData>(f.readText()))
    }.getOrNull()

    // version 1 files had no version field (decoded as the default); nothing to convert yet
    private fun migrate(data: LibraryData): LibraryData =
        if (data.version < LibraryData.CURRENT_VERSION) data.copy(version = LibraryData.CURRENT_VERSION) else data
}

/** Everything the user saves. State changes are immediate; disk writes are batched in the background. */
class Library(dir: File) {
    private val store = LibraryStore(dir)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val saveSignals = Channel<Unit>(Channel.CONFLATED)

    val loadResult: LoadResult = store.load()

    /** False after a failed load: then nothing may be deleted on the library's word (e.g. downloads). */
    val loadedCleanly: Boolean = loadResult !is LoadResult.Failed

    private val _data = MutableStateFlow(
        when (loadResult) {
            is LoadResult.Loaded -> loadResult.data
            else -> LibraryData()
        }
    )
    val data: StateFlow<LibraryData> = _data

    private val _saveError = MutableStateFlow<String?>(null)

    /** Set when the library couldn't be written after retries. */
    val saveError: StateFlow<String?> = _saveError

    // id sets so long song lists can check "liked?" / "downloaded?" cheaply
    val likedIds: StateFlow<Set<String>> =
        _data.map { d -> d.liked.mapTo(HashSet()) { it.id } }.stateIn(scope, SharingStarted.Eagerly, emptySet())
    val downloadedIds: StateFlow<Set<String>> =
        _data.map { d -> d.downloads.mapTo(HashSet()) { it.id } }.stateIn(scope, SharingStarted.Eagerly, emptySet())

    init {
        // one writer: rapid changes (EQ sliders, likes) collapse into a single write of the latest state
        scope.launch {
            for (signal in saveSignals) {
                delay(SAVE_COALESCE_MS)
                writeWithRetry()
            }
        }
    }

    // ---------------------------------------------------------------- liked songs

    fun isLiked(id: String) = _data.value.liked.any { it.id == id }

    fun toggleLike(song: Song) = update { d ->
        if (d.liked.any { it.id == song.id }) d.copy(liked = d.liked.filterNot { it.id == song.id })
        else d.copy(liked = listOf(song) + d.liked)
    }

    // ---------------------------------------------------------------- playlists

    fun createPlaylist(name: String, songs: List<Song> = emptyList()): String {
        val id = UUID.randomUUID().toString()
        update { it.copy(playlists = listOf(Playlist(id, name.trim(), songs, now(), addedAt = now())) + it.playlists) }
        return id
    }

    fun renamePlaylist(id: String, name: String) = updatePlaylist(id) { it.copy(name = name.trim()) }

    fun deletePlaylist(id: String) = update { d -> d.copy(playlists = d.playlists.filterNot { it.id == id }) }

    fun addToPlaylist(playlistId: String, song: Song) = updatePlaylist(playlistId) { p ->
        if (p.songs.any { it.id == song.id }) p else p.copy(songs = p.songs + song)
    }

    fun removeFromPlaylist(playlistId: String, songId: String) = updatePlaylist(playlistId) { p ->
        p.copy(songs = p.songs.filterNot { it.id == songId })
    }

    fun moveInPlaylist(playlistId: String, from: Int, to: Int) = updatePlaylist(playlistId) { p ->
        if (from !in p.songs.indices || to !in p.songs.indices) return@updatePlaylist p
        val songs = p.songs.toMutableList()
        songs.add(to, songs.removeAt(from))
        p.copy(songs = songs)
    }

    private fun updatePlaylist(id: String, change: (Playlist) -> Playlist) = update { d ->
        d.copy(playlists = d.playlists.map { if (it.id == id) change(it).copy(updatedAt = now()) else it })
    }

    // ---------------------------------------------------------------- albums & artists

    fun isAlbumSaved(url: String) = _data.value.albums.any { it.url == url }

    fun toggleAlbum(album: AlbumRef) = update { d ->
        if (d.albums.any { it.url == album.url }) d.copy(albums = d.albums.filterNot { it.url == album.url })
        else d.copy(albums = listOf(album.copy(addedAt = now())) + d.albums)
    }

    fun isFollowing(url: String) = _data.value.artists.any { it.url == url }

    fun toggleFollow(artist: ArtistRef) = update { d ->
        if (d.artists.any { it.url == artist.url }) d.copy(artists = d.artists.filterNot { it.url == artist.url })
        else d.copy(artists = listOf(artist.copy(addedAt = now())) + d.artists)
    }

    // ---------------------------------------------------------------- history & downloads

    fun addRecent(song: Song) = update { d ->
        d.copy(recent = (listOf(song) + d.recent.filterNot { it.id == song.id }).take(MAX_RECENT))
    }

    fun clearRecent() = update { it.copy(recent = emptyList()) }

    fun addSearch(query: String) = update { d ->
        val q = query.trim()
        d.copy(searches = (listOf(q) + d.searches.filterNot { it.equals(q, ignoreCase = true) }).take(MAX_SEARCHES))
    }

    fun removeSearch(query: String) = update { d -> d.copy(searches = d.searches - query) }

    fun clearSearches() = update { it.copy(searches = emptyList()) }

    fun addPendingDownload(song: Song) = update { d ->
        if (d.pendingDownloads.any { it.id == song.id }) d else d.copy(pendingDownloads = d.pendingDownloads + song)
    }

    fun removePendingDownload(id: String) = update { d -> d.copy(pendingDownloads = d.pendingDownloads.filterNot { it.id == id }) }

    /** Marks a download finished: one state change, so it can't be both pending and done. */
    fun completeDownload(song: Song) = update { d ->
        d.copy(
            downloads = listOf(song) + d.downloads.filterNot { it.id == song.id },
            pendingDownloads = d.pendingDownloads.filterNot { it.id == song.id },
        )
    }

    fun removeDownload(id: String) = update { d ->
        d.copy(downloads = d.downloads.filterNot { it.id == id }, pendingDownloads = d.pendingDownloads.filterNot { it.id == id })
    }

    // ---------------------------------------------------------------- settings

    fun setAutoplay(enabled: Boolean) = update { it.copy(autoplay = enabled) }

    fun setDataSaver(enabled: Boolean) = update { it.copy(dataSaver = enabled) }

    fun setAutoLyrics(enabled: Boolean) = update { it.copy(autoLyrics = enabled) }

    fun updateEq(change: (EqSettings) -> EqSettings) = update { it.copy(eq = change(it.eq)) }

    fun setAutoBackup(enabled: Boolean) = update { it.copy(autoBackup = enabled) }

    /** Merges a restored backup into the library (nothing already here is lost). */
    fun restore(incoming: LibraryData) = update { mergeLibraries(it, incoming) }

    /** Saves an imported playlist; returns its id. */
    fun importPlaylist(name: String, songs: List<Song>): String = createPlaylist(name, songs.distinctBy { it.id })

    // ---------------------------------------------------------------- storage

    private fun update(change: (LibraryData) -> LibraryData) {
        _data.updateAndGet(change)
        saveSignals.trySend(Unit)
    }

    /** Writes the latest state now (blocking). For tests and for shutdown paths. */
    fun flushBlocking() = writeOnce()

    private suspend fun writeWithRetry() {
        repeat(SAVE_ATTEMPTS) { attempt ->
            if (writeOnce()) return
            delay(200L shl attempt)
        }
    }

    private fun writeOnce(): Boolean = try {
        store.save(_data.value)
        _saveError.value = null
        true
    } catch (e: Exception) {
        _saveError.value = e.message ?: "Couldn't save your library"
        false
    }

    private fun now() = System.currentTimeMillis()

    companion object {
        private const val MAX_RECENT = 100
        private const val MAX_SEARCHES = 15
        private const val SAVE_COALESCE_MS = 150L
        private const val SAVE_ATTEMPTS = 3
    }
}
