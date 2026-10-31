package com.spitify.app.playback

import android.os.Handler
import android.os.Looper
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/** Pauses playback after a delay, or when the current song ends. */
object SleepTimer {
    sealed interface State {
        data object Off : State
        data class At(val endsAtMs: Long) : State
        data object EndOfSong : State
    }

    private val handler = Handler(Looper.getMainLooper())
    private val _state = MutableStateFlow<State>(State.Off)
    val state: StateFlow<State> = _state

    private val fire = Runnable {
        PlaybackService.player?.pause()
        _state.value = State.Off
    }

    fun start(minutes: Int) {
        cancel()
        _state.value = State.At(System.currentTimeMillis() + minutes * 60_000L)
        handler.postDelayed(fire, minutes * 60_000L)
    }

    fun endOfSong() {
        cancel()
        _state.value = State.EndOfSong
        PlaybackService.player?.pauseAtEndOfMediaItems = true
    }

    fun cancel() {
        handler.removeCallbacks(fire)
        if (_state.value == State.EndOfSong) PlaybackService.player?.pauseAtEndOfMediaItems = false
        _state.value = State.Off
    }

    /** Called by the service once the "end of song" pause has happened. */
    internal fun onSongEndPause() {
        if (_state.value == State.EndOfSong) {
            PlaybackService.player?.pauseAtEndOfMediaItems = false
            _state.value = State.Off
        }
    }
}
