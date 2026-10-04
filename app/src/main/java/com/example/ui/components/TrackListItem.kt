package com.example.ui.components

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ContentCut
import androidx.compose.material.icons.filled.DeleteForever
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.PlaylistAdd
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.SongEntity
import com.example.ui.theme.OneUICardElevated
import com.example.ui.theme.OneUISurfaceDark
import com.example.ui.theme.OneUITextPrimary
import com.example.ui.theme.OneUITextSecondary
import com.example.utils.CachedSongArtwork
import com.example.utils.cleanMetadataString
import com.example.utils.rememberDebouncedClick

/**
 * Standardized Track List Item with combinedClickable for multi-select long-press,
 * high-res embedded art resolution via CachedSongArtwork, optimistic favorite toggle,
 * metadata override editing, and physical/virtual deletion.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun TrackListItem(
    song: SongEntity,
    modifier: Modifier = Modifier,
    index: Int? = null,
    isCurrentPlaying: Boolean = false,
    isSelectionMode: Boolean = false,
    isSelected: Boolean = false,
    onClick: () -> Unit,
    onLongClick: (() -> Unit)? = null,
    onToggleFavorite: (() -> Unit)? = null,
    onToggleSoftHide: (() -> Unit)? = null,
    onDeleteFromStorage: (() -> Unit)? = null,
    onOpenAudioEditor: (() -> Unit)? = null,
    onAddToPlaylistClick: (() -> Unit)? = null,
    onEditMetadata: (() -> Unit)? = null
) {
    var showMenu by remember { mutableStateOf(false) }
    val debouncedFavorite = onToggleFavorite?.let { rememberDebouncedClick(300L, it) }

    val rowBackground = when {
        isSelected -> MaterialTheme.colorScheme.primary.copy(alpha = 0.22f)
        isCurrentPlaying -> MaterialTheme.colorScheme.primary.copy(alpha = 0.08f)
        else -> Color.Transparent
    }

    Row(
        modifier = modifier
            .fillMaxWidth()
            .then(
                if (isSelected) {
                    Modifier
                        .padding(horizontal = 8.dp, vertical = 2.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.22f))
                        .border(1.5.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.7f), RoundedCornerShape(12.dp))
                } else {
                    Modifier.background(rowBackground)
                }
            )
            .combinedClickable(
                onClick = onClick,
                onLongClick = onLongClick
            )
            .padding(horizontal = 16.dp, vertical = 10.dp)
            .testTag("track_list_item_${song.id}"),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Multi-select Checkbox
        if (isSelectionMode) {
            Checkbox(
                checked = isSelected,
                onCheckedChange = { onLongClick?.invoke() ?: onClick() },
                colors = CheckboxDefaults.colors(
                    checkedColor = MaterialTheme.colorScheme.primary,
                    uncheckedColor = OneUITextSecondary,
                    checkmarkColor = Color.Black
                ),
                modifier = Modifier.padding(end = 8.dp)
            )
        } else if (index != null) {
            // Track number index or animated playing indicator
            Box(
                modifier = Modifier.width(30.dp),
                contentAlignment = Alignment.Center
            ) {
                if (isCurrentPlaying) {
                    Icon(
                        imageVector = Icons.Default.GraphicEq,
                        contentDescription = "Playing",
                        tint = MaterialTheme.colorScheme.primary,
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

        // High-res Embedded Album Art via CachedSongArtwork (discards web video thumbnails for local audio)
        CachedSongArtwork(
            song = song,
            modifier = Modifier.size(46.dp),
            shape = RoundedCornerShape(8.dp),
            placeholderIconSize = 22.dp
        )

        Spacer(modifier = Modifier.width(12.dp))

        // Title & Artist
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = cleanMetadataString(song.title),
                fontSize = 14.sp,
                fontWeight = if (isCurrentPlaying || isSelected) FontWeight.Bold else FontWeight.SemiBold,
                color = if (isCurrentPlaying) MaterialTheme.colorScheme.primary else OneUITextPrimary,
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

        // Favorite Heart Button with debounced click
        if (debouncedFavorite != null && !isSelectionMode) {
            IconButton(
                onClick = debouncedFavorite,
                modifier = Modifier
                    .size(40.dp)
                    .testTag("track_favorite_button_${song.id}")
            ) {
                Icon(
                    imageVector = if (song.isFavorite) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                    contentDescription = "Favorite",
                    tint = if (song.isFavorite) Color(0xFFFF2A6D) else OneUITextSecondary,
                    modifier = Modifier.size(20.dp)
                )
            }
        }

        // Duration
        if (!isSelectionMode && song.durationMs > 0) {
            Text(
                text = formatDuration(song.durationMs),
                fontSize = 12.sp,
                color = OneUITextSecondary,
                modifier = Modifier.padding(horizontal = 4.dp)
            )
        }

        // Dropdown Menu for Actions
        if (!isSelectionMode) {
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
                    if (onEditMetadata != null) {
                        DropdownMenuItem(
                            text = { Text("Edit Tag & Title", color = OneUITextPrimary) },
                            onClick = {
                                onEditMetadata()
                                showMenu = false
                            },
                            leadingIcon = {
                                Icon(Icons.Default.Edit, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                            }
                        )
                    }

                    if (onToggleFavorite != null) {
                        DropdownMenuItem(
                            text = { Text(if (song.isFavorite) "Remove from Favorites" else "Add to Favorites", color = OneUITextPrimary) },
                            onClick = {
                                onToggleFavorite()
                                showMenu = false
                            },
                            leadingIcon = {
                                Icon(
                                    if (song.isFavorite) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                                    contentDescription = null,
                                    tint = Color(0xFFFF2A6D)
                                )
                            }
                        )
                    }

                    if (onAddToPlaylistClick != null) {
                        DropdownMenuItem(
                            text = { Text("Add to Playlist", color = OneUITextPrimary) },
                            onClick = {
                                onAddToPlaylistClick()
                                showMenu = false
                            },
                            leadingIcon = {
                                Icon(Icons.Default.PlaylistAdd, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                            }
                        )
                    }

                    if (onOpenAudioEditor != null) {
                        DropdownMenuItem(
                            text = { Text("Trim Audio / Cutter", color = OneUITextPrimary) },
                            onClick = {
                                onOpenAudioEditor()
                                showMenu = false
                            },
                            leadingIcon = {
                                Icon(Icons.Default.ContentCut, contentDescription = null, tint = OneUITextPrimary)
                            }
                        )
                    }

                    if (onEditMetadata != null) {
                        DropdownMenuItem(
                            text = { Text("Edit Tag & Title", color = OneUITextPrimary) },
                            onClick = {
                                onEditMetadata()
                                showMenu = false
                            },
                            leadingIcon = {
                                Icon(Icons.Default.Edit, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                            }
                        )
                    }

                    // Virtual Deletion: Remove from App (Sets isHidden = true)
                    if (onToggleSoftHide != null) {
                        DropdownMenuItem(
                            text = {
                                Text(
                                    if (song.isHiddenFromLibrary) "Restore to Library" else "Remove from App (Hide)",
                                    color = OneUITextPrimary
                                )
                            },
                            onClick = {
                                onToggleSoftHide()
                                showMenu = false
                            },
                            leadingIcon = {
                                Icon(
                                    if (song.isHiddenFromLibrary) Icons.Default.Visibility else Icons.Default.VisibilityOff,
                                    contentDescription = null,
                                    tint = OneUITextSecondary
                                )
                            }
                        )
                    }

                    // Physical Deletion: Delete from Storage (MediaStore.createDeleteRequest)
                    if (onDeleteFromStorage != null) {
                        DropdownMenuItem(
                            text = { Text("Delete from Storage", color = Color(0xFFFF453A)) },
                            onClick = {
                                onDeleteFromStorage()
                                showMenu = false
                            },
                            leadingIcon = {
                                Icon(Icons.Default.DeleteForever, contentDescription = null, tint = Color(0xFFFF453A))
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
