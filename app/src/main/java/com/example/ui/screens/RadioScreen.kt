package com.example.ui.screens

import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.FiberManualRecord
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Radio
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.example.api.RadioApiClient
import com.example.api.RadioStationItem
import com.example.playback.PlaybackManager
import com.example.playback.RadioRecorder
import com.example.ui.theme.OneUICardElevated
import com.example.ui.theme.OneUIDarkBackground
import com.example.ui.theme.OneUISurfaceDark
import com.example.ui.theme.OneUITextPrimary
import com.example.ui.theme.OneUITextSecondary
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@Composable
fun RadioScreen(
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    var searchQuery by remember { mutableStateOf("") }
    var selectedCountry by remember { mutableStateOf("India") }
    var selectedLanguage by remember { mutableStateOf("All") }

    var stations by remember { mutableStateOf<List<RadioStationItem>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }
    var searchJob by remember { mutableStateOf<Job?>(null) }

    // Recording state
    var recordingStationId by remember { mutableStateOf<String?>(null) }
    var recordingElapsedSec by remember { mutableIntStateOf(0) }

    val currentPlayingSong by PlaybackManager.currentSong.collectAsStateWithLifecycle()

    fun loadStations(query: String = searchQuery) {
        searchJob?.cancel()
        searchJob = coroutineScope.launch {
            isLoading = true
            val langParam = if (selectedLanguage == "All") "" else selectedLanguage
            val countryParam = if (selectedCountry == "All") "" else selectedCountry

            stations = RadioApiClient.searchStations(
                query = query,
                country = countryParam,
                language = langParam,
                limit = 50
            )
            isLoading = false
        }
    }

    LaunchedEffect(selectedCountry, selectedLanguage) {
        loadStations()
    }

    LaunchedEffect(searchQuery) {
        if (searchQuery.isNotBlank()) {
            delay(350L) // Debounce
            loadStations(searchQuery)
        } else {
            loadStations("")
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(OneUIDarkBackground)
            .testTag("radio_screen")
    ) {
        // Header
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 12.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Default.Radio,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(28.dp)
                )
                Spacer(modifier = Modifier.width(10.dp))
                Column {
                    Text(
                        text = "Live Radio",
                        fontSize = 26.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = OneUITextPrimary
                    )
                    Text(
                        text = "Stream & Record Free Global Stations",
                        fontSize = 12.sp,
                        color = OneUITextSecondary
                    )
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Search Bar
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                placeholder = { Text("Search radio stations worldwide...", fontSize = 13.sp) },
                leadingIcon = {
                    Icon(Icons.Default.Search, contentDescription = null, tint = OneUITextSecondary)
                },
                singleLine = true,
                shape = RoundedCornerShape(16.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedContainerColor = OneUISurfaceDark,
                    unfocusedContainerColor = OneUISurfaceDark,
                    focusedBorderColor = MaterialTheme.colorScheme.primary,
                    unfocusedBorderColor = Color.Transparent,
                    focusedTextColor = OneUITextPrimary,
                    unfocusedTextColor = OneUITextPrimary
                ),
                modifier = Modifier.fillMaxWidth().testTag("radio_search_input")
            )

            Spacer(modifier = Modifier.height(12.dp))

            // Country Filter Chips
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                listOf("India", "All", "United States", "United Kingdom", "Canada").forEach { country ->
                    FilterChip(
                        selected = selectedCountry == country,
                        onClick = { selectedCountry = country },
                        label = { Text(country, fontSize = 12.sp) },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = MaterialTheme.colorScheme.primary,
                            selectedLabelColor = Color.Black,
                            containerColor = OneUICardElevated,
                            labelColor = OneUITextSecondary
                        )
                    )
                }
            }

            Spacer(modifier = Modifier.height(6.dp))

            // Language Filter Chips
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                listOf("All", "Punjabi", "Hindi", "Marathi", "English", "Urdu").forEach { lang ->
                    FilterChip(
                        selected = selectedLanguage == lang,
                        onClick = { selectedLanguage = lang },
                        label = { Text(lang, fontSize = 12.sp) },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = OneUISurfaceDark,
                            selectedLabelColor = MaterialTheme.colorScheme.primary,
                            containerColor = OneUICardElevated,
                            labelColor = OneUITextSecondary
                        )
                    )
                }
            }
        }

        // Active Recording Banner
        AnimatedVisibility(visible = recordingStationId != null) {
            Surface(
                color = Color(0xFFFF2A6D).copy(alpha = 0.15f),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 4.dp)
            ) {
                Row(
                    modifier = Modifier.padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.FiberManualRecord,
                            contentDescription = "Recording",
                            tint = Color(0xFFFF2A6D),
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Recording live broadcast: ${recordingElapsedSec}s",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold,
                            color = OneUITextPrimary
                        )
                    }

                    IconButton(
                        onClick = {
                            RadioRecorder.stopRecording()
                            recordingStationId = null
                        },
                        modifier = Modifier.size(32.dp)
                    ) {
                        Icon(Icons.Default.Stop, contentDescription = "Stop Recording", tint = Color(0xFFFF2A6D))
                    }
                }
            }
        }

        // Stations List
        if (isLoading) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(bottom = 120.dp),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
            }
        } else if (stations.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(bottom = 120.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "No radio stations found for this query",
                    fontSize = 14.sp,
                    color = OneUITextSecondary
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(bottom = 120.dp)
            ) {
                items(stations, key = { it.id }) { station ->
                    val isPlayingThis = currentPlayingSong?.streamUrl == station.streamUrl
                    val isRecordingThis = recordingStationId == station.id

                    Card(
                        shape = RoundedCornerShape(14.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = if (isPlayingThis) OneUISurfaceDark else OneUICardElevated
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 5.dp)
                            .clickable {
                                PlaybackManager.playRadioStation(station)
                            }
                            .testTag("radio_station_card_${station.id}")
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            // Favicon
                            Box(
                                modifier = Modifier
                                    .size(48.dp)
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(OneUISurfaceDark),
                                contentAlignment = Alignment.Center
                            ) {
                                if (!station.favicon.isNullOrBlank()) {
                                    AsyncImage(
                                        model = station.favicon,
                                        contentDescription = station.name,
                                        contentScale = ContentScale.Crop,
                                        modifier = Modifier.fillMaxSize()
                                    )
                                } else {
                                    Icon(
                                        imageVector = Icons.Default.Radio,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(24.dp)
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.width(12.dp))

                            // Station details
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = station.name,
                                    fontSize = 14.sp,
                                    fontWeight = if (isPlayingThis) FontWeight.Bold else FontWeight.SemiBold,
                                    color = if (isPlayingThis) MaterialTheme.colorScheme.primary else OneUITextPrimary,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(
                                    text = "${station.language ?: "Global"} • ${station.country ?: "Internet"}",
                                    fontSize = 11.sp,
                                    color = OneUITextSecondary,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                if (station.bitrate > 0 || !station.codec.isNullOrBlank()) {
                                    Spacer(modifier = Modifier.height(2.dp))
                                    Text(
                                        text = "${if (station.bitrate > 0) "${station.bitrate} kbps • " else ""}${station.codec ?: "Stream"}",
                                        fontSize = 10.sp,
                                        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.8f)
                                    )
                                }
                            }

                            // Record Audio Stream Button
                            IconButton(
                                onClick = {
                                    if (isRecordingThis) {
                                        RadioRecorder.stopRecording()
                                        recordingStationId = null
                                    } else {
                                        recordingStationId = station.id
                                        recordingElapsedSec = 0
                                        coroutineScope.launch {
                                            Toast.makeText(context, "Recording live stream (30s)...", Toast.LENGTH_SHORT).show()
                                            val savedFile = RadioRecorder.recordStationStream(
                                                context = context,
                                                stationName = station.name,
                                                streamUrl = station.streamUrl,
                                                durationSeconds = 30,
                                                onProgress = { sec -> recordingElapsedSec = sec }
                                            )
                                            recordingStationId = null
                                            if (savedFile != null && savedFile.exists()) {
                                                Toast.makeText(context, "Saved recording: ${savedFile.name}", Toast.LENGTH_LONG).show()
                                            } else {
                                                Toast.makeText(context, "Stream recording ended", Toast.LENGTH_SHORT).show()
                                            }
                                        }
                                    }
                                },
                                modifier = Modifier
                                    .size(36.dp)
                                    .background(
                                        if (isRecordingThis) Color(0xFFFF2A6D).copy(alpha = 0.2f) else Color.Transparent,
                                        CircleShape
                                    )
                            ) {
                                Icon(
                                    imageVector = if (isRecordingThis) Icons.Default.Stop else Icons.Default.Mic,
                                    contentDescription = "Record Stream",
                                    tint = if (isRecordingThis) Color(0xFFFF2A6D) else OneUITextSecondary,
                                    modifier = Modifier.size(18.dp)
                                )
                            }

                            Spacer(modifier = Modifier.width(4.dp))

                            // Play button
                            IconButton(
                                onClick = { PlaybackManager.playRadioStation(station) },
                                modifier = Modifier
                                    .background(
                                        if (isPlayingThis) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.primary.copy(alpha = 0.15f),
                                        CircleShape
                                    )
                                    .size(36.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.PlayArrow,
                                    contentDescription = "Play Station",
                                    tint = if (isPlayingThis) Color.Black else MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
