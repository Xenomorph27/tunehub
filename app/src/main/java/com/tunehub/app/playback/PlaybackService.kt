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
import com.tunehub.app.ui.MainActivity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
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

    override fun onCreate() {
        super.onCreate()

        // the app's OkHttp client: its warm HTTP/2 connections to googlevideo make songs start faster
        val http = OkHttpDataSource.Factory(TuneHubApp.http)
        // DefaultDataSource also reads file:// URIs, which is how downloaded songs play
        val resolving = ResolvingDataSource.Factory(DefaultDataSource.Factory(this, http)) { spec -> resolve(spec) }
        // cache keyed by the stable tunehub:// URI, so replays and seeks skip the network
        val cached = CacheDataSource.Factory()
            .setCache(cache(this))
            .setUpstreamDataSourceFactory(resolving)
            .setFlags(CacheDataSource.FLAG_IGNORE_CACHE_ON_ERROR)

        exo = ExoPlayer.Builder(this)
            .setMediaSourceFactory(DefaultMediaSourceFactory(cached))
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
        announceAudioSession(true)

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
        SleepTimer.cancel()
        announceAudioSession(false)
        player = null
        session?.run {
            exo.release()
            release()
        }
        session = null
        super.onDestroy()
    }

    // Lets system / third-party equalizers attach to our audio
    private fun announceAudioSession(open: Boolean) {
        val action = if (open) {
            AudioEffect.ACTION_OPEN_AUDIO_EFFECT_CONTROL_SESSION
        } else {
            AudioEffect.ACTION_CLOSE_AUDIO_EFFECT_CONTROL_SESSION
        }
        sendBroadcast(
            Intent(action)
                .putExtra(AudioEffect.EXTRA_AUDIO_SESSION, exo.audioSessionId)
                .putExtra(AudioEffect.EXTRA_PACKAGE_NAME, packageName)
                .putExtra(AudioEffect.EXTRA_CONTENT_TYPE, AudioEffect.CONTENT_TYPE_MUSIC)
        )
    }

    /** Runs on ExoPlayer's loader thread, so the blocking lookup is fine here. */
    private fun resolve(spec: DataSpec): DataSpec {
        if (spec.uri.scheme != SONG_SCHEME) return spec
        val id = spec.uri.lastPathSegment ?: return spec

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
            .build()
    }

    private val callback = object : MediaSession.Callback {
        // Items from the UI arrive without their URI, so rebuild it from the media id
        override fun onAddMediaItems(
            mediaSession: MediaSession,
            controller: MediaSession.ControllerInfo,
            mediaItems: MutableList<MediaItem>,
        ): ListenableFuture<MutableList<MediaItem>> = Futures.immediateFuture(
            mediaItems.map { it.buildUpon().setUri(songUri(it.mediaId)).build() }.toMutableList()
        )
    }

    private val listener = object : Player.Listener {
        override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
            mediaItem ?: return
            if (reason == Player.MEDIA_ITEM_TRANSITION_REASON_PLAYLIST_CHANGED) radioSeedId = null
            TuneHubApp.library.addRecent(mediaItem.toSong())
            retriedId = null
            prefetchNext()
            extendQueueIfNeeded()
        }

        override fun onTimelineChanged(timeline: Timeline, reason: Int) {
            extendQueueIfNeeded()
        }

        override fun onPlayWhenReadyChanged(playWhenReady: Boolean, reason: Int) {
            if (reason == Player.PLAY_WHEN_READY_CHANGE_REASON_END_OF_MEDIA_ITEM) SleepTimer.onSongEndPause()
        }

        override fun onPlayerError(error: PlaybackException) {
            val id = exo.currentMediaItem?.mediaId ?: return
            if (error.errorCode == PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_FAILED ||
                error.errorCode == PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_TIMEOUT
            ) {
                // offline: don't burn through the queue, just wait for the user to hit play again
                Toast.makeText(this@PlaybackService, "No connection", Toast.LENGTH_SHORT).show()
                return
            }
            if (retriedId != id) {
                // most often an expired stream URL: fetch a fresh one and resume where we were
                retriedId = id
                StreamResolver.invalidate(id)
                exo.prepare()
                exo.play()
                return
            }
            val title = exo.currentMediaItem?.mediaMetadata?.title ?: "song"
            Toast.makeText(this@PlaybackService, "Couldn't play $title", Toast.LENGTH_SHORT).show()
            if (exo.hasNextMediaItem()) {
                exo.seekToNextMediaItem()
                exo.prepare()
                exo.play()
            }
        }
    }

    /** Resolve the next song's stream early so skipping is instant. */
    private fun prefetchNext() {
        val next = exo.nextMediaItemIndex.takeIf { it != C.INDEX_UNSET } ?: return
        val id = exo.getMediaItemAt(next).mediaId
        if (TuneHubApp.downloads.fileFor(id) != null) return
        scope.launch(Dispatchers.IO) { runCatching { StreamResolver.resolve(id) } }
    }

    /** Autoplay: when the queue is about to run out, append songs related to the current one. */
    private fun extendQueueIfNeeded() {
        if (!TuneHubApp.library.data.value.autoplay) return
        if (exo.repeatMode != Player.REPEAT_MODE_OFF || exo.mediaItemCount == 0) return
        if (exo.currentMediaItemIndex < exo.mediaItemCount - 2) return
        val seed = exo.currentMediaItem?.mediaId ?: return
        if (seed == radioSeedId) return
        radioSeedId = seed

        scope.launch {
            val related = withContext(Dispatchers.IO) {
                runCatching { StreamResolver.resolve(seed).related }.getOrDefault(emptyList())
            }
            val queued = (0 until exo.mediaItemCount).map { exo.getMediaItemAt(it).mediaId }.toSet()
            val additions = related.filter { it.id !in queued }.take(RADIO_BATCH)
            exo.addMediaItems(additions.map { it.toMediaItem() })
        }
    }

    companion object {
        private val POST_BODY = byteArrayOf(0x78, 0)
        private const val RADIO_BATCH = 10
        private const val CACHE_BYTES = 512L * 1024 * 1024

        /** The live player, for the sleep timer and equalizer. Null when the service isn't running. */
        var player: ExoPlayer? = null
            private set

        private var sharedCache: SimpleCache? = null

        // SimpleCache allows one instance per folder per process, and the service can be recreated
        @Synchronized
        private fun cache(context: Context): SimpleCache = sharedCache ?: SimpleCache(
            File(context.cacheDir, "media"),
            LeastRecentlyUsedCacheEvictor(CACHE_BYTES),
            StandaloneDatabaseProvider(context),
        ).also { sharedCache = it }

        fun cacheBytes(): Long = sharedCache?.cacheSpace ?: 0L
    }
}
