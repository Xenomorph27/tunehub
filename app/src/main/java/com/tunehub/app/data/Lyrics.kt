package com.tunehub.app.data

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.IOException
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
    private val cache = BoundedCache<String, Result>(200)

    // confirmed "no lyrics" answers, remembered for a while; network errors are never remembered
    private val misses = BoundedCache<String, Long>(500)

    /**
     * Blocking. Returns null when LRCLIB has no matching lyrics; throws [IOException] when it
     * couldn't be reached, so the caller can offer a retry.
     */
    fun find(client: OkHttpClient, song: Song): Result? {
        cache[song.id]?.let { return it }
        misses[song.id]?.let { if (System.currentTimeMillis() - it < MISS_TTL_MS) return null }

        val title = cleanTitle(song.title, song.artist)
        val best = pickBest(search(client, title, song.artist), title, song.durationSec)
            ?: pickBest(search(client, title, null), title, song.durationSec)

        if (best == null) {
            misses[song.id] = System.currentTimeMillis()
            return null
        }
        val result = Result(
            lines = best.syncedLyrics?.let(::parseLrc).orEmpty(),
            plain = best.plainLyrics ?: best.syncedLyrics.orEmpty().replace(Regex("""\[\d+:\d+(?:\.\d+)?]\s?"""), ""),
        )
        cache[song.id] = result
        return result
    }

    // usable, same title, then synced and closest in length; YouTube durations run a few seconds long
    private fun pickBest(candidates: List<Track>, title: String, durationSec: Long): Track? = candidates
        .filter { !it.instrumental && (it.syncedLyrics != null || it.plainLyrics != null) }
        .filter { titlesMatch(title, it.trackName.orEmpty()) }
        .sortedWith(
            compareBy<Track> { if (it.syncedLyrics != null) 0 else 1 }
                .thenBy { abs((it.duration ?: 0.0) - durationSec) }
        )
        .firstOrNull { durationSec <= 0 || abs((it.duration ?: 0.0) - durationSec) < 15 }

    /** Loose title comparison: case, punctuation and bracketed extras don't matter. */
    internal fun titlesMatch(a: String, b: String): Boolean {
        // apostrophes vanish ("don't" == "dont"); other punctuation separates words
        fun norm(s: String) = s.lowercase().replace(Regex("""\(.*?\)|\[.*?]"""), "").replace(Regex("""['’`]"""), "")
            .replace(Regex("""[^\p{L}\p{N}]+"""), " ").trim()
        val x = norm(a)
        val y = norm(b)
        return x.isNotEmpty() && y.isNotEmpty() && (x == y || x.contains(y) || y.contains(x))
    }

    private fun search(client: OkHttpClient, title: String, artist: String?): List<Track> {
        val url = "https://lrclib.net/api/search".toHttpUrl().newBuilder()
            .addQueryParameter("track_name", title)
            .apply { if (artist != null) addQueryParameter("artist_name", artist) }
            .build()
        val request = Request.Builder().url(url).header("User-Agent", "TuneHub (personal music player)").build()
        return client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) throw IOException("LRCLIB HTTP ${response.code}")
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

    internal fun parseLrc(lrc: String): List<Line> {
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

    private const val MISS_TTL_MS = 30 * 60_000L
}
