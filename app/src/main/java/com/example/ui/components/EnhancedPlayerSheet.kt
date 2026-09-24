package com.example.ui.components

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.drawable.BitmapDrawable
import android.media.audiofx.AudioEffect
import android.widget.Toast
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ContentCut
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Downloading
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.GraphicEq
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
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.TabRowDefaults
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.palette.graphics.Palette
import coil.ImageLoader
import coil.compose.AsyncImage
import coil.request.ImageRequest
import coil.request.SuccessResult
import com.example.data.SongEntity
import com.example.playback.PlaybackManager
import com.example.playback.RepeatMode
import com.example.utils.cleanMetadataString
import com.example.ui.theme.NeonMint
import com.example.ui.theme.OneUICardElevated
import com.example.ui.theme.OneUIDarkBackground
import com.example.ui.theme.OneUITextPrimary
import com.example.ui.theme.OneUITextSecondary
import com.example.ui.theme.SpotifyGreen
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Dynamic blurred player sheet engineered with AndroidX Palette color extraction,
 * system Equalizer Intent integration with One UI in-app equalizer fallback,
 * Spotify-style fluid synced lyrics, and Samsung Music reachability.
 */
@Composable
fun EnhancedPlayerSheet(
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
    onToggleFavorite: ((SongEntity) -> Unit)? = null,
    onDownloadTrack: ((SongEntity) -> Unit)? = null,
    onCollapse: () -> Unit,
    modifier: Modifier = Modifier
) {
    if (song == null) return

    val context = LocalContext.current

    // Extracted Palette dominant accent colors
    var dominantColor by remember { mutableStateOf(Color(0xFF1E2E22)) }
    var vibrantColor by remember { mutableStateOf(SpotifyGreen) }

    // Equalizer dialog state
    var showEqualizerDialog by remember { mutableStateOf(false) }

    // Palette extraction effect
    LaunchedEffect(song.id, song.albumArtUri) {
        val artUri = song.albumArtUri
        if (!artUri.isNullOrBlank()) {
            withContext(Dispatchers.IO) {
                try {
                    val loader = ImageLoader(context)
                    val request = ImageRequest.Builder(context)
                        .data(artUri)
                        .allowHardware(false)
                        .build()
                    val result = (loader.execute(request) as? SuccessResult)?.drawable
                    val bitmap = (result as? BitmapDrawable)?.bitmap
                    if (bitmap != null) {
                        val palette = Palette.from(bitmap).generate()
                        val dominant = palette.getDominantColor(0xFF1E2E22.toInt())
                        val vibrant = palette.getVibrantColor(0xFF1DB954.toInt())
                        withContext(Dispatchers.Main) {
                            dominantColor = Color(dominant)
                            vibrantColor = Color(vibrant)
                        }
                    }
                } catch (e: Exception) {
                    // Fallback to mood-based color
                    withContext(Dispatchers.Main) {
                        dominantColor = when (song.moodProfile) {
                            "ENERGETIC" -> Color(0xFF381515)
                            "UPBEAT" -> Color(0xFF382A10)
                            "CALM" -> Color(0xFF122838)
                            else -> Color(0xFF14241B)
                        }
                    }
                }
            }
        } else {
            dominantColor = when (song.moodProfile) {
                "ENERGETIC" -> Color(0xFF381515)
                "UPBEAT" -> Color(0xFF382A10)
                "CALM" -> Color(0xFF122838)
                else -> Color(0xFF14241B)
            }
            vibrantColor = SpotifyGreen
        }
    }

    val animatedDominantColor by animateColorAsState(
        targetValue = dominantColor,
        animationSpec = tween(700),
        label = "dominantColorAnim"
    )

    // 0: Artwork & Controls, 1: Synced Lyrics, 2: Interactive Queue
    var selectedTab by remember { mutableIntStateOf(0) }

    // Scrubbing
    var isUserScrubbing by remember { mutableStateOf(false) }
    var scrubPositionMs by remember { mutableFloatStateOf(0f) }

    val effectivePositionMs = if (isUserScrubbing) scrubPositionMs.toLong() else currentPositionMs
    val totalDuration = durationMs.coerceAtLeast(1L)

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    colors = listOf(
                        animatedDominantColor.copy(alpha = 0.85f),
                        OneUIDarkBackground.copy(alpha = 0.95f),
                        OneUIDarkBackground
                    )
                )
            )
            .testTag("enhanced_player_sheet")
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(top = 12.dp)
        ) {
            // Top Bar: Collapse, Playing Badge, Equalizer & Lossless Cutter
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
                        text = if (song.sourceType == "ONLINE") "STREAMING AUDIO" else "OFFLINE LIBRARY",
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

                Row(verticalAlignment = Alignment.CenterVertically) {
                    // Equalizer Action
                    IconButton(
                        onClick = {
                            openEqualizer(context, PlaybackManager.audioSessionId) {
                                showEqualizerDialog = true
                            }
                        },
                        modifier = Modifier
                            .size(48.dp)
                            .testTag("equalizer_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.GraphicEq,
                            contentDescription = "Equalizer",
                            tint = OneUITextPrimary,
                            modifier = Modifier.size(24.dp)
                        )
                    }

                    // Trim Track Action
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
                            modifier = Modifier.size(22.dp)
                        )
                    }
                }
            }

            // Tab Navigation: Artwork | Lyrics | Queue
            TabRow(
                selectedTabIndex = selectedTab,
                containerColor = Color.Transparent,
                contentColor = SpotifyGreen,
                indicator = { tabPositions ->
                    TabRowDefaults.SecondaryIndicator(
                        modifier = Modifier.tabIndicatorOffset(tabPositions[selectedTab]),
                        color = vibrantColor,
                        height = 3.dp
                    )
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp, vertical = 6.dp)
            ) {
                Tab(
                    selected = selectedTab == 0,
                    onClick = { selectedTab = 0 },
                    text = {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.MusicNote, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("Now Playing", fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                        }
                    },
                    selectedContentColor = OneUITextPrimary,
                    unselectedContentColor = OneUITextSecondary
                )
                Tab(
                    selected = selectedTab == 1,
                    onClick = { selectedTab = 1 },
                    text = {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Lyrics, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("Lyrics", fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                        }
                    },
                    selectedContentColor = OneUITextPrimary,
                    unselectedContentColor = OneUITextSecondary
                )
                Tab(
                    selected = selectedTab == 2,
                    onClick = { selectedTab = 2 },
                    text = {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.AutoMirrored.Filled.QueueMusic, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("Queue (${queue.size})", fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                        }
                    },
                    selectedContentColor = OneUITextPrimary,
                    unselectedContentColor = OneUITextSecondary
                )
            }

            // Body Switcher
            AnimatedContent(
                targetState = selectedTab,
                modifier = Modifier.weight(1f),
                label = "player_tab_animation"
            ) { targetTab ->
                when (targetTab) {
                    0 -> NowPlayingArtworkView(
                        song = song,
                        vibrantColor = vibrantColor,
                        onToggleSoftHide = onToggleSoftHide,
                        onDownloadTrack = onDownloadTrack
                    )
                    1 -> SyncedLyricsView(
                        lrcLyrics = song.lrcLyrics,
                        songTitle = cleanMetadataString(song.title),
                        songArtist = cleanMetadataString(song.artist),
                        durationMs = totalDuration,
                        currentPositionMs = currentPositionMs,
                        onSeekRequested = onSeekTo,
                        onSaveCustomLyrics = { updatedLyrics: String -> onSaveLyrics(song, updatedLyrics) }
                    )
                    2 -> QueueSheet(
                        queue = queue,
                        currentIndex = currentQueueIndex,
                        smartMoodQueueEnabled = smartMoodQueueEnabled,
                        onSongClick = onQueueSongClick,
                        onMoveItem = onMoveQueueItem,
                        onRemoveItem = onRemoveQueueItem,
                        onToggleSmartMoodQueue = onToggleSmartMoodQueue
                    )
                }
            }

            // Samsung One UI Bottom Reachability Controls
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp, vertical = 12.dp)
            ) {
                // Track Info Header
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
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

                    Row(verticalAlignment = Alignment.CenterVertically) {
                        // Spotify-Style Heart Favorite Button
                        if (onToggleFavorite != null) {
                            IconButton(
                                onClick = { onToggleFavorite(song) },
                                modifier = Modifier
                                    .size(48.dp)
                                    .testTag("player_favorite_button")
                            ) {
                                Icon(
                                    imageVector = if (song.isFavorite) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                                    contentDescription = "Favorite",
                                    tint = if (song.isFavorite) Color(0xFFFF4081) else OneUITextSecondary,
                                    modifier = Modifier.size(26.dp)
                                )
                            }
                        }

                        // Soft-Hide Vault Toggle
                        IconButton(
                            onClick = { onToggleSoftHide(song) },
                            modifier = Modifier
                                .size(48.dp)
                                .testTag("toggle_soft_hide_button")
                        ) {
                            Icon(
                                imageVector = if (song.isHiddenFromLibrary) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                                contentDescription = if (song.isHiddenFromLibrary) "In Hidden Vault" else "In Library",
                                tint = if (song.isHiddenFromLibrary) NeonMint else OneUITextSecondary,
                                modifier = Modifier.size(24.dp)
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                // Smooth Progress Scrub Slider
                Slider(
                    value = effectivePositionMs.toFloat(),
                    onValueChange = { newValue ->
                        isUserScrubbing = true
                        scrubPositionMs = newValue
                    },
                    onValueChangeFinished = {
                        isUserScrubbing = false
                        onSeekTo(scrubPositionMs.toLong())
                    },
                    valueRange = 0f..totalDuration.toFloat(),
                    colors = SliderDefaults.colors(
                        thumbColor = vibrantColor,
                        activeTrackColor = vibrantColor,
                        inactiveTrackColor = Color.White.copy(alpha = 0.2f)
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("player_progress_slider")
                )

                // Timestamps: Elapsed & Remaining
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = formatTimeMs(effectivePositionMs),
                        fontSize = 12.sp,
                        color = OneUITextSecondary
                    )
                    Text(
                        text = "-${formatTimeMs((totalDuration - effectivePositionMs).coerceAtLeast(0L))}",
                        fontSize = 12.sp,
                        color = OneUITextSecondary
                    )
                }

                Spacer(modifier = Modifier.height(8.dp))

                // Transport Controls
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Shuffle
                    IconButton(
                        onClick = onToggleShuffle,
                        modifier = Modifier
                            .size(48.dp)
                            .testTag("shuffle_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Shuffle,
                            contentDescription = "Shuffle",
                            tint = if (isShuffleEnabled) vibrantColor else OneUITextSecondary,
                            modifier = Modifier.size(24.dp)
                        )
                    }

                    // Previous Track
                    IconButton(
                        onClick = onPreviousClick,
                        modifier = Modifier
                            .size(52.dp)
                            .testTag("prev_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.SkipPrevious,
                            contentDescription = "Previous Track",
                            tint = OneUITextPrimary,
                            modifier = Modifier.size(36.dp)
                        )
                    }

                    // Primary Play / Pause Action Button
                    FloatingActionButton(
                        onClick = onPlayPauseClick,
                        shape = CircleShape,
                        containerColor = vibrantColor,
                        contentColor = Color.Black,
                        modifier = Modifier
                            .size(68.dp)
                            .testTag("play_pause_fab")
                    ) {
                        Icon(
                            imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                            contentDescription = if (isPlaying) "Pause" else "Play",
                            modifier = Modifier.size(38.dp)
                        )
                    }

                    // Next Track
                    IconButton(
                        onClick = onNextClick,
                        modifier = Modifier
                            .size(52.dp)
                            .testTag("next_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.SkipNext,
                            contentDescription = "Next Track",
                            tint = OneUITextPrimary,
                            modifier = Modifier.size(36.dp)
                        )
                    }

                    // Repeat Mode
                    IconButton(
                        onClick = onToggleRepeat,
                        modifier = Modifier
                            .size(48.dp)
                            .testTag("repeat_button")
                    ) {
                        Icon(
                            imageVector = when (repeatMode) {
                                RepeatMode.OFF -> Icons.Default.Repeat
                                RepeatMode.ALL -> Icons.Default.Repeat
                                RepeatMode.ONE -> Icons.Default.RepeatOne
                            },
                            contentDescription = "Repeat: $repeatMode",
                            tint = if (repeatMode != RepeatMode.OFF) vibrantColor else OneUITextSecondary,
                            modifier = Modifier.size(24.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))
            }
        }
    }

    // Built-in One UI Equalizer Fallback Dialog
    if (showEqualizerDialog) {
        OneUIEqualizerDialog(
            onDismiss = { showEqualizerDialog = false }
        )
    }
}

