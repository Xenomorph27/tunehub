package com.tunehub.app.data

import com.tunehub.app.data.YouTube.toSong
import org.schabi.newpipe.extractor.MediaFormat
import org.schabi.newpipe.extractor.services.youtube.YoutubeParsingHelper
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
        val related: List<Song>,
        /** e.g. "OPUS 160K", for the Now Playing quality badge. */
        val quality: String,
    )

    private val cache = ConcurrentHashMap<String, Resolved>()
    private val locks = ConcurrentHashMap<String, Any>()

    /** The cached stream for a song, if it was resolved recently. Never blocks. */
    fun peek(videoId: String): Resolved? =
        cache[videoId]?.takeIf { it.expiresAtMs > System.currentTimeMillis() }

    /** Picks the lowest-bitrate stream instead of the highest. Set from the app's settings. */
    @Volatile
    var dataSaver = false
        set(value) {
            if (field != value) cache.clear()
            field = value
        }

    /** Blocking network call. Concurrent calls for one song share a single lookup. */
    fun resolve(videoId: String): Resolved {
        peek(videoId)?.let { return it }
        synchronized(locks.getOrPut(videoId) { Any() }) {
            peek(videoId)?.let { return it }
            return fetch(videoId).also { cache[videoId] = it }
        }
    }

    private fun fetch(videoId: String): Resolved {
        val info = YouTube.streamInfo(videoId)
        val progressive = info.audioStreams.filter {
            it.deliveryMethod == DeliveryMethod.PROGRESSIVE_HTTP && it.isUrl
        }
        // skip dubbed / audio-description tracks when the original is available
        val original = progressive
            .filter { it.audioTrackType == null || it.audioTrackType == AudioTrackType.ORIGINAL }
            .ifEmpty { progressive }
        // YouTube also serves "DRC" copies with squashed dynamic range; they sound flatter
        val candidates = original.filterNot { isDrc(it.content) }.ifEmpty { original }
        val byQuality = compareBy<AudioStream>(
            { kbps(it) },
            { if (it.format == MediaFormat.M4A) 1 else 0 },
        )
        val best = (if (dataSaver) candidates.minWithOrNull(byQuality) else candidates.maxWithOrNull(byQuality))
            ?: throw IOException("No playable audio stream for $videoId")

        val url = best.content
        return Resolved(
            url = url,
            headers = headersFor(url),
            expiresAtMs = expiryOf(url),
            related = info.relatedItems.mapNotNull { (it as? StreamInfoItem)?.toSong() }
                .filter { it.id != videoId },
            quality = if (kbps(best) > 0) "${codecName(best)} ${kbps(best)}K" else codecName(best),
        )
    }

    private fun kbps(stream: AudioStream): Int = maxOf(stream.averageBitrate, stream.bitrate) / 1000

    private fun codecName(stream: AudioStream): String = when {
        stream.codec?.contains("opus", ignoreCase = true) == true -> "OPUS"
        stream.format == MediaFormat.M4A -> "AAC"
        else -> stream.format?.name ?: "AUDIO"
    }

    private fun isDrc(url: String) = url.contains("drc%3D1") || url.contains("drc=1")

    fun invalidate(videoId: String) {
        cache.remove(videoId)
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
