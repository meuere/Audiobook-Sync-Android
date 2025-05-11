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
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.audiobooksync.ui.theme.AudiobookSyncTheme // Your theme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge() // For modern edge-to-edge display
        setContent {
            AudiobookSyncTheme { // Use your app's theme
                // Scaffold provides structure and handles insets with enableEdgeToEdge
                Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                    AudioPlayerScreen(
                        modifier = Modifier.padding(innerPadding) // Apply padding from Scaffold
                    )
                }
            }
        }
    }
}

@Composable
fun AudioPlayerScreen(
    modifier: Modifier = Modifier, // Accept a modifier
    viewModel: AudioPlayerViewModel = viewModel()
) {
    val context = LocalContext.current
    // Observing state from ViewModel
    val selectedFileUri = viewModel.selectedFileUri
    val isPlaying = viewModel.isPlaying
    val sleepTimerRemaining = viewModel.sleepTimerRemainingSeconds
    val isSleepTimerRunning = viewModel.isSleepTimerRunning

    var sleepTimerInputMinutes by remember { mutableStateOf("30") } // Default 30 minutes

    val filePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument(),
        onResult = { uri ->
            uri?.let {
                try {
                    // Persist URI permission
                    val takeFlags: Int = Intent.FLAG_GRANT_READ_URI_PERMISSION
                    context.contentResolver.takePersistableUriPermission(it, takeFlags)
                    viewModel.onFileSelected(it)
                } catch (e: SecurityException) {
                    e.printStackTrace()
                    // TODO: Show user a friendly error message (e.g., via a Snackbar)
                }
            }
        }
    )

    Column(
        modifier = modifier // Use the modifier passed in
            .fillMaxSize()
            .padding(16.dp), // Additional padding for content inside Scaffold's padding
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.SpaceAround // Distributes space
    ) {
        // Section for File Selection and Playback Controls
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Button(onClick = {
                filePickerLauncher.launch(arrayOf("audio/*"))
            }) {
                Text("Open Audio File")
            }

            Spacer(modifier = Modifier.height(12.dp))

            Text(
                text = selectedFileUri?.lastPathSegment?.substringAfterLast('/') ?: "No file selected",
                style = MaterialTheme.typography.bodyLarge,
                maxLines = 1
            )

            Spacer(modifier = Modifier.height(24.dp))

            Button(
                onClick = { viewModel.playPause() },
                enabled = selectedFileUri != null || viewModel.exoPlayer.mediaItemCount > 0
            ) {
                Text(if (isPlaying) "Pause" else "Play")
            }
        }

        // Section for Sleep Timer
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
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
                    } else {
                        // TODO: Show error if input is invalid
                    }
                }) {
                    Text("Start")
                }
                Button(
                    onClick = { viewModel.cancelSleepTimer() },
                    enabled = isSleepTimerRunning
                ) {
                    Text("Cancel")
                }
            }
            Spacer(modifier = Modifier.height(12.dp))
            if (isSleepTimerRunning) {
                Text(
                    "Time remaining: ${String.format("%02d:%02d", sleepTimerRemaining / 60, sleepTimerRemaining % 60)}",
                    style = MaterialTheme.typography.bodyMedium
                )
            } else {
                Text("Timer not active.", style = MaterialTheme.typography.bodyMedium)
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
fun AudioPlayerScreenPreview() {
    AudiobookSyncTheme {
        // Provide a default Modifier for the preview if needed, or use the one from Scaffold
        Scaffold { paddingValues ->
            AudioPlayerScreen(modifier = Modifier.padding(paddingValues))
        }
    }
}