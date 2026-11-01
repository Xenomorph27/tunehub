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
import java.util.concurrent.atomic.AtomicInteger

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
        /**
         * Which exact audio file this is (format itag + DRC flag). Part of the media cache key,
         * so bytes of one rendition are never served as another.
         */
        val rendition: String,
        val fast: Boolean = false,
    )

    private class Candidate(
        val url: String,
        val kbps: Int,
        val opus: Boolean,
        val m4a: Boolean,
        val itag: Int,
        val drc: Boolean,
    )

    // keyed by "<id>|hi" or "<id>|lo": each quality has its own entry, so a slow lookup started
    // before a quality switch can only ever publish into its own slot
    private val cache = BoundedCache<String, Resolved>(300)
    private val locks = BoundedCache<String, Any>(300)
    private val relatedCache = BoundedCache<String, List<Song>>(100)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    // Fast-path health. Songs whose fast stream failed to play use the full extractor; after a
    // couple of those, or a run of failed fast lookups, the fast path rests for a while.
    private val fullOnly = BoundedCache<String, Boolean>(500)
    private val fastPlaybackFailures = AtomicInteger()
    private val fastLookupFailuresInARow = AtomicInteger()
    @Volatile
    private var fastPausedUntilMs = 0L

    /** Picks the lowest-bitrate stream instead of the highest. Set from the app's settings. */
    @Volatile
    var dataSaver = false

    private fun key(videoId: String, saver: Boolean) = videoId + if (saver) "|lo" else "|hi"

    /** The cached stream for a song at the current quality, if resolved recently. Never blocks. */
    fun peek(videoId: String): Resolved? = peek(videoId, dataSaver)

    private fun peek(videoId: String, saver: Boolean): Resolved? =
        cache[key(videoId, saver)]?.takeIf { it.expiresAtMs > System.currentTimeMillis() }

    /** Starts resolving in the background, e.g. the moment a finger touches a song. */
    fun prefetch(videoId: String) {
        if (!isValidVideoId(videoId) || peek(videoId) != null) return
        scope.launch { runCatching { resolve(videoId) } }
    }

    /**
     * Blocking network call. The quality is read once, at the start, and the whole lookup uses
     * that snapshot. Concurrent calls for one song and quality share a single lookup.
     */
    fun resolve(videoId: String): Resolved {
        if (!isValidVideoId(videoId)) throw IOException("Invalid video id")
        val saver = dataSaver
        peek(videoId, saver)?.let { return it }
        val k = key(videoId, saver)
        synchronized(locks.getOrPut(k) { Any() }) {
            peek(videoId, saver)?.let { return it }
            val resolved = (if (fastPathAllowed(videoId)) fetchFastOrNull(videoId, saver) else null)
                ?: fetchFull(videoId, saver)
            cache[k] = resolved
            return resolved
        }
    }

    /** Songs YouTube relates to this one (for radio and autoplay). Blocking; cached; shared in flight. */
    fun related(videoId: String): List<Song> {
        if (!isValidVideoId(videoId)) return emptyList()
        relatedCache[videoId]?.let { return it }
        synchronized(locks.getOrPut("rel|$videoId") { Any() }) {
            relatedCache[videoId]?.let { return it }
            val info = YouTube.streamInfo(videoId)
            val related = info.relatedItems.mapNotNull { (it as? StreamInfoItem)?.toSong() }.filter { it.id != videoId }
            relatedCache[videoId] = related
            return related
        }
    }

    /** Drops a stream that failed to play (both qualities); the retry uses the full extractor. */
    fun invalidate(videoId: String) {
        val failed = listOfNotNull(cache.remove(key(videoId, false)), cache.remove(key(videoId, true)))
        if (failed.any { it.fast } && fullOnly[videoId] == null) {
            fullOnly[videoId] = true
            if (fastPlaybackFailures.incrementAndGet() >= 2) pauseFastPath()
        }
    }

    private fun fastPathAllowed(videoId: String) =
        fullOnly[videoId] == null && System.currentTimeMillis() >= fastPausedUntilMs

    private fun pauseFastPath() {
        fastPausedUntilMs = System.currentTimeMillis() + FAST_PATH_PAUSE_MS
        fastPlaybackFailures.set(0)
        fastLookupFailuresInARow.set(0)
    }

    // ---------------------------------------------------------------- fast path

    private fun fetchFastOrNull(videoId: String, saver: Boolean): Resolved? = try {
        fetchFast(videoId, saver).also { fastLookupFailuresInARow.set(0) }
    } catch (e: Exception) {
        // if the fast lookup itself keeps failing (YouTube changed something), stop paying for it
        if (fastLookupFailuresInARow.incrementAndGet() >= 3) pauseFastPath()
        null
    }

    private fun fetchFast(videoId: String, saver: Boolean): Resolved {
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
        val preferred = audio.filterNot { it.getBoolean("isDrc", false) }.ifEmpty { audio }
        // one malformed format shouldn't sink the others
        val candidates = preferred.mapNotNull { runCatching { candidate(videoId, it, cpn) }.getOrNull() }
        val best = pick(candidates, saver) ?: throw IOException("No audio formats")
        return resolved(best, fast = true)
    }

    private fun candidate(videoId: String, format: JsonObject, cpn: String): Candidate {
        val mime = format.getString("mimeType", "")
        val url = YoutubeJavaScriptPlayerManager.getUrlWithThrottlingParameterDeobfuscated(videoId, format.getString("url")) +
            "&" + YoutubeParsingHelper.CPN + "=" + cpn
        val bitrate = maxOf(format.getInt("averageBitrate", 0), format.getInt("bitrate", 0))
        return Candidate(
            url, bitrate / 1000, mime.contains("opus"), mime.startsWith("audio/mp4"),
            itag = format.getInt("itag", 0), drc = format.getBoolean("isDrc", false),
        )
    }

    // ---------------------------------------------------------------- full extractor (fallback)

    private fun fetchFull(videoId: String, saver: Boolean): Resolved {
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
                itag = it.itag,
                drc = isDrc(it),
            )
        }
        val best = pick(candidates, saver) ?: throw IOException("No playable audio stream for $videoId")
        return resolved(best, fast = false)
    }

    private fun isDrc(stream: AudioStream) =
        stream.itagItem?.isDrc == true || stream.content.contains("drc%3D1") || stream.content.contains("drc=1")

    // ---------------------------------------------------------------- shared

    private fun resolved(c: Candidate, fast: Boolean) = Resolved(
        url = c.url,
        headers = headersFor(c.url),
        expiresAtMs = expiryOf(c.url),
        quality = qualityLabel(c),
        rendition = "${c.itag}${if (c.drc) "d" else ""}",
        fast = fast,
    )

    /** Highest bitrate (Opus wins ties), or the lowest in data-saver mode. */
    internal fun <T> pickBest(items: List<T>, saver: Boolean, kbps: (T) -> Int, opus: (T) -> Boolean): T? {
        val order = compareBy<T>({ kbps(it) }, { if (opus(it)) 1 else 0 })
        return if (saver) items.minWithOrNull(order) else items.maxWithOrNull(order)
    }

    private fun pick(candidates: List<Candidate>, saver: Boolean): Candidate? =
        pickBest(candidates, saver, { it.kbps }, { it.opus })

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

    private const val FAST_PATH_PAUSE_MS = 10 * 60_000L
}
