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
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
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

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var future: ListenableFuture<MediaController>? = null
    private var pending: ((MediaController) -> Unit)? = null

    fun connect(context: Context) {
        if (future != null) return
        val token = SessionToken(context, ComponentName(context, PlaybackService::class.java))
        val f = MediaController.Builder(context, token).buildAsync()
        future = f
        f.addListener({
            val c = runCatching { f.get() }.getOrNull() ?: return@addListener
            c.addListener(listener)
            controller = c
            sync(c)
            pending?.invoke(c)
            pending = null
        }, ContextCompat.getMainExecutor(context))
    }

    fun disconnect() {
        future?.let(MediaController::releaseFuture)
        future = null
        controller = null
    }

    private val listener = object : Player.Listener {
        override fun onEvents(player: Player, events: Player.Events) = sync(player)

        override fun onPlayerError(error: PlaybackException) {
            this@PlayerConnection.error = error.message
        }
    }

    private fun sync(p: Player) {
        current = p.currentMediaItem?.toSong()
        isPlaying = p.isPlaying
        isBuffering = p.playbackState == Player.STATE_BUFFERING
        durationMs = p.duration.coerceAtLeast(0)
        shuffle = p.shuffleModeEnabled
        repeatMode = p.repeatMode
        index = p.currentMediaItemIndex
        queue = (0 until p.mediaItemCount).map { p.getMediaItemAt(it).toSong() }
    }

    // Runs now if connected, otherwise as soon as the controller is ready
    private fun withController(action: (MediaController) -> Unit) {
        controller?.let(action) ?: run { pending = action }
    }

    fun play(songs: List<Song>, startIndex: Int = 0, shuffled: Boolean = false) {
        if (songs.isEmpty()) return
        withController { c ->
            c.shuffleModeEnabled = shuffled
            c.setMediaItems(songs.map { it.toMediaItem() }, startIndex, 0)
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

    /** Plays the song now, then queues songs YouTube relates to it. */
    fun startRadio(song: Song) {
        play(listOf(song))
        scope.launch {
            val related = withContext(Dispatchers.IO) {
                runCatching { StreamResolver.related(song.id) }.getOrDefault(emptyList())
            }
            withController { c ->
                val queued = (0 until c.mediaItemCount).map { c.getMediaItemAt(it).mediaId }.toSet()
                c.addMediaItems(related.filter { it.id !in queued }.take(25).map { it.toMediaItem() })
            }
        }
    }

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
