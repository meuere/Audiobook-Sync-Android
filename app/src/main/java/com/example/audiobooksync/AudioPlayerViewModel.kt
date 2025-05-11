package com.example.audiobooksync

import android.app.Application
import android.net.Uri
import android.os.CountDownTimer
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.media3.common.C // For C.TIME_UNSET
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

const val SKIP_INTERVAL_MS = 15000L // 15 seconds

class AudioPlayerViewModel(application: Application) : AndroidViewModel(application) {

    var selectedFileUri by mutableStateOf<Uri?>(null)
        private set
    var isPlaying by mutableStateOf(false)
        private set

    private val _currentPositionMs = MutableStateFlow(0L)
    val currentPositionMs: StateFlow<Long> = _currentPositionMs.asStateFlow()

    private val _totalDurationMs = MutableStateFlow(0L)
    val totalDurationMs: StateFlow<Long> = _totalDurationMs.asStateFlow()

    private var positionPollingJob: Job? = null

    val exoPlayer: ExoPlayer by lazy {
        ExoPlayer.Builder(application).build().apply {
            addListener(object : Player.Listener {
                override fun onIsPlayingChanged(playing: Boolean) {
                    this@AudioPlayerViewModel.isPlaying = playing
                    // Polling logic is now tied to playWhenReady
                }

                override fun onPlaybackStateChanged(playbackState: Int) {
                    when (playbackState) {
                        Player.STATE_READY -> {
                            val duration = exoPlayer.duration
                            _totalDurationMs.value = if (duration > 0 && duration != C.TIME_UNSET) duration else 0L
                        }
                        Player.STATE_ENDED -> {
                            // Set position to end, or 0 if you prefer reset
                            _currentPositionMs.value = exoPlayer.duration.takeIf { it != C.TIME_UNSET } ?: 0L
                            stopPositionPolling()
                        }
                        Player.STATE_IDLE -> {
                            _totalDurationMs.value = 0L
                            _currentPositionMs.value = 0L
                            stopPositionPolling()
                        }
                        // Player.STATE_BUFFERING can also be handled if needed
                    }
                }

                override fun onPlayWhenReadyChanged(playWhenReady: Boolean, reason: Int) {
                    if (playWhenReady && exoPlayer.playbackState != Player.STATE_ENDED && exoPlayer.playbackState != Player.STATE_IDLE) {
                        startPositionPolling()
                    } else {
                        stopPositionPolling()
                        // Capture position when paused manually
                        if (exoPlayer.playbackState == Player.STATE_READY || exoPlayer.playbackState == Player.STATE_BUFFERING) {
                            _currentPositionMs.value = exoPlayer.currentPosition
                        }
                    }
                }
            })
        }
    }

    private fun startPositionPolling() {
        stopPositionPolling() // Ensure only one poller is active
        positionPollingJob = viewModelScope.launch {
            while (isActive) { // kotlinx.coroutines.isActive
                _currentPositionMs.value = exoPlayer.currentPosition
                // Update total duration defensively if it wasn't set or changed (for some dynamic streams)
                val currentDuration = exoPlayer.duration
                if (currentDuration > 0 && currentDuration != C.TIME_UNSET && _totalDurationMs.value != currentDuration) {
                    _totalDurationMs.value = currentDuration
                }
                delay(500L) // Update UI every 500ms for smoother progress
            }
        }
    }

    private fun stopPositionPolling() {
        positionPollingJob?.cancel()
        positionPollingJob = null
    }

    fun onFileSelected(uri: Uri?) {
        selectedFileUri = uri
        _currentPositionMs.value = 0L // Reset position for new file
        _totalDurationMs.value = 0L   // Reset duration for new file
        uri?.let {
            val mediaItem = MediaItem.fromUri(it)
            exoPlayer.setMediaItem(mediaItem)
            exoPlayer.prepare()
            // Consider auto-play or let user press play
        } ?: run {
            exoPlayer.stop()
            exoPlayer.clearMediaItems()
            isPlaying = false
        }
    }

    fun playPause() {
        if (selectedFileUri == null && exoPlayer.mediaItemCount == 0) return

        if (exoPlayer.isPlaying) {
            exoPlayer.pause()
        } else {
            // If playback ended, seek to start before playing again.
            if (exoPlayer.playbackState == Player.STATE_ENDED) {
                exoPlayer.seekTo(0)
            }
            exoPlayer.play()
        }
    }

    fun seekTo(positionMs: Long) {
        if (exoPlayer.isCommandAvailable(Player.COMMAND_SEEK_IN_CURRENT_MEDIA_ITEM)) {
            exoPlayer.seekTo(positionMs.coerceIn(0, _totalDurationMs.value))
            _currentPositionMs.value = exoPlayer.currentPosition // Update position immediately after seek
        }
    }

    fun skipForward() {
        val newPosition = (exoPlayer.currentPosition + SKIP_INTERVAL_MS).coerceAtMost(_totalDurationMs.value)
        seekTo(newPosition)
    }

    fun skipBackward() {
        val newPosition = (exoPlayer.currentPosition - SKIP_INTERVAL_MS).coerceAtLeast(0L)
        seekTo(newPosition)
    }

    // Sleep Timer (existing code)
    private var sleepTimer: CountDownTimer? = null
    var sleepTimerRemainingSeconds by mutableStateOf(0L)
        private set
    var isSleepTimerRunning by mutableStateOf(false)
        private set

    fun startSleepTimer(minutes: Long) { /* ... existing code ... */ }
    fun cancelSleepTimer() { /* ... existing code ... */ }

    override fun onCleared() {
        super.onCleared()
        stopPositionPolling()
        exoPlayer.release()
        cancelSleepTimer()
    }
}