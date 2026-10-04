package com.example.ui.screens

import android.widget.Toast
import androidx.compose.foundation.background
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
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.PlaylistAdd
import androidx.compose.material.icons.filled.QueueMusic
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.MusicRepository
import com.example.data.PlaylistEntity
import com.example.data.SongEntity
import com.example.playback.PlaybackManager
import com.example.ui.components.TrackListItem
import com.example.ui.theme.OneUICardElevated
import com.example.ui.theme.OneUIDarkBackground
import com.example.ui.theme.OneUISurfaceDark
import com.example.ui.theme.OneUITextPrimary
import com.example.ui.theme.OneUITextSecondary
import com.example.utils.CachedSongArtwork
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlaylistDetailScreen(
    playlistId: Long,
    repository: MusicRepository,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    var playlist by remember { mutableStateOf<PlaylistEntity?>(null) }
    var songs by remember { mutableStateOf<List<SongEntity>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }
    var showAddTracksSheet by remember { mutableStateOf(false) }

    val librarySongs by repository.librarySongs.collectAsStateWithLifecycle(emptyList())
    val currentSong by PlaybackManager.currentSong.collectAsStateWithLifecycle()

    fun refreshPlaylist() {
        coroutineScope.launch {
            playlist = repository.getPlaylistById(playlistId)
            repository.getSongsForPlaylist(playlistId).collect { list ->
                songs = list
                isLoading = false
            }
        }
    }

    LaunchedEffect(playlistId) {
        refreshPlaylist()
    }

    val totalDurationMs = remember(songs) { songs.sumOf { it.durationMs } }
    val totalMinutes = totalDurationMs / 60000

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(OneUIDarkBackground)
            .testTag("playlist_detail_screen")
    ) {
        // Top App Bar
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(
                onClick = onBack,
                modifier = Modifier.testTag("playlist_back_button")
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "Back",
                    tint = OneUITextPrimary
                )
            }
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = playlist?.name ?: "Playlist",
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
                color = OneUITextPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f)
            )

            // Delete Playlist (if user-created)
            if (playlist?.isAutoGenerated == false) {
                IconButton(
                    onClick = {
                        coroutineScope.launch {
                            repository.deletePlaylist(playlistId)
                            Toast.makeText(context, "Playlist deleted", Toast.LENGTH_SHORT).show()
                            onBack()
                        }
                    }
                ) {
                    Icon(
                        imageVector = Icons.Default.DeleteOutline,
                        contentDescription = "Delete Playlist",
                        tint = Color(0xFFFF5252)
                    )
                }
            }
        }

        if (isLoading) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(bottom = 120.dp)
            ) {
                // Header Card
                item {
                    Card(
                        shape = RoundedCornerShape(20.dp),
                        colors = CardDefaults.cardColors(containerColor = OneUICardElevated),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp)
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(18.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            // Cover Artwork Box / First song artwork
                            Box(
                                modifier = Modifier
                                    .size(110.dp)
                                    .clip(RoundedCornerShape(16.dp))
                                    .background(OneUISurfaceDark),
                                contentAlignment = Alignment.Center
                            ) {
                                val firstSong = songs.firstOrNull()
                                if (firstSong != null) {
                                    CachedSongArtwork(
                                        song = firstSong,
                                        modifier = Modifier.fillMaxSize(),
                                        shape = RoundedCornerShape(16.dp),
                                        placeholderIconSize = 44.dp
                                    )
                                } else {
                                    Icon(
                                        imageVector = Icons.Default.QueueMusic,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(48.dp)
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.height(14.dp))

                            Text(
                                text = playlist?.name ?: "Playlist",
                                fontSize = 22.sp,
                                fontWeight = FontWeight.ExtraBold,
                                color = OneUITextPrimary
                            )

                            if (!playlist?.description.isNullOrBlank()) {
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = playlist!!.description,
                                    fontSize = 13.sp,
                                    color = OneUITextSecondary
                                )
                            }

                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "${songs.size} tracks • ${totalMinutes} min",
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.primary,
                                fontWeight = FontWeight.SemiBold
                            )

                            Spacer(modifier = Modifier.height(16.dp))

                            // Play All & Shuffle & Add Tracks Buttons
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                Button(
                                    onClick = {
                                        if (songs.isNotEmpty()) {
                                            PlaybackManager.playSong(songs.first(), songs)
                                        } else {
                                            Toast.makeText(context, "Playlist is empty", Toast.LENGTH_SHORT).show()
                                        }
                                    },
                                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                                    shape = RoundedCornerShape(12.dp),
                                    modifier = Modifier.weight(1f).testTag("play_all_playlist_btn")
                                ) {
                                    Icon(Icons.Default.PlayArrow, contentDescription = null, tint = Color.Black, modifier = Modifier.size(18.dp))
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("Play", color = Color.Black, fontWeight = FontWeight.Bold)
                                }

                                Button(
                                    onClick = {
                                        if (songs.isNotEmpty()) {
                                            val shuffled = songs.shuffled()
                                            PlaybackManager.playSong(shuffled.first(), shuffled)
                                        }
                                    },
                                    colors = ButtonDefaults.buttonColors(containerColor = OneUISurfaceDark),
                                    shape = RoundedCornerShape(12.dp),
                                    modifier = Modifier.weight(1f).testTag("shuffle_playlist_btn")
                                ) {
                                    Icon(Icons.Default.Shuffle, contentDescription = null, tint = OneUITextPrimary, modifier = Modifier.size(18.dp))
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("Shuffle", color = OneUITextPrimary, fontWeight = FontWeight.SemiBold)
                                }

                                IconButton(
                                    onClick = { showAddTracksSheet = true },
                                    modifier = Modifier
                                        .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.15f), CircleShape)
                                        .size(42.dp)
                                        .testTag("add_tracks_to_playlist_btn")
                                ) {
                                    Icon(Icons.Default.Add, contentDescription = "Add Tracks", tint = MaterialTheme.colorScheme.primary)
                                }
                            }
                        }
                    }
                }

                // Song list
                if (songs.isEmpty()) {
                    item {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(40.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text(
                                text = "No tracks in this playlist yet",
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Bold,
                                color = OneUITextPrimary
                            )
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(
                                text = "Tap the + button above to add tracks from your library.",
                                fontSize = 13.sp,
                                color = OneUITextSecondary
                            )
                            Spacer(modifier = Modifier.height(16.dp))
                            Button(
                                onClick = { showAddTracksSheet = true },
                                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                                shape = RoundedCornerShape(10.dp)
                            ) {
                                Text("Add Tracks Now", color = Color.Black, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                } else {
                    itemsIndexed(songs, key = { _, s -> s.id }) { index, song ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(modifier = Modifier.weight(1f)) {
                                TrackListItem(
                                    song = song,
                                    index = index + 1,
                                    isCurrentPlaying = currentSong?.id == song.id,
                                    onClick = { PlaybackManager.playSong(song, songs) },
                                    onToggleFavorite = {
                                        coroutineScope.launch {
                                            repository.toggleFavorite(song.id)
                                        }
                                    }
                                )
                            }

                            // Reorder Up / Down & Remove
                            Row(
                                modifier = Modifier.padding(end = 8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                if (index > 0) {
                                    IconButton(
                                        onClick = {
                                            val reordered = songs.toMutableList()
                                            java.util.Collections.swap(reordered, index, index - 1)
                                            songs = reordered
                                            coroutineScope.launch {
                                                repository.reorderPlaylist(playlistId, reordered)
                                            }
                                        },
                                        modifier = Modifier.size(32.dp)
                                    ) {
                                        Icon(Icons.Default.ArrowUpward, contentDescription = "Move Up", tint = OneUITextSecondary, modifier = Modifier.size(16.dp))
                                    }
                                }

                                if (index < songs.size - 1) {
                                    IconButton(
                                        onClick = {
                                            val reordered = songs.toMutableList()
                                            java.util.Collections.swap(reordered, index, index + 1)
                                            songs = reordered
                                            coroutineScope.launch {
                                                repository.reorderPlaylist(playlistId, reordered)
                                            }
                                        },
                                        modifier = Modifier.size(32.dp)
                                    ) {
                                        Icon(Icons.Default.ArrowDownward, contentDescription = "Move Down", tint = OneUITextSecondary, modifier = Modifier.size(16.dp))
                                    }
                                }

                                IconButton(
                                    onClick = {
                                        coroutineScope.launch {
                                            repository.removeSongFromPlaylist(playlistId, song.id)
                                            songs = songs.filter { it.id != song.id }
                                            Toast.makeText(context, "Removed from playlist", Toast.LENGTH_SHORT).show()
                                        }
                                    },
                                    modifier = Modifier.size(32.dp)
                                ) {
                                    Icon(Icons.Default.Delete, contentDescription = "Remove", tint = OneUITextSecondary, modifier = Modifier.size(16.dp))
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    // Modal Bottom Sheet to Add Tracks from Library
    if (showAddTracksSheet) {
        val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
        var addSearchQuery by remember { mutableStateOf("") }
        val existingIds = remember(songs) { songs.map { it.id }.toSet() }
        val candidateSongs = remember(librarySongs, existingIds, addSearchQuery) {
            librarySongs.filter { s ->
                !existingIds.contains(s.id) &&
                    (addSearchQuery.isBlank() || s.title.contains(addSearchQuery, ignoreCase = true) || s.artist.contains(addSearchQuery, ignoreCase = true))
            }
        }

        ModalBottomSheet(
            onDismissRequest = { showAddTracksSheet = false },
            sheetState = sheetState,
            containerColor = OneUICardElevated
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp)
            ) {
                Text(
                    text = "Add Songs to ${playlist?.name ?: "Playlist"}",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    color = OneUITextPrimary
                )

                Spacer(modifier = Modifier.height(10.dp))

                OutlinedTextField(
                    value = addSearchQuery,
                    onValueChange = { addSearchQuery = it },
                    placeholder = { Text("Search songs in library...", color = OneUITextSecondary) },
                    singleLine = true,
                    shape = RoundedCornerShape(12.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedContainerColor = OneUISurfaceDark,
                        unfocusedContainerColor = OneUISurfaceDark,
                        focusedBorderColor = MaterialTheme.colorScheme.primary,
                        unfocusedBorderColor = Color.Transparent,
                        focusedTextColor = OneUITextPrimary,
                        unfocusedTextColor = OneUITextPrimary
                    ),
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(12.dp))

                LazyColumn(modifier = Modifier.height(380.dp)) {
                    if (candidateSongs.isEmpty()) {
                        item {
                            Text(
                                text = "All eligible library songs are already in this playlist!",
                                color = OneUITextSecondary,
                                modifier = Modifier.padding(24.dp)
                            )
                        }
                    } else {
                        itemsIndexed(candidateSongs, key = { _, s -> s.id }) { _, candidate ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Box(modifier = Modifier.weight(1f)) {
                                    TrackListItem(
                                        song = candidate,
                                        onClick = {
                                            coroutineScope.launch {
                                                repository.addSongToPlaylist(playlistId, candidate.id)
                                                songs = songs + candidate
                                                Toast.makeText(context, "Added '${candidate.title}'", Toast.LENGTH_SHORT).show()
                                            }
                                        }
                                    )
                                }
                                IconButton(
                                    onClick = {
                                        coroutineScope.launch {
                                            repository.addSongToPlaylist(playlistId, candidate.id)
                                            songs = songs + candidate
                                            Toast.makeText(context, "Added '${candidate.title}'", Toast.LENGTH_SHORT).show()
                                        }
                                    }
                                ) {
                                    Icon(Icons.Default.Add, contentDescription = "Add", tint = MaterialTheme.colorScheme.primary)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
