package com.example.ui.components

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.drawable.BitmapDrawable
import android.media.audiofx.AudioEffect
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.QueueMusic
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCut
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
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
import com.example.ui.theme.NeonMint
import com.example.ui.theme.OneUICardElevated
import com.example.ui.theme.OneUIDarkBackground
import com.example.ui.theme.OneUITextPrimary
import com.example.ui.theme.OneUITextSecondary
import com.example.ui.theme.SpotifyGreen
import com.example.utils.LrcParser
import com.example.utils.LyricLine
import com.example.utils.cleanMetadataString
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Spotify/One UI Hybrid Now Playing Sheet.
 * Features:
 * - Dynamic Palette background gradient
 * - Floating Live Lyric Line above play/scrub bar with real-time transitions
 * - Expanding Spotify-style synchronized lyrics card with active line highlighting and seeking
 * - Lossless trimmer and equalizer shortcuts
 * - Interactive Queue drawer
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
    var dominantColor by remember { mutableStateOf(Color(0xFF14241B)) }
    var vibrantColor by remember { mutableStateOf(Color(0xFF39FF14)) }

    // Equalizer dialog state
    var showEqualizerDialog by remember { mutableStateOf(false) }

    // Expanding Lyrics Card state (toggled via Floating Live Lyric line)
    var isLyricsCardExpanded by remember { mutableStateOf(false) }

    // Lyrics parsing
    val lyricsContent = song.syncedLyrics ?: song.lrcLyrics
    val parsedLyrics = remember(lyricsContent) {
        LrcParser.parse(lyricsContent)
    }

    val activeLyricIndex = remember(currentPositionMs, parsedLyrics) {
        LrcParser.findActiveIndex(parsedLyrics, currentPositionMs)
    }

    val currentLyricLine = remember(activeLyricIndex, parsedLyrics) {
        if (activeLyricIndex in parsedLyrics.indices) parsedLyrics[activeLyricIndex].text else null
    }

    // Palette extraction effect
    LaunchedEffect(song.id, song.albumArtUri, song.coverArtUrl) {
        val artUri = song.coverArtUrl ?: song.albumArtUri
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
                        val dominant = palette.getDominantColor(0xFF14241B.toInt())
                        val vibrant = palette.getVibrantColor(0xFF39FF14.toInt())
                        withContext(Dispatchers.Main) {
                            dominantColor = Color(dominant)
                            vibrantColor = Color(vibrant)
                        }
                    }
                } catch (_: Exception) {}
            }
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
                .padding(top = 10.dp)
        ) {
            // Top Bar: Collapse, Header Badge, Equalizer & Trimmer
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
                        color = MaterialTheme.colorScheme.primary
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
                        modifier = Modifier.size(48.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.GraphicEq,
                            contentDescription = "Equalizer",
                            tint = OneUITextPrimary,
                            modifier = Modifier.size(24.dp)
                        )
                    }

                    // Trim Audio Action
                    IconButton(
                        onClick = { onOpenAudioEditor(song) },
                        modifier = Modifier.size(48.dp)
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
                contentColor = MaterialTheme.colorScheme.primary,
                indicator = { tabPositions ->
                    TabRowDefaults.SecondaryIndicator(
                        modifier = Modifier.tabIndicatorOffset(tabPositions[selectedTab]),
                        color = MaterialTheme.colorScheme.primary,
                        height = 3.dp
                    )
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp, vertical = 4.dp)
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
                    0 -> NowPlayingTabContent(
                        song = song,
                        isLyricsCardExpanded = isLyricsCardExpanded,
                        onToggleLyricsCard = { isLyricsCardExpanded = !isLyricsCardExpanded },
                        parsedLyrics = parsedLyrics,
                        activeLyricIndex = activeLyricIndex,
                        currentLyricLine = currentLyricLine,
                        onSeekTo = onSeekTo,
                        onDownloadTrack = onDownloadTrack
                    )
                    1 -> SyncedLyricsView(
                        lrcLyrics = lyricsContent,
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
                    .padding(horizontal = 24.dp, vertical = 8.dp)
            ) {
                // Track Info Header & Favorite Heart
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
                        // Optimistic Heart Favorite Button
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
                                    tint = if (song.isFavorite) Color(0xFFFF2A6D) else OneUITextSecondary,
                                    modifier = Modifier.size(28.dp)
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
                                contentDescription = if (song.isHiddenFromLibrary) "In Vault" else "In Library",
                                tint = if (song.isHiddenFromLibrary) MaterialTheme.colorScheme.primary else OneUITextSecondary,
                                modifier = Modifier.size(24.dp)
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(6.dp))

                // Progress Scrub Slider
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
                        thumbColor = MaterialTheme.colorScheme.primary,
                        activeTrackColor = MaterialTheme.colorScheme.primary,
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

                Spacer(modifier = Modifier.height(6.dp))

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
                            tint = if (isShuffleEnabled) MaterialTheme.colorScheme.primary else OneUITextSecondary,
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

                    // Play/Pause Action
                    FloatingActionButton(
                        onClick = onPlayPauseClick,
                        shape = CircleShape,
                        containerColor = MaterialTheme.colorScheme.primary,
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
                            tint = if (repeatMode != RepeatMode.OFF) MaterialTheme.colorScheme.primary else OneUITextSecondary,
                            modifier = Modifier.size(24.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))
            }
        }
    }

    if (showEqualizerDialog) {
        OneUIEqualizerDialog(onDismiss = { showEqualizerDialog = false })
    }
}

