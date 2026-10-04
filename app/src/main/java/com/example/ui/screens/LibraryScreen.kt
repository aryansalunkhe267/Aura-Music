package com.example.ui.screens

import android.net.Uri
import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
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
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.QueueMusic
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Album
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.CreateNewFolder
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.PlaylistAdd
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.SelectAll
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.input.pointer.pointerInput
import com.example.playback.PlaybackManager
import com.example.ui.components.EditMetadataDialog
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import coil.compose.AsyncImage
import com.example.data.PlaylistEntity
import com.example.data.SongEntity
import com.example.engine.SmartCategorizer
import com.example.ui.components.FastScrollAlphabetIndexer
import com.example.ui.components.TrackListItem
import com.example.ui.theme.OneUICardElevated
import com.example.ui.theme.OneUIDarkBackground
import com.example.ui.theme.OneUISurfaceDark
import com.example.ui.theme.OneUITextPrimary
import com.example.ui.theme.OneUITextSecondary
import com.example.utils.MetadataSanitizer
import com.example.utils.cleanMetadataString
import java.io.File

/**
 * Samsung One UI Library Screen with automated language & genre sections:
 * Punjabi, Hindi, Marathi, Bollywood, 90s Hindi, Devotional, Playlists, Albums, Artists, Folders.
 * Features fast-scroll alphabet indexer, custom wallpaper with Gaussian blur & scrim,
 * and physical/virtual file actions.
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
    onDeleteFromStorage: (SongEntity) -> Unit,
    onOpenAudioEditor: (SongEntity) -> Unit,
    onCreatePlaylist: (String) -> Unit,
    onAddSongToPlaylist: (playlistId: Long, songId: Long) -> Unit,
    onSelectPlaylist: (PlaylistEntity) -> Unit,
    onRequestSafImport: () -> Unit,
    onOpenOnlineSearch: () -> Unit,
    onOpenSettings: () -> Unit,
    onSaveMetadataOverride: (songId: Long, title: String, artist: String, album: String, genre: String?) -> Unit = { _, _, _, _, _ -> },
    customWallpaperUri: String? = null,
    wallpaperScrimAlpha: Float = 0.50f,
    modifier: Modifier = Modifier
) {
    val context = androidx.compose.ui.platform.LocalContext.current

    // Automated Library Sections
    val allTabTitles = listOf(
        "Tracks",
        "Punjabi",
        "Hindi",
        "Marathi",
        "Bollywood",
        "90s Hindi",
        "Devotional",
        "Playlists",
        "Albums",
        "Artists",
        "Folders"
    )

    var selectedTopTabIndex by remember { mutableIntStateOf(0) }
    val currentTabTitle = allTabTitles.getOrElse(selectedTopTabIndex.coerceIn(0, allTabTitles.size - 1)) { "Tracks" }

    var searchQuery by remember { mutableStateOf("") }
    var isSearchActive by remember { mutableStateOf(false) }

    // Dialogs & Sheets
    var showUniversalPlusSheet by remember { mutableStateOf(false) }
    var showCreatePlaylistDialog by remember { mutableStateOf(false) }
    var songForPlaylistSelection by remember { mutableStateOf<SongEntity?>(null) }
    var songForMetadataEdit by remember { mutableStateOf<SongEntity?>(null) }
    var showBulkPlaylistDialog by remember { mutableStateOf(false) }
    var showBulkDeleteConfirmDialog by remember { mutableStateOf(false) }

    // Multi-Select Selection Mode
    var selectedSongIds by remember { mutableStateOf<Set<Long>>(emptySet()) }
    val isSelectionMode = selectedSongIds.isNotEmpty()

    fun toggleSelection(songId: Long) {
        selectedSongIds = if (selectedSongIds.contains(songId)) {
            selectedSongIds - songId
        } else {
            selectedSongIds + songId
        }
    }

    fun setMultipleSongsSelected(songIds: Collection<Long>, isSelected: Boolean) {
        selectedSongIds = if (isSelected) {
            selectedSongIds + songIds
        } else {
            selectedSongIds - songIds.toSet()
        }
    }

    fun clearSelection() {
        selectedSongIds = emptySet()
    }

    // Categorized song filtering
    val currentSectionTracks = remember(librarySongs, currentTabTitle, searchQuery) {
        val baseList = when (currentTabTitle) {
            "Punjabi" -> librarySongs.filter {
                SmartCategorizer.classify(it.title, it.artist, it.album, it.genre ?: "") == SmartCategorizer.Category.PUNJABI
            }
            "Hindi" -> librarySongs.filter {
                val cat = SmartCategorizer.classify(it.title, it.artist, it.album, it.genre ?: "")
                cat == SmartCategorizer.Category.HINDI || cat == SmartCategorizer.Category.BOLLYWOOD || cat == SmartCategorizer.Category.NINETIES_HINDI
            }
            "Marathi" -> librarySongs.filter {
                SmartCategorizer.classify(it.title, it.artist, it.album, it.genre ?: "") == SmartCategorizer.Category.MARATHI
            }
            "Bollywood" -> librarySongs.filter {
                SmartCategorizer.classify(it.title, it.artist, it.album, it.genre ?: "") == SmartCategorizer.Category.BOLLYWOOD
            }
            "90s Hindi" -> librarySongs.filter {
                SmartCategorizer.classify(it.title, it.artist, it.album, it.genre ?: "") == SmartCategorizer.Category.NINETIES_HINDI
            }
            "Devotional" -> librarySongs.filter {
                SmartCategorizer.classify(it.title, it.artist, it.album, it.genre ?: "") == SmartCategorizer.Category.DEVOTIONAL
            }
            else -> librarySongs // "Tracks" shows all
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
        // Custom Gallery Wallpaper with Gaussian Blur & Scrim
        if (!customWallpaperUri.isNullOrBlank()) {
            AsyncImage(
                model = customWallpaperUri,
                contentDescription = "Custom Wallpaper",
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .fillMaxSize()
                    .blur(24.dp)
            )
            // Configurable 40%-60% dark scrim overlay
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = wallpaperScrimAlpha))
            )
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(if (customWallpaperUri.isNullOrBlank()) OneUIDarkBackground else Color.Transparent)
        ) {
            // Samsung One UI Top Header
            if (isSelectionMode) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp)
                        .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.15f), RoundedCornerShape(16.dp))
                        .padding(horizontal = 6.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        IconButton(onClick = { clearSelection() }) {
                            Icon(Icons.Default.Close, contentDescription = "Cancel Selection", tint = OneUITextPrimary)
                        }
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = "${selectedSongIds.size} Selected",
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold,
                            color = OneUITextPrimary
                        )
                    }

                    Row(verticalAlignment = Alignment.CenterVertically) {
                        // Select All / Deselect All
                        IconButton(onClick = {
                            selectedSongIds = if (selectedSongIds.size == currentSectionTracks.size) {
                                emptySet()
                            } else {
                                currentSectionTracks.map { it.id }.toSet()
                            }
                        }) {
                            Icon(Icons.Default.SelectAll, contentDescription = "Select All", tint = OneUITextPrimary)
                        }

                        // Play Next
                        IconButton(onClick = {
                            val selectedList = currentSectionTracks.filter { selectedSongIds.contains(it.id) }
                            if (selectedList.isNotEmpty()) {
                                PlaybackManager.playNext(selectedList)
                                Toast.makeText(context, "Added ${selectedList.size} tracks to Play Next", Toast.LENGTH_SHORT).show()
                                clearSelection()
                            }
                        }) {
                            Icon(Icons.AutoMirrored.Filled.QueueMusic, contentDescription = "Play Next", tint = OneUITextPrimary)
                        }

                        // Add to Playlist
                        IconButton(onClick = {
                            if (selectedSongIds.isNotEmpty()) {
                                showBulkPlaylistDialog = true
                            }
                        }) {
                            Icon(Icons.Default.PlaylistAdd, contentDescription = "Add to Playlist", tint = OneUITextPrimary)
                        }

                        // Bulk Delete
                        IconButton(onClick = {
                            if (selectedSongIds.isNotEmpty()) {
                                showBulkDeleteConfirmDialog = true
                            }
                        }) {
                            Icon(Icons.Default.Delete, contentDescription = "Bulk Delete", tint = Color(0xFFFF5252))
                        }

                        // Play Selected
                        IconButton(onClick = {
                            val selectedList = currentSectionTracks.filter { selectedSongIds.contains(it.id) }
                            if (selectedList.isNotEmpty()) {
                                onSongSelected(selectedList.first(), selectedList)
                                clearSelection()
                            }
                        }) {
                            Icon(Icons.Default.PlayArrow, contentDescription = "Play Selected", tint = MaterialTheme.colorScheme.primary)
                        }
                    }
                }
            } else {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp, vertical = 14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column {
                        Text(
                            text = "Pulse Music",
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
                                tint = if (isSearchActive) MaterialTheme.colorScheme.primary else OneUITextPrimary
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
            }

            // Expandable Search Bar
            AnimatedVisibility(visible = isSearchActive) {
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    placeholder = { Text("Search title, artist, or album...", fontSize = 14.sp) },
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
                        .padding(horizontal = 16.dp, vertical = 6.dp)
                        .testTag("search_text_field")
                )
            }

            // Scrollable Automated Library Top Tabs
            val activeTabIdx = selectedTopTabIndex.coerceIn(0, allTabTitles.size - 1)
            ScrollableTabRow(
                selectedTabIndex = activeTabIdx,
                containerColor = Color.Transparent,
                contentColor = MaterialTheme.colorScheme.primary,
                edgePadding = 16.dp,
                indicator = { tabPositions ->
                    TabRowDefaults.SecondaryIndicator(
                        modifier = Modifier.tabIndicatorOffset(tabPositions[activeTabIdx]),
                        color = MaterialTheme.colorScheme.primary,
                        height = 3.dp
                    )
                },
                modifier = Modifier.fillMaxWidth()
            ) {
                allTabTitles.forEachIndexed { index, title ->
                    Tab(
                        selected = activeTabIdx == index,
                        onClick = { selectedTopTabIndex = index },
                        text = {
                            Text(
                                text = title,
                                fontSize = 14.sp,
                                fontWeight = if (activeTabIdx == index) FontWeight.Bold else FontWeight.Normal,
                                color = if (activeTabIdx == index) MaterialTheme.colorScheme.primary else OneUITextSecondary
                            )
                        },
                        modifier = Modifier.testTag("tab_${title.lowercase().replace(" ", "_")}")
                    )
                }
            }

            // Content Body
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
            ) {
                when (currentTabTitle) {
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
                    else -> TrackListWithFastScroll(
                        tracks = currentSectionTracks,
                        currentPlayingSongId = currentPlayingSongId,
                        isSelectionMode = isSelectionMode,
                        selectedSongIds = selectedSongIds,
                        onToggleSelection = { toggleSelection(it) },
                        onSetSongsSelected = { ids, sel -> setMultipleSongsSelected(ids, sel) },
                        onSongSelected = { song -> onSongSelected(song, currentSectionTracks) },
                        onToggleFavorite = onToggleFavorite,
                        onToggleSoftHide = onToggleSoftHide,
                        onDeleteFromStorage = onDeleteFromStorage,
                        onOpenAudioEditor = onOpenAudioEditor,
                        onAddToPlaylistClick = { songForPlaylistSelection = it },
                        onEditMetadata = { songForMetadataEdit = it }
                    )
                }
            }
        }

        // Universal Plus (+) Action FloatingActionButton
        FloatingActionButton(
            onClick = { showUniversalPlusSheet = true },
            containerColor = MaterialTheme.colorScheme.primary,
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

                // Create Playlist
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
                            .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.2f), CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(Icons.Default.PlaylistAdd, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                    }
                    Spacer(Modifier.width(16.dp))
                    Column {
                        Text("Create Playlist", fontSize = 16.sp, fontWeight = FontWeight.SemiBold, color = OneUITextPrimary)
                        Text("Build custom offline collections", fontSize = 12.sp, color = OneUITextSecondary)
                    }
                }

                // Import Local Audio via SAF
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
                            .background(Color(0xFF00E5FF).copy(alpha = 0.2f), CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(Icons.Default.CreateNewFolder, contentDescription = null, tint = Color(0xFF00E5FF))
                    }
                    Spacer(Modifier.width(16.dp))
                    Column {
                        Text("Import via SAF", fontSize = 16.sp, fontWeight = FontWeight.SemiBold, color = OneUITextPrimary)
                        Text("Pick audio files or storage directory directly", fontSize = 12.sp, color = OneUITextSecondary)
                    }
                }

                // Online Search
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
                            .background(Color(0xFFFFB300).copy(alpha = 0.2f), CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(Icons.Default.CloudDownload, contentDescription = null, tint = Color(0xFFFFB300))
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
                            focusedBorderColor = MaterialTheme.colorScheme.primary
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
                            Text("Create", color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
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
                                Icon(Icons.Default.PlaylistAdd, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
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
                        Text("Close", color = MaterialTheme.colorScheme.primary)
                    }
                }
            }
        }
    }

    // Bulk Add to Playlist Dialog
    if (showBulkPlaylistDialog) {
        val selectedSongsList = currentSectionTracks.filter { selectedSongIds.contains(it.id) }
        Dialog(onDismissRequest = { showBulkPlaylistDialog = false }) {
            Surface(
                shape = RoundedCornerShape(20.dp),
                color = OneUICardElevated,
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(20.dp)) {
                    Text(
                        text = "Add ${selectedSongsList.size} Songs to Playlist",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        color = OneUITextPrimary
                    )
                    Spacer(modifier = Modifier.height(12.dp))

                    LazyColumn(modifier = Modifier.height(240.dp)) {
                        items(playlists) { playlist ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        selectedSongsList.forEach { s ->
                                            onAddSongToPlaylist(playlist.playlistId, s.id)
                                        }
                                        Toast.makeText(context, "Added ${selectedSongsList.size} songs to ${playlist.name}", Toast.LENGTH_SHORT).show()
                                        showBulkPlaylistDialog = false
                                        clearSelection()
                                    }
                                    .padding(vertical = 10.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(Icons.Default.PlaylistAdd, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
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
                        onClick = { showBulkPlaylistDialog = false },
                        modifier = Modifier.align(Alignment.End)
                    ) {
                        Text("Cancel", color = MaterialTheme.colorScheme.primary)
                    }
                }
            }
        }
    }

    // Bulk Delete Confirmation Dialog
    if (showBulkDeleteConfirmDialog) {
        val selectedSongsList = currentSectionTracks.filter { selectedSongIds.contains(it.id) }
        AlertDialog(
            onDismissRequest = { showBulkDeleteConfirmDialog = false },
            title = {
                Text("Delete ${selectedSongsList.size} Songs?", color = OneUITextPrimary, fontWeight = FontWeight.Bold)
            },
            text = {
                Text("Are you sure you want to delete these songs from your storage?", color = OneUITextSecondary)
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        selectedSongsList.forEach { s ->
                            onDeleteFromStorage(s)
                        }
                        Toast.makeText(context, "Deleted ${selectedSongsList.size} songs", Toast.LENGTH_SHORT).show()
                        showBulkDeleteConfirmDialog = false
                        clearSelection()
                    }
                ) {
                    Text("Delete", color = Color(0xFFFF5252), fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showBulkDeleteConfirmDialog = false }) {
                    Text("Cancel", color = OneUITextSecondary)
                }
            },
            containerColor = OneUICardElevated
        )
    }

    // Permanent Tag & Title Metadata Editor Dialog (Room Override)
    songForMetadataEdit?.let { editingSong ->
        EditMetadataDialog(
            song = editingSong,
            onDismiss = { songForMetadataEdit = null },
            onSave = { title, artist, album, genre ->
                onSaveMetadataOverride(editingSong.id, title, artist, album, genre)
                songForMetadataEdit = null
            }
        )
    }
}

/**
 * Track list layout with pinned fast-scroll alphabet indexer on the right edge
 * and continuous drag-to-select multi-selection.
 */
