package com.tunehub.app.playback

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.media.audiofx.AudioEffect
import android.net.Uri
import android.widget.Toast
import androidx.annotation.OptIn
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.Timeline
import androidx.media3.common.util.UnstableApi
import androidx.media3.database.StandaloneDatabaseProvider
import androidx.media3.datasource.DataSpec
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.okhttp.OkHttpDataSource
import androidx.media3.datasource.HttpDataSource
import androidx.media3.datasource.ResolvingDataSource
import androidx.media3.datasource.cache.CacheDataSource
import androidx.media3.datasource.cache.LeastRecentlyUsedCacheEvictor
import androidx.media3.datasource.cache.SimpleCache
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture
import com.tunehub.app.TuneHubApp
import com.tunehub.app.data.StreamResolver
import com.tunehub.app.data.isValidVideoId
import com.tunehub.app.ui.MainActivity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException
import java.util.concurrent.atomic.AtomicLong

/**
 * The invisible "backend": an ExoPlayer that keeps playing with the screen off, exposed
 * through a MediaSession so the notification, lock screen and headphones can control it.
 */
@OptIn(UnstableApi::class)
class PlaybackService : MediaSessionService() {

    private var session: MediaSession? = null
    private lateinit var exo: ExoPlayer
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private val requestNumber = AtomicLong()

    private var retriedId: String? = null
    private var radioSeedId: String? = null
    private var failuresInARow = 0
    private var announcedSessionId = C.AUDIO_SESSION_ID_UNSET

    /** Bumped whenever the queue is replaced, so slow radio lookups for an old queue are dropped. */
    private var queueGeneration = 0

    override fun onCreate() {
        super.onCreate()

        // The stream is resolved first, so the cache key names the exact audio file (rendition);
        // downloaded songs come back as file:// and skip the streaming cache entirely.
        // The app's OkHttp client keeps warm HTTP/2 connections to googlevideo.
        val cached = CacheDataSource.Factory()
            .setCache(cache(this))
            .setUpstreamDataSourceFactory(OkHttpDataSource.Factory(TuneHubApp.http))
            .setFlags(CacheDataSource.FLAG_IGNORE_CACHE_ON_ERROR)
        val routed = DefaultDataSource.Factory(this, cached)
        val resolving = ResolvingDataSource.Factory(routed) { spec -> resolve(spec) }

        exo = ExoPlayer.Builder(this)
            .setMediaSourceFactory(DefaultMediaSourceFactory(resolving))
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(C.USAGE_MEDIA)
                    .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
                    .build(),
                true,
            )
            .setHandleAudioBecomingNoisy(true)
            .setWakeMode(C.WAKE_MODE_NETWORK)
            // start after half a second of audio instead of the default 2.5 s
            .setLoadControl(
                DefaultLoadControl.Builder()
                    .setBufferDurationsMs(15_000, 60_000, 500, 2_000)
                    .build()
            )
            .build()
        exo.addListener(listener)
        player = exo
        onAudioSession(exo.audioSessionId)
        scope.launch {
            TuneHubApp.library.data.map { it.eq }.distinctUntilChanged().collect { AudioFx.apply(it) }
        }
        // the cache before rendition-keyed entries; its keys can't be trusted any more
        scope.launch(Dispatchers.IO) { File(cacheDir, "media").deleteRecursively() }

        val openApp = PendingIntent.getActivity(
            this, 0, Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        session = MediaSession.Builder(this, exo)
            .setCallback(callback)
            .setSessionActivity(openApp)
            .build()
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo) = session

    override fun onTaskRemoved(rootIntent: Intent?) {
        // swiping the app away keeps music going, unless nothing is playing
        if (!exo.playWhenReady || exo.mediaItemCount == 0) stopSelf()
    }

    override fun onDestroy() {
        scope.cancel()
        AudioFx.release()
        SleepTimer.cancel()
        announce(announcedSessionId, open = false)
        player = null
        session?.run {
            exo.release()
            release()
        }
        session = null
        super.onDestroy()
    }

    /** Effects and external equalizers follow the player's real audio session. */
    private fun onAudioSession(sessionId: Int) {
        if (sessionId == announcedSessionId) return
        announce(announcedSessionId, open = false)
        if (sessionId != C.AUDIO_SESSION_ID_UNSET) {
            announce(sessionId, open = true)
            AudioFx.attach(sessionId)
        }
        announcedSessionId = sessionId
    }

