package com.example.audiobooksync

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowForward
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.audiobooksync.ui.theme.AudiobookSyncTheme
import java.util.concurrent.TimeUnit

// Helper function to format milliseconds to MM:SS or HH:MM:SS
fun formatTime(millis: Long): String {
    val hours = TimeUnit.MILLISECONDS.toHours(millis)
    val minutes = TimeUnit.MILLISECONDS.toMinutes(millis) % TimeUnit.HOURS.toMinutes(1)
    val seconds = TimeUnit.MILLISECONDS.toSeconds(millis) % TimeUnit.MINUTES.toSeconds(1)
    return if (hours > 0) {
        String.format("%02d:%02d:%02d", hours, minutes, seconds)
    } else {
        String.format("%02d:%02d", minutes, seconds)
    }
}

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            AudiobookSyncTheme {
                Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                    AudioPlayerScreen(
                        modifier = Modifier.padding(innerPadding)
                    )
                }
            }
        }
    }
}

@Composable
fun AudioPlayerScreen(
    modifier: Modifier = Modifier,
    viewModel: AudioPlayerViewModel = viewModel()
) {
    val context = LocalContext.current
    val selectedFileUri = viewModel.selectedFileUri
    val isPlaying = viewModel.isPlaying

    // Collect StateFlows from ViewModel
    val currentPositionMs by viewModel.currentPositionMs.collectAsState()
    val totalDurationMs by viewModel.totalDurationMs.collectAsState()

    val sleepTimerRemaining = viewModel.sleepTimerRemainingSeconds
    val isSleepTimerRunning = viewModel.isSleepTimerRunning
    var sleepTimerInputMinutes by remember { mutableStateOf("30") }

    var sliderIsBeingDragged by remember { mutableStateOf(false) }
    var sliderDraggedPosition by remember { mutableStateOf(0f) }


    val filePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument(),
        onResult = { uri ->
            uri?.let {
                try {
                    val takeFlags: Int = Intent.FLAG_GRANT_READ_URI_PERMISSION
                    context.contentResolver.takePersistableUriPermission(it, takeFlags)
                    viewModel.onFileSelected(it)
                } catch (e: SecurityException) {
                    e.printStackTrace()
                    // TODO: Show user a friendly error message
                }
            }
        }
    )

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.SpaceBetween // Pushes sleep timer to bottom
    ) {
        // Top section for file and playback controls
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Button(onClick = {
                filePickerLauncher.launch(
                    arrayOf( /* Your MIME types array */
                        "audio/m4a", "audio/x-m4a", "audio/mp4", "application/mp4",
                        "audio/aac", "audio/vnd.dlna.adts", "audio/mpeg",
                        "audio/ogg", "audio/wav", "audio/flac", "audio/*"
                    )
                )
            }) {
                Text("Open Audio File")
            }

            Spacer(modifier = Modifier.height(12.dp))

            Text(
                text = selectedFileUri?.lastPathSegment?.substringAfterLast('/') ?: "No file selected",
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 1,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth(0.8f)
            )

            Spacer(modifier = Modifier.height(24.dp))

            // Time Display
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(formatTime(if (sliderIsBeingDragged) sliderDraggedPosition.toLong() else currentPositionMs))
                Text(formatTime(totalDurationMs))
            }

            // Slider (SeekBar)
            Slider(
                value = if (sliderIsBeingDragged) sliderDraggedPosition else currentPositionMs.toFloat(),
                onValueChange = { newValue ->
                    sliderIsBeingDragged = true
                    sliderDraggedPosition = newValue
                },
                onValueChangeFinished = {
                    viewModel.seekTo(sliderDraggedPosition.toLong())
                    sliderIsBeingDragged = false
                },
                valueRange = 0f..(if (totalDurationMs > 0) totalDurationMs.toFloat() else 1f), // Avoid 0 range
                enabled = totalDurationMs > 0 && (selectedFileUri != null || viewModel.exoPlayer.mediaItemCount > 0),
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(modifier = Modifier.height(16.dp))

            // Playback Controls Row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(
                    onClick = { viewModel.skipBackward() },
                    enabled = totalDurationMs > 0 && (selectedFileUri != null || viewModel.exoPlayer.mediaItemCount > 0)
                ) {
                    Icon(Icons.Filled.ArrowForward, contentDescription = "Skip Backward", modifier = Modifier.size(36.dp))
                }

                IconButton(
                    onClick = { viewModel.playPause() },
                    enabled = selectedFileUri != null || viewModel.exoPlayer.mediaItemCount > 0,
                ) {
                    Icon(
                        imageVector = if (isPlaying) Icons.Filled.Check else Icons.Filled.PlayArrow,
                        contentDescription = if (isPlaying) "Pause" else "Play",
                        modifier = Modifier.size(48.dp)
                    )
                }

                IconButton(
                    onClick = { viewModel.skipForward() },
                    enabled = totalDurationMs > 0 && (selectedFileUri != null || viewModel.exoPlayer.mediaItemCount > 0)
                ) {
                    Icon(Icons.Filled.ArrowBack, contentDescription = "Skip Forward", modifier = Modifier.size(36.dp))
                }
            }
        }


        // Sleep Timer Section (bottom)
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text("Sleep Timer", style = MaterialTheme.typography.headlineSmall)
            Spacer(modifier = Modifier.height(8.dp))

            OutlinedTextField(
                value = sleepTimerInputMinutes,
                onValueChange = { if (it.all { char -> char.isDigit() }) sleepTimerInputMinutes = it },
                label = { Text("Minutes") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                singleLine = true,
                modifier = Modifier.width(150.dp)
            )
            Spacer(modifier = Modifier.height(8.dp))

            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Button(onClick = {
                    val minutes = sleepTimerInputMinutes.toLongOrNull()
                    if (minutes != null && minutes > 0) {
                        viewModel.startSleepTimer(minutes)
                    }
                }) { Text("Start") }
                Button(
                    onClick = { viewModel.cancelSleepTimer() },
                    enabled = isSleepTimerRunning
                ) { Text("Cancel") }
            }
            Spacer(modifier = Modifier.height(12.dp))
            Text(
                text = if (isSleepTimerRunning) "Time remaining: ${formatTime(sleepTimerRemaining * 1000)}" else "Timer not active.",
                style = MaterialTheme.typography.bodyMedium
            )
        }
    }
}

@Preview(showBackground = true)
@Composable
fun AudioPlayerScreenPreview() {
    AudiobookSyncTheme {
        Scaffold { paddingValues ->
            AudioPlayerScreen(modifier = Modifier.padding(paddingValues))
        }
    }
}