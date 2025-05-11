package com.example.audiobooksync

import android.app.Application
import android.net.Uri
import android.os.CountDownTimer
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel


import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer

class AudioPlayerViewModel(application: Application) : AndroidViewModel(application) {

    var selectedFileUri by mutableStateOf<Uri?>(null)
        private set

    var isPlaying by mutableStateOf(false)
        private set

    // ExoPlayer instance
    // Consider lazy initialization if context is needed immediately from application
    val exoPlayer: ExoPlayer by lazy {
        ExoPlayer.Builder(application).build().apply {
            addListener(object : Player.Listener {
                override fun onIsPlayingChanged(playing: Boolean) {
                    this@AudioPlayerViewModel.isPlaying = playing
                }
            })
        }
    }


    // Sleep Timer
    private var sleepTimer: CountDownTimer? = null
    var sleepTimerRemainingSeconds by mutableStateOf(0L)
        private set
    var isSleepTimerRunning by mutableStateOf(false)
        private set

    // No need for init block to add listener if ExoPlayer is initialized with it in lazy delegate

    fun onFileSelected(uri: Uri?) {
        selectedFileUri = uri
        uri?.let {
            val mediaItem = MediaItem.fromUri(it)
            exoPlayer.setMediaItem(mediaItem)
            exoPlayer.prepare()
            // Optionally, you might want to auto-play: exoPlayer.play()
        } ?: run {
            // Handle case where URI is null (selection cancelled or failed)
            exoPlayer.stop() // Use stop to reset player state more completely
            exoPlayer.clearMediaItems()
            isPlaying = false
        }
    }

    fun playPause() {
        if (selectedFileUri == null && exoPlayer.mediaItemCount == 0) return // Nothing to play

        if (exoPlayer.playbackState == Player.STATE_IDLE || exoPlayer.playbackState == Player.STATE_ENDED) {
            // Re-prepare if needed, though setMediaItem and prepare should handle this.
            // If STATE_IDLE is due to an error, you might need more robust error handling.
            exoPlayer.prepare()
            exoPlayer.play()
        } else if (exoPlayer.isPlaying) {
            exoPlayer.pause()
        } else {
            exoPlayer.play()
        }
    }

    fun startSleepTimer(minutes: Long) {
        if (minutes <= 0) return
        cancelSleepTimer() // Cancel any existing timer

        isSleepTimerRunning = true
        sleepTimerRemainingSeconds = minutes * 60

        sleepTimer = object : CountDownTimer(minutes * 60 * 1000, 1000) {
            override fun onTick(millisUntilFinished: Long) {
                sleepTimerRemainingSeconds = millisUntilFinished / 1000
            }

            override fun onFinish() {
                if (isPlaying) { // Only pause if it was playing
                    exoPlayer.pause()
                }
                isSleepTimerRunning = false
                sleepTimerRemainingSeconds = 0
                // Optionally, reset the input field or give other feedback
            }
        }.start()
    }

    fun cancelSleepTimer() {
        sleepTimer?.cancel()
        isSleepTimerRunning = false
        sleepTimerRemainingSeconds = 0
    }

    override fun onCleared() {
        super.onCleared()
        exoPlayer.release() // Crucial: release ExoPlayer resources
        cancelSleepTimer()
    }
}