    // Lets system / third-party equalizers attach to our audio
    private fun announce(sessionId: Int, open: Boolean) {
        if (sessionId == C.AUDIO_SESSION_ID_UNSET) return
        val action = if (open) AudioEffect.ACTION_OPEN_AUDIO_EFFECT_CONTROL_SESSION else AudioEffect.ACTION_CLOSE_AUDIO_EFFECT_CONTROL_SESSION
        runCatching {
            sendBroadcast(
                Intent(action)
                    .putExtra(AudioEffect.EXTRA_AUDIO_SESSION, sessionId)
                    .putExtra(AudioEffect.EXTRA_PACKAGE_NAME, packageName)
                    .putExtra(AudioEffect.EXTRA_CONTENT_TYPE, AudioEffect.CONTENT_TYPE_MUSIC)
            )
        }
    }

    /** Runs on ExoPlayer's loader thread, so the blocking lookup is fine here. */
    private fun resolve(spec: DataSpec): DataSpec {
        if (spec.uri.scheme != SONG_SCHEME) return spec
        val id = spec.uri.lastPathSegment?.takeIf(::isValidVideoId) ?: throw IOException("Invalid song")

        TuneHubApp.downloads.fileFor(id)?.let { return spec.buildUpon().setUri(Uri.fromFile(it)).build() }

        val stream = try {
            StreamResolver.resolve(id)
        } catch (e: IOException) {
            throw e
        } catch (e: Exception) {
            throw IOException("Couldn't load stream for $id", e)
        }
        // Same request shape NewPipe's player uses for googlevideo URLs
        return spec.buildUpon()
            .setUri(stream.url + "&rn=" + requestNumber.incrementAndGet())
            .setHttpMethod(DataSpec.HTTP_METHOD_POST)
            .setHttpBody(POST_BODY)
            .setHttpRequestHeaders(spec.httpRequestHeaders + stream.headers)
            .setKey("$CACHE_KEY_VERSION:$id:${stream.rendition}")
            .build()
    }

    private val callback = object : MediaSession.Callback {
        // Items from controllers arrive without their URI: rebuild it from a validated media id
        override fun onAddMediaItems(
            mediaSession: MediaSession,
            controller: MediaSession.ControllerInfo,
            mediaItems: MutableList<MediaItem>,
        ): ListenableFuture<MutableList<MediaItem>> = Futures.immediateFuture(
            mediaItems.filter { isValidVideoId(it.mediaId) }
                .map { it.buildUpon().setUri(songUri(it.mediaId)).build() }
                .toMutableList()
        )
    }

    private val listener = object : Player.Listener {
        override fun onAudioSessionIdChanged(audioSessionId: Int) = onAudioSession(audioSessionId)

        override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
            mediaItem ?: return
            if (reason == Player.MEDIA_ITEM_TRANSITION_REASON_PLAYLIST_CHANGED) {
                queueGeneration++
                radioSeedId = null
            }
            if (mediaItem.mediaId != retriedId) retriedId = null
            TuneHubApp.library.addRecent(mediaItem.toSong())
            trimPlayedHistory()
            prefetchNext()
            extendQueueIfNeeded()
        }

        override fun onTimelineChanged(timeline: Timeline, reason: Int) {
            extendQueueIfNeeded()
        }

        override fun onIsPlayingChanged(isPlaying: Boolean) {
            if (isPlaying) failuresInARow = 0
        }

        override fun onPlayWhenReadyChanged(playWhenReady: Boolean, reason: Int) {
            if (reason == Player.PLAY_WHEN_READY_CHANGE_REASON_END_OF_MEDIA_ITEM) SleepTimer.onSongEndPause()
        }

