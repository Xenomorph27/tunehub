package com.spitify.app.playback

import android.app.PendingIntent
import android.content.Intent
import android.widget.Toast
import androidx.annotation.OptIn
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.Timeline
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DataSpec
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.datasource.ResolvingDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture
import com.spitify.app.SpitifyApp
import com.spitify.app.data.StreamResolver
import com.spitify.app.ui.MainActivity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.IOException
import java.util.concurrent.atomic.AtomicLong

/**
 * The invisible "backend": an ExoPlayer that keeps playing with the screen off, exposed
 * through a MediaSession so the notification, lock screen and headphones can control it.
 */
@OptIn(UnstableApi::class)
class PlaybackService : MediaSessionService() {

    private var session: MediaSession? = null
    private lateinit var player: ExoPlayer
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private val requestNumber = AtomicLong()

    private var retriedId: String? = null
    private var radioSeedId: String? = null

    override fun onCreate() {
        super.onCreate()

        val http = DefaultHttpDataSource.Factory()
            .setAllowCrossProtocolRedirects(true)
            .setConnectTimeoutMs(15_000)
            .setReadTimeoutMs(20_000)
        val dataSource = ResolvingDataSource.Factory(http) { spec -> resolve(spec) }

        player = ExoPlayer.Builder(this)
            .setMediaSourceFactory(DefaultMediaSourceFactory(dataSource))
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(C.USAGE_MEDIA)
                    .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
                    .build(),
                true,
            )
            .setHandleAudioBecomingNoisy(true)
            .setWakeMode(C.WAKE_MODE_NETWORK)
            .build()
        player.addListener(listener)

        val openApp = PendingIntent.getActivity(
            this, 0, Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        session = MediaSession.Builder(this, player)
            .setCallback(callback)
            .setSessionActivity(openApp)
            .build()
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo) = session

    override fun onTaskRemoved(rootIntent: Intent?) {
        // swiping the app away keeps music going, unless nothing is playing
        if (!player.playWhenReady || player.mediaItemCount == 0) stopSelf()
    }

    override fun onDestroy() {
        scope.cancel()
        session?.run {
            player.release()
            release()
        }
        session = null
        super.onDestroy()
    }

    /** Runs on ExoPlayer's loader thread, so the blocking lookup is fine here. */
    private fun resolve(spec: DataSpec): DataSpec {
        if (spec.uri.scheme != SONG_SCHEME) return spec
        val id = spec.uri.lastPathSegment ?: return spec
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
            SpitifyApp.library.addRecent(mediaItem.toSong())
            retriedId = null
            prefetchNext()
            extendQueueIfNeeded()
        }

        override fun onTimelineChanged(timeline: Timeline, reason: Int) {
            extendQueueIfNeeded()
        }

        override fun onPlayerError(error: PlaybackException) {
            val id = player.currentMediaItem?.mediaId ?: return
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
                player.prepare()
                player.play()
                return
            }
            val title = player.currentMediaItem?.mediaMetadata?.title ?: "song"
            Toast.makeText(this@PlaybackService, "Couldn't play $title", Toast.LENGTH_SHORT).show()
            if (player.hasNextMediaItem()) {
                player.seekToNextMediaItem()
                player.prepare()
                player.play()
            }
        }
    }

    /** Resolve the next song's stream early so skipping is instant. */
    private fun prefetchNext() {
        val next = player.nextMediaItemIndex.takeIf { it != C.INDEX_UNSET } ?: return
        val id = player.getMediaItemAt(next).mediaId
        scope.launch(Dispatchers.IO) { runCatching { StreamResolver.resolve(id) } }
    }

    /** Autoplay: when the queue is about to run out, append songs related to the current one. */
    private fun extendQueueIfNeeded() {
        if (!SpitifyApp.library.data.value.autoplay) return
        if (player.repeatMode != Player.REPEAT_MODE_OFF || player.mediaItemCount == 0) return
        if (player.currentMediaItemIndex < player.mediaItemCount - 2) return
        val seed = player.currentMediaItem?.mediaId ?: return
        if (seed == radioSeedId) return
        radioSeedId = seed

        scope.launch {
            val related = withContext(Dispatchers.IO) {
                runCatching { StreamResolver.resolve(seed).related }.getOrDefault(emptyList())
            }
            val queued = (0 until player.mediaItemCount).map { player.getMediaItemAt(it).mediaId }.toSet()
            val additions = related.filter { it.id !in queued }.take(RADIO_BATCH)
            player.addMediaItems(additions.map { it.toMediaItem() })
        }
    }

    companion object {
        private val POST_BODY = byteArrayOf(0x78, 0)
        private const val RADIO_BATCH = 10
    }
}
