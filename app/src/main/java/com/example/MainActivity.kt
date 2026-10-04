package com.example

import android.Manifest
import android.app.Activity
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.MediaStore
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Album
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material3.BottomSheetScaffold
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.SheetValue
import androidx.compose.material3.Text
import androidx.compose.material3.rememberBottomSheetScaffoldState
import androidx.compose.material3.rememberStandardBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.SongEntity
import com.example.playback.PlaybackManager
import com.example.ui.components.AudioEditorDialog
import androidx.compose.material.icons.filled.Radio
import com.example.ui.components.EnhancedPlayerSheet
import com.example.ui.components.MiniPlayerBar
import com.example.ui.components.SpotifyLoginDialog
import com.example.ui.screens.LibraryScreen
import com.example.ui.screens.OnlineExploreScreen
import com.example.ui.screens.PlaylistDetailScreen
import com.example.ui.screens.RadioScreen
import com.example.ui.screens.ServerSettingsScreen
import com.example.ui.screens.SettingsScreen
import com.example.ui.theme.OneUIDarkBackground
import com.example.ui.theme.OneUITextSecondary
import com.example.ui.theme.PulseMusicTheme
import kotlinx.coroutines.launch

enum class MainNavTab {
    LOCAL_LIBRARY,
    ONLINE_EXPLORE,
    RADIO
}

class MainActivity : ComponentActivity() {

