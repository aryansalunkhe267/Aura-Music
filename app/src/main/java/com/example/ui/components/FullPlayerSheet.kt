package com.example.ui.components

import androidx.compose.animation.AnimatedContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.QueueMusic
import androidx.compose.material.icons.filled.ContentCut
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.Lyrics
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Repeat
import androidx.compose.material.icons.filled.RepeatOne
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.TabRowDefaults
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.example.data.SongEntity
import com.example.engine.AudioMoodClassifier
import com.example.playback.RepeatMode
import com.example.ui.theme.NeonMint
import com.example.ui.theme.OneUICardElevated
import com.example.ui.theme.OneUIDarkBackground
import com.example.ui.theme.OneUITextPrimary
import com.example.ui.theme.OneUITextSecondary
import com.example.ui.theme.SpotifyGreen
import com.example.utils.cleanMetadataString

@Composable
fun FullPlayerSheet(
    song: SongEntity?,
    isPlaying: Boolean,
    currentPositionMs: Long,
    durationMs: Long,
    isShuffleEnabled: Boolean,
    repeatMode: RepeatMode,
    queue: List<SongEntity>,
    currentQueueIndex: Int,
    smartMoodQueueEnabled: Boolean,
    onPlayPauseClick: () -> Unit,
    onNextClick: () -> Unit,
    onPreviousClick: () -> Unit,
    onSeekTo: (Long) -> Unit,
    onToggleShuffle: () -> Unit,
    onToggleRepeat: () -> Unit,
    onToggleSmartMoodQueue: () -> Unit,
    onQueueSongClick: (Int) -> Unit,
    onMoveQueueItem: (from: Int, to: Int) -> Unit,
    onRemoveQueueItem: (Int) -> Unit,
    onToggleSoftHide: (SongEntity) -> Unit,
    onOpenAudioEditor: (SongEntity) -> Unit,
    onSaveLyrics: (SongEntity, String) -> Unit,
    onCollapse: () -> Unit,
    modifier: Modifier = Modifier
) {
    if (song == null) return

    // 0: Now Playing Artwork & Controls, 1: Synced Lyrics, 2: Interactive Queue
    var selectedTab by remember { mutableIntStateOf(0) }

    // User scrubbing state
    var isUserScrubbing by remember { mutableStateOf(false) }
    var scrubPositionMs by remember { mutableFloatStateOf(0f) }

    val effectivePositionMs = if (isUserScrubbing) scrubPositionMs.toLong() else currentPositionMs
    val totalDuration = durationMs.coerceAtLeast(1L)

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    colors = listOf(
                        Color(0xFF14241B),
                        OneUIDarkBackground,
                        OneUIDarkBackground
                    )
                )
            )
            .padding(top = 12.dp)
            .testTag("full_player_sheet")
    ) {
        // Top Bar: Collapse Handle & Down Arrow
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            IconButton(
                onClick = onCollapse,
                modifier = Modifier
                    .size(48.dp)
                    .testTag("collapse_player_button")
            ) {
                Icon(
                    imageVector = Icons.Default.KeyboardArrowDown,
                    contentDescription = "Collapse Player",
                    tint = OneUITextPrimary,
                    modifier = Modifier.size(32.dp)
                )
            }

            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = "PLAYING FROM LIBRARY",
                    fontSize = 10.sp,
                    letterSpacing = 1.5.sp,
                    fontWeight = FontWeight.Bold,
                    color = OneUITextSecondary
                )
                Text(
                    text = song.languageScript.replace("_", " "),
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = NeonMint
                )
            }

            IconButton(
                onClick = { onOpenAudioEditor(song) },
                modifier = Modifier
                    .size(48.dp)
                    .testTag("trim_audio_button")
            ) {
                Icon(
                    imageVector = Icons.Default.ContentCut,
                    contentDescription = "Trim Track",
                    tint = OneUITextPrimary,
                    modifier = Modifier.size(24.dp)
                )
            }
        }

        // Three-way Tab Switcher: Artwork | Lyrics | Queue
        TabRow(
            selectedTabIndex = selectedTab,
            containerColor = Color.Transparent,
            contentColor = SpotifyGreen,
            indicator = { tabPositions ->
                TabRowDefaults.SecondaryIndicator(
                    modifier = Modifier.tabIndicatorOffset(tabPositions[selectedTab]),
                    color = SpotifyGreen,
                    height = 3.dp
                )
            },
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 32.dp, vertical = 6.dp)
        ) {
            Tab(
                selected = selectedTab == 0,
                onClick = { selectedTab = 0 },
                text = { Text("Player", fontWeight = FontWeight.Bold, fontSize = 13.sp) }
            )
            Tab(
                selected = selectedTab == 1,
                onClick = { selectedTab = 1 },
                text = { Text("Lyrics", fontWeight = FontWeight.Bold, fontSize = 13.sp) }
            )
            Tab(
                selected = selectedTab == 2,
                onClick = { selectedTab = 2 },
                text = { Text("Queue (${queue.size})", fontWeight = FontWeight.Bold, fontSize = 13.sp) }
            )
        }

        // Tab Content
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
        ) {
            when (selectedTab) {
                0 -> {
                    // Artwork & Mood Card
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(horizontal = 24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Card(
                            modifier = Modifier
                                .fillMaxWidth(0.85f)
                                .aspectRatio(1f),
                            shape = RoundedCornerShape(24.dp),
                            elevation = CardDefaults.cardElevation(defaultElevation = 12.dp),
                            colors = CardDefaults.cardColors(containerColor = OneUICardElevated)
                        ) {
                            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                if (song.albumArtUri != null) {
                                    AsyncImage(
                                        model = song.albumArtUri,
                                        contentDescription = song.title,
                                        contentScale = ContentScale.Crop,
                                        modifier = Modifier.fillMaxSize()
                                    )
                                } else {
                                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                        Icon(
                                            imageVector = Icons.Default.MusicNote,
                                            contentDescription = null,
                                            tint = NeonMint,
                                            modifier = Modifier.size(80.dp)
                                        )
                                        Spacer(modifier = Modifier.height(8.dp))
                                        Text(
                                            text = "AURA MUSIC",
                                            fontWeight = FontWeight.Bold,
                                            letterSpacing = 2.sp,
                                            color = Color.White.copy(alpha = 0.6f),
                                            fontSize = 12.sp
                                        )
                                    }
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(20.dp))

                        // Mood Badge & Soft-Hide Row
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Card(
                                shape = RoundedCornerShape(16.dp),
                                colors = CardDefaults.cardColors(containerColor = OneUICardElevated)
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = "Mood: ${song.moodProfile} (${(song.moodScore * 100).toInt()}%)",
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = NeonMint
                                    )
                                }
                            }

                            // Soft-Hide Button
                            Card(
                                shape = RoundedCornerShape(16.dp),
                                colors = CardDefaults.cardColors(
                                    containerColor = if (song.isHiddenFromLibrary) Color.Red.copy(alpha = 0.25f) else OneUICardElevated
                                ),
                                modifier = Modifier.clip(RoundedCornerShape(16.dp))
                            ) {
                                Row(
                                    modifier = Modifier
                                        .padding(horizontal = 12.dp, vertical = 6.dp)
                                        .testTag("toggle_soft_hide_button"),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    IconButton(
                                        onClick = { onToggleSoftHide(song) },
                                        modifier = Modifier.size(20.dp)
                                    ) {
                                        Icon(
                                            imageVector = if (song.isHiddenFromLibrary) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                                            contentDescription = "Soft-Hide from Library",
                                            tint = if (song.isHiddenFromLibrary) Color(0xFFFF5252) else OneUITextSecondary,
                                            modifier = Modifier.size(16.dp)
                                        )
                                    }
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text(
                                        text = if (song.isHiddenFromLibrary) "Playlist-Only (Hidden)" else "Soft-Hide",
                                        fontSize = 11.sp,
                                        color = if (song.isHiddenFromLibrary) Color(0xFFFF8A80) else OneUITextSecondary
                                    )
                                }
                            }
                        }
                    }
                }
                1 -> {
                    // Spotify-Style Synced Lyrics Tab
                    SyncedLyricsView(
                        lrcLyrics = song.lrcLyrics,
                        songTitle = song.title,
                        songArtist = song.artist,
                        durationMs = song.durationMs,
                        currentPositionMs = effectivePositionMs,
                        onSeekRequested = { targetMs -> onSeekTo(targetMs) },
                        onSaveCustomLyrics = { updatedLrc -> onSaveLyrics(song, updatedLrc) }
                    )
                }
                2 -> {
                    // Interactive Drag-and-Drop Queue Tab
                    QueueSheet(
                        queue = queue,
                        currentIndex = currentQueueIndex,
                        smartMoodQueueEnabled = smartMoodQueueEnabled,
                        onToggleSmartMoodQueue = onToggleSmartMoodQueue,
                        onSongClick = onQueueSongClick,
                        onMoveItem = onMoveQueueItem,
                        onRemoveItem = onRemoveQueueItem
                    )
                }
            }
        }

        // Bottom-Heavy One UI Playback Controls & Scrubber (Always Visible)
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp)
                .padding(bottom = 36.dp)
        ) {
            // Track Info
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                // Track Info: single, cleaned strings without string concatenation or hardcoded subtitles
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = cleanMetadataString(song.title),
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold,
                        color = OneUITextPrimary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = cleanMetadataString(song.artist),
                        fontSize = 14.sp,
                        color = OneUITextSecondary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Scrubbable Progress Bar
            Slider(
                value = effectivePositionMs.toFloat().coerceIn(0f, totalDuration.toFloat()),
                onValueChange = { newVal ->
                    isUserScrubbing = true
                    scrubPositionMs = newVal
                },
                onValueChangeFinished = {
                    onSeekTo(scrubPositionMs.toLong())
                    isUserScrubbing = false
                },
                valueRange = 0f..totalDuration.toFloat(),
                colors = SliderDefaults.colors(
                    thumbColor = SpotifyGreen,
                    activeTrackColor = SpotifyGreen,
                    inactiveTrackColor = Color.White.copy(alpha = 0.15f)
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("player_seek_slider")
            )

            // Exact Elapsed and Total Duration Readout
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = formatTime(effectivePositionMs),
                    fontSize = 12.sp,
                    color = OneUITextSecondary,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    text = formatTime(totalDuration),
                    fontSize = 12.sp,
                    color = OneUITextSecondary,
                    fontWeight = FontWeight.SemiBold
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Samsung One UI Signature Playback Controls
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                // Shuffle
                IconButton(
                    onClick = onToggleShuffle,
                    modifier = Modifier.size(48.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Shuffle,
                        contentDescription = "Toggle Shuffle",
                        tint = if (isShuffleEnabled) NeonMint else OneUITextSecondary,
                        modifier = Modifier.size(24.dp)
                    )
                }

                // Previous
                IconButton(
                    onClick = onPreviousClick,
                    modifier = Modifier.size(54.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.SkipPrevious,
                        contentDescription = "Previous Song",
                        tint = OneUITextPrimary,
                        modifier = Modifier.size(36.dp)
                    )
                }

                // Giant Circular Play/Pause FAB
                FloatingActionButton(
                    onClick = onPlayPauseClick,
                    shape = CircleShape,
                    containerColor = SpotifyGreen,
                    contentColor = Color.Black,
                    modifier = Modifier
                        .size(68.dp)
                        .testTag("full_player_play_pause")
                ) {
                    Icon(
                        imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                        contentDescription = if (isPlaying) "Pause" else "Play",
                        modifier = Modifier.size(38.dp)
                    )
                }

                // Next
                IconButton(
                    onClick = onNextClick,
                    modifier = Modifier.size(54.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.SkipNext,
                        contentDescription = "Next Song",
                        tint = OneUITextPrimary,
                        modifier = Modifier.size(36.dp)
                    )
                }

                // Repeat Mode (Off / All / One)
                IconButton(
                    onClick = onToggleRepeat,
                    modifier = Modifier.size(48.dp)
                ) {
                    Icon(
                        imageVector = if (repeatMode == RepeatMode.ONE) Icons.Default.RepeatOne else Icons.Default.Repeat,
                        contentDescription = "Toggle Repeat",
                        tint = if (repeatMode != RepeatMode.OFF) NeonMint else OneUITextSecondary,
                        modifier = Modifier.size(24.dp)
                    )
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
