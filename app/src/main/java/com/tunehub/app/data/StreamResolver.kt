package com.tunehub.app.data

import com.grack.nanojson.JsonObject
import com.tunehub.app.data.YouTube.toSong
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import org.schabi.newpipe.extractor.MediaFormat
import org.schabi.newpipe.extractor.NewPipe
import org.schabi.newpipe.extractor.services.youtube.YoutubeJavaScriptPlayerManager
import org.schabi.newpipe.extractor.services.youtube.YoutubeParsingHelper
import org.schabi.newpipe.extractor.services.youtube.YoutubeStreamHelper
import org.schabi.newpipe.extractor.stream.AudioStream
import org.schabi.newpipe.extractor.stream.AudioTrackType
import org.schabi.newpipe.extractor.stream.DeliveryMethod
import org.schabi.newpipe.extractor.stream.StreamInfoItem
import java.io.IOException
import java.util.concurrent.ConcurrentHashMap

/**
 * Turns a video id into a direct googlevideo.com audio URL plus the headers YouTube
 * expects for that URL. Results are cached until shortly before the URL expires.
 */
object StreamResolver {

    class Resolved(
        val url: String,
        val headers: Map<String, String>,
        val expiresAtMs: Long,
        /** e.g. "OPUS 160K", for the Now Playing quality badge. */
        val quality: String,
        val fast: Boolean = false,
    )

    private class Candidate(val url: String, val kbps: Int, val opus: Boolean, val m4a: Boolean)

    private val cache = ConcurrentHashMap<String, Resolved>()
    private val locks = ConcurrentHashMap<String, Any>()
    private val relatedCache = ConcurrentHashMap<String, List<Song>>()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    // songs whose fast-path stream failed to play, and how often that happened: after a couple of
    // failures the fast path is skipped for the rest of the session
    private val fullOnly = ConcurrentHashMap.newKeySet<String>()
    @Volatile
    private var fastFailures = 0

    /** Picks the lowest-bitrate stream instead of the highest. Set from the app's settings. */
    @Volatile
    var dataSaver = false
        set(value) {
            if (field != value) cache.clear()
            field = value
        }

    /** The cached stream for a song, if it was resolved recently. Never blocks. */
    fun peek(videoId: String): Resolved? =
        cache[videoId]?.takeIf { it.expiresAtMs > System.currentTimeMillis() }

    /** Starts resolving in the background, e.g. the moment a finger touches a song. */
    fun prefetch(videoId: String) {
        if (peek(videoId) != null) return
        scope.launch { runCatching { resolve(videoId) } }
    }

    /** Blocking network call. Concurrent calls for one song share a single lookup. */
    fun resolve(videoId: String): Resolved {
        peek(videoId)?.let { return it }
        synchronized(locks.getOrPut(videoId) { Any() }) {
            peek(videoId)?.let { return it }
            // one player request is enough to start playing; the full extractor makes four
            val useFast = fastFailures < 2 && videoId !in fullOnly
            val resolved = (if (useFast) runCatching { fetchFast(videoId) }.getOrNull() else null) ?: fetchFull(videoId)
            cache[videoId] = resolved
            return resolved
        }
    }

    /** Songs YouTube relates to this one (for radio and autoplay). Blocking; cached. */
    fun related(videoId: String): List<Song> {
        relatedCache[videoId]?.let { return it }
        val info = YouTube.streamInfo(videoId)
        val related = info.relatedItems.mapNotNull { (it as? StreamInfoItem)?.toSong() }.filter { it.id != videoId }
        relatedCache[videoId] = related
        return related
    }

    /** Drops a stream that failed to play; the retry uses the full extractor. */
    fun invalidate(videoId: String) {
        val failed = cache.remove(videoId)
        if (failed != null && failed.fast && fullOnly.add(videoId)) fastFailures++
    }

    // ---------------------------------------------------------------- fast path

    private fun fetchFast(videoId: String): Resolved {
        val cpn = YoutubeParsingHelper.generateContentPlaybackNonce()
        val response = YoutubeStreamHelper.getAndroidReelPlayerResponse(
            NewPipe.getPreferredContentCountry(), NewPipe.getPreferredLocalization(), videoId, cpn,
        )
        val status = response.getObject("playabilityStatus").getString("status")
        if (status != null && !status.equals("OK", ignoreCase = true)) throw IOException("Not playable: $status")
        if (response.getObject("videoDetails").getString("videoId") != videoId) throw IOException("Wrong video")

        val formats = response.getObject("streamingData").getArray("adaptiveFormats")
        val audio = (0 until formats.size).mapNotNull { formats.getObject(it) }
            .filter { it.getString("mimeType", "").startsWith("audio/") && it.has("url") }
            // the default language track, and not YouTube's dynamic-range-compressed copy
            .filter { !it.has("audioTrack") || it.getObject("audioTrack").getBoolean("audioIsDefault", true) }
        val candidates = audio.filterNot { it.getBoolean("isDrc", false) }.ifEmpty { audio }
            .map { candidate(videoId, it, cpn) }
        val best = pick(candidates) ?: throw IOException("No audio formats")
        return Resolved(best.url, headersFor(best.url), expiryOf(best.url), qualityLabel(best), fast = true)
    }