    @OptIn(ExperimentalMaterial3Api::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        setContent {
            val context = LocalContext.current
            val app = context.applicationContext as PulseMusicApp
            val repository = app.repository
            val settingsManager = app.settingsManager
            val spotifyRepository = app.spotifyRepository
            val personalizationRepository = app.personalizationRepository
            val coroutineScope = rememberCoroutineScope()

            val currentThemePreset by settingsManager.themePreset.collectAsStateWithLifecycle()
            val customWallpaperUri by settingsManager.customWallpaperUri.collectAsStateWithLifecycle()
            val wallpaperScrimAlpha by settingsManager.wallpaperScrimAlpha.collectAsStateWithLifecycle()

            PulseMusicTheme(preset = currentThemePreset) {
                // Media Deletion tracking for scoped storage prompt
                var pendingDeleteSong by remember { mutableStateOf<SongEntity?>(null) }

                val deleteRequestLauncher = rememberLauncherForActivityResult(
                    ActivityResultContracts.StartIntentSenderForResult()
                ) { result ->
                    if (result.resultCode == Activity.RESULT_OK) {
                        pendingDeleteSong?.let { song ->
                            coroutineScope.launch {
                                repository.deleteSongPermanently(song.id)
                                Toast.makeText(context, "Deleted permanently from storage", Toast.LENGTH_SHORT).show()
                                pendingDeleteSong = null
                            }
                        }
                    } else {
                        pendingDeleteSong = null
                    }
                }

                // SAF Import launcher for local audio files
                val safLauncher = rememberLauncherForActivityResult(
                    ActivityResultContracts.OpenMultipleDocuments()
                ) { uris ->
                    if (uris.isNotEmpty()) {
                        coroutineScope.launch {
                            val count = repository.importSafUris(uris)
                            Toast.makeText(context, "Imported $count audio files via SAF", Toast.LENGTH_SHORT).show()
                        }
                    }
                }

                // Permission launcher for Media & Notifications
                val permissionLauncher = rememberLauncherForActivityResult(
                    ActivityResultContracts.RequestMultiplePermissions()
                ) { permissions ->
                    val audioGranted = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                        permissions[Manifest.permission.READ_MEDIA_AUDIO] == true
                    } else {
                        permissions[Manifest.permission.READ_EXTERNAL_STORAGE] == true
                    }

                    if (audioGranted) {
                        coroutineScope.launch {
                            val count = repository.scanLocalAudioLibrary()
                            Toast.makeText(context, "Loaded $count songs offline", Toast.LENGTH_SHORT).show()
                        }
                    }
                }

                LaunchedEffect(Unit) {
                    val permissionsToRequest = mutableListOf<String>()
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                        if (ContextCompat.checkSelfPermission(context, Manifest.permission.READ_MEDIA_AUDIO) != PackageManager.PERMISSION_GRANTED) {
                            permissionsToRequest.add(Manifest.permission.READ_MEDIA_AUDIO)
                        }
                        if (ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                            permissionsToRequest.add(Manifest.permission.POST_NOTIFICATIONS)
                        }
                    } else {
                        if (ContextCompat.checkSelfPermission(context, Manifest.permission.READ_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
                            permissionsToRequest.add(Manifest.permission.READ_EXTERNAL_STORAGE)
                        }
                    }

                    if (permissionsToRequest.isNotEmpty()) {
                        permissionLauncher.launch(permissionsToRequest.toTypedArray())
                    }

                    // Scan MediaStore on start
                    repository.scanLocalAudioLibrary()
                }

                // Data Observation from Room
                val librarySongs by repository.librarySongs.collectAsStateWithLifecycle(initialValue = emptyList())
                val hiddenVaultSongs by repository.hiddenVaultSongs.collectAsStateWithLifecycle(initialValue = emptyList())
                val playlists by repository.playlists.collectAsStateWithLifecycle(initialValue = emptyList())
                val favoriteSongs by repository.favoriteSongs.collectAsStateWithLifecycle(initialValue = emptyList())

                // Reactive Playback State
                val currentSong by PlaybackManager.currentSong.collectAsStateWithLifecycle()
                val isPlaying by PlaybackManager.isPlaying.collectAsStateWithLifecycle()
                val currentPositionMs by PlaybackManager.currentPositionMs.collectAsStateWithLifecycle()
                val durationMs by PlaybackManager.durationMs.collectAsStateWithLifecycle()
                val queue by PlaybackManager.queue.collectAsStateWithLifecycle()
                val currentQueueIndex by PlaybackManager.currentQueueIndex.collectAsStateWithLifecycle()
                val isShuffleEnabled by PlaybackManager.isShuffleEnabled.collectAsStateWithLifecycle()
                val repeatMode by PlaybackManager.repeatMode.collectAsStateWithLifecycle()
                val smartMoodQueueEnabled by PlaybackManager.languageLockedQueueEnabled.collectAsStateWithLifecycle()
                val downloadProgressMap by repository.downloadManager.downloadProgressMap.collectAsStateWithLifecycle()

                // Dialogs & Navigation State
                var currentNavTab by remember { mutableStateOf(MainNavTab.LOCAL_LIBRARY) }
                var activePlaylistId by remember { mutableStateOf<Long?>(null) }
                var editingSongForTrim by remember { mutableStateOf<SongEntity?>(null) }
                var showSettingsScreen by remember { mutableStateOf(false) }
                var showServerSettingsScreen by remember { mutableStateOf(false) }
                var showSpotifyLoginDialog by remember { mutableStateOf(false) }
                var exploreRefreshTrigger by remember { mutableLongStateOf(0L) }

                BackHandler(enabled = activePlaylistId != null) {
                    activePlaylistId = null
                }

                BackHandler(enabled = showSettingsScreen) {
                    showSettingsScreen = false
                }

                BackHandler(enabled = showServerSettingsScreen) {
                    showServerSettingsScreen = false
                }

                BackHandler(enabled = !showSettingsScreen && !showServerSettingsScreen && activePlaylistId == null && currentNavTab != MainNavTab.LOCAL_LIBRARY) {
                    currentNavTab = MainNavTab.LOCAL_LIBRARY
                }

                // Samsung One UI BottomSheetScaffold configuration
                val bottomSheetState = rememberStandardBottomSheetState(
                    initialValue = SheetValue.PartiallyExpanded,
                    skipHiddenState = false
                )
                val scaffoldState = rememberBottomSheetScaffoldState(bottomSheetState = bottomSheetState)

                val isExpanded = bottomSheetState.currentValue == SheetValue.Expanded
                BackHandler(enabled = isExpanded && !showSettingsScreen && !showServerSettingsScreen) {
                    coroutineScope.launch {
                        bottomSheetState.partialExpand()
                    }
                }

                // Peek height calculates space for MiniPlayerBar (74.dp) + NavigationBar (64.dp)
                val peekHeight = if (currentSong != null) 138.dp else 64.dp

                BottomSheetScaffold(
                    scaffoldState = scaffoldState,
                    sheetPeekHeight = peekHeight,
                    sheetShape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
                    sheetContainerColor = OneUIDarkBackground,
                    sheetDragHandle = null,
                    sheetContent = {
                        Box(modifier = Modifier.fillMaxSize()) {
                            if (isExpanded) {
                                EnhancedPlayerSheet(
                                    song = currentSong,
                                    isPlaying = isPlaying,
                                    currentPositionMs = currentPositionMs,
                                    durationMs = durationMs,
                                    isShuffleEnabled = isShuffleEnabled,
                                    repeatMode = repeatMode,
                                    queue = queue,
                                    currentQueueIndex = currentQueueIndex,
                                    smartMoodQueueEnabled = smartMoodQueueEnabled,
                                    onPlayPauseClick = { PlaybackManager.playPause() },
                                    onNextClick = { PlaybackManager.playNext() },
                                    onPreviousClick = { PlaybackManager.playPrevious() },
                                    onSeekTo = { pos -> PlaybackManager.seekTo(pos) },
                                    onToggleShuffle = { PlaybackManager.toggleShuffle() },
                                    onToggleRepeat = { PlaybackManager.toggleRepeat() },
                                    onToggleSmartMoodQueue = { PlaybackManager.toggleLanguageLockedQueue() },
                                    onQueueSongClick = { idx -> PlaybackManager.playAtIndex(idx) },
                                    onMoveQueueItem = { from, to -> PlaybackManager.reorderQueue(from, to) },
                                    onRemoveQueueItem = { idx -> PlaybackManager.removeFromQueue(idx) },
                                    onToggleSoftHide = { target ->
                                        coroutineScope.launch {
                                            repository.setSongHidden(target.id, !target.isHiddenFromLibrary)
                                        }
                                    },
                                    onToggleFavorite = { target ->
                                        coroutineScope.launch {
                                            repository.toggleFavorite(target.id)
                                        }
                                    },
                                    onOpenAudioEditor = { target -> editingSongForTrim = target },
                                    onSaveLyrics = { target, newLrc ->
                                        coroutineScope.launch {
                                            repository.updateLyrics(target.id, newLrc)
                                        }
                                    },
                                    onDownloadTrack = { target ->
                                        coroutineScope.launch {
                                            repository.downloadTrack(target) {
                                                Toast.makeText(context, "Downloaded ${target.title} offline", Toast.LENGTH_SHORT).show()
                                            }
                                        }
                                    },
                                    onCollapse = {
                                        coroutineScope.launch { bottomSheetState.partialExpand() }
                                    }
                                )
                            } else {
                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .background(OneUIDarkBackground)
                                ) {
                                    if (currentSong != null) {
                                        MiniPlayerBar(
                                            song = currentSong,
                                            isPlaying = isPlaying,
                                            currentPositionMs = currentPositionMs,
                                            durationMs = durationMs,
                                            onPlayPauseClick = { PlaybackManager.playPause() },
                                            onNextClick = { PlaybackManager.playNext() },
                                            onToggleFavorite = {
                                                currentSong?.let { target ->
                                                    coroutineScope.launch {
                                                        repository.toggleFavorite(target.id)
                                                    }
                                                }
                                            },
                                            onClick = {
                                                coroutineScope.launch { bottomSheetState.expand() }
                                            }
                                        )
                                    }

                                    // Main Dual-Mode Bottom Navigation Bar (Local Library vs Online Explore)
                                    NavigationBar(
                                        containerColor = OneUIDarkBackground,
                                        contentColor = MaterialTheme.colorScheme.primary,
                                        tonalElevation = 8.dp,
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .height(64.dp)
                                            .testTag("main_bottom_nav_bar")
                                    ) {
                                        NavigationBarItem(
                                            selected = currentNavTab == MainNavTab.LOCAL_LIBRARY && !showSettingsScreen,
                                            onClick = {
                                                currentNavTab = MainNavTab.LOCAL_LIBRARY
                                                showSettingsScreen = false
                                            },
                                            icon = {
                                                Icon(Icons.Default.Album, contentDescription = "Local Library")
                                            },
                                            label = {
                                                Text(
                                                    text = "Local Library",
                                                    fontSize = 11.sp,
                                                    fontWeight = FontWeight.SemiBold
                                                )
                                            },
                                            colors = NavigationBarItemDefaults.colors(
                                                selectedIconColor = Color.Black,
                                                selectedTextColor = MaterialTheme.colorScheme.primary,
                                                indicatorColor = MaterialTheme.colorScheme.primary,
                                                unselectedIconColor = OneUITextSecondary,
                                                unselectedTextColor = OneUITextSecondary
                                            ),
                                            modifier = Modifier.testTag("tab_local_library")
                                        )

                                        NavigationBarItem(
                                            selected = currentNavTab == MainNavTab.ONLINE_EXPLORE && !showSettingsScreen && activePlaylistId == null,
                                            onClick = {
                                                currentNavTab = MainNavTab.ONLINE_EXPLORE
                                                showSettingsScreen = false
                                                activePlaylistId = null
                                            },
                                            icon = {
                                                Icon(Icons.Default.CloudDownload, contentDescription = "Online Explore")
                                            },
                                            label = {
                                                Text(
                                                    text = "Online Explore",
                                                    fontSize = 11.sp,
                                                    fontWeight = FontWeight.SemiBold
                                                )
                                            },
                                            colors = NavigationBarItemDefaults.colors(
                                                selectedIconColor = Color.Black,
                                                selectedTextColor = MaterialTheme.colorScheme.primary,
                                                indicatorColor = MaterialTheme.colorScheme.primary,
                                                unselectedIconColor = OneUITextSecondary,
                                                unselectedTextColor = OneUITextSecondary
                                            ),
                                            modifier = Modifier.testTag("tab_online_explore")
                                        )

                                        NavigationBarItem(
                                            selected = currentNavTab == MainNavTab.RADIO && !showSettingsScreen && activePlaylistId == null,
                                            onClick = {
                                                currentNavTab = MainNavTab.RADIO
                                                showSettingsScreen = false
                                                activePlaylistId = null
                                            },
                                            icon = {
                                                Icon(Icons.Default.Radio, contentDescription = "Radio")
                                            },
                                            label = {
                                                Text(
                                                    text = "Radio",
                                                    fontSize = 11.sp,
                                                    fontWeight = FontWeight.SemiBold
                                                )
                                            },
                                            colors = NavigationBarItemDefaults.colors(
                                                selectedIconColor = Color.Black,
                                                selectedTextColor = MaterialTheme.colorScheme.primary,
                                                indicatorColor = MaterialTheme.colorScheme.primary,
                                                unselectedIconColor = OneUITextSecondary,
                                                unselectedTextColor = OneUITextSecondary
                                            ),
                                            modifier = Modifier.testTag("tab_radio")
                                        )
                                    }
                                }
                            }
                        }
                    },
                    content = { paddingValues ->
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .background(OneUIDarkBackground)
                                .padding(paddingValues)
                        ) {
                            if (showSettingsScreen) {
                                SettingsScreen(
                                    settingsManager = settingsManager,
                                    onBack = { showSettingsScreen = false }
                                )
                            } else if (showServerSettingsScreen) {
                                ServerSettingsScreen(
                                    settingsManager = settingsManager,
                                    onBack = { showServerSettingsScreen = false }
                                )
                            } else if (activePlaylistId != null) {
                                PlaylistDetailScreen(
                                    playlistId = activePlaylistId!!,
                                    repository = repository,
                                    onBack = { activePlaylistId = null }
                                )
                            } else {
                                when (currentNavTab) {
                                    MainNavTab.LOCAL_LIBRARY -> {
                                        LibraryScreen(
                                            librarySongs = librarySongs,
                                            hiddenVaultSongs = hiddenVaultSongs,
                                            favoriteSongs = favoriteSongs,
                                            playlists = playlists,
                                            currentPlayingSongId = currentSong?.id,
                                            onSongSelected = { song, playlistContext ->
                                                PlaybackManager.playSong(song, playlistContext)
                                            },
                                            onRescanRequested = {
                                                coroutineScope.launch {
                                                    val count = repository.scanLocalAudioLibrary()
                                                    Toast.makeText(context, "Scanned $count offline songs", Toast.LENGTH_SHORT).show()
                                                }
                                            },
                                            onToggleSoftHide = { targetSong ->
                                                coroutineScope.launch {
                                                    val newState = !targetSong.isHiddenFromLibrary
                                                    repository.setSongHidden(targetSong.id, newState)
                                                    val msg = if (newState) "Removed from App (Hidden)" else "Restored to Library"
                                                    Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
                                                }
                                            },
                                            onToggleFavorite = { targetSong ->
                                                coroutineScope.launch {
                                                    repository.toggleFavorite(targetSong.id)
                                                }
                                            },
                                            onDeleteFromStorage = { targetSong ->
                                                pendingDeleteSong = targetSong
                                                try {
                                                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                                                        val uriList = listOf(Uri.parse(targetSong.contentUri))
                                                        val deleteRequest = MediaStore.createDeleteRequest(context.contentResolver, uriList)
                                                        val request = IntentSenderRequest.Builder(deleteRequest.intentSender).build()
                                                        deleteRequestLauncher.launch(request)
                                                    } else {
                                                        context.contentResolver.delete(Uri.parse(targetSong.contentUri), null, null)
                                                        coroutineScope.launch {
                                                            repository.deleteSongPermanently(targetSong.id)
                                                            Toast.makeText(context, "Deleted from storage", Toast.LENGTH_SHORT).show()
                                                        }
                                                    }
                                                } catch (e: Exception) {
                                                    coroutineScope.launch {
                                                        repository.deleteSongPermanently(targetSong.id)
                                                        Toast.makeText(context, "Deleted track from app", Toast.LENGTH_SHORT).show()
                                                    }
                                                }
                                            },
                                            onOpenAudioEditor = { targetSong ->
                                                editingSongForTrim = targetSong
                                            },
                                            onCreatePlaylist = { name ->
                                                coroutineScope.launch {
                                                    repository.createPlaylist(name)
                                                    Toast.makeText(context, "Playlist '$name' created", Toast.LENGTH_SHORT).show()
                                                }
                                            },
                                            onAddSongToPlaylist = { playlistId, songId ->
                                                coroutineScope.launch {
                                                    repository.addSongToPlaylist(playlistId, songId)
                                                    Toast.makeText(context, "Added to playlist", Toast.LENGTH_SHORT).show()
                                                }
                                            },
                                            onSelectPlaylist = { selectedPlaylist ->
                                                activePlaylistId = selectedPlaylist.playlistId
                                            },
                                            onRequestSafImport = {
                                                safLauncher.launch(arrayOf("audio/*"))
                                            },
                                            onOpenOnlineSearch = {
                                                currentNavTab = MainNavTab.ONLINE_EXPLORE
                                            },
                                            onOpenSettings = {
                                                showSettingsScreen = true
                                            },
                                            onSaveMetadataOverride = { songId, title, artist, album, genre ->
                                                coroutineScope.launch {
                                                    repository.saveSongMetadataOverride(songId, title, artist, album, genre)
                                                    Toast.makeText(context, "Saved metadata override permanently", Toast.LENGTH_SHORT).show()
                                                }
                                            },
                                            customWallpaperUri = customWallpaperUri,
                                            wallpaperScrimAlpha = wallpaperScrimAlpha
                                        )
                                    }
                                    MainNavTab.ONLINE_EXPLORE -> {
                                        OnlineExploreScreen(
                                            personalizationRepository = personalizationRepository,
                                            currentPlayingSongId = currentSong?.id,
                                            onPlayTrack = { song, playlistContext ->
                                                coroutineScope.launch {
                                                    repository.insertOnlineTrack(song)
                                                    PlaybackManager.playSong(song, playlistContext)
                                                }
                                            },
                                            onDownloadTrack = { song ->
                                                coroutineScope.launch {
                                                    repository.downloadTrack(song) {
                                                        Toast.makeText(context, "Downloaded ${song.title} to offline library", Toast.LENGTH_SHORT).show()
                                                    }
                                                }
                                            },
                                            downloadProgressMap = downloadProgressMap,
                                            onOpenSpotifySync = { showSpotifyLoginDialog = true },
                                            onOpenServerSettings = { showServerSettingsScreen = true },
                                            refreshTrigger = exploreRefreshTrigger
                                        )
                                    }
                                    MainNavTab.RADIO -> {
                                        RadioScreen()
                                    }
                                }
                            }
                        }
                    }
                )

                // Lossless Audio Cutter Dialog
                editingSongForTrim?.let { songToCut ->
                    AudioEditorDialog(
                        song = songToCut,
                        onDismiss = { editingSongForTrim = null }
                    )
                }

                // Zero-Client-ID Spotify Login & Sync Sheet
                if (showSpotifyLoginDialog) {
                    SpotifyLoginDialog(
                        spotifyRepository = spotifyRepository,
                        onDismiss = { showSpotifyLoginDialog = false },
                        onImportCompleted = { result ->
                            exploreRefreshTrigger = System.currentTimeMillis()
                            Toast.makeText(
                                context,
                                "Synced ${result.likedSongsCount} liked tracks & ${result.playlistsCount} playlists from Spotify!",
                                Toast.LENGTH_LONG
                            ).show()
                        }
                    )
                }
            }
        }
    }
}
