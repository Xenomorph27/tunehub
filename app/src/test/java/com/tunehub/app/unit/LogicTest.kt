package com.tunehub.app.unit

import com.tunehub.app.data.AlbumRef
import com.tunehub.app.data.BoundedCache
import com.tunehub.app.data.Lyrics
import com.tunehub.app.data.StreamResolver
import com.tunehub.app.data.YouTube
import com.tunehub.app.data.isValidVideoId
import com.tunehub.app.ui.NavState
import com.tunehub.app.ui.Route
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class LogicTest {

    // ---------------------------------------------------------------- lyrics

    @Test
    fun parsesTimedLyricsIncludingRepeatedStamps() {
        val lines = Lyrics.parseLrc("[00:01.50]Hello\n[00:10.00][00:20.00]Chorus\nno stamp\n[01:02.25]End")
        assertEquals(listOf(1500L, 10_000L, 20_000L, 62_250L), lines.map { it.timeMs })
        assertEquals(listOf("Hello", "Chorus", "Chorus", "End"), lines.map { it.text })
    }

    @Test
    fun lyricsTitleMatchingIgnoresCaseAndExtrasButRejectsOtherSongs() {
        assertTrue(Lyrics.titlesMatch("Kesariya", "KESARIYA (From \"Brahmastra\")"))
        assertTrue(Lyrics.titlesMatch("Don't Panic", "dont panic"))
        assertFalse(Lyrics.titlesMatch("Yellow", "Fix You"))
        assertFalse(Lyrics.titlesMatch("", "Anything"))
    }

    // ---------------------------------------------------------------- stream choice

    private data class Fmt(val kbps: Int, val opus: Boolean)

    @Test
    fun picksHighestBitrateOpusOnTiesAndLowestForDataSaver() {
        val formats = listOf(Fmt(128, false), Fmt(160, true), Fmt(160, false), Fmt(48, true))
        assertEquals(Fmt(160, true), StreamResolver.pickBest(formats, saver = false, { it.kbps }, { it.opus }))
        assertEquals(Fmt(48, true), StreamResolver.pickBest(formats, saver = true, { it.kbps }, { it.opus }))
        assertNull(StreamResolver.pickBest(emptyList<Fmt>(), saver = false, { it.kbps }, { it.opus }))
    }

    // ---------------------------------------------------------------- ids and links

    @Test
    fun videoIdValidation() {
        assertTrue(isValidVideoId("dQw4w9WgXcQ"))
        assertFalse(isValidVideoId("../../etc/x"))
        assertFalse(isValidVideoId("short"))
        assertFalse(isValidVideoId("dQw4w9WgXcQ/extra"))
        assertFalse(isValidVideoId(null))
    }

    @Test
    fun sharedLinksOnlyAcceptYouTubeHosts() {
        assertEquals("dQw4w9WgXcQ", YouTube.videoIdFromText("listen https://www.youtube.com/watch?v=dQw4w9WgXcQ&t=5"))
        assertEquals("dQw4w9WgXcQ", YouTube.videoIdFromText("https://youtu.be/dQw4w9WgXcQ?si=abc"))
        assertEquals("dQw4w9WgXcQ", YouTube.videoIdFromText("https://music.youtube.com/watch?v=dQw4w9WgXcQ"))
        assertEquals("dQw4w9WgXcQ", YouTube.videoIdFromText("https://youtube.com/shorts/dQw4w9WgXcQ"))
        assertNull(YouTube.videoIdFromText("https://evil.example/watch?v=dQw4w9WgXcQ"))
        assertNull(YouTube.videoIdFromText("no link here v=dQw4w9WgXcQ"))
    }

    // ---------------------------------------------------------------- navigation state

    @Test
    fun navigationStateSurvivesSerialization() {
        val json = Json { ignoreUnknownKeys = true }
        val state = NavState(
            tab = 1,
            stacks = listOf(
                listOf(Route.Settings, Route.Equalizer),
                listOf(Route.Album(AlbumRef("https://music.youtube.com/playlist?list=X", "Parachutes"))),
                emptyList(),
            ),
            nowPlayingOpen = true,
        )
        val restored = json.decodeFromString(NavState.serializer(), json.encodeToString(NavState.serializer(), state))
        assertEquals(state, restored)
    }

    // ---------------------------------------------------------------- bounded cache

    @Test
    fun boundedCacheDropsLeastRecentlyUsed() {
        val cache = BoundedCache<String, Int>(2)
        cache["a"] = 1
        cache["b"] = 2
        cache["a"] // touch a, so b is now the oldest
        cache["c"] = 3
        assertEquals(1, cache["a"])
        assertNull(cache["b"])
        assertEquals(3, cache["c"])
    }
}