@Composable
private fun TrackListWithFastScroll(
    tracks: List<SongEntity>,
    currentPlayingSongId: Long?,
    isSelectionMode: Boolean = false,
    selectedSongIds: Set<Long> = emptySet(),
    onToggleSelection: (Long) -> Unit = {},
    onSetSongsSelected: (Collection<Long>, Boolean) -> Unit = { _, _ -> },
    onSongSelected: (SongEntity) -> Unit,
    onToggleFavorite: (SongEntity) -> Unit,
    onToggleSoftHide: (SongEntity) -> Unit,
    onDeleteFromStorage: (SongEntity) -> Unit,
    onOpenAudioEditor: (SongEntity) -> Unit,
    onAddToPlaylistClick: (SongEntity) -> Unit,
    onEditMetadata: (SongEntity) -> Unit = {}
) {
    val listState = rememberLazyListState()
    var dragSelectMode by remember { mutableStateOf<Boolean?>(null) }
    var initialDragIndex by remember { mutableIntStateOf(-1) }

    val currentTracks by rememberUpdatedState(tracks)
    val currentSelectedIds by rememberUpdatedState(selectedSongIds)
    val currentOnToggle by rememberUpdatedState(onToggleSelection)
    val currentOnSetSongsSelected by rememberUpdatedState(onSetSongsSelected)

    if (tracks.isEmpty()) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text("No songs found in this section", color = OneUITextSecondary, fontSize = 14.sp)
        }
        return
    }

    Box(modifier = Modifier.fillMaxSize()) {
        LazyColumn(
            state = listState,
            modifier = Modifier
                .fillMaxSize()
                .padding(end = 28.dp)
                .pointerInput(Unit) {
                    detectDragGesturesAfterLongPress(
                        onDragStart = { offset ->
                            val hitItem = listState.layoutInfo.visibleItemsInfo.firstOrNull { item ->
                                offset.y.toInt() in item.offset..(item.offset + item.size)
                            }
                            if (hitItem != null && hitItem.index in currentTracks.indices) {
                                val song = currentTracks[hitItem.index]
                                val isCurrentlySelected = currentSelectedIds.contains(song.id)
                                val willSelect = !isCurrentlySelected
                                dragSelectMode = willSelect
                                initialDragIndex = hitItem.index
                                currentOnToggle(song.id)
                            }
                        },
                        onDrag = { change, _ ->
                            change.consume()
                            val hitItem = listState.layoutInfo.visibleItemsInfo.firstOrNull { item ->
                                change.position.y.toInt() in item.offset..(item.offset + item.size)
                            }
                            if (hitItem != null && hitItem.index in currentTracks.indices) {
                                val currentIndex = hitItem.index
                                val willSelect = dragSelectMode ?: true
                                val start = if (initialDragIndex >= 0) initialDragIndex else currentIndex
                                val minIdx = minOf(start, currentIndex)
                                val maxIdx = maxOf(start, currentIndex)
                                val idsToUpdate = (minIdx..maxIdx).mapNotNull { idx ->
                                    if (idx in currentTracks.indices) currentTracks[idx].id else null
                                }
                                currentOnSetSongsSelected(idsToUpdate, willSelect)
                            }
                        },
                        onDragEnd = {
                            dragSelectMode = null
                            initialDragIndex = -1
                        },
                        onDragCancel = {
                            dragSelectMode = null
                            initialDragIndex = -1
                        }
                    )
                },
            contentPadding = PaddingValues(bottom = 96.dp)
        ) {
            itemsIndexed(tracks, key = { _, s -> s.id }) { index, song ->
                TrackListItem(
                    index = index + 1,
                    song = song,
                    isCurrentPlaying = song.id == currentPlayingSongId,
                    isSelectionMode = isSelectionMode,
                    isSelected = selectedSongIds.contains(song.id),
                    onClick = {
                        if (isSelectionMode) {
                            onToggleSelection(song.id)
                        } else {
                            onSongSelected(song)
                        }
                    },
                    onLongClick = {
                        onToggleSelection(song.id)
                    },
                    onToggleFavorite = { onToggleFavorite(song) },
                    onToggleSoftHide = { onToggleSoftHide(song) },
                    onDeleteFromStorage = { onDeleteFromStorage(song) },
                    onOpenAudioEditor = { onOpenAudioEditor(song) },
                    onAddToPlaylistClick = { onAddToPlaylistClick(song) },
                    onEditMetadata = { onEditMetadata(song) }
                )
            }
        }

        // Pinned interactive fast-scroll alphabet indexer
        FastScrollAlphabetIndexer(
            tracks = tracks,
            listState = listState,
            modifier = Modifier.align(Alignment.CenterEnd)
        )
    }
}

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
                                .background(Color(0xFFFF2A6D).copy(alpha = 0.2f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Favorite,
                                contentDescription = null,
                                tint = Color(0xFFFF2A6D),
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
                            tint = MaterialTheme.colorScheme.primary
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
                            .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.2f), CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(Icons.Default.Add, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                    }
                    Spacer(Modifier.width(14.dp))
                    Column {
                        Text("Create New Playlist", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = OneUITextPrimary)
                        Text("Organize custom Punjabi, Hindi, and Marathi collections", fontSize = 12.sp, color = OneUITextSecondary)
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
                            tint = if (playlist.isAutoGenerated) MaterialTheme.colorScheme.primary else Color.White,
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
                        tint = MaterialTheme.colorScheme.primary
                    )
                }
            }
        }
    }
}

