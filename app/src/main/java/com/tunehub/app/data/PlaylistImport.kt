package com.tunehub.app.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.IOException
import java.util.concurrent.atomic.AtomicInteger

/**
 * Brings playlists in from other apps. Spotify and YouTube links are read directly; anything
 * else (Amazon Music, Apple Music, …) comes in as a text or CSV song list, e.g. exported with a
 * converter such as TuneMyMusic. Each song is then found on YouTube Music.
 */
object PlaylistImport {

    data class SourceTrack(val title: String, val artist: String, val durationSec: Long = -1)

    class Source(val name: String, val tracks: List<SourceTrack>, val alreadyMatched: List<Song>? = null)

    /** One imported song: [song] is null when nothing was found; [confident] when title and artist agree. */
    data class Match(val source: SourceTrack, val song: Song?, val confident: Boolean)

    private val spotifyId = Regex("""(playlist|album)[/:]([A-Za-z0-9]{22})""")
    private val json = Json { ignoreUnknownKeys = true }

    fun isSpotifyLink(text: String) = text.contains("spotify") && spotifyId.containsMatchIn(text)

    /** Reads a Spotify or YouTube / YouTube Music playlist (or album) link. Blocking. */
    suspend fun fromLink(client: OkHttpClient, link: String): Source = withContext(Dispatchers.IO) {
        val text = link.trim()
        when {
            isSpotifyLink(text) -> spotify(client, text)
            text.contains("list=") && (text.contains("youtube.com") || text.contains("youtu.be")) -> {
                val page = YouTube.albumPage(text, null)
                Source(page.album.title, page.songs.map { SourceTrack(it.title, it.artist, it.durationSec) }, page.songs)
            }
            else -> throw IOException("Paste a Spotify or YouTube Music playlist link")
        }
    }

    // Spotify's public embed page carries the track list in its page data; no login needed
    private fun spotify(client: OkHttpClient, link: String): Source {
        val m = spotifyId.find(link) ?: throw IOException("That doesn't look like a Spotify playlist link")
        val (kind, id) = m.destructured
        val request = Request.Builder()
            .url("https://open.spotify.com/embed/$kind/$id")
            .header("User-Agent", YtDownloader.USER_AGENT)
            .build()
        val html = client.newCall(request).execute().use { r ->
            if (!r.isSuccessful) throw IOException("Spotify returned HTTP ${r.code}. Is the playlist public?")
            r.body?.string().orEmpty()
        }
        return parseSpotifyEmbed(html)
    }

    /** Pulls the name and tracks out of a Spotify embed page. */
    internal fun parseSpotifyEmbed(html: String): Source {
        val data = Regex("""<script id="__NEXT_DATA__" type="application/json">(.*?)</script>""", RegexOption.DOT_MATCHES_ALL)
            .find(html)?.groupValues?.get(1) ?: throw IOException("Couldn't read that Spotify page")
        val entity = json.parseToJsonElement(data).path("props", "pageProps", "state", "data", "entity") as? JsonObject
            ?: throw IOException("Couldn't read that Spotify page")
        val name = entity.string("name") ?: entity.string("title") ?: "Imported playlist"
        val tracks = (entity["trackList"] as? JsonArray).orEmpty().mapNotNull { t ->
            val o = t as? JsonObject ?: return@mapNotNull null
            val title = o.string("title") ?: return@mapNotNull null
            SourceTrack(title, o.string("subtitle").orEmpty(), (o["duration"]?.jsonPrimitive?.longOrNull ?: -1000) / 1000)
        }
        if (tracks.isEmpty()) throw IOException("That Spotify playlist looks empty or private")
        return Source(name, tracks)
    }