@Composable
private fun NowPlayingArtworkView(
    song: SongEntity,
    vibrantColor: Color,
    onToggleSoftHide: (SongEntity) -> Unit,
    onDownloadTrack: ((SongEntity) -> Unit)?
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 28.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Card(
            shape = RoundedCornerShape(20.dp),
            elevation = CardDefaults.cardElevation(defaultElevation = 14.dp),
            colors = CardDefaults.cardColors(containerColor = OneUICardElevated),
            modifier = Modifier
                .fillMaxWidth(0.85f)
                .aspectRatio(1f)
        ) {
            Box(modifier = Modifier.fillMaxSize()) {
                val artModel = song.coverArtUrl ?: song.albumArtUri
                if (!artModel.isNullOrBlank()) {
                    AsyncImage(
                        model = artModel,
                        contentDescription = "Album Artwork",
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    )
                } else {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(
                                Brush.linearGradient(
                                    colors = listOf(
                                        Color(0xFF233529),
                                        Color(0xFF111E16)
                                    )
                                )
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.MusicNote,
                            contentDescription = "Music Note",
                            tint = SpotifyGreen.copy(alpha = 0.5f),
                            modifier = Modifier.size(80.dp)
                        )
                    }
                }

                // Download status badge overlay for online songs
                if (song.sourceType == "ONLINE") {
                    Box(
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .padding(12.dp)
                    ) {
                        if (song.isDownloaded) {
                            Surface(
                                shape = RoundedCornerShape(12.dp),
                                color = Color.Black.copy(alpha = 0.65f)
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(Icons.Default.CheckCircle, contentDescription = null, tint = SpotifyGreen, modifier = Modifier.size(14.dp))
                                    Spacer(Modifier.width(4.dp))
                                    Text("Downloaded", fontSize = 11.sp, color = Color.White, fontWeight = FontWeight.Bold)
                                }
                            }
                        } else if (onDownloadTrack != null) {
                            IconButton(
                                onClick = { onDownloadTrack(song) },
                                modifier = Modifier
                                    .size(40.dp)
                                    .background(Color.Black.copy(alpha = 0.65f), CircleShape)
                            ) {
                                Icon(Icons.Default.Download, contentDescription = "Download Offline", tint = Color.White, modifier = Modifier.size(20.dp))
                            }
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(20.dp))

        // Acoustic Mood & Language Tag Chips
        Row(
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = Color.White.copy(alpha = 0.10f),
                modifier = Modifier.padding(horizontal = 4.dp)
            ) {
                Text(
                    text = "Mood: ${song.moodProfile}",
                    fontSize = 12.sp,
                    color = OneUITextPrimary,
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                    fontWeight = FontWeight.Medium
                )
            }

            Surface(
                shape = RoundedCornerShape(16.dp),
                color = vibrantColor.copy(alpha = 0.20f),
                modifier = Modifier.padding(horizontal = 4.dp)
            ) {
                Text(
                    text = if (song.sourceType == "ONLINE") "Stream / Cloud" else "Offline Lossless",
                    fontSize = 12.sp,
                    color = vibrantColor,
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                    fontWeight = FontWeight.SemiBold
                )
            }
        }
    }
}

