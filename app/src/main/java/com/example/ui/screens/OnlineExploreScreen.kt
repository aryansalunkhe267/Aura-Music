package com.example.ui.screens

import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
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
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
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
import coil.compose.AsyncImage
import com.example.api.JioSaavnApiClient
import com.example.data.OnlineMusicCatalog
import androidx.compose.material.icons.filled.Dns
import com.example.api.StreamingApiClient
import com.example.data.PersonalizationRepository
import com.example.data.SongEntity
import com.example.ui.theme.OneUICardElevated
import com.example.ui.theme.OneUIDarkBackground
import com.example.ui.theme.OneUISurfaceDark
import com.example.ui.theme.OneUITextPrimary
import com.example.ui.theme.OneUITextSecondary
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Online Explore Screen designed to match Spotify & open-source streaming standards.
 * Features:
 * - Zero-Config Spotify Import Quick-Sync action.
 * - Open-Source Piped/Invidious streaming integration with custom instance routing.
 * - Dynamic Personalized Rails ("Made For You", "Based on Recent Listens", "Rediscover Offline Favorites").
 * - Curated Regional Rails ("Punjabi Top 50", "Hindi Trending", "Marathi Top Charts").
 * - Direct lossless downloading into Android's public Music storage.
 */
@Composable
fun OnlineExploreScreen(
    personalizationRepository: PersonalizationRepository,
    currentPlayingSongId: Long?,
    onPlayTrack: (SongEntity, List<SongEntity>) -> Unit,
    onDownloadTrack: (SongEntity) -> Unit,
    downloadProgressMap: Map<Long, Int>,
    onOpenSpotifySync: () -> Unit,
    onOpenServerSettings: () -> Unit = {},
    refreshTrigger: Long = 0L,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    var searchQuery by remember { mutableStateOf("") }
    var isSearchingOnline by remember { mutableStateOf(false) }
    var searchResults by remember { mutableStateOf<List<SongEntity>>(emptyList()) }
    var searchJob by remember { mutableStateOf<Job?>(null) }

    // Dynamic Personalized Rails state
    var madeForYouTracks by remember { mutableStateOf<List<SongEntity>>(emptyList()) }
    var recentListensTracks by remember { mutableStateOf<List<SongEntity>>(emptyList()) }
    var rediscoverOfflineTracks by remember { mutableStateOf<List<SongEntity>>(emptyList()) }

    // Curated catalog rails
    val punjabiTop50 = remember { OnlineMusicCatalog.getTrendingNow() }
    val hindiTrending = remember { OnlineMusicCatalog.getTopCharts() }
    val marathiTopCharts = remember { OnlineMusicCatalog.getNewReleases() }
    val featuredPlaylists = remember { OnlineMusicCatalog.getFeaturedPlaylists() }

    // Load personalized rails from taste vector
    LaunchedEffect(refreshTrigger) {
        val rails = personalizationRepository.getPersonalizedRails()
        madeForYouTracks = rails.madeForYou
        recentListensTracks = rails.basedOnRecentListens
        rediscoverOfflineTracks = rails.rediscoverOffline
    }

    // Dynamic multi-source search with debounce
    LaunchedEffect(searchQuery) {
        val q = searchQuery.trim()
        if (q.isBlank()) {
            searchResults = emptyList()
            isSearchingOnline = false
            return@LaunchedEffect
        }

        searchJob?.cancel()
        searchJob = coroutineScope.launch {
            delay(350L) // Debounce
            isSearchingOnline = true
            var foundItems = emptyList<SongEntity>()
            try {
                // 1. Try configured open-source streaming instance
                val pipedResp = StreamingApiClient.api.searchSongs(q, filter = "music_songs")
                val pItems = pipedResp.body() ?: emptyList()
                if (pItems.isNotEmpty()) {
                    foundItems = pItems.map { StreamingApiClient.mapSearchResultToSong(it) }
                }
            } catch (_: Exception) {}

            if (foundItems.isEmpty()) {
                try {
                    // 2. Fallback to regional API
                    val response = JioSaavnApiClient.api.searchSongs(q, limit = 25, languages = "punjabi,hindi,marathi")
                    val items = response.body()?.data?.results ?: emptyList()
                    if (items.isNotEmpty()) {
                        foundItems = items.map { JioSaavnApiClient.mapToSongEntity(it) }
                    }
                } catch (_: Exception) {}
            }

            searchResults = if (foundItems.isNotEmpty()) foundItems else OnlineMusicCatalog.searchTracks(q)
            isSearchingOnline = false
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(OneUIDarkBackground)
    ) {
        // Top Header
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column {
                Text(
                    text = "Explore & Stream",
                    fontSize = 26.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = OneUITextPrimary
                )
                Text(
                    text = "Open-Source Streams + Spotify Sync",
                    fontSize = 12.sp,
                    color = OneUITextSecondary
                )
            }

            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(
                    onClick = onOpenServerSettings,
                    modifier = Modifier
                        .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.15f), shape = CircleShape)
                        .size(40.dp)
                        .testTag("server_settings_btn")
                ) {
                    Icon(
                        imageVector = Icons.Default.Dns,
                        contentDescription = "Server Settings",
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(20.dp)
                    )
                }

                Spacer(modifier = Modifier.width(8.dp))

                IconButton(
                    onClick = onOpenSpotifySync,
                    modifier = Modifier
                        .background(Color(0xFF1DB954).copy(alpha = 0.18f), shape = CircleShape)
                        .size(40.dp)
                        .testTag("spotify_sync_header_btn")
                ) {
                    Icon(
                        imageVector = Icons.Default.Sync,
                        contentDescription = "Sync Spotify",
                        tint = Color(0xFF1DB954),
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
        }

        // Search Input Field
        OutlinedTextField(
            value = searchQuery,
            onValueChange = { searchQuery = it },
            placeholder = { Text("Search songs, artists, Punjabi, Bollywood, Marathi...", fontSize = 13.sp) },
            leadingIcon = {
                Icon(Icons.Default.Search, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
            },
            trailingIcon = {
                if (searchQuery.isNotEmpty()) {
                    IconButton(onClick = { searchQuery = "" }) {
                        Icon(Icons.Default.Clear, contentDescription = "Clear", tint = OneUITextSecondary)
                    }
                }
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
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 4.dp)
                .testTag("online_search_field")
        )

        Spacer(modifier = Modifier.height(6.dp))

        // Main Body: Search Results OR Categorized & Personalized Rails
        if (searchQuery.isNotBlank()) {
            // Search Mode View
            if (isSearchingOnline) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
                        Spacer(modifier = Modifier.height(12.dp))
                        Text("Querying JioSaavn Regional Stream...", color = OneUITextSecondary, fontSize = 13.sp)
                    }
                }
            } else if (searchResults.isEmpty()) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text("No tracks found for \"$searchQuery\"", color = OneUITextSecondary, fontSize = 14.sp)
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 140.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    item {
                        Text(
                            text = "${searchResults.size} Songs Found",
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(vertical = 4.dp)
                        )
                    }
                    items(searchResults, key = { it.id }) { song ->
                        OnlineTrackRowItem(
                            song = song,
                            isCurrentPlaying = song.id == currentPlayingSongId,
                            downloadProgress = downloadProgressMap[song.id],
                            onPlay = { onPlayTrack(song, searchResults) },
                            onDownload = { onDownloadTrack(song) }
                        )
                    }
                }
            }
        } else {
            // Standard Explore Rails View
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(bottom = 150.dp),
                verticalArrangement = Arrangement.spacedBy(20.dp)
            ) {
                // Spotify Zero-Config Sync Banner Card
                item {
                    Card(
                        shape = RoundedCornerShape(18.dp),
                        colors = CardDefaults.cardColors(containerColor = OneUICardElevated),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp)
                            .clickable(onClick = onOpenSpotifySync)
                            .testTag("spotify_sync_banner")
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(46.dp)
                                    .background(Color(0xFF1DB954), shape = CircleShape),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = "♫",
                                    color = Color.Black,
                                    fontSize = 22.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }

                            Spacer(modifier = Modifier.width(14.dp))

                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "Import Spotify Library",
                                    fontSize = 15.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = OneUITextPrimary
                                )
                                Text(
                                    text = "Zero Client ID needed • Playlists, Liked Songs & Taste Profile",
                                    fontSize = 11.sp,
                                    color = OneUITextSecondary,
                                    lineHeight = 15.sp
                                )
                            }

                            Button(
                                onClick = onOpenSpotifySync,
                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1DB954)),
                                shape = RoundedCornerShape(12.dp),
                                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                            ) {
                                Text("Sync", color = Color.Black, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }

                // Rail 1: ✨ Made For You (Dynamic Personalization)
                if (madeForYouTracks.isNotEmpty()) {
                    item {
                        ExploreRailHeader(
                            title = "✨ Made For You",
                            subtitle = "Personalized from your Spotify & listening taste profile"
                        )
                        Spacer(modifier = Modifier.height(10.dp))
                        LazyRow(
                            contentPadding = PaddingValues(horizontal = 16.dp),
                            horizontalArrangement = Arrangement.spacedBy(14.dp)
                        ) {
                            items(madeForYouTracks, key = { "mfy_${it.id}" }) { song ->
                                OnlineTrackCard(
                                    song = song,
                                    isCurrentPlaying = song.id == currentPlayingSongId,
                                    downloadProgress = downloadProgressMap[song.id],
                                    onPlay = { onPlayTrack(song, madeForYouTracks) },
                                    onDownload = { onDownloadTrack(song) }
                                )
                            }
                        }
                    }
                }

                // Rail 2: 🎧 Based on Recent Listens (Rolling 14-Day History)
                if (recentListensTracks.isNotEmpty()) {
                    item {
                        ExploreRailHeader(
                            title = "🎧 Based on Recent Listens",
                            subtitle = "Dynamic recommendations based on your recent 14-day history"
                        )
                        Spacer(modifier = Modifier.height(10.dp))
                        LazyRow(
                            contentPadding = PaddingValues(horizontal = 16.dp),
                            horizontalArrangement = Arrangement.spacedBy(14.dp)
                        ) {
                            items(recentListensTracks, key = { "rec_${it.id}" }) { song ->
                                OnlineTrackCard(
                                    song = song,
                                    isCurrentPlaying = song.id == currentPlayingSongId,
                                    downloadProgress = downloadProgressMap[song.id],
                                    onPlay = { onPlayTrack(song, recentListensTracks) },
                                    onDownload = { onDownloadTrack(song) }
                                )
                            }
                        }
                    }
                }

                // Rail 3: 🔁 Rediscover Offline Favorites
                if (rediscoverOfflineTracks.isNotEmpty()) {
                    item {
                        ExploreRailHeader(
                            title = "🔁 Rediscover Offline Favorites",
                            subtitle = "Top tracks from your local device storage"
                        )
                        Spacer(modifier = Modifier.height(10.dp))
                        LazyRow(
                            contentPadding = PaddingValues(horizontal = 16.dp),
                            horizontalArrangement = Arrangement.spacedBy(14.dp)
                        ) {
                            items(rediscoverOfflineTracks, key = { "off_${it.id}" }) { song ->
                                OnlineTrackCard(
                                    song = song,
                                    isCurrentPlaying = song.id == currentPlayingSongId,
                                    downloadProgress = downloadProgressMap[song.id],
                                    onPlay = { onPlayTrack(song, rediscoverOfflineTracks) },
                                    onDownload = { onDownloadTrack(song) }
                                )
                            }
                        }
                    }
                }

                // Rail 4: 🔥 Punjabi Top 50 (Regional Prioritization)
                item {
                    ExploreRailHeader(
                        title = "🔥 Punjabi Top 50",
                        subtitle = "Trending Punjabi hits, Sidhu Moose Wala, AP Dhillon, Karan Aujla"
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    LazyRow(
                        contentPadding = PaddingValues(horizontal = 16.dp),
                        horizontalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        items(punjabiTop50, key = { "pun_${it.id}" }) { song ->
                            OnlineTrackCard(
                                song = song,
                                isCurrentPlaying = song.id == currentPlayingSongId,
                                downloadProgress = downloadProgressMap[song.id],
                                onPlay = { onPlayTrack(song, punjabiTop50) },
                                onDownload = { onDownloadTrack(song) }
                            )
                        }
                    }
                }

                // Rail 5: 🏆 Hindi Trending
                item {
                    ExploreRailHeader(
                        title = "🏆 Hindi Trending",
                        subtitle = "Top Bollywood hits & romantic soundtracks"
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    LazyRow(
                        contentPadding = PaddingValues(horizontal = 16.dp),
                        horizontalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        itemsIndexed(hindiTrending, key = { _, s -> "hin_${s.id}" }) { index, song ->
                            OnlineTrackCard(
                                song = song,
                                isCurrentPlaying = song.id == currentPlayingSongId,
                                downloadProgress = downloadProgressMap[song.id],
                                rankBadge = "#${index + 1}",
                                onPlay = { onPlayTrack(song, hindiTrending) },
                                onDownload = { onDownloadTrack(song) }
                            )
                        }
                    }
                }

                // Rail 6: ⚡ Marathi Top Charts
                item {
                    ExploreRailHeader(
                        title = "⚡ Marathi Top Charts",
                        subtitle = "Dhol Tasha, Ajay-Atul classics & modern energy"
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    LazyRow(
                        contentPadding = PaddingValues(horizontal = 16.dp),
                        horizontalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        items(marathiTopCharts, key = { "mar_${it.id}" }) { song ->
                            OnlineTrackCard(
                                song = song,
                                isCurrentPlaying = song.id == currentPlayingSongId,
                                downloadProgress = downloadProgressMap[song.id],
                                isNew = true,
                                onPlay = { onPlayTrack(song, marathiTopCharts) },
                                onDownload = { onDownloadTrack(song) }
                            )
                        }
                    }
                }

                // Rail 7: 🎧 Featured Playlists
                item {
                    ExploreRailHeader(
                        title = "🎧 Featured Playlists",
                        subtitle = "Curated mood mixes & regional collections"
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    LazyRow(
                        contentPadding = PaddingValues(horizontal = 16.dp),
                        horizontalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        items(featuredPlaylists, key = { it.id }) { playlist ->
                            FeaturedPlaylistCard(
                                playlist = playlist,
                                onClick = {
                                    if (playlist.songs.isNotEmpty()) {
                                        onPlayTrack(playlist.songs.first(), playlist.songs)
                                        Toast.makeText(context, "Playing playlist: ${playlist.title}", Toast.LENGTH_SHORT).show()
                                    }
                                }
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ExploreRailHeader(
    title: String,
    subtitle: String,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier.padding(horizontal = 16.dp)) {
        Text(
            text = title,
            fontSize = 19.sp,
            fontWeight = FontWeight.Bold,
            color = OneUITextPrimary
        )
        Text(
            text = subtitle,
            fontSize = 12.sp,
            color = OneUITextSecondary
        )
    }
}

/**
 * Spotify / JioSaavn style square music card with image, overlay play button, and download button.
 */
@Composable
private fun OnlineTrackCard(
    song: SongEntity,
    isCurrentPlaying: Boolean,
    downloadProgress: Int?,
    rankBadge: String? = null,
    isNew: Boolean = false,
    onPlay: () -> Unit,
    onDownload: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = OneUICardElevated,
        modifier = modifier
            .width(148.dp)
            .clickable(onClick = onPlay)
            .testTag("online_card_${song.id}")
    ) {
        Column(modifier = Modifier.padding(10.dp)) {
            Box(
                modifier = Modifier
                    .size(128.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(OneUISurfaceDark)
            ) {
                AsyncImage(
                    model = song.albumArtUri ?: song.coverArtUrl,
                    contentDescription = song.title,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )

                // Optional Rank Badge (Top Charts)
                if (rankBadge != null) {
                    Box(
                        modifier = Modifier
                            .align(Alignment.TopStart)
                            .padding(6.dp)
                            .clip(RoundedCornerShape(6.dp))
                            .background(Color.Black.copy(alpha = 0.75f))
                            .padding(horizontal = 6.dp, vertical = 2.dp)
                    ) {
                        Text(
                            text = rankBadge,
                            color = MaterialTheme.colorScheme.primary,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.ExtraBold
                        )
                    }
                }

                // Optional "NEW" Badge
                if (isNew) {
                    Box(
                        modifier = Modifier
                            .align(Alignment.TopStart)
                            .padding(6.dp)
                            .clip(RoundedCornerShape(6.dp))
                            .background(MaterialTheme.colorScheme.primary)
                            .padding(horizontal = 6.dp, vertical = 2.dp)
                    ) {
                        Text(
                            text = "NEW",
                            color = Color.Black,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.ExtraBold
                        )
                    }
                }

                // Overlay Play Indicator
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(6.dp)
                        .size(32.dp)
                        .clip(CircleShape)
                        .background(if (isCurrentPlaying) MaterialTheme.colorScheme.primary else Color.Black.copy(alpha = 0.65f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.PlayArrow,
                        contentDescription = "Play",
                        tint = if (isCurrentPlaying) Color.Black else Color.White,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Title & Artist
            Text(
                text = song.title,
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold,
                color = if (isCurrentPlaying) MaterialTheme.colorScheme.primary else OneUITextPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )

            Text(
                text = song.artist,
                fontSize = 11.sp,
                color = OneUITextSecondary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )

            Spacer(modifier = Modifier.height(6.dp))

            // Native Download Action Button with progress feedback
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "320 kbps",
                    fontSize = 10.sp,
                    color = OneUITextSecondary.copy(alpha = 0.7f),
                    fontWeight = FontWeight.SemiBold
                )

                IconButton(
                    onClick = onDownload,
                    modifier = Modifier.size(28.dp).testTag("download_btn_${song.id}")
                ) {
                    when {
                        song.isDownloaded -> {
                            Icon(
                                imageVector = Icons.Default.Check,
                                contentDescription = "Downloaded",
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                        downloadProgress != null && downloadProgress in 1..99 -> {
                            CircularProgressIndicator(
                                progress = { downloadProgress / 100f },
                                modifier = Modifier.size(16.dp),
                                color = MaterialTheme.colorScheme.primary,
                                strokeWidth = 2.dp
                            )
                        }
                        else -> {
                            Icon(
                                imageVector = Icons.Default.Download,
                                contentDescription = "Download",
                                tint = OneUITextSecondary,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}

/**
 * Featured playlist wide card with cover artwork, title, and track count.
 */
@Composable
private fun FeaturedPlaylistCard(
    playlist: OnlineMusicCatalog.OnlinePlaylist,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = OneUICardElevated,
        modifier = modifier
            .width(180.dp)
            .clickable(onClick = onClick)
            .testTag("playlist_card_${playlist.id}")
    ) {
        Column(modifier = Modifier.padding(10.dp)) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(110.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(OneUISurfaceDark)
            ) {
                AsyncImage(
                    model = playlist.coverUrl,
                    contentDescription = playlist.title,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )

                Box(
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(6.dp)
                        .size(30.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.primary),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.PlayArrow,
                        contentDescription = "Play Playlist",
                        tint = Color.Black,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = playlist.title,
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold,
                color = OneUITextPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )

            Text(
                text = playlist.subtitle,
                fontSize = 11.sp,
                color = OneUITextSecondary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )

            Text(
                text = "${playlist.songCount} Tracks",
                fontSize = 10.sp,
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.padding(top = 2.dp)
            )
        }
    }
}

/**
 * Single search result row item in online search view.
 */
@Composable
private fun OnlineTrackRowItem(
    song: SongEntity,
    isCurrentPlaying: Boolean,
    downloadProgress: Int?,
    onPlay: () -> Unit,
    onDownload: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = OneUICardElevated,
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onPlay)
            .testTag("online_row_${song.id}")
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            AsyncImage(
                model = song.albumArtUri ?: song.coverArtUrl,
                contentDescription = song.title,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .size(50.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(OneUISurfaceDark)
            )

            Spacer(modifier = Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = song.title,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = if (isCurrentPlaying) MaterialTheme.colorScheme.primary else OneUITextPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = "${song.artist} • ${song.album}",
                    fontSize = 12.sp,
                    color = OneUITextSecondary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            IconButton(
                onClick = onDownload,
                modifier = Modifier.size(36.dp).testTag("download_row_btn_${song.id}")
            ) {
                when {
                    song.isDownloaded -> {
                        Icon(
                            imageVector = Icons.Default.Check,
                            contentDescription = "Downloaded",
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                    downloadProgress != null && downloadProgress in 1..99 -> {
                        CircularProgressIndicator(
                            progress = { downloadProgress / 100f },
                            modifier = Modifier.size(20.dp),
                            color = MaterialTheme.colorScheme.primary,
                            strokeWidth = 2.dp
                        )
                    }
                    else -> {
                        Icon(
                            imageVector = Icons.Default.Download,
                            contentDescription = "Download 320kbps",
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
            }
        }
    }
}
