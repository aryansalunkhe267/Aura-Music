package com.example.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Reorder
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import coil.compose.AsyncImage
import com.example.data.SongEntity
import com.example.ui.theme.NeonMint
import com.example.ui.theme.OneUICardElevated
import com.example.ui.theme.OneUISurfaceDark
import com.example.ui.theme.OneUITextPrimary
import com.example.ui.theme.OneUITextSecondary
import com.example.utils.cleanMetadataString
import kotlin.math.roundToInt

@Composable
fun QueueSheet(
    queue: List<SongEntity>,
    currentIndex: Int,
    smartMoodQueueEnabled: Boolean,
    onToggleSmartMoodQueue: () -> Unit,
    onSongClick: (Int) -> Unit,
    onMoveItem: (from: Int, to: Int) -> Unit,
    onRemoveItem: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    var draggedIndex by remember { mutableIntStateOf(-1) }
    var dragOffsetY by remember { mutableFloatStateOf(0f) }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(OneUISurfaceDark)
            .padding(horizontal = 16.dp)
            .testTag("queue_sheet")
    ) {
        // Header with Smart Mood / Language-Locked Queue toggle
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 12.dp),
            shape = RoundedCornerShape(18.dp),
            colors = CardDefaults.cardColors(containerColor = OneUICardElevated)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(
                        imageVector = Icons.Default.AutoAwesome,
                        contentDescription = null,
                        tint = if (smartMoodQueueEnabled) MaterialTheme.colorScheme.primary else OneUITextSecondary,
                        modifier = Modifier.size(24.dp)
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Column {
                        Text(
                            text = "Language-Locked Auto-Queue",
                            fontWeight = FontWeight.Bold,
                            color = OneUITextPrimary,
                            fontSize = 15.sp
                        )
                        Text(
                            text = if (smartMoodQueueEnabled) "Auto-enqueuing matching genre tracks" else "Manual playback queue only",
                            color = OneUITextSecondary,
                            fontSize = 12.sp
                        )
                    }
                }

                Switch(
                    checked = smartMoodQueueEnabled,
                    onCheckedChange = { onToggleSmartMoodQueue() },
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = MaterialTheme.colorScheme.primary,
                        checkedTrackColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.4f)
                    ),
                    modifier = Modifier.testTag("smart_mood_queue_switch")
                )
            }
        }

        Text(
            text = "Playback Queue (${queue.size} songs)",
            fontWeight = FontWeight.Bold,
            color = OneUITextPrimary,
            fontSize = 16.sp,
            modifier = Modifier.padding(horizontal = 4.dp, vertical = 6.dp)
        )

        LazyColumn(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
            contentPadding = PaddingValues(bottom = 96.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            itemsIndexed(queue, key = { _, s -> s.id }) { index, song ->
                val isCurrentlyPlaying = index == currentIndex
                val isBeingDragged = draggedIndex == index

                val itemOffsetY = if (isBeingDragged) dragOffsetY else 0f
                val animatedElevation by animateFloatAsState(
                    targetValue = if (isBeingDragged) 12f else 0f,
                    label = "dragElevation"
                )

                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .offset { IntOffset(0, itemOffsetY.roundToInt()) }
                        .zIndex(if (isBeingDragged) 2f else 1f)
                        .clickable { onSongClick(index) }
                        .testTag("queue_item_$index"),
                    shape = RoundedCornerShape(14.dp),
                    elevation = CardDefaults.cardElevation(defaultElevation = animatedElevation.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = when {
                            isBeingDragged -> MaterialTheme.colorScheme.primary.copy(alpha = 0.25f)
                            isCurrentlyPlaying -> MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)
                            else -> OneUICardElevated
                        }
                    )
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Smooth Drag-and-Drop Gesture Handle
                        Box(
                            modifier = Modifier
                                .size(36.dp)
                                .pointerInput(index, queue.size) {
                                    detectDragGestures(
                                        onDragStart = {
                                            draggedIndex = index
                                            dragOffsetY = 0f
                                        },
                                        onDragEnd = {
                                            // Compute destination index based on drag offset (approx 64dp per item)
                                            val itemHeightPx = 64.dp.toPx()
                                            val movedSlots = (dragOffsetY / itemHeightPx).roundToInt()
                                            val targetIndex = (index + movedSlots).coerceIn(0, queue.size - 1)
                                            if (targetIndex != index) {
                                                onMoveItem(index, targetIndex)
                                            }
                                            draggedIndex = -1
                                            dragOffsetY = 0f
                                        },
                                        onDragCancel = {
                                            draggedIndex = -1
                                            dragOffsetY = 0f
                                        },
                                        onDrag = { change, dragAmount ->
                                            change.consume()
                                            dragOffsetY += dragAmount.y
                                        }
                                    )
                                },
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Reorder,
                                contentDescription = "Drag to reorder",
                                tint = if (isBeingDragged) MaterialTheme.colorScheme.primary else Color.White.copy(alpha = 0.5f),
                                modifier = Modifier.size(20.dp)
                            )
                        }

                        // Thumbnail or Icon
                        Box(
                            modifier = Modifier
                                .size(44.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .background(Color.DarkGray),
                            contentAlignment = Alignment.Center
                        ) {
                            val artUri = song.albumArtUri ?: song.coverArtUrl
                            if (!artUri.isNullOrBlank()) {
                                AsyncImage(
                                    model = artUri,
                                    contentDescription = song.title,
                                    contentScale = ContentScale.Crop,
                                    modifier = Modifier.fillMaxSize()
                                )
                            } else {
                                Icon(
                                    imageVector = Icons.Default.MusicNote,
                                    contentDescription = null,
                                    tint = if (isCurrentlyPlaying) MaterialTheme.colorScheme.primary else Color.White.copy(alpha = 0.6f)
                                )
                            }
                        }

                        Spacer(modifier = Modifier.width(10.dp))

                        Column(modifier = Modifier.weight(1f)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                if (isCurrentlyPlaying) {
                                    Icon(
                                        imageVector = Icons.Default.GraphicEq,
                                        contentDescription = "Playing",
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier
                                            .size(16.dp)
                                            .padding(end = 4.dp)
                                    )
                                }
                                Text(
                                    text = cleanMetadataString(song.title),
                                    fontWeight = if (isCurrentlyPlaying) FontWeight.Bold else FontWeight.SemiBold,
                                    color = if (isCurrentlyPlaying) MaterialTheme.colorScheme.primary else OneUITextPrimary,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    fontSize = 14.sp
                                )
                            }
                            Text(
                                text = cleanMetadataString(song.artist),
                                color = OneUITextSecondary,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                fontSize = 12.sp
                            )
                        }

                        // Directional Buttons & Explicit Remove
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            // Move Up
                            IconButton(
                                onClick = {
                                    if (index > 0) onMoveItem(index, index - 1)
                                },
                                enabled = index > 0,
                                modifier = Modifier.size(32.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.KeyboardArrowUp,
                                    contentDescription = "Move Up",
                                    tint = if (index > 0) Color.White.copy(alpha = 0.8f) else Color.White.copy(alpha = 0.2f),
                                    modifier = Modifier.size(20.dp)
                                )
                            }

                            // Move Down
                            IconButton(
                                onClick = {
                                    if (index < queue.size - 1) onMoveItem(index, index + 1)
                                },
                                enabled = index < queue.size - 1,
                                modifier = Modifier.size(32.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.KeyboardArrowDown,
                                    contentDescription = "Move Down",
                                    tint = if (index < queue.size - 1) Color.White.copy(alpha = 0.8f) else Color.White.copy(alpha = 0.2f),
                                    modifier = Modifier.size(20.dp)
                                )
                            }

                            // Remove from Queue
                            IconButton(
                                onClick = { onRemoveItem(index) },
                                modifier = Modifier
                                    .size(32.dp)
                                    .testTag("remove_from_queue_$index")
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Close,
                                    contentDescription = "Remove from Queue",
                                    tint = Color.White.copy(alpha = 0.7f),
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
