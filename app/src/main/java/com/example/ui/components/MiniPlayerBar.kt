package com.example.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.example.data.SongEntity
import com.example.ui.theme.NeonMint
import com.example.ui.theme.OneUICardElevated
import com.example.ui.theme.OneUITextPrimary
import com.example.ui.theme.OneUITextSecondary
import com.example.ui.theme.SpotifyGreen
import com.example.utils.CachedSongArtwork
import com.example.utils.cleanMetadataString
import com.example.utils.rememberDebouncedClick

@Composable
fun MiniPlayerBar(
    song: SongEntity?,
    isPlaying: Boolean,
    currentPositionMs: Long,
    durationMs: Long,
    onPlayPauseClick: () -> Unit,
    onNextClick: () -> Unit,
    onToggleFavorite: (() -> Unit)? = null,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    if (song == null) return

    val progress = if (durationMs > 0) {
        (currentPositionMs.toFloat() / durationMs.toFloat()).coerceIn(0f, 1f)
    } else 0f

    val debouncedFavorite = onToggleFavorite?.let { rememberDebouncedClick(300L, it) }
    val debouncedPlayPause = rememberDebouncedClick(300L, onPlayPauseClick)
    val debouncedNext = rememberDebouncedClick(300L, onNextClick)

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(72.dp)
            .background(OneUICardElevated)
            .clickable { onClick() }
            .testTag("mini_player_bar")
    ) {
        // Signature Samsung Music top slim progress indicator
        LinearProgressIndicator(
            progress = { progress },
            modifier = Modifier
                .fillMaxWidth()
                .height(3.dp)
                .align(Alignment.TopCenter),
            color = SpotifyGreen,
            trackColor = Color.White.copy(alpha = 0.1f)
        )

        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 14.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Album Art Thumbnail with memory cache and ID3 fallback
            CachedSongArtwork(
                song = song,
                modifier = Modifier.size(50.dp),
                shape = RoundedCornerShape(12.dp),
                placeholderIconSize = 24.dp
            )

            Spacer(modifier = Modifier.width(12.dp))

            // Title & Artist: strictly single cleaned strings without subtitles or concatenation
            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(end = 4.dp)
            ) {
                Text(
                    text = cleanMetadataString(song.title),
                    fontWeight = FontWeight.Bold,
                    fontSize = 15.sp,
                    color = OneUITextPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = cleanMetadataString(song.artist),
                    fontSize = 12.sp,
                    color = OneUITextSecondary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            // Favorite Button
            if (onToggleFavorite != null) {
                IconButton(
                    onClick = { debouncedFavorite?.invoke() },
                    modifier = Modifier
                        .size(44.dp)
                        .testTag("mini_player_favorite")
                ) {
                    Icon(
                        imageVector = if (song.isFavorite) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                        contentDescription = "Favorite",
                        tint = if (song.isFavorite) Color(0xFFFF4081) else OneUITextSecondary,
                        modifier = Modifier.size(22.dp)
                    )
                }
            }

            // Explicit Controls: Play/Pause and Next
            IconButton(
                onClick = debouncedPlayPause,
                modifier = Modifier
                    .size(44.dp)
                    .testTag("mini_player_play_pause")
            ) {
                Icon(
                    imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                    contentDescription = if (isPlaying) "Pause" else "Play",
                    tint = OneUITextPrimary,
                    modifier = Modifier.size(28.dp)
                )
            }

            IconButton(
                onClick = debouncedNext,
                modifier = Modifier
                    .size(44.dp)
                    .testTag("mini_player_next")
            ) {
                Icon(
                    imageVector = Icons.Default.SkipNext,
                    contentDescription = "Next Track",
                    tint = OneUITextPrimary,
                    modifier = Modifier.size(28.dp)
                )
            }
        }
    }
}
