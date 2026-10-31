package com.tunehub.app.data

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.ConcurrentHashMap
import kotlin.math.abs

/** Lyrics from LRCLIB (lrclib.net), a free, keyless lyrics database with time-synced lines. */
object Lyrics {

    data class Line(val timeMs: Long, val text: String)

    /** [lines] is empty when only plain (unsynced) text is known. */
    class Result(val lines: List<Line>, val plain: String) {
        val synced get() = lines.isNotEmpty()
    }

    @Serializable
    private data class Track(
        val trackName: String? = null,
        val artistName: String? = null,
        val duration: Double? = null,
        val instrumental: Boolean = false,
        val plainLyrics: String? = null,
        val syncedLyrics: String? = null,
    )

    private val json = Json { ignoreUnknownKeys = true }
    private val cache = ConcurrentHashMap<String, Result>()
    private val misses = ConcurrentHashMap.newKeySet<String>()

    /** Blocking. Returns null when nothing was found. */
    fun find(client: OkHttpClient, song: Song): Result? {
        cache[song.id]?.let { return it }
        if (song.id in misses) return null

        val title = cleanTitle(song.title, song.artist)
        val candidates = search(client, title, song.artist).ifEmpty { search(client, title, null) }
        // prefer synced lyrics whose length matches the song; YouTube durations run a few seconds long
        val best = candidates
            .filter { !it.instrumental && (it.syncedLyrics != null || it.plainLyrics != null) }
            .sortedWith(
                compareBy<Track> { if (it.syncedLyrics != null) 0 else 1 }
                    .thenBy { abs((it.duration ?: 0.0) - song.durationSec) }
            )
            .firstOrNull { song.durationSec <= 0 || abs((it.duration ?: 0.0) - song.durationSec) < 15 }

        if (best == null) {
            misses += song.id
            return null
        }
        val result = Result(
            lines = best.syncedLyrics?.let(::parseLrc).orEmpty(),
            plain = best.plainLyrics ?: best.syncedLyrics.orEmpty().replace(Regex("""\[\d+:\d+(?:\.\d+)?]\s?"""), ""),
        )
        cache[song.id] = result
        return result
    }

    private fun search(client: OkHttpClient, title: String, artist: String?): List<Track> {
        val url = "https://lrclib.net/api/search".toHttpUrl().newBuilder()
            .addQueryParameter("track_name", title)
            .apply { if (artist != null) addQueryParameter("artist_name", artist) }
            .build()
        val request = Request.Builder().url(url).header("User-Agent", "TuneHub (personal music player)").build()
        return client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) return emptyList()
            json.decodeFromString<List<Track>>(response.body?.string() ?: return emptyList())
        }
    }

    // "Artist - Song (Official Video) [4K]" -> "Song"
    private fun cleanTitle(title: String, artist: String): String {
        var t = title.replace(Regex("""\(.*?\)|\[.*?]"""), "")
        val dash = t.indexOf(" - ")
        if (dash > 0 && t.substring(0, dash).contains(artist, ignoreCase = true)) t = t.substring(dash + 3)
        t = t.replace(Regex("""(?i)\b(official|lyrics?|audio|video|music video|hd|4k)\b"""), "")
        t = t.split(Regex("""(?i)\s+(ft\.?|feat\.?)\s+""")).first()
        return t.replace(Regex("""\s+"""), " ").trim(' ', '-', '|').ifBlank { title }
    }

    private fun parseLrc(lrc: String): List<Line> {
        val tag = Regex("""\[(\d+):(\d+(?:\.\d+)?)]""")
        return lrc.lines().flatMap { raw ->
            val stamps = tag.findAll(raw).toList()
            if (stamps.isEmpty()) return@flatMap emptyList()
            val text = raw.substring(stamps.last().range.last + 1).trim()
            stamps.map { m ->
                val ms = m.groupValues[1].toLong() * 60_000 + (m.groupValues[2].toDouble() * 1000).toLong()
                Line(ms, text)
            }
        }.sortedBy { it.timeMs }
    }
}
