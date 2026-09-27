package com.example.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.Headphones
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.WifiOff
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
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import coil.compose.AsyncImage
import com.example.data.OnlineMusicCatalog
import com.example.data.SongEntity
import com.example.network.OnlineMusicRepository
import com.example.ui.theme.NeonMint
import com.example.ui.theme.OneUICardElevated
import com.example.ui.theme.OneUIDarkBackground
import com.example.ui.theme.OneUISurfaceDark
import com.example.ui.theme.OneUITextPrimary
import com.example.ui.theme.OneUITextSecondary
import com.example.ui.theme.SpotifyGreen
import com.example.utils.cleanMetadataString
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Spotify-Style Hybrid Online & Offline Music Search Dialog.
 * Interfaces with JioSaavn API for live streaming and offline download,
 * with graceful fallbacks to curated high-quality tracks and genre explorers.
 */
@Composable
fun OnlineSearchDialog(
    onDismiss: () -> Unit,
    onPlayTrack: (SongEntity) -> Unit,
    onDownloadTrack: (SongEntity) -> Unit,
    downloadProgressMap: Map<Long, Int>
) {
    val context = LocalContext.current
    val repository = remember { OnlineMusicRepository(context) }
    val coroutineScope = rememberCoroutineScope()

    var searchQuery by remember { mutableStateOf("") }
    var searchResults by remember { mutableStateOf<List<SongEntity>>(emptyList()) }
    var isLoading by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var searchJob by remember { mutableStateOf<Job?>(null) }

    val genreChips = listOf(
        "Top Hits",
        "Punjabi Beats",
        "Bollywood Hindi",
        "Marathi Hits",
        "Devotional Bhakti",
        "Lo-Fi Chill"
    )
    var selectedGenreIndex by remember { mutableIntStateOf(0) }

    fun executeSearch(query: String) {
        searchJob?.cancel()
        searchJob = coroutineScope.launch {
            isLoading = true
            errorMessage = null
            try {
                repository.searchTracks(query).collect { result ->
                    result.onSuccess { songs ->
                        searchResults = songs
                        isLoading = false
                    }.onFailure { error ->
                        errorMessage = error.localizedMessage ?: "Failed to load music online"
                        // Fallback to local catalog
                        val fallback = OnlineMusicCatalog.searchTracks(query)
                        if (fallback.isNotEmpty()) {
                            searchResults = fallback
                        }
                        isLoading = false
                    }
                }
            } catch (e: Exception) {
                errorMessage = e.localizedMessage
                searchResults = OnlineMusicCatalog.searchTracks(query)
                isLoading = false
            }
        }
    }

    // Initial load: trending catalog
    LaunchedEffect(Unit) {
        executeSearch("")
    }

    // Debounced query execution when user types
    fun onQueryChanged(newQuery: String) {
        searchQuery = newQuery
        searchJob?.cancel()
        searchJob = coroutineScope.launch {
            delay(400L) // 400ms debounce
            executeSearch(newQuery)
        }
    }

    fun selectGenre(index: Int) {
        selectedGenreIndex = index
        val term = when (index) {
            1 -> "Punjabi"
            2 -> "Hindi"
            3 -> "Marathi"
            4 -> "Devotional"
            5 -> "Lo-Fi"
            else -> ""
        }
        searchQuery = term
        executeSearch(term)
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth(0.95f)
                .fillMaxHeight(0.90f),
            shape = RoundedCornerShape(24.dp),
            color = OneUIDarkBackground,
            tonalElevation = 12.dp
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(18.dp)
            ) {
                // Header Row
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(38.dp)
                                .clip(CircleShape)
                                .background(SpotifyGreen.copy(alpha = 0.18f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Headphones,
                                contentDescription = null,
                                tint = SpotifyGreen,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        Column {
                            Text(
                                text = "Spotify Online Stream",
                                fontSize = 19.sp,
                                fontWeight = FontWeight.Bold,
                                color = OneUITextPrimary
                            )
                            Text(
                                text = "JioSaavn catalog • 320kbps streams • Offline sync",
                                fontSize = 11.sp,
                                color = OneUITextSecondary
                            )
                        }
                    }
                    IconButton(
                        onClick = onDismiss,
                        modifier = Modifier.testTag("close_online_search_button")
                    ) {
                        Icon(Icons.Default.Close, contentDescription = "Close", tint = OneUITextSecondary)
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                // Search TextField
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { onQueryChanged(it) },
                    placeholder = { Text("Search songs, artists, Punjabi, Bollywood...", fontSize = 13.sp) },
                    leadingIcon = {
                        Icon(Icons.Default.Search, contentDescription = null, tint = SpotifyGreen)
                    },
                    trailingIcon = {
                        if (isLoading) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(18.dp),
                                color = SpotifyGreen,
                                strokeWidth = 2.dp
                            )
                        } else if (searchQuery.isNotEmpty()) {
                            IconButton(onClick = {
                                searchQuery = ""
                                executeSearch("")
                            }) {
                                Icon(Icons.Default.Close, contentDescription = "Clear", tint = OneUITextSecondary)
                            }
                        }
                    },
                    singleLine = true,
                    shape = RoundedCornerShape(16.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedContainerColor = OneUISurfaceDark,
                        unfocusedContainerColor = OneUISurfaceDark,
                        focusedBorderColor = SpotifyGreen,
                        unfocusedBorderColor = Color.Transparent,
                        focusedTextColor = OneUITextPrimary,
                        unfocusedTextColor = OneUITextPrimary
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("online_search_input")
                )

                Spacer(modifier = Modifier.height(10.dp))

                // Spotify-style Genre filter chips
                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    contentPadding = PaddingValues(vertical = 4.dp)
                ) {
                    itemsIndexed(genreChips) { index, title ->
                        FilterChip(
                            selected = selectedGenreIndex == index,
                            onClick = { selectGenre(index) },
                            label = { Text(title, fontSize = 12.sp) },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = SpotifyGreen,
                                selectedLabelColor = Color.Black,
                                containerColor = OneUISurfaceDark,
                                labelColor = OneUITextSecondary
                            ),
                            shape = RoundedCornerShape(16.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                // Offline Notice Banner if offline
                if (!repository.isOnline()) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .background(Color(0xFF2A2218))
                            .padding(horizontal = 12.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Default.WifiOff, contentDescription = null, tint = Color(0xFFFFB300), modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Offline Mode: Showing curated high-quality library",
                            fontSize = 11.sp,
                            color = Color(0xFFFFE082)
                        )
                    }
                    Spacer(modifier = Modifier.height(10.dp))
                }

                // Results List / Empty / Loading
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                ) {
                    if (isLoading && searchResults.isEmpty()) {
                        Column(
                            modifier = Modifier.fillMaxSize(),
                            verticalArrangement = Arrangement.Center,
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            CircularProgressIndicator(color = SpotifyGreen)
                            Spacer(modifier = Modifier.height(12.dp))
                            Text("Searching JioSaavn catalog...", color = OneUITextSecondary, fontSize = 13.sp)
                        }
                    } else if (searchResults.isEmpty()) {
                        Column(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(24.dp),
                            verticalArrangement = Arrangement.Center,
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Icon(Icons.Default.MusicNote, contentDescription = null, tint = Color.Gray, modifier = Modifier.size(48.dp))
                            Spacer(modifier = Modifier.height(12.dp))
                            Text(
                                text = "No tracks found for \"$searchQuery\"",
                                color = OneUITextPrimary,
                                fontWeight = FontWeight.Bold,
                                fontSize = 16.sp
                            )
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(
                                text = "Try searching by artist name, movie, or explore genre chips above.",
                                color = OneUITextSecondary,
                                fontSize = 12.sp,
                                textAlign = TextAlign.Center
                            )
                            Spacer(modifier = Modifier.height(16.dp))
                            TextButton(onClick = { selectGenre(0) }) {
                                Icon(Icons.Default.Refresh, contentDescription = null, tint = SpotifyGreen)
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Reset to Trending", color = SpotifyGreen)
                            }
                        }
                    } else {
                        LazyColumn(
                            modifier = Modifier.fillMaxSize(),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            items(searchResults, key = { it.id }) { song ->
                                val progress = downloadProgressMap[song.id] ?: if (song.isDownloaded) 100 else 0
                                val isDownloading = progress in 1..99

                                Card(
                                    shape = RoundedCornerShape(16.dp),
                                    colors = CardDefaults.cardColors(containerColor = OneUICardElevated),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable { onPlayTrack(song) }
                                        .testTag("online_song_item_${song.id}")
                                ) {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(12.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        // Cover Art with 320kbps badge
                                        Box(
                                            modifier = Modifier
                                                .size(54.dp)
                                                .clip(RoundedCornerShape(12.dp))
                                        ) {
                                            if (!song.albumArtUri.isNullOrBlank()) {
                                                AsyncImage(
                                                    model = song.albumArtUri,
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
                                                    Icon(Icons.Default.MusicNote, contentDescription = null, tint = SpotifyGreen)
                                                }
                                            }
                                        }

                                        Spacer(modifier = Modifier.width(12.dp))

                                        // Track details & bit-rate badge
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(
                                                text = cleanMetadataString(song.title),
                                                fontSize = 15.sp,
                                                fontWeight = FontWeight.Bold,
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
                                            Spacer(modifier = Modifier.height(3.dp))
                                            Row(
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                                            ) {
                                                Box(
                                                    modifier = Modifier
                                                        .clip(RoundedCornerShape(4.dp))
                                                        .background(SpotifyGreen.copy(alpha = 0.2f))
                                                        .padding(horizontal = 5.dp, vertical = 2.dp)
                                                ) {
                                                    Text(
                                                        text = "320 kbps",
                                                        fontSize = 9.sp,
                                                        color = SpotifyGreen,
                                                        fontWeight = FontWeight.Bold
                                                    )
                                                }
                                                Text(
                                                    text = song.languageScript.replace("_", " "),
                                                    fontSize = 10.sp,
                                                    color = NeonMint,
                                                    fontWeight = FontWeight.Medium
                                                )
                                            }
                                        }

                                        // Play Stream Action
                                        IconButton(
                                            onClick = { onPlayTrack(song) },
                                            modifier = Modifier.size(42.dp)
                                        ) {
                                            Icon(
                                                Icons.Default.PlayArrow,
                                                contentDescription = "Stream Now",
                                                tint = SpotifyGreen
                                            )
                                        }

                                        // Download Offline Action
                                        if (song.isDownloaded || progress == 100) {
                                            Icon(
                                                imageVector = Icons.Default.CheckCircle,
                                                contentDescription = "Downloaded Offline",
                                                tint = SpotifyGreen,
                                                modifier = Modifier
                                                    .size(36.dp)
                                                    .padding(6.dp)
                                            )
                                        } else if (isDownloading) {
                                            Box(
                                                modifier = Modifier.size(40.dp),
                                                contentAlignment = Alignment.Center
                                            ) {
                                                CircularProgressIndicator(
                                                    progress = { progress / 100f },
                                                    modifier = Modifier.size(24.dp),
                                                    color = SpotifyGreen,
                                                    strokeWidth = 2.5.dp
                                                )
                                            }
                                        } else {
                                            IconButton(
                                                onClick = { onDownloadTrack(song) },
                                                modifier = Modifier.size(42.dp)
                                            ) {
                                                Icon(
                                                    imageVector = Icons.Default.CloudDownload,
                                                    contentDescription = "Download Offline",
                                                    tint = OneUITextPrimary
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