/**
 * Albums tab with Canonical Album Consolidation (merges duplicate split albums).
 */
@Composable
private fun AlbumsTabView(
    librarySongs: List<SongEntity>,
    onSongSelected: (SongEntity, List<SongEntity>) -> Unit
) {
    val albumGroups = remember(librarySongs) {
        librarySongs.groupBy { MetadataSanitizer.getCanonicalAlbum(it.album) }
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
        items(albumGroups.keys.toList()) { canonicalAlbumName ->
            val albumTracks = albumGroups[canonicalAlbumName] ?: emptyList()
            val representative = albumTracks.firstOrNull()

            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = OneUICardElevated),
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable {
                        representative?.let { onSongSelected(it, albumTracks) }
                    }
            ) {
                Column {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .aspectRatio(1f)
                            .background(OneUISurfaceDark)
                    ) {
                        val art = representative?.coverArtUrl ?: representative?.albumArtUri
                        if (!art.isNullOrBlank()) {
                            AsyncImage(
                                model = art,
                                contentDescription = canonicalAlbumName,
                                contentScale = ContentScale.Crop,
                                modifier = Modifier.fillMaxSize()
                            )
                        } else {
                            Icon(
                                imageVector = Icons.Default.Album,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.5f),
                                modifier = Modifier.size(54.dp).align(Alignment.Center)
                            )
                        }
                    }

                    Column(modifier = Modifier.padding(12.dp)) {
                        Text(
                            text = canonicalAlbumName,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold,
                            color = OneUITextPrimary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = "${albumTracks.size} tracks",
                            fontSize = 12.sp,
                            color = OneUITextSecondary
                        )
                    }
                }
            }
        }
    }
}

