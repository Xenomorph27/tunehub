package com.spitify.app

import com.spitify.app.data.BrowseItem
import com.spitify.app.data.Lyrics
import com.spitify.app.data.Song
import com.spitify.app.data.StreamResolver
import com.spitify.app.data.YouTube
import com.spitify.app.data.YtDownloader
import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.junit.Assert.assertTrue
import org.junit.BeforeClass
import org.junit.Test
import org.schabi.newpipe.extractor.NewPipe
import org.schabi.newpipe.extractor.localization.ContentCountry
import org.schabi.newpipe.extractor.localization.Localization

/**
 * Live smoke tests against YouTube Music and LRCLIB. YouTube blocks stream access from
 * datacenter IPs, so CI treats failures here as warnings; read the output.
 */
class ExtractionTest {

    @Test
    fun searchAllKinds() = runBlocking {
        val top = YouTube.searchAll("arijit singh")
        println("all: ${top.songs.size} songs, ${top.artists.size} artists, ${top.albums.size} albums, ${top.playlists.size} playlists")
        println("first artist: ${top.artists.firstOrNull()}")
        println("first album: ${top.albums.firstOrNull()}")
        assertTrue(top.songs.isNotEmpty())
        assertTrue(top.artists.isNotEmpty())
        assertTrue(top.albums.isNotEmpty())
    }

    @Test
    fun artistPage() = runBlocking {
        val artist = YouTube.searchAll("coldplay").artists.first()
        val page = YouTube.artistPage(artist.url, artist.name)
        println("artist ${page.artist.name}: ${page.popular.size} popular, ${page.albums.size} albums, ${page.videos.size} videos, banner=${page.banner != null}")
        println("popular: ${page.popular.take(3).map { it.title }}")
        println("albums: ${page.albums.take(3).map { it.title }}")
        assertTrue(page.popular.isNotEmpty())
    }

    @Test
    fun albumPage() = runBlocking {
        val album = YouTube.searchAll("parachutes coldplay").albums.first()
        val page = YouTube.albumPage(album.url, album)
        println("album ${page.album.title} by ${page.album.subtitle}: ${page.songs.size} songs; first ${page.songs.firstOrNull()}")
        assertTrue(page.songs.isNotEmpty())
    }

    @Test
    fun lyrics() {
        val song = Song("dQw4w9WgXcQ", "Never Gonna Give You Up (Official Video)", "Rick Astley", 213)
        val result = Lyrics.find(client, song)
        println("lyrics: synced=${result?.synced}, lines=${result?.lines?.size}, first=${result?.lines?.firstOrNull()}")
        assertTrue(result != null)
    }

    @Test
    fun streamFetch() {
        val songs = YouTube.Search("daft punk get lucky", YouTube.FILTER_SONGS).loadNext()
            .filterIsInstance<BrowseItem.SongItem>().map { it.song }
        assertTrue("search returned nothing", songs.isNotEmpty())

        val stream = StreamResolver.resolve(songs.first().id)
        println("stream host: ${stream.url.substringAfter("://").substringBefore("/")}, related: ${stream.related.size}")
        val request = Request.Builder()
            .url(stream.url + "&rn=1")
            .post(byteArrayOf(0x78, 0).toRequestBody())
            .header("Range", "bytes=0-65535")
            .apply { stream.headers.forEach { (k, v) -> header(k, v) } }
            .build()
        client.newCall(request).execute().use { response ->
            val bytes = response.body?.bytes()?.size ?: 0
            println("stream fetch: HTTP ${response.code}, $bytes bytes")
            assertTrue("stream fetch failed: HTTP ${response.code}", response.code in 200..299 && bytes > 0)
        }
    }

    companion object {
        private val client = OkHttpClient()

        @JvmStatic
        @BeforeClass
        fun setUp() {
            NewPipe.init(YtDownloader(client), Localization("en", "US"), ContentCountry("US"))
        }
    }
}