    private fun candidate(videoId: String, format: JsonObject, cpn: String): Candidate {
        val mime = format.getString("mimeType", "")
        val url = YoutubeJavaScriptPlayerManager.getUrlWithThrottlingParameterDeobfuscated(videoId, format.getString("url")) +
            "&" + YoutubeParsingHelper.CPN + "=" + cpn
        val bitrate = maxOf(format.getInt("averageBitrate", 0), format.getInt("bitrate", 0))
        return Candidate(url, bitrate / 1000, mime.contains("opus"), mime.startsWith("audio/mp4"))
    }

    // ---------------------------------------------------------------- full extractor (fallback)

    private fun fetchFull(videoId: String): Resolved {
        val info = YouTube.streamInfo(videoId)
        relatedCache[videoId] = info.relatedItems.mapNotNull { (it as? StreamInfoItem)?.toSong() }.filter { it.id != videoId }
        val progressive = info.audioStreams.filter {
            it.deliveryMethod == DeliveryMethod.PROGRESSIVE_HTTP && it.isUrl
        }
        // skip dubbed / audio-description tracks when the original is available
        val original = progressive
            .filter { it.audioTrackType == null || it.audioTrackType == AudioTrackType.ORIGINAL }
            .ifEmpty { progressive }
        val candidates = original.filterNot { isDrc(it) }.ifEmpty { original }.map {
            Candidate(
                it.content,
                maxOf(it.averageBitrate, it.bitrate) / 1000,
                it.codec?.contains("opus", ignoreCase = true) == true,
                it.format == MediaFormat.M4A,
            )
        }
        val best = pick(candidates) ?: throw IOException("No playable audio stream for $videoId")
        return Resolved(best.url, headersFor(best.url), expiryOf(best.url), qualityLabel(best))
    }

    private fun isDrc(stream: AudioStream) =
        stream.itagItem?.isDrc == true || stream.content.contains("drc%3D1") || stream.content.contains("drc=1")

    // ---------------------------------------------------------------- shared

    /** Highest bitrate (Opus wins ties), or the lowest in data-saver mode. */
    private fun pick(candidates: List<Candidate>): Candidate? {
        val order = compareBy<Candidate>({ it.kbps }, { if (it.opus) 1 else 0 })
        return if (dataSaver) candidates.minWithOrNull(order) else candidates.maxWithOrNull(order)
    }

    private fun qualityLabel(c: Candidate): String {
        val codec = when {
            c.opus -> "OPUS"
            c.m4a -> "AAC"
            else -> "AUDIO"
        }
        return if (c.kbps > 0) "$codec ${c.kbps}K" else codec
    }

    // Mirrors what NewPipe's player sends: each YouTube client's stream URLs only work
    // with that client's user agent.
    private fun headersFor(url: String): Map<String, String> = when {
        YoutubeParsingHelper.isAndroidStreamingUrl(url) ->
            mapOf("User-Agent" to YoutubeParsingHelper.getAndroidUserAgent(null))
        YoutubeParsingHelper.isIosStreamingUrl(url) ->
            mapOf("User-Agent" to YoutubeParsingHelper.getIosUserAgent(null))
        YoutubeParsingHelper.isVisionOsStreamingUrl(url) ->
            mapOf("User-Agent" to YoutubeParsingHelper.getVisionOsUserAgent(null))
        YoutubeParsingHelper.isWebStreamingUrl(url) ||
            YoutubeParsingHelper.isWebEmbeddedPlayerStreamingUrl(url) -> mapOf(
            "User-Agent" to YtDownloader.USER_AGENT,
            "Origin" to "https://www.youtube.com",
            "Referer" to "https://www.youtube.com/",
        )
        else -> mapOf("User-Agent" to YtDownloader.USER_AGENT)
    }

    private fun expiryOf(url: String): Long {
        val expireSec = Regex("""[?&]expire=(\d+)""").find(url)?.groupValues?.get(1)?.toLongOrNull()
        val now = System.currentTimeMillis()
        return if (expireSec != null) expireSec * 1000 - 5 * 60_000 else now + 3 * 3600_000
    }
}