    /**
     * Reads a pasted or exported song list: a CSV with track/title and artist columns
     * (TuneMyMusic, Soundiiz, Exportify…), or plain lines like "Song - Artist".
     */
    fun fromText(text: String, name: String): Source {
        val lines = text.lines().map { it.trim() }.filter { it.isNotEmpty() }
        if (lines.isEmpty()) throw IOException("That file has no songs in it")
        val header = csv(lines.first()).map { it.lowercase() }
        val titleCol = header.indexOfFirst { it == "track name" || it == "title" || it == "track" || it == "song" || it == "name" }
        val artistCol = header.indexOfFirst { it.startsWith("artist") }
        val tracks = if (titleCol >= 0 && artistCol >= 0) {
            lines.drop(1).mapNotNull { line ->
                val cells = csv(line)
                val title = cells.getOrNull(titleCol)?.trim().orEmpty()
                if (title.isEmpty()) null else SourceTrack(title, cells.getOrNull(artistCol)?.trim().orEmpty())
            }
        } else {
            lines.map { line ->
                val parts = line.split(" - ", " – ", " — ", " by ", limit = 2)
                if (parts.size == 2) SourceTrack(parts[0].trim(), parts[1].trim()) else SourceTrack(line, "")
            }
        }
        if (tracks.isEmpty()) throw IOException("Couldn't find any songs in that file")
        return Source(name, tracks)
    }

    /** Finds each song on YouTube Music, 4 at a time. [onProgress] gets (done, total). */
    suspend fun match(tracks: List<SourceTrack>, onProgress: (Int, Int) -> Unit): List<Match> = coroutineScope {
        val slots = Semaphore(4)
        val done = AtomicInteger()
        tracks.map { track ->
            async(Dispatchers.IO) {
                slots.withPermit {
                    val match = runCatching { matchOne(track) }.getOrElse { Match(track, null, false) }
                    onProgress(done.incrementAndGet(), tracks.size)
                    match
                }
            }
        }.awaitAll()
    }

    private fun matchOne(track: SourceTrack): Match {
        val firstArtist = track.artist.split(",", "&", " feat").first().trim()
        val results = YouTube.Search("${track.title} $firstArtist".trim(), YouTube.FILTER_SONGS).loadNext()
            .filterIsInstance<BrowseItem.SongItem>().map { it.song }
        val confident = results.firstOrNull {
            titlesMatch(track.title, it.title) && (firstArtist.isEmpty() || namesOverlap(firstArtist, it.artist))
        }
        if (confident != null) return Match(track, confident, true)
        val byTitle = results.firstOrNull { titlesMatch(track.title, it.title) }
        return Match(track, byTitle ?: results.firstOrNull(), false)
    }

    // ---------------------------------------------------------------- helpers

    /** Loose title comparison: case, punctuation, apostrophes and bracketed extras don't matter. */
    fun titlesMatch(a: String, b: String): Boolean {
        val x = normalize(a)
        val y = normalize(b)
        return x.isNotEmpty() && y.isNotEmpty() && (x == y || x.contains(y) || y.contains(x))
    }

    private fun namesOverlap(a: String, b: String): Boolean {
        val x = normalize(a)
        val y = normalize(b)
        return x.isNotEmpty() && y.isNotEmpty() && (x.contains(y) || y.contains(x))
    }

    internal fun normalize(s: String) = s.lowercase()
        .replace(Regex("""\(.*?\)|\[.*?]"""), "")
        .replace(Regex("""['’`]"""), "")
        .replace(Regex("""[^\p{L}\p{N}]+"""), " ")
        .trim()

    // one CSV line, honouring quotes and doubled quotes
    internal fun csv(line: String): List<String> {
        val cells = mutableListOf<String>()
        val cell = StringBuilder()
        var quoted = false
        var i = 0
        while (i < line.length) {
            val c = line[i]
            when {
                quoted && c == '"' && line.getOrNull(i + 1) == '"' -> { cell.append('"'); i++ }
                c == '"' -> quoted = !quoted
                c == ',' && !quoted -> { cells += cell.toString(); cell.clear() }
                else -> cell.append(c)
            }
            i++
        }
        cells += cell.toString()
        return cells
    }

    private fun JsonElement.path(vararg keys: String): JsonElement? =
        keys.fold(this as JsonElement?) { e, k -> (e as? JsonObject)?.get(k) }

    private fun JsonObject.string(key: String): String? =
        runCatching { this[key]?.jsonPrimitive?.contentOrNull }.getOrNull()
}
