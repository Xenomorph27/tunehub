package com.tunehub.app.unit

import com.tunehub.app.data.AlbumRef
import com.tunehub.app.data.BackupCodec
import com.tunehub.app.data.LibraryData
import com.tunehub.app.data.Playlist
import com.tunehub.app.data.PlaylistImport
import com.tunehub.app.data.Song
import com.tunehub.app.data.isEmptyLibrary
import com.tunehub.app.data.mergeLibraries
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.IOException

class ImportBackupTest {
    private val a = Song("aaaaaaaaaaa", "Song A", "Artist A")
    private val b = Song("bbbbbbbbbbb", "Song B", "Artist B")

    // ---------------------------------------------------------------- backup

    @Test
    fun backupRoundTripsAndLeavesDownloadsOut() {
        val data = LibraryData(liked = listOf(a), playlists = listOf(Playlist("p1", "Road trip", listOf(a, b))), downloads = listOf(b))
        val restored = BackupCodec.decode(BackupCodec.encode(data))
        assertEquals(data.liked, restored.liked)
        assertEquals(data.playlists, restored.playlists)
        assertTrue("downloaded audio doesn't travel with a backup", restored.downloads.isEmpty())
    }

    @Test(expected = IOException::class)
    fun rejectsFilesThatAreNotBackups() {
        BackupCodec.decode("""{"hello":"world","app":"SomethingElse"}""")
    }

    @Test
    fun mergeKeepsEverythingAndNewerPlaylistWins() {
        val current = LibraryData(
            liked = listOf(a),
            playlists = listOf(Playlist("p1", "Old name", listOf(a), updatedAt = 1)),
        )
        val incoming = LibraryData(
            liked = listOf(a, b),
            playlists = listOf(Playlist("p1", "New name", listOf(a, b), updatedAt = 5), Playlist("p2", "Other")),
            albums = listOf(AlbumRef("u", "Album")),
            autoplay = false,
        )
        val merged = mergeLibraries(current, incoming)
        assertEquals(listOf(a, b), merged.liked)
        assertEquals(setOf("p1", "p2"), merged.playlists.map { it.id }.toSet())
        assertEquals("New name", merged.playlists.first { it.id == "p1" }.name)
        assertEquals(1, merged.albums.size)
        assertFalse(merged.autoplay)
    }

    @Test
    fun emptyLibraryDetection() {
        assertTrue(LibraryData().isEmptyLibrary())
        assertFalse(LibraryData(liked = listOf(a)).isEmptyLibrary())
    }

    // ---------------------------------------------------------------- song lists

    @Test
    fun readsTuneMyMusicStyleCsvWithQuotes() {
        val csv = "Track name,Artist name,Album\n\"Kesariya\",\"Arijit Singh\",Brahmastra\n\"Hello, World\",Someone,X\n"
        val source = PlaylistImport.fromText(csv, "My list")
        assertEquals(2, source.tracks.size)
        assertEquals(PlaylistImport.SourceTrack("Kesariya", "Arijit Singh"), source.tracks[0])
        assertEquals("Hello, World", source.tracks[1].title)
    }

    @Test
    fun readsPlainSongArtistLines() {
        val source = PlaylistImport.fromText("Yellow - Coldplay\nTum Hi Ho – Arijit Singh\nJust a title\n", "x")
        assertEquals(PlaylistImport.SourceTrack("Yellow", "Coldplay"), source.tracks[0])
        assertEquals(PlaylistImport.SourceTrack("Tum Hi Ho", "Arijit Singh"), source.tracks[1])
        assertEquals(PlaylistImport.SourceTrack("Just a title", ""), source.tracks[2])
    }

    @Test
    fun readsSpotifyEmbedPageData() {
        val html = """<html><script id="__NEXT_DATA__" type="application/json">
            {"props":{"pageProps":{"state":{"data":{"entity":{"name":"Today's Top Hits","trackList":[
            {"title":"Loser","subtitle":"Tame Impala","duration":223069},
            {"title":"Kesariya","subtitle":"Pritam, Arijit Singh","duration":268000}]}}}}}}
            </script></html>"""
        val source = PlaylistImport.parseSpotifyEmbed(html)
        assertEquals("Today's Top Hits", source.name)
        assertEquals(PlaylistImport.SourceTrack("Loser", "Tame Impala", 223), source.tracks[0])
        assertEquals("Pritam, Arijit Singh", source.tracks[1].artist)
    }

    @Test
    fun recognisesSpotifyLinks() {
        assertTrue(PlaylistImport.isSpotifyLink("https://open.spotify.com/playlist/37i9dQZF1DXcBWIGoYBM5M?si=abc"))
        assertTrue(PlaylistImport.isSpotifyLink("https://open.spotify.com/intl-de/album/4aawyAB9vmqN3uQ7FjRGTy"))
        assertFalse(PlaylistImport.isSpotifyLink("https://music.youtube.com/playlist?list=PL123"))
    }

    @Test
    fun titleMatchingHandlesApostrophesAndExtras() {
        assertTrue(PlaylistImport.titlesMatch("Don't Panic", "dont panic"))
        assertTrue(PlaylistImport.titlesMatch("Kesariya (From \"Brahmastra\")", "Kesariya"))
        assertFalse(PlaylistImport.titlesMatch("Yellow", "Fix You"))
    }
}
