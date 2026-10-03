package com.example.ui.screens

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ElectricBolt
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.Headphones
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Subtitles
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.api.StreamingApiClient
import com.example.data.SettingsManager
import com.example.ui.theme.OneUICardElevated
import com.example.ui.theme.OneUIDarkBackground
import com.example.ui.theme.OneUISurfaceDark
import com.example.ui.theme.OneUITextPrimary
import com.example.ui.theme.OneUITextSecondary
import kotlinx.coroutines.launch

@Composable
fun ServerSettingsScreen(
    settingsManager: SettingsManager,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    var isTestingEngine by remember { mutableStateOf(false) }
    var testResultStatus by remember { mutableStateOf<String?>(null) }
    var testSucceeded by remember { mutableStateOf<Boolean?>(null) }

    fun testDirectExtraction() {
        isTestingEngine = true
        testResultStatus = "Running in-app extraction test..."
        testSucceeded = null

        coroutineScope.launch {
            try {
                val results = StreamingApiClient.searchSongs("lofi hip hop")
                if (results.isNotEmpty()) {
                    val firstTrack = results.first()
                    val directStream = StreamingApiClient.extractDirectStream(
                        songOrUrl = firstTrack.contentUri,
                        fallbackTitle = firstTrack.title,
                        fallbackArtist = firstTrack.artist
                    )
                    if (directStream != null && directStream.streamUrl.isNotBlank()) {
                        testSucceeded = true
                        testResultStatus = "Success! Direct m4a stream extracted (${results.size} tracks indexed)."
                        Toast.makeText(context, "Direct extraction verified!", Toast.LENGTH_SHORT).show()
                    } else {
                        testSucceeded = false
                        testResultStatus = "Found ${results.size} tracks, but stream extraction returned null."
                    }
                } else {
                    testSucceeded = false
                    testResultStatus = "Search returned 0 items. Check internet connection."
                }
            } catch (e: Exception) {
                testSucceeded = false
                testResultStatus = "Test failed: ${e.localizedMessage ?: "Unknown error"}"
            } finally {
                isTestingEngine = false
            }
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(OneUIDarkBackground)
            .verticalScroll(rememberScrollState())
            .padding(bottom = 120.dp)
            .testTag("server_settings_screen")
    ) {
        // Top Header
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "Back",
                    tint = OneUITextPrimary
                )
            }
            Spacer(modifier = Modifier.width(8.dp))
            Column {
                Text(
                    text = "Streaming Engine",
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    color = OneUITextPrimary
                )
                Text(
                    text = "Direct In-App Extraction (NewPipeExtractor)",
                    fontSize = 12.sp,
                    color = OneUITextSecondary
                )
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        // Active Architecture Card
        Card(
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = OneUICardElevated),
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
        ) {
            Column(modifier = Modifier.padding(18.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(40.dp)
                            .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.18f), CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.ElectricBolt,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(24.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    Column {
                        Text(
                            text = "Direct Media Extraction",
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold,
                            color = OneUITextPrimary
                        )
                        Text(
                            text = "Zero Intermediary Server Proxies",
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                Text(
                    text = "Pulse Music uses native in-app extraction via NewPipeExtractor (v0.26.5). External Piped and Invidious proxy dependencies have been dropped. Audio streams and synchronized captions resolve natively on your device without middleman server bottlenecks.",
                    fontSize = 13.sp,
                    color = OneUITextSecondary,
                    lineHeight = 18.sp
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Engine Highlights
        Text(
            text = "ENGINE CAPABILITIES",
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold,
            color = OneUITextSecondary,
            modifier = Modifier.padding(horizontal = 24.dp, vertical = 6.dp)
        )

        EngineFeatureItem(
            icon = Icons.Default.Headphones,
            title = "Strict High-Quality M4A / AAC Stream Filtering",
            subtitle = "ExoPlayer demuxer static bug eliminated. Prioritizes pure audio/mp4 (AAC) streams."
        )

        EngineFeatureItem(
            icon = Icons.Default.Subtitles,
            title = "Real-Time Timed Captions & Synced Lyrics",
            subtitle = "Converts YouTube WebVTT, TTML, and XML captions to standard .lrc format, backed by LRCLIB."
        )

        EngineFeatureItem(
            icon = Icons.Default.Security,
            title = "Decentralized & Resilient",
            subtitle = "No custom instance configurations required. Immune to public Piped instance downtimes."
        )

        Spacer(modifier = Modifier.height(20.dp))

        // Test Engine Card
        Card(
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = OneUICardElevated),
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
        ) {
            Column(modifier = Modifier.padding(18.dp)) {
                Text(
                    text = "Self-Diagnostics",
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold,
                    color = OneUITextPrimary
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "Verify that native extraction and direct m4a stream resolution are functioning properly.",
                    fontSize = 12.sp,
                    color = OneUITextSecondary
                )

                Spacer(modifier = Modifier.height(14.dp))

                Button(
                    onClick = { testDirectExtraction() },
                    enabled = !isTestingEngine,
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth().testTag("test_engine_btn")
                ) {
                    if (isTestingEngine) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(16.dp),
                            color = Color.Black,
                            strokeWidth = 2.dp
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                    } else {
                        Icon(Icons.Default.PlayArrow, contentDescription = null, tint = Color.Black, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                    }
                    Text(
                        text = if (isTestingEngine) "Testing Extractor..." else "Run Extraction Diagnostic",
                        color = Color.Black,
                        fontWeight = FontWeight.Bold
                    )
                }

                testResultStatus?.let { status ->
                    Spacer(modifier = Modifier.height(10.dp))
                    Text(
                        text = status,
                        fontSize = 12.sp,
                        color = when (testSucceeded) {
                            true -> MaterialTheme.colorScheme.primary
                            false -> Color(0xFFFF5252)
                            else -> OneUITextSecondary
                        }
                    )
                }
            }
        }
    }
}

@Composable
private fun Box(modifier: Modifier, contentAlignment: Alignment, content: @Composable () -> Unit) {
    androidx.compose.foundation.layout.Box(modifier = modifier, contentAlignment = contentAlignment) {
        content()
    }
}

@Composable
private fun EngineFeatureItem(
    icon: ImageVector,
    title: String,
    subtitle: String
) {
    Card(
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = OneUISurfaceDark),
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 5.dp)
    ) {
        Row(
            modifier = Modifier.padding(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(24.dp)
            )
            Spacer(modifier = Modifier.width(14.dp))
            Column {
                Text(
                    text = title,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = OneUITextPrimary
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = subtitle,
                    fontSize = 11.sp,
                    color = OneUITextSecondary,
                    lineHeight = 15.sp
                )
            }
        }
    }
}