        override fun onPlayerError(error: PlaybackException) = handleError(error)
    }

    private fun handleError(error: PlaybackException) {
        val id = exo.currentMediaItem?.mediaId ?: return
        failuresInARow++

        // offline, rate-limited or YouTube having a bad moment: wait instead of burning through the queue
        if (isTemporaryOutage(error)) {
            toast(if (isNetworkDown(error)) "No connection" else "YouTube isn't responding. Try again in a moment")
            return
        }
        if (failuresInARow >= MAX_FAILURES_IN_A_ROW) {
            exo.pause()
            toast("Playback keeps failing. Check your connection, then press play")
            failuresInARow = 0
            return
        }
        if (retriedId != id) {
            // most often an expired or bad stream: look it up again and rebuild the source,
            // so a different rendition never reuses the old one's parser state
            retriedId = id
            StreamResolver.invalidate(id)
            reloadQueue(exo, resume = true)
            return
        }
        val title = exo.currentMediaItem?.mediaMetadata?.title ?: "song"
        toast("Couldn't play $title")
        if (exo.hasNextMediaItem()) {
            exo.seekToNextMediaItem()
            exo.prepare()
            exo.play()
        }
    }

    private fun isNetworkDown(error: PlaybackException) =
        error.errorCode == PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_FAILED ||
            error.errorCode == PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_TIMEOUT

    private fun isTemporaryOutage(error: PlaybackException): Boolean {
        if (isNetworkDown(error)) return true
        val http = generateSequence(error.cause) { it.cause }
            .filterIsInstance<HttpDataSource.InvalidResponseCodeException>().firstOrNull()
        return http != null && (http.responseCode == 429 || http.responseCode >= 500)
    }

    private fun toast(message: String) = Toast.makeText(this, message, Toast.LENGTH_SHORT).show()

    /** Resolve the next song's stream early so skipping is instant. */
    private fun prefetchNext() {
        val next = exo.nextMediaItemIndex.takeIf { it != C.INDEX_UNSET } ?: return
        val id = exo.getMediaItemAt(next).mediaId
        if (TuneHubApp.downloads.fileFor(id) != null) return
        StreamResolver.prefetch(id)
    }

    /** Keeps at most [HISTORY_KEPT] already-played songs in the queue so long sessions stay light. */
    private fun trimPlayedHistory() {
        val index = exo.currentMediaItemIndex
        if (index > HISTORY_KEPT + 10) exo.removeMediaItems(0, index - HISTORY_KEPT)
    }

    /**
     * Radio and autoplay: when the queue is about to run out, append songs related to the current
     * one. Runs for every queue when autoplay is on, and for "song radio" queues always. The
     * result is only used if the queue it was fetched for is still the one playing.
     */
    private fun extendQueueIfNeeded() {
        if (exo.repeatMode != Player.REPEAT_MODE_OFF || exo.mediaItemCount == 0) return
        if (exo.currentMediaItemIndex < exo.mediaItemCount - 2) return
        if (!radioWanted()) return
        val seed = exo.currentMediaItem?.mediaId ?: return
        if (seed == radioSeedId) return
        radioSeedId = seed
        val generation = queueGeneration

        scope.launch {
            val related = withContext(Dispatchers.IO) {
                runCatching { StreamResolver.related(seed) }.getOrDefault(emptyList())
            }
            val stillValid = generation == queueGeneration &&
                exo.currentMediaItem?.mediaId == seed &&
                exo.repeatMode == Player.REPEAT_MODE_OFF &&
                radioWanted()
            if (!stillValid) return@launch
            val queued = (0 until exo.mediaItemCount).map { exo.getMediaItemAt(it).mediaId }.toSet()
            val additions = related.filter { it.id !in queued }.take(RADIO_BATCH)
            if (additions.isEmpty()) return@launch
            val endedWhileWaiting = exo.playbackState == Player.STATE_ENDED && exo.playWhenReady
            exo.addMediaItems(additions.map { it.toMediaItem() })
            // the last song finished before the related songs arrived: carry on, as the user expects
            if (endedWhileWaiting) {
                exo.seekToNextMediaItem()
                exo.prepare()
                exo.play()
            }
        }
    }

    private fun radioWanted() =
        TuneHubApp.library.data.value.autoplay || (exo.mediaItemCount > 0 && exo.getMediaItemAt(0).isRadio())

    companion object {
        private val POST_BODY = byteArrayOf(0x78, 0)
        private const val RADIO_BATCH = 10
        private const val HISTORY_KEPT = 50
        private const val MAX_FAILURES_IN_A_ROW = 3
        private const val CACHE_KEY_VERSION = "v2"
        private const val CACHE_BYTES = 512L * 1024 * 1024

        /** The live player, for the sleep timer and equalizer. Null when the service isn't running. */
        var player: ExoPlayer? = null
            private set

        private var sharedCache: SimpleCache? = null

        // SimpleCache allows one instance per folder per process, and the service can be recreated
        @Synchronized
        private fun cache(context: Context): SimpleCache = sharedCache ?: SimpleCache(
            File(context.cacheDir, "media2"),
            LeastRecentlyUsedCacheEvictor(CACHE_BYTES),
            StandaloneDatabaseProvider(context),
        ).also { sharedCache = it }

        fun cacheBytes(): Long = sharedCache?.cacheSpace ?: 0L

        /** Reloads the queue at the same spot, e.g. so a quality switch takes effect right away. */
        fun reloadCurrent() {
            val p = player ?: return
            reloadQueue(p, resume = p.playWhenReady)
        }

        // a fresh media source for the current song, at the same position
        private fun reloadQueue(p: ExoPlayer, resume: Boolean) {
            if (p.mediaItemCount == 0) return
            val items = (0 until p.mediaItemCount).map { p.getMediaItemAt(it) }
            val index = p.currentMediaItemIndex
            val position = p.currentPosition
            p.setMediaItems(items, index, position)
            p.prepare()
            p.playWhenReady = resume
        }
    }
}
