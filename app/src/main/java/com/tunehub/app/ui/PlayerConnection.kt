package com.tunehub.app.ui

import android.content.ComponentName
import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.content.ContextCompat
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.google.common.util.concurrent.ListenableFuture
import com.tunehub.app.TuneHubApp
import com.tunehub.app.data.Song
import com.tunehub.app.data.StreamResolver
import com.tunehub.app.playback.PlaybackService
import com.tunehub.app.playback.toMediaItem
import com.tunehub.app.playback.toSong

/** The UI's handle on PlaybackService, mirrored into Compose state. */
object PlayerConnection {
    var controller by mutableStateOf<MediaController?>(null)
        private set
    var current by mutableStateOf<Song?>(null)
        private set
    var isPlaying by mutableStateOf(false)
        private set
    var isBuffering by mutableStateOf(false)
        private set
    var queue by mutableStateOf<List<Song>>(emptyList())
        private set
    var index by mutableIntStateOf(0)
        private set
    var shuffle by mutableStateOf(false)
        private set
    var repeatMode by mutableIntStateOf(Player.REPEAT_MODE_OFF)
        private set
    var durationMs by mutableLongStateOf(0L)
        private set
    var error by mutableStateOf<String?>(null)

    private var appContext: Context? = null
    private var started = false
    private var future: ListenableFuture<MediaController>? = null

    // commands issued before the controller connects, run in order once it does
    private val pending = ArrayDeque<(MediaController) -> Unit>()

    /** Call from the Activity's onStart; reconnects by itself if the connection drops while started. */
    fun connect(context: Context) {
        appContext = context.applicationContext
        started = true
        ensureConnected()
    }

    /** Call from the Activity's onStop. */
    fun disconnect() {
        started = false
        future?.let(MediaController::releaseFuture)
        future = null
        controller = null
    }

    private fun ensureConnected() {
        val context = appContext ?: return
        if (!started || future != null) return
        val token = SessionToken(context, ComponentName(context, PlaybackService::class.java))
        val f = MediaController.Builder(context, token)
            .setListener(object : MediaController.Listener {
                override fun onDisconnected(controller: MediaController) {
                    if (this@PlayerConnection.controller === controller) {
                        this@PlayerConnection.controller = null
                        future = null
                        ensureConnected()
                    }
                }
            })
            .buildAsync()
        future = f
        f.addListener({
            // a newer connect/disconnect happened while this one was in flight
            if (future !== f) {
                MediaController.releaseFuture(f)
                return@addListener
            }
            val c = runCatching { f.get() }.getOrNull()
            if (c == null) {
                future = null
                error = "Couldn't connect to the player"
                return@addListener
            }
            c.addListener(listener)
            controller = c
            sync(c, timelineChanged = true)
            while (pending.isNotEmpty()) pending.removeFirst().invoke(c)
        }, ContextCompat.getMainExecutor(context))
    }

    private val listener = object : Player.Listener {
        override fun onEvents(player: Player, events: Player.Events) =
            sync(player, timelineChanged = events.contains(Player.EVENT_TIMELINE_CHANGED))

        override fun onPlayerError(error: PlaybackException) {
            this@PlayerConnection.error = error.message
        }
    }

    private fun sync(p: Player, timelineChanged: Boolean) {
        current = p.currentMediaItem?.toSong()
        isPlaying = p.isPlaying
        isBuffering = p.playbackState == Player.STATE_BUFFERING
        durationMs = p.duration.coerceAtLeast(0)
        shuffle = p.shuffleModeEnabled
        repeatMode = p.repeatMode
        index = p.currentMediaItemIndex
        // rebuilding the whole queue on every position or play/pause event is wasted work
        if (timelineChanged) queue = (0 until p.mediaItemCount).map { p.getMediaItemAt(it).toSong() }
    }

    /**
     * Runs now if connected, otherwise once the controller is ready. A command that replaces the
     * queue ([replaces]) drops pending ones, since they'd apply to a queue that's about to go.
     */
    private fun withController(replaces: Boolean = false, action: (MediaController) -> Unit) {
        val c = controller
        if (c != null) {
            action(c)
            return
        }
        if (replaces) pending.clear()
        pending.addLast(action)
        ensureConnected()
    }

    fun play(songs: List<Song>, startIndex: Int = 0, shuffled: Boolean = false, radio: Boolean = false) {
        if (songs.isEmpty()) return
        val items = songs.mapIndexed { i, song -> song.toMediaItem(radio = radio && i == 0) }
        withController(replaces = true) { c ->
            c.shuffleModeEnabled = shuffled
            c.setMediaItems(items, startIndex, 0)
            c.prepare()
            c.play()
        }
    }

    fun playNext(song: Song) = withController { c ->
        if (c.mediaItemCount == 0) {
            play(listOf(song))
        } else {
            c.addMediaItem(c.currentMediaItemIndex + 1, song.toMediaItem())
        }
    }

    fun enqueue(song: Song) = withController { c ->
        if (c.mediaItemCount == 0) play(listOf(song)) else c.addMediaItem(song.toMediaItem())
    }

    fun togglePlay() = withController { c ->
        if (c.isPlaying) {
            c.pause()
        } else {
            if (c.playbackState == Player.STATE_IDLE) c.prepare()
            if (c.playbackState == Player.STATE_ENDED) c.seekToDefaultPosition()
            c.play()
        }
    }

    /**
     * Plays the song now as the seed of a radio: the playback service keeps the queue going with
     * related songs (it owns radio, so a stale lookup can never land on a newer queue).
     */
    fun startRadio(song: Song) = play(listOf(song), radio = true)

    fun clearUpcoming() = withController { c ->
        val from = c.currentMediaItemIndex + 1
        if (from < c.mediaItemCount) c.removeMediaItems(from, c.mediaItemCount)
    }

    fun move(from: Int, to: Int) = withController { c ->
        if (from in 0 until c.mediaItemCount && to in 0 until c.mediaItemCount) c.moveMediaItem(from, to)
    }

    /** Switches stream quality and reloads the current song at the same spot so it applies now. */
    fun setHiRes(on: Boolean) {
        TuneHubApp.library.setDataSaver(!on)
        StreamResolver.dataSaver = !on
        PlaybackService.reloadCurrent()
    }

    fun next() = withController { it.seekToNext() }
    fun previous() = withController { it.seekToPrevious() }
    fun seekTo(ms: Long) = withController { it.seekTo(ms) }
    fun jumpTo(i: Int) = withController { it.seekTo(i, 0); it.play() }
    fun removeAt(i: Int) = withController { it.removeMediaItem(i) }
    fun toggleShuffle() = withController { it.shuffleModeEnabled = !it.shuffleModeEnabled }

    fun cycleRepeat() = withController {
        it.repeatMode = when (it.repeatMode) {
            Player.REPEAT_MODE_OFF -> Player.REPEAT_MODE_ALL
            Player.REPEAT_MODE_ALL -> Player.REPEAT_MODE_ONE
            else -> Player.REPEAT_MODE_OFF
        }
    }

    fun positionMs(): Long = controller?.currentPosition ?: 0L
}
