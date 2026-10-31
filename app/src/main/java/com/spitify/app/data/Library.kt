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
    val albums: List<AlbumRef> = emptyList(),
    val artists: List<ArtistRef> = emptyList(),
    val downloads: List<Song> = emptyList(),
    val recent: List<Song> = emptyList(),
    val searches: List<String> = emptyList(),
    val autoplay: Boolean = true,
    val dataSaver: Boolean = false,
)

/** Everything the user saves, kept in a JSON file in app storage. */
class Library(context: Context) {
    private val file = File(context.filesDir, "library.json")
    private val json = Json { ignoreUnknownKeys = true }
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val saveLock = Mutex()

    private val _data = MutableStateFlow(load())
    val data: StateFlow<LibraryData> = _data

    // ---------------------------------------------------------------- liked songs

    fun isLiked(id: String) = _data.value.liked.any { it.id == id }

    fun toggleLike(song: Song) = update { d ->
        if (d.liked.any { it.id == song.id }) d.copy(liked = d.liked.filterNot { it.id == song.id })
        else d.copy(liked = listOf(song) + d.liked)
    }

    // ---------------------------------------------------------------- playlists

    fun createPlaylist(name: String, songs: List<Song> = emptyList()): String {
        val id = UUID.randomUUID().toString()
        update { it.copy(playlists = listOf(Playlist(id, name.trim(), songs, now())) + it.playlists) }
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
        else d.copy(albums = listOf(album) + d.albums)
    }

    fun isFollowing(url: String) = _data.value.artists.any { it.url == url }

    fun toggleFollow(artist: ArtistRef) = update { d ->
        if (d.artists.any { it.url == artist.url }) d.copy(artists = d.artists.filterNot { it.url == artist.url })
        else d.copy(artists = listOf(artist) + d.artists)
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

    fun addDownload(song: Song) = update { d ->
        d.copy(downloads = listOf(song) + d.downloads.filterNot { it.id == song.id })
    }

    fun removeDownload(id: String) = update { d -> d.copy(downloads = d.downloads.filterNot { it.id == id }) }

    // ---------------------------------------------------------------- settings

    fun setAutoplay(enabled: Boolean) = update { it.copy(autoplay = enabled) }

    fun setDataSaver(enabled: Boolean) = update { it.copy(dataSaver = enabled) }

    // ---------------------------------------------------------------- storage

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

    private fun now() = System.currentTimeMillis()

    companion object {
        private const val MAX_RECENT = 100
        private const val MAX_SEARCHES = 15
    }
}
