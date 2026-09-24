package com.example.ui.screens

import androidx.compose.animation.AnimatedVisibility
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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Album
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.ContentCut
import androidx.compose.material.icons.filled.CreateNewFolder
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.PlaylistAdd
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.ScrollableTabRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRowDefaults
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import coil.compose.AsyncImage
import com.example.data.PlaylistEntity
import com.example.data.SongEntity
import com.example.ui.theme.NeonMint
import com.example.ui.theme.OneUICardElevated
import com.example.ui.theme.OneUIDarkBackground
import com.example.ui.theme.OneUISurfaceDark
import com.example.ui.theme.OneUITextPrimary
import com.example.ui.theme.OneUITextSecondary
import com.example.ui.theme.SpotifyGreen
import com.example.ui.components.TrackListItem
import com.example.utils.cleanMetadataString
import java.io.File

/**
 * Samsung Music Core UI with 5 Top Tabs:
 * Tracks, Playlists, Albums, Artists, and Folders.
 * Features a Universal Plus (+) Action button triggering playlist creation,
 * SAF local storage import, and online music search.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LibraryScreen(
    librarySongs: List<SongEntity>,
    hiddenVaultSongs: List<SongEntity>,
    favoriteSongs: List<SongEntity>,
    playlists: List<PlaylistEntity>,
    currentPlayingSongId: Long?,
    onSongSelected: (SongEntity, List<SongEntity>) -> Unit,
    onRescanRequested: () -> Unit,
    onToggleSoftHide: (SongEntity) -> Unit,
    onToggleFavorite: (SongEntity) -> Unit,
    onOpenAudioEditor: (SongEntity) -> Unit,
    onCreatePlaylist: (String) -> Unit,
    onAddSongToPlaylist: (playlistId: Long, songId: Long) -> Unit,
    onSelectPlaylist: (PlaylistEntity) -> Unit,
    onRequestSafImport: () -> Unit,
    onOpenOnlineSearch: () -> Unit,
    onOpenSettings: () -> Unit,
    visibleTabs: Set<String> = setOf("Tracks", "Playlists", "Albums", "Artists", "Folders"),
    modifier: Modifier = Modifier
) {
    val allTabTitles = listOf("Tracks", "Playlists", "Albums", "Artists", "Folders")
    val tabTitles = remember(visibleTabs) {
        val filtered = allTabTitles.filter { visibleTabs.contains(it) }
        if (filtered.isEmpty()) listOf("Tracks") else filtered
    }

    var selectedTopTabIndex by remember { mutableIntStateOf(0) }
    val currentTabTitle = tabTitles.getOrElse(selectedTopTabIndex.coerceIn(0, tabTitles.size - 1)) { "Tracks" }

    var searchQuery by remember { mutableStateOf("") }
    var isSearchActive by remember { mutableStateOf(false) }

    // Sub-filters for Tracks tab: 0: All, 1: Punjabi, 2: Hindi/Marathi, 3: Hidden Vault
    var selectedTrackSubFilter by remember { mutableIntStateOf(0) }

    // Dialogs & Sheets
    var showUniversalPlusSheet by remember { mutableStateOf(false) }
    var showCreatePlaylistDialog by remember { mutableStateOf(false) }
    var songForPlaylistSelection by remember { mutableStateOf<SongEntity?>(null) }

    val filteredTracks = remember(librarySongs, hiddenVaultSongs, searchQuery, selectedTrackSubFilter) {
        val baseList = if (selectedTrackSubFilter == 3) {
            hiddenVaultSongs
        } else {
            when (selectedTrackSubFilter) {
                1 -> librarySongs.filter { it.languageScript == "PUNJABI" }
                2 -> librarySongs.filter { it.languageScript == "HINDI_MARATHI" }
                else -> librarySongs
            }
        }

        if (searchQuery.isBlank()) {
            baseList
        } else {
            baseList.filter {
                it.title.contains(searchQuery, ignoreCase = true) ||
                it.artist.contains(searchQuery, ignoreCase = true) ||
                it.album.contains(searchQuery, ignoreCase = true)
            }
        }
    }

    Box(modifier = modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(OneUIDarkBackground)
        ) {
            // Samsung One UI Top Header
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column {
                    Text(
                        text = "Aura Music",
                        fontSize = 26.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = OneUITextPrimary
                    )
                    Text(
                        text = "${librarySongs.size} tracks offline",
                        fontSize = 12.sp,
                        color = OneUITextSecondary
                    )
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(
                        onClick = { isSearchActive = !isSearchActive },
                        modifier = Modifier.testTag("toggle_search_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Search,
                            contentDescription = "Search",
                            tint = if (isSearchActive) SpotifyGreen else OneUITextPrimary
                        )
                    }

                    IconButton(
                        onClick = onRescanRequested,
                        modifier = Modifier.testTag("rescan_media_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Refresh,
                            contentDescription = "Rescan Media",
                            tint = OneUITextPrimary
                        )
                    }

                    IconButton(
                        onClick = onOpenSettings,
                        modifier = Modifier.testTag("settings_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Settings,
                            contentDescription = "Settings",
                            tint = OneUITextPrimary
                        )
                    }
                }
            }

            // Expandable Search Bar
            AnimatedVisibility(visible = isSearchActive) {
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    placeholder = { Text("Search songs, artists, albums...", fontSize = 14.sp) },
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
                        .padding(horizontal = 16.dp, vertical = 6.dp)
                        .testTag("search_text_field")
                )
            }

            // Samsung One UI Top Tab Row
            val activeTabIdx = selectedTopTabIndex.coerceIn(0, tabTitles.size - 1)
            ScrollableTabRow(
                selectedTabIndex = activeTabIdx,
                containerColor = OneUIDarkBackground,
                contentColor = SpotifyGreen,
                edgePadding = 16.dp,
                indicator = { tabPositions ->
                    TabRowDefaults.SecondaryIndicator(
                        modifier = Modifier.tabIndicatorOffset(tabPositions[activeTabIdx]),
                        color = SpotifyGreen,
                        height = 3.dp
                    )
                },
                modifier = Modifier.fillMaxWidth()
            ) {
                tabTitles.forEachIndexed { index, title ->
                    Tab(
                        selected = activeTabIdx == index,
                        onClick = { selectedTopTabIndex = index },
                        text = {
                            Text(
                                text = title,
                                fontSize = 14.sp,
                                fontWeight = if (activeTabIdx == index) FontWeight.Bold else FontWeight.Normal,
                                color = if (activeTabIdx == index) SpotifyGreen else OneUITextSecondary
                            )
                        },
                        modifier = Modifier.testTag("tab_${title.lowercase()}")
                    )
                }
            }

            // Content Body based on selected Top Tab
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
            ) {
                when (currentTabTitle) {
                    "Tracks" -> TracksTabView(
                        tracks = filteredTracks,
                        selectedSubFilter = selectedTrackSubFilter,
                        onSelectSubFilter = { selectedTrackSubFilter = it },
                        hiddenVaultCount = hiddenVaultSongs.size,
                        currentPlayingSongId = currentPlayingSongId,
                        onSongSelected = { song -> onSongSelected(song, filteredTracks) },
                        onToggleSoftHide = onToggleSoftHide,
                        onToggleFavorite = onToggleFavorite,
                        onOpenAudioEditor = onOpenAudioEditor,
                        onAddToPlaylistClick = { songForPlaylistSelection = it }
                    )
                    "Playlists" -> PlaylistsTabView(
                        playlists = playlists,
                        favoriteSongs = favoriteSongs,
                        onSelectPlaylist = onSelectPlaylist,
                        onCreatePlaylistClick = { showCreatePlaylistDialog = true }
                    )
                    "Albums" -> AlbumsTabView(
                        librarySongs = librarySongs,
                        onSongSelected = onSongSelected
                    )
                    "Artists" -> ArtistsTabView(
                        librarySongs = librarySongs,
                        onSongSelected = onSongSelected
                    )
                    "Folders" -> FoldersTabView(
                        librarySongs = librarySongs,
                        onSongSelected = onSongSelected
                    )
                }
            }
        }

        // Universal Plus (+) Action FloatingActionButton
        FloatingActionButton(
            onClick = { showUniversalPlusSheet = true },
            containerColor = SpotifyGreen,
            contentColor = Color.Black,
            shape = CircleShape,
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(end = 20.dp, bottom = 24.dp)
                .size(60.dp)
                .testTag("universal_plus_fab")
        ) {
            Icon(
                imageVector = Icons.Default.Add,
                contentDescription = "Universal Add Menu",
                modifier = Modifier.size(32.dp)
            )
        }
    }

    // Universal Plus Action BottomSheet
    if (showUniversalPlusSheet) {
        val sheetState = rememberModalBottomSheetState()
        ModalBottomSheet(
            onDismissRequest = { showUniversalPlusSheet = false },
            sheetState = sheetState,
            containerColor = OneUICardElevated,
            shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp, vertical = 16.dp)
            ) {
                Text(
                    text = "Quick Actions",
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    color = OneUITextPrimary
                )
                Spacer(Modifier.height(16.dp))

                // Action 1: Create Playlist
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable {
                            showUniversalPlusSheet = false
                            showCreatePlaylistDialog = true
                        }
                        .padding(vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(44.dp)
                            .background(SpotifyGreen.copy(alpha = 0.2f), CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(Icons.Default.PlaylistAdd, contentDescription = null, tint = SpotifyGreen)
                    }
                    Spacer(Modifier.width(16.dp))
                    Column {
                        Text("Create Playlist", fontSize = 16.sp, fontWeight = FontWeight.SemiBold, color = OneUITextPrimary)
                        Text("Build custom offline collections", fontSize = 12.sp, color = OneUITextSecondary)
                    }
                }

                // Action 2: Import Local Audio / Folder via SAF
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable {
                            showUniversalPlusSheet = false
                            onRequestSafImport()
                        }
                        .padding(vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(44.dp)
                            .background(NeonMint.copy(alpha = 0.2f), CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(Icons.Default.CreateNewFolder, contentDescription = null, tint = NeonMint)
                    }
                    Spacer(Modifier.width(16.dp))
                    Column {
                        Text("Import via SAF", fontSize = 16.sp, fontWeight = FontWeight.SemiBold, color = OneUITextPrimary)
                        Text("Pick audio files or storage directory directly", fontSize = 12.sp, color = OneUITextSecondary)
                    }
                }

                // Action 3: Search Online Music
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable {
                            showUniversalPlusSheet = false
                            onOpenOnlineSearch()
                        }
                        .padding(vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(44.dp)
                            .background(Color(0xFF388E3C).copy(alpha = 0.2f), CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(Icons.Default.CloudDownload, contentDescription = null, tint = SpotifyGreen)
                    }
                    Spacer(Modifier.width(16.dp))
                    Column {
                        Text("Search Online Music", fontSize = 16.sp, fontWeight = FontWeight.SemiBold, color = OneUITextPrimary)
                        Text("Stream and download royalty-free tracks offline", fontSize = 12.sp, color = OneUITextSecondary)
                    }
                }

                Spacer(Modifier.height(24.dp))
            }
        }
    }

    // Create Playlist Dialog
    if (showCreatePlaylistDialog) {
        var newPlaylistName by remember { mutableStateOf("") }
        Dialog(onDismissRequest = { showCreatePlaylistDialog = false }) {
            Surface(
                shape = RoundedCornerShape(20.dp),
                color = OneUICardElevated,
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(20.dp)) {
                    Text(
                        text = "New Playlist",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        color = OneUITextPrimary
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    OutlinedTextField(
                        value = newPlaylistName,
                        onValueChange = { newPlaylistName = it },
                        placeholder = { Text("Playlist title") },
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = OneUITextPrimary,
                            unfocusedTextColor = OneUITextPrimary,
                            focusedBorderColor = SpotifyGreen
                        ),
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End
                    ) {
                        TextButton(onClick = { showCreatePlaylistDialog = false }) {
                            Text("Cancel", color = OneUITextSecondary)
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                        TextButton(
                            onClick = {
                                if (newPlaylistName.isNotBlank()) {
                                    onCreatePlaylist(newPlaylistName.trim())
                                    showCreatePlaylistDialog = false
                                }
                            }
                        ) {
                            Text("Create", color = SpotifyGreen, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }
    }

    // Add Song to Playlist Picker Dialog
    songForPlaylistSelection?.let { song ->
        Dialog(onDismissRequest = { songForPlaylistSelection = null }) {
            Surface(
                shape = RoundedCornerShape(20.dp),
                color = OneUICardElevated,
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(20.dp)) {
                    Text(
                        text = "Add to Playlist",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        color = OneUITextPrimary
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = cleanMetadataString(song.title),
                        fontSize = 13.sp,
                        color = OneUITextSecondary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Spacer(modifier = Modifier.height(12.dp))

                    LazyColumn(modifier = Modifier.height(240.dp)) {
                        items(playlists) { playlist ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        onAddSongToPlaylist(playlist.playlistId, song.id)
                                        songForPlaylistSelection = null
                                    }
                                    .padding(vertical = 10.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(Icons.Default.PlaylistAdd, contentDescription = null, tint = SpotifyGreen)
                                Spacer(modifier = Modifier.width(12.dp))
                                Column {
                                    Text(playlist.name, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = OneUITextPrimary)
                                    Text(playlist.description, fontSize = 11.sp, color = OneUITextSecondary)
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))
                    TextButton(
                        onClick = { songForPlaylistSelection = null },
                        modifier = Modifier.align(Alignment.End)
                    ) {
                        Text("Close", color = SpotifyGreen)
                    }
                }
            }
        }
    }
}

// -------------------------------------------------------------
// TAB 1: Tracks Tab View (Alphabetical list with Track numbers)
// -------------------------------------------------------------
@Composable
private fun TracksTabView(
    tracks: List<SongEntity>,
    selectedSubFilter: Int,
    onSelectSubFilter: (Int) -> Unit,
    hiddenVaultCount: Int,
    currentPlayingSongId: Long?,
    onSongSelected: (SongEntity) -> Unit,
    onToggleSoftHide: (SongEntity) -> Unit,
    onToggleFavorite: (SongEntity) -> Unit,
    onOpenAudioEditor: (SongEntity) -> Unit,
    onAddToPlaylistClick: (SongEntity) -> Unit
) {
    val subFilters = listOf(
        "All (${tracks.size})",
        "Punjabi",
        "Hindi & Marathi",
        "Hidden Vault ($hiddenVaultCount)"
    )

    Column(modifier = Modifier.fillMaxSize()) {
        // Linguistic / Soft-Hide Filter Chips
        LazyRow(
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            itemsIndexed(subFilters) { index, label ->
                FilterChip(
                    selected = selectedSubFilter == index,
                    onClick = { onSelectSubFilter(index) },
                    label = { Text(label, fontSize = 12.sp) },
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

        // Song List with track index and duration
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(bottom = 96.dp)
        ) {
            itemsIndexed(tracks, key = { _, s -> s.id }) { index, song ->
                TrackItemRow(
                    index = index + 1,
                    song = song,
                    isCurrentPlaying = song.id == currentPlayingSongId,
                    onItemClick = { onSongSelected(song) },
                    onToggleSoftHide = { onToggleSoftHide(song) },
                    onToggleFavorite = { onToggleFavorite(song) },
                    onOpenAudioEditor = { onOpenAudioEditor(song) },
                    onAddToPlaylistClick = { onAddToPlaylistClick(song) }
                )
            }
        }
    }
}

@Composable
private fun TrackItemRow(
    index: Int,
    song: SongEntity,
    isCurrentPlaying: Boolean,
    onItemClick: () -> Unit,
    onToggleSoftHide: () -> Unit,
    onToggleFavorite: () -> Unit,
    onOpenAudioEditor: () -> Unit,
    onAddToPlaylistClick: () -> Unit
) {
    var showMenu by remember { mutableStateOf(false) }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onItemClick)
            .background(if (isCurrentPlaying) SpotifyGreen.copy(alpha = 0.08f) else Color.Transparent)
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Track number index or animated playing indicator
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

        // Title & Artist: strictly clean metadata without subtitles or concatenations
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
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = cleanMetadataString(song.artist),
                    fontSize = 12.sp,
                    color = OneUITextSecondary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false)
                )
                if (song.sourceType == "ONLINE") {
                    Spacer(Modifier.width(6.dp))
                    Text("• Online", fontSize = 10.sp, color = NeonMint)
                }
            }
        }

        // Favorite Heart Button
        IconButton(
            onClick = onToggleFavorite,
            modifier = Modifier.size(36.dp)
        ) {
            Icon(
                imageVector = if (song.isFavorite) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                contentDescription = "Favorite",
                tint = if (song.isFavorite) Color(0xFFFF4081) else OneUITextSecondary,
                modifier = Modifier.size(20.dp)
            )
        }

        // Duration
        Text(
            text = formatDuration(song.durationMs),
            fontSize = 12.sp,
            color = OneUITextSecondary,
            modifier = Modifier.padding(horizontal = 4.dp)
        )

        // Dropdown Menu for Actions
        Box {
            IconButton(
                onClick = { showMenu = true },
                modifier = Modifier.size(36.dp)
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
                DropdownMenuItem(
                    text = { Text(if (song.isFavorite) "Remove from Favorites" else "Add to Favorites", color = OneUITextPrimary) },
                    onClick = {
                        showMenu = false
                        onToggleFavorite()
                    },
                    leadingIcon = {
                        Icon(
                            if (song.isFavorite) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                            contentDescription = null,
                            tint = Color(0xFFFF4081)
                        )
                    }
                )
                DropdownMenuItem(
                    text = { Text("Add to Playlist", color = OneUITextPrimary) },
                    onClick = {
                        showMenu = false
                        onAddToPlaylistClick()
                    },
                    leadingIcon = {
                        Icon(Icons.Default.PlaylistAdd, contentDescription = null, tint = SpotifyGreen)
                    }
                )
                DropdownMenuItem(
                    text = {
                        Text(
                            if (song.isHiddenFromLibrary) "Unhide from Library" else "Soft-Hide (Playlist Only)",
                            color = OneUITextPrimary
                        )
                    },
                    onClick = {
                        showMenu = false
                        onToggleSoftHide()
                    },
                    leadingIcon = {
                        Icon(
                            if (song.isHiddenFromLibrary) Icons.Default.Visibility else Icons.Default.VisibilityOff,
                            contentDescription = null,
                            tint = NeonMint
                        )
                    }
                )
                DropdownMenuItem(
                    text = { Text("Trim / Cut Audio", color = OneUITextPrimary) },
                    onClick = {
                        showMenu = false
                        onOpenAudioEditor()
                    },
                    leadingIcon = {
                        Icon(Icons.Default.ContentCut, contentDescription = null, tint = OneUITextPrimary)
                    }
                )
            }
        }
    }
}

// -------------------------------------------------------------
// TAB 2: Playlists Tab View
// -------------------------------------------------------------
@Composable
private fun PlaylistsTabView(
    playlists: List<PlaylistEntity>,
    favoriteSongs: List<SongEntity>,
    onSelectPlaylist: (PlaylistEntity) -> Unit,
    onCreatePlaylistClick: () -> Unit
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 96.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        // Pinned Favorites Playlist Card
        if (favoriteSongs.isNotEmpty()) {
            item {
                Card(
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = OneUICardElevated),
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable {
                            val favPlaylist = playlists.firstOrNull { it.playlistType == "FAVORITES" }
                            if (favPlaylist != null) {
                                onSelectPlaylist(favPlaylist)
                            }
                        }
                ) {
                    Row(
                        modifier = Modifier.padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(50.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .background(Color(0xFFE91E63).copy(alpha = 0.2f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Favorite,
                                contentDescription = null,
                                tint = Color(0xFFFF4081),
                                modifier = Modifier.size(28.dp)
                            )
                        }
                        Spacer(Modifier.width(14.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "❤️ Liked Songs (Favorites)",
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Bold,
                                color = OneUITextPrimary
                            )
                            Text(
                                text = "${favoriteSongs.size} pinned favorite tracks",
                                fontSize = 12.sp,
                                color = OneUITextSecondary
                            )
                        }
                        Icon(
                            imageVector = Icons.Default.PlayArrow,
                            contentDescription = "Play Favorites",
                            tint = SpotifyGreen
                        )
                    }
                }
            }
        }

        item {
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = OneUICardElevated),
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(onClick = onCreatePlaylistClick)
            ) {
                Row(
                    modifier = Modifier.padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(48.dp)
                            .background(SpotifyGreen.copy(alpha = 0.2f), CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(Icons.Default.Add, contentDescription = null, tint = SpotifyGreen)
                    }
                    Spacer(Modifier.width(14.dp))
                    Column {
                        Text("Create New Playlist", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = OneUITextPrimary)
                        Text("Organize your favorite Punjabi, Hindi, and Marathi tracks", fontSize = 12.sp, color = OneUITextSecondary)
                    }
                }
            }
        }

        items(playlists, key = { it.playlistId }) { playlist ->
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = OneUICardElevated),
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onSelectPlaylist(playlist) }
            ) {
                Row(
                    modifier = Modifier.padding(14.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(50.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(OneUISurfaceDark),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.PlaylistAdd,
                            contentDescription = null,
                            tint = if (playlist.isAutoGenerated) NeonMint else SpotifyGreen,
                            modifier = Modifier.size(26.dp)
                        )
                    }
                    Spacer(Modifier.width(14.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = playlist.name,
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold,
                            color = OneUITextPrimary
                        )
                        Spacer(Modifier.height(2.dp))
                        Text(
                            text = if (playlist.isAutoGenerated) "Smart Auto Playlist • ${playlist.description}" else playlist.description.ifBlank { "Custom Playlist" },
                            fontSize = 12.sp,
                            color = OneUITextSecondary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    Icon(
                        imageVector = Icons.Default.PlayArrow,
                        contentDescription = "Play Playlist",
                        tint = SpotifyGreen
                    )
                }
            }
        }
    }
}

// -------------------------------------------------------------
// TAB 3: Albums Tab View (Grid View with Cover Art Cards)
// -------------------------------------------------------------
@Composable
private fun AlbumsTabView(
    librarySongs: List<SongEntity>,
    onSongSelected: (SongEntity, List<SongEntity>) -> Unit
) {
    val albumGroups = remember(librarySongs) {
        librarySongs.groupBy { it.album }
    }

    if (albumGroups.isEmpty()) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text("No albums found in offline storage", color = OneUITextSecondary)
        }
        return
    }

    LazyVerticalGrid(
        columns = GridCells.Fixed(2),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 96.dp),
        horizontalArrangement = Arrangement.spacedBy(14.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
        modifier = Modifier.fillMaxSize()
    ) {
        items(albumGroups.keys.toList()) { albumName ->
            val albumTracks = albumGroups[albumName] ?: emptyList()
            val representativeSong = albumTracks.firstOrNull()

            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = OneUICardElevated),
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable {
                        representativeSong?.let { onSongSelected(it, albumTracks) }
                    }
            ) {
                Column {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .aspectRatio(1f)
                    ) {
                        if (!representativeSong?.albumArtUri.isNullOrBlank()) {
                            AsyncImage(
                                model = representativeSong?.albumArtUri,
                                contentDescription = albumName,
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
                                    imageVector = Icons.Default.Album,
                                    contentDescription = null,
                                    tint = SpotifyGreen.copy(alpha = 0.7f),
                                    modifier = Modifier.size(48.dp)
                                )
                            }
                        }
                    }

                    Column(modifier = Modifier.padding(12.dp)) {
                        Text(
                            text = albumName,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold,
                            color = OneUITextPrimary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Spacer(Modifier.height(2.dp))
                        Text(
                            text = representativeSong?.artist ?: "Unknown Artist",
                            fontSize = 12.sp,
                            color = OneUITextSecondary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Spacer(Modifier.height(2.dp))
                        Text(
                            text = "${albumTracks.size} tracks",
                            fontSize = 11.sp,
                            color = NeonMint
                        )
                    }
                }
            }
        }
    }
}

// -------------------------------------------------------------
// TAB 4: Artists Tab View (Circular Avatar List)
// -------------------------------------------------------------
@Composable
private fun ArtistsTabView(
    librarySongs: List<SongEntity>,
    onSongSelected: (SongEntity, List<SongEntity>) -> Unit
) {
    val artistGroups = remember(librarySongs) {
        librarySongs.groupBy { it.artist }
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 96.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        items(artistGroups.keys.toList()) { artistName ->
            val artistTracks = artistGroups[artistName] ?: emptyList()
            val sample = artistTracks.firstOrNull()

            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = OneUICardElevated),
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable {
                        sample?.let { onSongSelected(it, artistTracks) }
                    }
            ) {
                Row(
                    modifier = Modifier.padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Circular Avatar
                    Box(
                        modifier = Modifier
                            .size(54.dp)
                            .clip(CircleShape)
                    ) {
                        if (!sample?.albumArtUri.isNullOrBlank()) {
                            AsyncImage(
                                model = sample?.albumArtUri,
                                contentDescription = artistName,
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
                                Icon(Icons.Default.Person, contentDescription = null, tint = SpotifyGreen)
                            }
                        }
                    }

                    Spacer(Modifier.width(14.dp))

                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = artistName,
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold,
                            color = OneUITextPrimary
                        )
                        Spacer(Modifier.height(2.dp))
                        Text(
                            text = "${artistTracks.size} tracks",
                            fontSize = 12.sp,
                            color = OneUITextSecondary
                        )
                    }

                    IconButton(onClick = { sample?.let { onSongSelected(it, artistTracks) } }) {
                        Icon(Icons.Default.PlayArrow, contentDescription = "Play Artist", tint = SpotifyGreen)
                    }
                }
            }
        }
    }
}

// -------------------------------------------------------------
// TAB 5: Folders Tab View (Direct Local Directory Browser)
// -------------------------------------------------------------
@Composable
private fun FoldersTabView(
    librarySongs: List<SongEntity>,
    onSongSelected: (SongEntity, List<SongEntity>) -> Unit
) {
    val folderGroups = remember(librarySongs) {
        librarySongs.groupBy { song ->
            val path = song.dataPath
            if (!path.isNullOrBlank()) {
                val f = File(path)
                f.parentFile?.name ?: "Storage"
            } else {
                "Music Storage"
            }
        }
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 96.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        items(folderGroups.keys.toList()) { folderName ->
            val folderTracks = folderGroups[folderName] ?: emptyList()
            val sample = folderTracks.firstOrNull()

            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = OneUICardElevated),
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable {
                        sample?.let { onSongSelected(it, folderTracks) }
                    }
            ) {
                Row(
                    modifier = Modifier.padding(14.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(48.dp)
                            .background(SpotifyGreen.copy(alpha = 0.2f), RoundedCornerShape(12.dp)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(Icons.Default.Folder, contentDescription = null, tint = SpotifyGreen, modifier = Modifier.size(28.dp))
                    }

                    Spacer(Modifier.width(14.dp))

                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = folderName,
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold,
                            color = OneUITextPrimary
                        )
                        Spacer(Modifier.height(2.dp))
                        Text(
                            text = "${folderTracks.size} audio files",
                            fontSize = 12.sp,
                            color = OneUITextSecondary
                        )
                    }

                    Icon(Icons.Default.PlayArrow, contentDescription = "Play Folder", tint = SpotifyGreen)
                }
            }
        }
    }
}

private fun formatDuration(durationMs: Long): String {
    val totalSecs = (durationMs / 1000).coerceAtLeast(0L)
    val mins = totalSecs / 60
    val secs = totalSecs % 60
    return String.format("%d:%02d", mins, secs)
}