/**
 * Attempts to launch system Equalizer Intent, falling back to internal Equalizer dialog.
 */
private fun openEqualizer(context: Context, audioSessionId: Int, onFallback: () -> Unit) {
    try {
        val intent = Intent(AudioEffect.ACTION_DISPLAY_AUDIO_EFFECT_CONTROL_PANEL).apply {
            putExtra(AudioEffect.EXTRA_AUDIO_SESSION, audioSessionId)
            putExtra(AudioEffect.EXTRA_PACKAGE_NAME, context.packageName)
            putExtra(AudioEffect.EXTRA_CONTENT_TYPE, AudioEffect.CONTENT_TYPE_MUSIC)
        }
        val resolveInfo = context.packageManager.resolveActivity(intent, 0)
        if (resolveInfo != null) {
            context.startActivity(intent)
        } else {
            onFallback()
        }
    } catch (e: Exception) {
        onFallback()
    }
}

/**
 * Built-in Samsung One UI Style Equalizer Dialog.
 */
@Composable
fun OneUIEqualizerDialog(
    onDismiss: () -> Unit
) {
    var selectedPreset by remember { mutableStateOf("Pop") }
    var bassBoost by remember { mutableFloatStateOf(0.7f) }
    var clarity by remember { mutableFloatStateOf(0.6f) }
    var band60Hz by remember { mutableFloatStateOf(0.75f) }
    var band230Hz by remember { mutableFloatStateOf(0.60f) }
    var band910Hz by remember { mutableFloatStateOf(0.50f) }
    var band3kHz by remember { mutableFloatStateOf(0.65f) }
    var band14kHz by remember { mutableFloatStateOf(0.80f) }

    val presets = listOf("Flat", "Pop", "Rock", "Bass Boost", "Classic", "Jazz")

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = RoundedCornerShape(24.dp),
            color = OneUIDarkBackground,
            tonalElevation = 8.dp,
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Sound Equalizer",
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold,
                        color = OneUITextPrimary
                    )
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Default.KeyboardArrowDown, contentDescription = "Close", tint = OneUITextSecondary)
                    }
                }

                Spacer(Modifier.height(12.dp))

                // Presets row
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    presets.take(4).forEach { preset ->
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = if (selectedPreset == preset) SpotifyGreen else OneUICardElevated,
                            modifier = Modifier
                                .weight(1f)
                                .clickable {
                                    selectedPreset = preset
                                    when (preset) {
                                        "Bass Boost" -> {
                                            bassBoost = 0.95f; band60Hz = 0.9f; band230Hz = 0.75f
                                        }
                                        "Pop" -> {
                                            bassBoost = 0.6f; band60Hz = 0.65f; band3kHz = 0.75f
                                        }
                                        "Rock" -> {
                                            bassBoost = 0.8f; band60Hz = 0.85f; band14kHz = 0.85f
                                        }
                                        else -> {
                                            bassBoost = 0.5f; band60Hz = 0.5f; band230Hz = 0.5f; band910Hz = 0.5f; band3kHz = 0.5f; band14kHz = 0.5f
                                        }
                                    }
                                }
                        ) {
                            Text(
                                text = preset,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = if (selectedPreset == preset) Color.Black else OneUITextPrimary,
                                textAlign = TextAlign.Center,
                                modifier = Modifier.padding(vertical = 8.dp)
                            )
                        }
                    }
                }

                Spacer(Modifier.height(16.dp))

                // Bass Boost & Clarity Knobs
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Bass Boost", fontSize = 12.sp, color = OneUITextSecondary)
                        Slider(
                            value = bassBoost,
                            onValueChange = { bassBoost = it },
                            colors = SliderDefaults.colors(thumbColor = NeonMint, activeTrackColor = NeonMint)
                        )
                    }
                    Spacer(Modifier.width(16.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Clarity", fontSize = 12.sp, color = OneUITextSecondary)
                        Slider(
                            value = clarity,
                            onValueChange = { clarity = it },
                            colors = SliderDefaults.colors(thumbColor = SpotifyGreen, activeTrackColor = SpotifyGreen)
                        )
                    }
                }

                Spacer(Modifier.height(12.dp))

                Text("Frequency Bands", fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = OneUITextSecondary)
                Spacer(Modifier.height(4.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    EqualizerBandColumn("60Hz", band60Hz) { band60Hz = it }
                    EqualizerBandColumn("230Hz", band230Hz) { band230Hz = it }
                    EqualizerBandColumn("910Hz", band910Hz) { band910Hz = it }
                    EqualizerBandColumn("3.6kHz", band3kHz) { band3kHz = it }
                    EqualizerBandColumn("14kHz", band14kHz) { band14kHz = it }
                }

                Spacer(Modifier.height(16.dp))

                TextButton(
                    onClick = onDismiss,
                    modifier = Modifier.align(Alignment.End)
                ) {
                    Text("Apply & Close", color = SpotifyGreen, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

@Composable
private fun EqualizerBandColumn(label: String, value: Float, onValueChange: (Float) -> Unit) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.width(54.dp)
    ) {
        Text(
            text = "${(value * 12 - 6).toInt()}dB",
            fontSize = 10.sp,
            color = OneUITextSecondary
        )
        Slider(
            value = value,
            onValueChange = onValueChange,
            colors = SliderDefaults.colors(thumbColor = SpotifyGreen, activeTrackColor = SpotifyGreen),
            modifier = Modifier.height(110.dp)
        )
        Text(
            text = label,
            fontSize = 10.sp,
            color = OneUITextPrimary,
            fontWeight = FontWeight.Medium
        )
    }
}

private fun formatTimeMs(timeMs: Long): String {
    val totalSeconds = (timeMs / 1000).coerceAtLeast(0L)
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60
    return String.format("%d:%02d", minutes, seconds)
}
