package com.spitify.app.data

import android.content.Context
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.updateAndGet
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.File
import java.util.UUID

@Serializable
data class LibraryData(
    val liked: List<Song> = emptyList(),
    val playlists: List<Playlist> = emptyList(),
    val recent: List<Song> = emptyList(),
    val autoplay: Boolean = true,
)

/** Liked songs, playlists and history, kept in a JSON file in app storage. */
class Library(context: Context) {
    private val file = File(context.filesDir, "library.json")
    private val json = Json { ignoreUnknownKeys = true }
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val saveLock = Mutex()

    private val _data = MutableStateFlow(load())
    val data: StateFlow<LibraryData> = _data

    fun isLiked(id: String) = _data.value.liked.any { it.id == id }

    fun toggleLike(song: Song) = update { d ->
        if (d.liked.any { it.id == song.id }) d.copy(liked = d.liked.filterNot { it.id == song.id })
        else d.copy(liked = listOf(song) + d.liked)
    }

    fun addRecent(song: Song) = update { d ->
        d.copy(recent = (listOf(song) + d.recent.filterNot { it.id == song.id }).take(MAX_RECENT))
    }

    fun setAutoplay(enabled: Boolean) = update { it.copy(autoplay = enabled) }

    fun createPlaylist(name: String): String {
        val id = UUID.randomUUID().toString()
        update { it.copy(playlists = it.playlists + Playlist(id, name.trim())) }
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

    private fun updatePlaylist(id: String, change: (Playlist) -> Playlist) = update { d ->
        d.copy(playlists = d.playlists.map { if (it.id == id) change(it) else it })
    }

    private fun update(change: (LibraryData) -> LibraryData) {
        _data.updateAndGet(change)
        scope.launch { save() }
    }

    private fun load(): LibraryData = runCatching {
        json.decodeFromString<LibraryData>(file.readText())
    }.getOrDefault(LibraryData())

    // Always writes the latest state, via a temp file so a crash never leaves half a library
    private suspend fun save() = saveLock.withLock {
        val tmp = File(file.parentFile, "library.json.tmp")
        tmp.writeText(json.encodeToString(LibraryData.serializer(), _data.value))
        tmp.renameTo(file)
    }

    companion object {
        private const val MAX_RECENT = 50
    }
}
