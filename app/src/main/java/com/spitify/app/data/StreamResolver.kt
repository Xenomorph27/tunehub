package com.spitify.app.data

import com.spitify.app.data.YouTube.toSong
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
    )

    private val cache = ConcurrentHashMap<String, Resolved>()

    /** Picks the lowest-bitrate stream instead of the highest. Set from the app's settings. */
    @Volatile
    var dataSaver = false
        set(value) {
            if (field != value) cache.clear()
            field = value
        }

    /** Blocking network call. */
    fun resolve(videoId: String): Resolved {
        cache[videoId]?.takeIf { it.expiresAtMs > System.currentTimeMillis() }?.let { return it }

        val info = YouTube.streamInfo(videoId)
        val progressive = info.audioStreams.filter {
            it.deliveryMethod == DeliveryMethod.PROGRESSIVE_HTTP && it.isUrl
        }
        // skip dubbed / audio-description tracks when the original is available
        val candidates = progressive
            .filter { it.audioTrackType == null || it.audioTrackType == AudioTrackType.ORIGINAL }
            .ifEmpty { progressive }
        val byQuality = compareBy<AudioStream>(
            { maxOf(it.averageBitrate, it.bitrate) },
            { if (it.format == MediaFormat.M4A) 1 else 0 },
        )
        val best = (if (dataSaver) candidates.minWithOrNull(byQuality) else candidates.maxWithOrNull(byQuality))
            ?: throw IOException("No playable audio stream for $videoId")

        val url = best.content
        val resolved = Resolved(
            url = url,
            headers = headersFor(url),
            expiresAtMs = expiryOf(url),
            related = info.relatedItems.mapNotNull { (it as? StreamInfoItem)?.toSong() }
                .filter { it.id != videoId },
        )
        cache[videoId] = resolved
        return resolved
    }

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
