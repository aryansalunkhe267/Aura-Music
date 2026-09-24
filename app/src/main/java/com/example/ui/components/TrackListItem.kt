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
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
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
import com.example.ui.theme.OneUICardElevated
import com.example.ui.theme.OneUISurfaceDark
import com.example.ui.theme.OneUITextPrimary
import com.example.ui.theme.OneUITextSecondary
import com.example.ui.theme.SpotifyGreen
import com.example.utils.cleanMetadataString

/**
 * Standardized Track List Item displaying sanitized English-only song title and artist
 * without string concatenations or hardcoded subtitles.
 */
@Composable
fun TrackListItem(
    song: SongEntity,
    modifier: Modifier = Modifier,
    index: Int? = null,
    isCurrentPlaying: Boolean = false,
    onClick: () -> Unit,
    onToggleFavorite: (() -> Unit)? = null,
    onToggleSoftHide: (() -> Unit)? = null,
    onOpenAudioEditor: (() -> Unit)? = null,
    onAddToPlaylistClick: (() -> Unit)? = null
) {
    var showMenu by remember { mutableStateOf(false) }

    Row(
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .background(if (isCurrentPlaying) SpotifyGreen.copy(alpha = 0.08f) else Color.Transparent)
            .padding(horizontal = 16.dp, vertical = 10.dp)
            .testTag("track_list_item_${song.id}"),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Track number index or animated playing indicator
        if (index != null) {
            Box(
                modifier = Modifier.width(32.dp),
                contentAlignment = Alignment.Center
            ) {
                if (isCurrentPlaying) {
                    Icon(
                        imageVector = Icons.Default.GraphicEq,
                        contentDescription = "Playing",
                        tint = SpotifyGreen,
                        modifier = Modifier.size(18.dp)
                    )
                } else {
                    Text(
                        text = index.toString(),
                        fontSize = 12.sp,
                        color = OneUITextSecondary,
                        fontWeight = FontWeight.Medium
                    )
                }
            }
        }

        // Album Art
        Box(
            modifier = Modifier
                .size(46.dp)
                .clip(RoundedCornerShape(8.dp))
        ) {
            val artModel = song.coverArtUrl ?: song.albumArtUri
            if (!artModel.isNullOrBlank()) {
                AsyncImage(
                    model = artModel,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
            } else {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(OneUISurfaceDark),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.MusicNote,
                        contentDescription = null,
                        tint = SpotifyGreen.copy(alpha = 0.6f),
                        modifier = Modifier.size(22.dp)
                    )
                }
            }
        }

        Spacer(modifier = Modifier.width(12.dp))

        // Title & Artist: strictly clean metadata string without concatenation or subtitles
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = cleanMetadataString(song.title),
                fontSize = 14.sp,
                fontWeight = if (isCurrentPlaying) FontWeight.Bold else FontWeight.SemiBold,
                color = if (isCurrentPlaying) SpotifyGreen else OneUITextPrimary,
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

        // Favorite Heart Button
        if (onToggleFavorite != null) {
            IconButton(
                onClick = onToggleFavorite,
                modifier = Modifier
                    .size(40.dp)
                    .testTag("track_favorite_button_${song.id}")
            ) {
                Icon(
                    imageVector = if (song.isFavorite) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                    contentDescription = "Favorite",
                    tint = if (song.isFavorite) Color(0xFFFF4081) else OneUITextSecondary,
                    modifier = Modifier.size(20.dp)
                )
            }
        }

        // Duration
        Text(
            text = formatDuration(song.durationMs),
            fontSize = 12.sp,
            color = OneUITextSecondary,
            modifier = Modifier.padding(horizontal = 4.dp)
        )

        // Dropdown Menu for Actions
        if (onToggleSoftHide != null || onOpenAudioEditor != null || onAddToPlaylistClick != null) {
            Box {
                IconButton(
                    onClick = { showMenu = true },
                    modifier = Modifier
                        .size(40.dp)
                        .testTag("track_menu_button_${song.id}")
                ) {
                    Icon(
                        imageVector = Icons.Default.MoreVert,
                        contentDescription = "Options",
                        tint = OneUITextSecondary,
                        modifier = Modifier.size(20.dp)
                    )
                }

                DropdownMenu(
                    expanded = showMenu,
                    onDismissRequest = { showMenu = false },
                    modifier = Modifier.background(OneUICardElevated)
                ) {
                    if (onToggleFavorite != null) {
                        DropdownMenuItem(
                            text = { Text(if (song.isFavorite) "Remove from Favorites" else "Add to Favorites", color = OneUITextPrimary) },
                            onClick = {
                                onToggleFavorite()
                                showMenu = false
                            }
                        )
                    }
                    if (onAddToPlaylistClick != null) {
                        DropdownMenuItem(
                            text = { Text("Add to Playlist", color = OneUITextPrimary) },
                            onClick = {
                                onAddToPlaylistClick()
                                showMenu = false
                            }
                        )
                    }
                    if (onOpenAudioEditor != null) {
                        DropdownMenuItem(
                            text = { Text("Trim Audio / Edit Tags", color = OneUITextPrimary) },
                            onClick = {
                                onOpenAudioEditor()
                                showMenu = false
                            }
                        )
                    }
                    if (onToggleSoftHide != null) {
                        DropdownMenuItem(
                            text = {
                                Text(
                                    if (song.isHiddenFromLibrary) "Unhide (Restore to Library)" else "Soft-Hide (Playlist Only)",
                                    color = OneUITextPrimary
                                )
                            },
                            onClick = {
                                onToggleSoftHide()
                                showMenu = false
                            }
                        )
                    }
                }
            }
        }
    }
}

private fun formatDuration(durationMs: Long): String {
    val totalSeconds = durationMs / 1000
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60
    return String.format("%d:%02d", minutes, seconds)
}