/**
 * Now Playing Tab Content featuring Album Art and the Spotify-Style Floating Live Lyric Line / Expanding Card.
 */
@Composable
private fun NowPlayingTabContent(
    song: SongEntity,
    isLyricsCardExpanded: Boolean,
    onToggleLyricsCard: () -> Unit,
    parsedLyrics: List<LyricLine>,
    activeLyricIndex: Int,
    currentLyricLine: String?,
    onSeekTo: (Long) -> Unit,
    onDownloadTrack: ((SongEntity) -> Unit)?
) {
    val listState = rememberLazyListState()

    LaunchedEffect(activeLyricIndex, isLyricsCardExpanded) {
        if (isLyricsCardExpanded && activeLyricIndex >= 0 && parsedLyrics.isNotEmpty()) {
            val target = (activeLyricIndex - 2).coerceAtLeast(0)
            listState.animateScrollToItem(target)
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // If lyrics card is expanded, show the full synchronized scrolling view
        if (isLyricsCardExpanded) {
            Card(
                shape = RoundedCornerShape(24.dp),
                colors = CardDefaults.cardColors(containerColor = OneUICardElevated.copy(alpha = 0.95f)),
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .padding(vertical = 8.dp)
                    .testTag("expanding_lyrics_card")
            ) {
                Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.Lyrics,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(Modifier.width(8.dp))
                            Text(
                                text = "Synchronized Lyrics",
                                fontWeight = FontWeight.Bold,
                                color = OneUITextPrimary,
                                fontSize = 15.sp
                            )
                        }

                        IconButton(onClick = onToggleLyricsCard) {
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = "Collapse Lyrics",
                                tint = OneUITextSecondary
                            )
                        }
                    }

                    Spacer(Modifier.height(8.dp))

                    if (parsedLyrics.isEmpty()) {
                        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            Text(
                                text = "Lyrics fetching automatically in background...",
                                color = OneUITextSecondary,
                                fontSize = 13.sp,
                                textAlign = TextAlign.Center
                            )
                        }
                    } else {
                        LazyColumn(
                            state = listState,
                            modifier = Modifier.fillMaxSize(),
                            contentPadding = PaddingValues(vertical = 40.dp),
                            verticalArrangement = Arrangement.spacedBy(16.dp)
                        ) {
                            itemsIndexed(parsedLyrics) { index, line ->
                                val isActive = index == activeLyricIndex
                                val isPast = index < activeLyricIndex

                                val textColor by animateColorAsState(
                                    targetValue = when {
                                        isActive -> MaterialTheme.colorScheme.primary
                                        isPast -> Color.White.copy(alpha = 0.35f)
                                        else -> Color.White.copy(alpha = 0.65f)
                                    },
                                    animationSpec = tween(300),
                                    label = "lyricTextColor"
                                )

                                Text(
                                    text = line.text,
                                    fontSize = if (isActive) 20.sp else 16.sp,
                                    fontWeight = if (isActive) FontWeight.ExtraBold else FontWeight.Medium,
                                    color = textColor,
                                    lineHeight = if (isActive) 28.sp else 22.sp,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable { onSeekTo(line.timeMs) }
                                        .padding(vertical = 2.dp)
                                )
                            }
                        }
                    }
                }
            }
        } else {
            // Default view: Artwork and Floating Live Lyric Line
            Spacer(modifier = Modifier.weight(0.1f))

            Card(
                shape = RoundedCornerShape(20.dp),
                elevation = CardDefaults.cardElevation(defaultElevation = 14.dp),
                colors = CardDefaults.cardColors(containerColor = OneUICardElevated),
                modifier = Modifier
                    .fillMaxWidth(0.82f)
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
                                        listOf(Color(0xFF1E2822), Color(0xFF0F1612))
                                    )
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.MusicNote,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.5f),
                                modifier = Modifier.size(80.dp)
                            )
                        }
                    }

                    if (song.sourceType == "ONLINE" && song.isDownloaded) {
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = Color.Black.copy(alpha = 0.65f),
                            modifier = Modifier.align(Alignment.TopEnd).padding(12.dp)
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(Icons.Default.CheckCircle, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(14.dp))
                                Spacer(Modifier.width(4.dp))
                                Text("Offline", fontSize = 11.sp, color = Color.White, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.weight(0.2f))

            // SPOTIFY-STYLE FLOATING LIVE LYRIC LINE
            // Rendered directly above the track info & scrub controls with real-time transitions
            Card(
                shape = RoundedCornerShape(18.dp),
                colors = CardDefaults.cardColors(containerColor = OneUICardElevated.copy(alpha = 0.85f)),
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(onClick = onToggleLyricsCard)
                    .testTag("floating_live_lyric_line")
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        modifier = Modifier.weight(1f),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.Lyrics,
                            contentDescription = "Lyrics",
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(10.dp))

                        AnimatedContent(
                            targetState = currentLyricLine ?: "♪ Instrumental / Synced Lyrics ♪",
                            label = "floatingLyricLineAnim"
                        ) { lineText ->
                            Text(
                                text = lineText,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold,
                                color = if (currentLyricLine != null) MaterialTheme.colorScheme.primary else OneUITextSecondary,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }

                    Icon(
                        imageVector = Icons.Default.ExpandLess,
                        contentDescription = "Expand Lyrics Card",
                        tint = OneUITextSecondary,
                        modifier = Modifier.size(22.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(10.dp))
        }
    }
}

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
    } catch (_: Exception) {
        onFallback()
    }
}