/**
 * Artists tab with Canonical Artist Consolidation (merges "feat.", "&", and casing duplicates).
 */
@Composable
private fun ArtistsTabView(
    librarySongs: List<SongEntity>,
    onSongSelected: (SongEntity, List<SongEntity>) -> Unit
) {
    val artistGroups = remember(librarySongs) {
        librarySongs.groupBy { MetadataSanitizer.getCanonicalArtist(it.artist) }
    }

    if (artistGroups.isEmpty()) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text("No artists found in offline storage", color = OneUITextSecondary)
        }
        return
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 96.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        items(artistGroups.keys.toList()) { canonicalArtist ->
            val tracks = artistGroups[canonicalArtist] ?: emptyList()
            val representative = tracks.firstOrNull()

            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = OneUICardElevated),
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable {
                        representative?.let { onSongSelected(it, tracks) }
                    }
            ) {
                Row(
                    modifier = Modifier.padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(52.dp)
                            .clip(CircleShape)
                            .background(OneUISurfaceDark),
                        contentAlignment = Alignment.Center
                    ) {
                        val art = representative?.coverArtUrl ?: representative?.albumArtUri
                        if (!art.isNullOrBlank()) {
                            AsyncImage(
                                model = art,
                                contentDescription = canonicalArtist,
                                contentScale = ContentScale.Crop,
                                modifier = Modifier.fillMaxSize()
                            )
                        } else {
                            Icon(
                                imageVector = Icons.Default.Person,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(28.dp)
                            )
                        }
                    }

                    Spacer(Modifier.width(16.dp))

                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = canonicalArtist,
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold,
                            color = OneUITextPrimary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Spacer(Modifier.height(2.dp))
                        Text(
                            text = "${tracks.size} songs",
                            fontSize = 12.sp,
                            color = OneUITextSecondary
                        )
                    }

                    Icon(
                        imageVector = Icons.Default.PlayArrow,
                        contentDescription = "Play Artist",
                        tint = MaterialTheme.colorScheme.primary
                    )
                }
            }
        }
    }
}

