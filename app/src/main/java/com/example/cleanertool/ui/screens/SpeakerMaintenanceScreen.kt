package com.example.cleanertool.ui.screens

import android.media.MediaPlayer
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import kotlinx.coroutines.delay

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SpeakerMaintenanceScreen(navController: NavController) {
    val context = LocalContext.current
    var isPlaying by remember { mutableStateOf(false) }
    var selectedFrequency by remember { mutableStateOf<Int?>(null) }
    var mediaPlayer: MediaPlayer? by remember { mutableStateOf(null) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Speaker Maintenance") },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.Default.ArrowBack, "Back")
                    }
                }
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Info Card
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer
                )
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Info,
                            contentDescription = null
                        )
                        Text(
                            text = "About Speaker Cleaning",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "This tool helps diagnose speaker and microphone issues. Physical cleaning must be done manually. Use the test tones to check if your speakers are working properly.",
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            }

            // Cleaning Instructions
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(
                        text = "Cleaning Instructions",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold
                    )
                    CleaningTipItem(
                        icon = Icons.Default.Build,
                        title = "Remove Dust",
                        description = "Use a soft, dry brush to gently remove dust from speaker grilles"
                    )
                    CleaningTipItem(
                        icon = Icons.Default.Settings,
                        title = "Clean with Isopropyl Alcohol",
                        description = "Dampen a cotton swab with 70% isopropyl alcohol and gently clean the grilles"
                    )
                    CleaningTipItem(
                        icon = Icons.Default.Build,
                        title = "Use Compressed Air",
                        description = "Blow compressed air into speaker grilles to remove debris (keep device off)"
                    )
                    CleaningTipItem(
                        icon = Icons.Default.Warning,
                        title = "Avoid Water",
                        description = "Never use water directly on speakers. Keep device powered off during cleaning"
                    )
                }
            }

            // Test Tones Section
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(
                        text = "Test Tones",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "Play test tones to check speaker functionality. Adjust volume before playing.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(8.dp))

                    // Frequency buttons
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        TestToneButton(
                            frequency = 440,
                            label = "440 Hz",
                            isSelected = selectedFrequency == 440,
                            isPlaying = isPlaying && selectedFrequency == 440,
                            onClick = {
                                if (isPlaying && selectedFrequency == 440) {
                                    mediaPlayer?.stop()
                                    mediaPlayer?.release()
                                    mediaPlayer = null
                                    isPlaying = false
                                    selectedFrequency = null
                                } else {
                                    playTestTone(context, 440) { player ->
                                        mediaPlayer?.stop()
                                        mediaPlayer?.release()
                                        mediaPlayer = player
                                        isPlaying = true
                                        selectedFrequency = 440
                                    }
                                }
                            },
                            modifier = Modifier.weight(1f)
                        )
                        TestToneButton(
                            frequency = 1000,
                            label = "1 kHz",
                            isSelected = selectedFrequency == 1000,
                            isPlaying = isPlaying && selectedFrequency == 1000,
                            onClick = {
                                if (isPlaying && selectedFrequency == 1000) {
                                    mediaPlayer?.stop()
                                    mediaPlayer?.release()
                                    mediaPlayer = null
                                    isPlaying = false
                                    selectedFrequency = null
                                } else {
                                    playTestTone(context, 1000) { player ->
                                        mediaPlayer?.stop()
                                        mediaPlayer?.release()
                                        mediaPlayer = player
                                        isPlaying = true
                                        selectedFrequency = 1000
                                    }
                                }
                            },
                            modifier = Modifier.weight(1f)
                        )
                        TestToneButton(
                            frequency = 2000,
                            label = "2 kHz",
                            isSelected = selectedFrequency == 2000,
                            isPlaying = isPlaying && selectedFrequency == 2000,
                            onClick = {
                                if (isPlaying && selectedFrequency == 2000) {
                                    mediaPlayer?.stop()
                                    mediaPlayer?.release()
                                    mediaPlayer = null
                                    isPlaying = false
                                    selectedFrequency = null
                                } else {
                                    playTestTone(context, 2000) { player ->
                                        mediaPlayer?.stop()
                                        mediaPlayer?.release()
                                        mediaPlayer = player
                                        isPlaying = true
                                        selectedFrequency = 2000
                                    }
                                }
                            },
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            }

            // Microphone Test
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(
                        text = "Microphone Test",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "Use your device's voice recorder app to test microphone functionality. Speak clearly and check if audio is recorded properly.",
                        style = MaterialTheme.typography.bodyMedium
                    )
                    OutlinedButton(
                        onClick = {
                            // Open voice recorder intent
                            val intent = android.content.Intent(android.provider.MediaStore.Audio.Media.RECORD_SOUND_ACTION)
                            try {
                                context.startActivity(intent)
                            } catch (e: Exception) {
                                // Handle error - voice recorder might not be available
                            }
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Default.Info, "Microphone")
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Open Voice Recorder")
                    }
                }
            }
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            mediaPlayer?.stop()
            mediaPlayer?.release()
            mediaPlayer = null
        }
    }
}

@Composable
fun CleaningTipItem(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    description: String
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            modifier = Modifier.size(24.dp),
            tint = MaterialTheme.colorScheme.primary
        )
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Medium
            )
            Text(
                text = description,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
fun TestToneButton(
    frequency: Int,
    label: String,
    isSelected: Boolean,
    isPlaying: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Button(
        onClick = onClick,
        modifier = modifier,
        colors = ButtonDefaults.buttonColors(
            containerColor = if (isSelected) MaterialTheme.colorScheme.primary
            else MaterialTheme.colorScheme.secondaryContainer
        )
    ) {
        if (isPlaying) {
            Icon(Icons.Default.Close, "Stop")
        } else {
            Icon(Icons.Default.PlayArrow, "Play")
        }
        Spacer(modifier = Modifier.width(4.dp))
        Text(label)
    }
}

fun playTestTone(context: android.content.Context, frequency: Int, onPlayerCreated: (MediaPlayer) -> Unit) {
    // Generate a simple sine wave tone
    // Note: This is a simplified implementation
    // In a real app, you'd generate proper audio samples
    try {
        val mediaPlayer = MediaPlayer()
        // For a real implementation, you'd need to generate audio samples
        // This is a placeholder - actual tone generation requires audio synthesis
        onPlayerCreated(mediaPlayer)
    } catch (e: Exception) {
        // Handle error
    }
}