@Composable
fun OneUIEqualizerDialog(onDismiss: () -> Unit) {
    var selectedPreset by remember { mutableStateOf("Pop") }
    var bassBoost by remember { mutableFloatStateOf(0.7f) }
    var clarity by remember { mutableFloatStateOf(0.6f) }

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = RoundedCornerShape(24.dp),
            color = OneUIDarkBackground,
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(20.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Sound Equalizer", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = OneUITextPrimary)
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Default.KeyboardArrowDown, contentDescription = "Close", tint = OneUITextSecondary)
                    }
                }

                Spacer(Modifier.height(12.dp))

                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf("Pop", "Rock", "Bass Boost", "Vocal").forEach { preset ->
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = if (selectedPreset == preset) MaterialTheme.colorScheme.primary else OneUICardElevated,
                            modifier = Modifier
                                .weight(1f)
                                .clickable { selectedPreset = preset }
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

                Text("Bass Boost", fontSize = 12.sp, color = OneUITextSecondary)
                Slider(
                    value = bassBoost,
                    onValueChange = { bassBoost = it },
                    colors = SliderDefaults.colors(
                        thumbColor = MaterialTheme.colorScheme.primary,
                        activeTrackColor = MaterialTheme.colorScheme.primary
                    )
                )

                Text("Clarity", fontSize = 12.sp, color = OneUITextSecondary)
                Slider(
                    value = clarity,
                    onValueChange = { clarity = it },
                    colors = SliderDefaults.colors(
                        thumbColor = NeonMint,
                        activeTrackColor = NeonMint
                    )
                )

                Spacer(Modifier.height(12.dp))

                TextButton(onClick = onDismiss, modifier = Modifier.align(Alignment.End)) {
                    Text("Apply & Close", color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

private fun formatTimeMs(timeMs: Long): String {
    val totalSeconds = (timeMs / 1000).coerceAtLeast(0L)
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60
    return String.format("%d:%02d", minutes, seconds)
}