/**
 * Folders tab showing storage directory structure.
 */
@Composable
private fun FoldersTabView(
    librarySongs: List<SongEntity>,
    onSongSelected: (SongEntity, List<SongEntity>) -> Unit
) {
    val folderGroups = remember(librarySongs) {
        librarySongs.groupBy { song ->
            val path = song.dataPath ?: song.localPath
            if (!path.isNullOrBlank()) {
                File(path).parentFile?.name ?: "Music"
            } else {
                "Music"
            }
        }
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 96.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        items(folderGroups.keys.toList()) { folderName ->
            val tracks = folderGroups[folderName] ?: emptyList()
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = OneUICardElevated),
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable {
                        tracks.firstOrNull()?.let { onSongSelected(it, tracks) }
                    }
            ) {
                Row(
                    modifier = Modifier.padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(46.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .background(Color(0xFF2C3E50).copy(alpha = 0.5f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Folder,
                            contentDescription = null,
                            tint = Color(0xFFF39C12),
                            modifier = Modifier.size(24.dp)
                        )
                    }
                    Spacer(Modifier.width(16.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = folderName,
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold,
                            color = OneUITextPrimary
                        )
                        Text(
                            text = "${tracks.size} audio files",
                            fontSize = 12.sp,
                            color = OneUITextSecondary
                        )
                    }
                    Icon(
                        imageVector = Icons.Default.PlayArrow,
                        contentDescription = "Play Folder",
                        tint = MaterialTheme.colorScheme.primary
                    )
                }
            }
        }
    }
}
