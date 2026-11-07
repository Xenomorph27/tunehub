package com.tunehub.app.unit

import com.tunehub.app.data.Library
import com.tunehub.app.data.LibraryData
import com.tunehub.app.data.LibraryStore
import com.tunehub.app.data.LoadResult
import com.tunehub.app.data.Song
import com.tunehub.app.data.homePlaylists
import com.tunehub.app.data.mergeLibraries
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class LibraryStoreTest {
    @get:Rule
    val tmp = TemporaryFolder()

    private val song = Song("dQw4w9WgXcQ", "Never Gonna Give You Up", "Rick Astley", 213)

    @Test
    fun missingFileIsFirstRun() {
        assertEquals(LoadResult.Missing, LibraryStore(tmp.root).load())
    }

    @Test
    fun homeRanksPlayedPlaylistsAndPersistsTheCounts() {
        val library = Library(tmp.root)
        val most = library.createPlaylist("Most played", listOf(song))
        val second = library.createPlaylist("Second", listOf(song))
        val unused = library.createPlaylist("Unused", listOf(song))
        library.createPlaylist("Empty")
        library.recordPlaylistPlay(second)
        repeat(2) { library.recordPlaylistPlay(most) }
        library.flushBlocking()

        val saved = (LibraryStore(tmp.root).load() as LoadResult.Loaded).data
        assertEquals(listOf(most, second), saved.playlists.homePlaylists().map { it.id })
        assertEquals(0L, saved.playlists.single { it.id == unused }.playCount)
        val edited = saved.playlists.single { it.id == most }.let {
            it.copy(name = "Renamed", updatedAt = it.updatedAt + 1, playCount = 0, lastPlayedAt = 0)
        }
        val restored = mergeLibraries(saved, LibraryData(playlists = listOf(edited)))
        val first = restored.playlists.homePlaylists().first()
        assertEquals("Renamed", first.name)
        assertEquals(2L, first.playCount)
        assertTrue(first.lastPlayedAt > 0)
    }

    @Test
    fun saveThenLoadRoundTrips() {
        val store = LibraryStore(tmp.root)
        store.save(LibraryData(liked = listOf(song), autoplay = false))
        val loaded = store.load() as LoadResult.Loaded
        assertFalse(loaded.fromBackup)
        assertEquals(listOf(song), loaded.data.liked)
        assertFalse(loaded.data.autoplay)
    }

    @Test
    fun corruptFileFallsBackToLastGoodCopyAndIsKept() {
        val store = LibraryStore(tmp.root)
        store.save(LibraryData(liked = listOf(song)))
        store.load() // a successful load records the last good copy
        File(tmp.root, "library.json").writeText("{ this is not json")

        val result = store.load() as LoadResult.Loaded
        assertTrue(result.fromBackup)
        assertEquals(listOf(song), result.data.liked)
        assertTrue("damaged file must be kept", tmp.root.listFiles()!!.any { it.name.startsWith("library.json.corrupt-") })
    }

    @Test
    fun corruptFileWithoutBackupFailsWithoutDeletingIt() {
        File(tmp.root, "library.json").writeText("garbage")
        val result = LibraryStore(tmp.root).load()
        assertTrue(result is LoadResult.Failed)
        assertTrue((result as LoadResult.Failed).quarantined!!.exists())
    }

    @Test
    fun failedLoadIsNotCleanSoDownloadsAreLeftAlone() {
        File(tmp.root, "library.json").writeText("garbage")
        assertFalse(Library(tmp.root).loadedCleanly)
    }

    @Test
    fun versionOneFileWithoutNewFieldsLoadsWithDefaults() {
        // the shape written by builds before the library had a version or these settings
        File(tmp.root, "library.json").writeText(
            """{"liked":[{"id":"dQw4w9WgXcQ","title":"T","artist":"A"}],"autoplay":false}"""
        )
        val data = (LibraryStore(tmp.root).load() as LoadResult.Loaded).data
        assertEquals(LibraryData.CURRENT_VERSION, data.version)
        assertFalse(data.autoplay)
        assertTrue(data.autoLyrics)
        assertTrue(data.pendingDownloads.isEmpty())
    }

    @Test
    fun libraryChangesReachDisk() {
        val library = Library(tmp.root)
        library.toggleLike(song)
        library.completeDownload(song)
        library.flushBlocking()

        val data = (LibraryStore(tmp.root).load() as LoadResult.Loaded).data
        assertEquals(listOf(song), data.liked)
        assertEquals(listOf(song), data.downloads)
        assertTrue(data.pendingDownloads.isEmpty())
    }

    @Test
    fun completingADownloadClearsItsPendingEntry() {
        val library = Library(tmp.root)
        library.addPendingDownload(song)
        assertEquals(1, library.data.value.pendingDownloads.size)
        library.completeDownload(song)
        assertTrue(library.data.value.pendingDownloads.isEmpty())
        assertEquals(1, library.data.value.downloads.size)
    }
}
