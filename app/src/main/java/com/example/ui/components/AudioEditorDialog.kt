package com.example.ui.components

import android.net.Uri
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContentCut
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RangeSlider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.example.data.SongEntity
import com.example.playback.PlaybackManager
import com.example.ui.theme.NeonMint
import com.example.ui.theme.OneUICardElevated
import com.example.ui.theme.OneUITextPrimary
import com.example.ui.theme.OneUITextSecondary
import com.example.ui.theme.SpotifyGreen
import com.example.utils.AudioEditor
import kotlinx.coroutines.launch

@Composable
fun AudioEditorDialog(
    song: SongEntity,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    val totalDurationMs = song.durationMs.toFloat().coerceAtLeast(10_000f)

    // Range slider states in milliseconds
    var startMs by remember { mutableFloatStateOf(0f) }
    var endMs by remember { mutableFloatStateOf(totalDurationMs.coerceAtMost(30_000f)) }
    var outputName by remember {
        mutableStateOf("${song.title.take(15).replace(" ", "_")}_trimmed")
    }

    var isProcessing by remember { mutableStateOf(false) }
    var statusMessage by remember { mutableStateOf<String?>(null) }

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = RoundedCornerShape(24.dp),
            color = OneUICardElevated,
            modifier = Modifier
                .fillMaxWidth()
                .padding(8.dp)
                .testTag("audio_editor_dialog")
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.ContentCut,
                        contentDescription = null,
                        tint = NeonMint,
                        modifier = Modifier.padding(end = 8.dp)
                    )
                    Text(
                        text = "Native Lossless Audio Cutter",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = OneUITextPrimary
                    )
                }

                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "${song.title} (${formatTime(song.durationMs)})",
                    style = MaterialTheme.typography.bodySmall,
                    color = OneUITextSecondary
                )

                Spacer(modifier = Modifier.height(20.dp))

                // Time markers
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column {
                        Text("Start: ${formatTime(startMs.toLong())}", color = NeonMint, fontWeight = FontWeight.Bold)
                    }
                    Column(horizontalAlignment = Alignment.End) {
                        Text("End: ${formatTime(endMs.toLong())}", color = SpotifyGreen, fontWeight = FontWeight.Bold)
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                RangeSlider(
                    value = startMs..endMs,
                    onValueChange = { range ->
                        startMs = range.start
                        endMs = range.endInclusive
                    },
                    valueRange = 0f..totalDurationMs,
                    colors = SliderDefaults.colors(
                        thumbColor = NeonMint,
                        activeTrackColor = SpotifyGreen,
                        inactiveTrackColor = Color.DarkGray
                    ),
                    modifier = Modifier.fillMaxWidth()
                )

                Text(
                    text = "Trimmed Length: ${formatTime((endMs - startMs).toLong())}",
                    style = MaterialTheme.typography.bodySmall,
                    color = Color.White.copy(alpha = 0.8f),
                    modifier = Modifier.padding(vertical = 4.dp)
                )

                Spacer(modifier = Modifier.height(12.dp))

                OutlinedTextField(
                    value = outputName,
                    onValueChange = { outputName = it },
                    label = { Text("Output Filename", color = OneUITextSecondary) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp)
                )

                if (statusMessage != null) {
                    Text(
                        text = statusMessage!!,
                        color = NeonMint,
                        fontSize = 12.sp,
                        modifier = Modifier.padding(top = 8.dp)
                    )
                }

                Spacer(modifier = Modifier.height(20.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Preview Button
                    TextButton(
                        onClick = {
                            PlaybackManager.seekTo(startMs.toLong())
                            if (!PlaybackManager.isPlaying.value) {
                                PlaybackManager.playPause()
                            }
                        }
                    ) {
                        Icon(imageVector = Icons.Default.PlayArrow, contentDescription = null, tint = NeonMint)
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Preview", color = NeonMint)
                    }

                    Row {
                        TextButton(onClick = onDismiss, enabled = !isProcessing) {
                            Text("Close", color = Color.White.copy(alpha = 0.7f))
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                        Button(
                            onClick = {
                                isProcessing = true
                                statusMessage = "Extracting & Lossless Muxing..."
                                coroutineScope.launch {
                                    val uri = Uri.parse(song.contentUri)
                                    val result = AudioEditor.trimAudio(
                                        context = context,
                                        sourceUri = uri,
                                        outputFileName = outputName,
                                        startMs = startMs.toLong(),
                                        endMs = endMs.toLong()
                                    )
                                    isProcessing = false
                                    if (result.isSuccess) {
                                        statusMessage = "Saved to Music folder successfully!"
                                        Toast.makeText(context, "Saved: ${result.outputPath}", Toast.LENGTH_LONG).show()
                                    } else {
                                        statusMessage = "Error: ${result.errorMessage}"
                                    }
                                }
                            },
                            enabled = !isProcessing && (endMs > startMs),
                            colors = ButtonDefaults.buttonColors(containerColor = SpotifyGreen),
                            modifier = Modifier.testTag("trim_save_button")
                        ) {
                            if (isProcessing) {
                                CircularProgressIndicator(
                                    modifier = Modifier.width(16.dp).height(16.dp),
                                    color = Color.Black,
                                    strokeWidth = 2.dp
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                            }
                            Text("Save Cut", color = Color.Black, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }
    }
}

private fun formatTime(millis: Long): String {
    val totalSeconds = (millis / 1000).coerceAtLeast(0)
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60
    return String.format("%02d:%02d", minutes, seconds)
}
