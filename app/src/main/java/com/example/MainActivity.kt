package com.example

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.BottomSheetDefaults
import androidx.compose.material3.BottomSheetScaffold
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.SheetValue
import androidx.compose.material3.rememberBottomSheetScaffoldState
import androidx.compose.material3.rememberStandardBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.PlaylistEntity
import com.example.data.SongEntity
import com.example.playback.PlaybackManager
import com.example.ui.components.AudioEditorDialog
import com.example.ui.components.EnhancedPlayerSheet
import com.example.ui.components.MiniPlayerBar
import com.example.ui.components.OnlineSearchDialog
import com.example.ui.screens.LibraryScreen
import com.example.ui.screens.SettingsScreen
import com.example.ui.theme.MyApplicationTheme
import com.example.ui.theme.OneUIDarkBackground
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {

    @OptIn(ExperimentalMaterial3Api::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        setContent {
            MyApplicationTheme {
                val context = LocalContext.current
                val app = context.applicationContext as AuraMusicApp
                val repository = app.repository
                val coroutineScope = rememberCoroutineScope()

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
                val visibleTabs by app.settingsManager.visibleTabs.collectAsStateWithLifecycle()

                // Reactive Playback State
                val currentSong by PlaybackManager.currentSong.collectAsStateWithLifecycle()
                val isPlaying by PlaybackManager.isPlaying.collectAsStateWithLifecycle()
                val currentPositionMs by PlaybackManager.currentPositionMs.collectAsStateWithLifecycle()
                val durationMs by PlaybackManager.durationMs.collectAsStateWithLifecycle()
                val queue by PlaybackManager.queue.collectAsStateWithLifecycle()
                val currentQueueIndex by PlaybackManager.currentQueueIndex.collectAsStateWithLifecycle()
                val isShuffleEnabled by PlaybackManager.isShuffleEnabled.collectAsStateWithLifecycle()
                val repeatMode by PlaybackManager.repeatMode.collectAsStateWithLifecycle()
                val smartMoodQueueEnabled by PlaybackManager.smartMoodQueueEnabled.collectAsStateWithLifecycle()
                val downloadProgressMap by repository.downloadManager.downloadProgressMap.collectAsStateWithLifecycle()

                // Dialogs & Navigation
                var editingSongForTrim by remember { mutableStateOf<SongEntity?>(null) }
                var showOnlineSearchDialog by remember { mutableStateOf(false) }
                var showSettingsScreen by remember { mutableStateOf(false) }

                // Clean Back navigation: collapse expanded player or back from settings
                BackHandler(enabled = showSettingsScreen) {
                    showSettingsScreen = false
                }

                // Samsung One UI BottomSheetScaffold configuration
                val bottomSheetState = rememberStandardBottomSheetState(
                    initialValue = SheetValue.PartiallyExpanded,
                    skipHiddenState = false
                )
                val scaffoldState = rememberBottomSheetScaffoldState(bottomSheetState = bottomSheetState)

                val isExpanded = bottomSheetState.currentValue == SheetValue.Expanded
                BackHandler(enabled = isExpanded && !showSettingsScreen) {
                    coroutineScope.launch {
                        bottomSheetState.partialExpand()
                    }
                }

                val peekHeight = if (currentSong != null) 74.dp else 0.dp

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
                                    onToggleSmartMoodQueue = { PlaybackManager.toggleSmartMoodQueue() },
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
                                    settingsManager = app.settingsManager,
                                    onBack = { showSettingsScreen = false }
                                )
                            } else {
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
                                            val msg = if (newState) "Moved to Hidden Vault (Playlist only)" else "Restored to Library"
                                            Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
                                        }
                                    },
                                    onToggleFavorite = { targetSong ->
                                        coroutineScope.launch {
                                            repository.toggleFavorite(targetSong.id)
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
                                        coroutineScope.launch {
                                            app.database.musicDao().getSongsForPlaylist(selectedPlaylist.playlistId).collect { playlistSongs ->
                                                if (playlistSongs.isNotEmpty()) {
                                                    PlaybackManager.playSong(playlistSongs.first(), playlistSongs)
                                                } else {
                                                    Toast.makeText(context, "Playlist is empty", Toast.LENGTH_SHORT).show()
                                                }
                                            }
                                        }
                                    },
                                    onRequestSafImport = {
                                        safLauncher.launch(arrayOf("audio/*"))
                                    },
                                    onOpenOnlineSearch = {
                                        showOnlineSearchDialog = true
                                    },
                                    onOpenSettings = {
                                        showSettingsScreen = true
                                    },
                                    visibleTabs = visibleTabs
                                )
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

                // Online Music Search Dialog
                if (showOnlineSearchDialog) {
                    OnlineSearchDialog(
                        onDismiss = { showOnlineSearchDialog = false },
                        onPlayTrack = { song ->
                            coroutineScope.launch {
                                repository.insertOnlineTrack(song)
                                PlaybackManager.playSong(song, listOf(song))
                            }
                        },
                        onDownloadTrack = { song ->
                            coroutineScope.launch {
                                repository.downloadTrack(song) {
                                    Toast.makeText(context, "Downloaded ${song.title} offline", Toast.LENGTH_SHORT).show()
                                }
                            }
                        },
                        downloadProgressMap = downloadProgressMap
                    )
                }
            }
        }
    }
}
