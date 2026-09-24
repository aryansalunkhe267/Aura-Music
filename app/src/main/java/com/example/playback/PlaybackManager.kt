package com.example.playback

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.util.Log
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import com.example.data.MusicRepository
import com.example.data.SongEntity
import com.example.engine.AudioMoodClassifier
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.util.Collections

enum class RepeatMode {
    OFF, ALL, ONE
}

object PlaybackManager {

    private const val TAG = "PlaybackManager"

    private lateinit var appContext: Context
    private lateinit var repository: MusicRepository
    private val scope = CoroutineScope(Dispatchers.Main + Job())

    // Internal ExoPlayer reference
    private var exoPlayer: ExoPlayer? = null
    private var progressJob: Job? = null

    // Exposed reactive state flows
    private val _currentSong = MutableStateFlow<SongEntity?>(null)
    val currentSong: StateFlow<SongEntity?> = _currentSong.asStateFlow()

    private val _isPlaying = MutableStateFlow(false)
    val isPlaying: StateFlow<Boolean> = _isPlaying.asStateFlow()

    private val _isBuffering = MutableStateFlow(false)
    val isBuffering: StateFlow<Boolean> = _isBuffering.asStateFlow()

    private val _currentPositionMs = MutableStateFlow(0L)
    val currentPositionMs: StateFlow<Long> = _currentPositionMs.asStateFlow()

    private val _durationMs = MutableStateFlow(0L)
    val durationMs: StateFlow<Long> = _durationMs.asStateFlow()

    private val _queue = MutableStateFlow<List<SongEntity>>(emptyList())
    val queue: StateFlow<List<SongEntity>> = _queue.asStateFlow()

    private val _currentQueueIndex = MutableStateFlow(-1)
    val currentQueueIndex: StateFlow<Int> = _currentQueueIndex.asStateFlow()

    private val _isShuffleEnabled = MutableStateFlow(false)
    val isShuffleEnabled: StateFlow<Boolean> = _isShuffleEnabled.asStateFlow()

    private val _repeatMode = MutableStateFlow(RepeatMode.ALL)
    val repeatMode: StateFlow<RepeatMode> = _repeatMode.asStateFlow()

    private val _smartMoodQueueEnabled = MutableStateFlow(true)
    val smartMoodQueueEnabled: StateFlow<Boolean> = _smartMoodQueueEnabled.asStateFlow()

    fun initialize(context: Context, repo: MusicRepository) {
        appContext = context.applicationContext
        repository = repo
    }

    /**
     * Attaches the ExoPlayer instance from MusicService to this manager.
     */
    fun attachPlayer(player: ExoPlayer) {
        exoPlayer = player
        player.addListener(object : Player.Listener {
            override fun onIsPlayingChanged(isPlayingNow: Boolean) {
                _isPlaying.value = isPlayingNow
                if (isPlayingNow) {
                    startProgressTracker()
                } else {
                    stopProgressTracker()
                }
            }

            override fun onPlaybackStateChanged(playbackState: Int) {
                when (playbackState) {
                    Player.STATE_BUFFERING -> _isBuffering.value = true
                    Player.STATE_READY -> {
                        _isBuffering.value = false
                        val dur = player.duration
                        if (dur > 0) {
                            _durationMs.value = dur
                        }
                    }
                    Player.STATE_ENDED -> {
                        _isBuffering.value = false
                        handleSongCompletion()
                    }
                    Player.STATE_IDLE -> {
                        _isBuffering.value = false
                    }
                }
            }

            override fun onPositionDiscontinuity(
                oldPosition: Player.PositionInfo,
                newPosition: Player.PositionInfo,
                reason: Int
            ) {
                _currentPositionMs.value = newPosition.positionMs
            }
        })
    }

    fun detachPlayer() {
        stopProgressTracker()
        exoPlayer = null
    }

    fun getPlayer(): ExoPlayer? = exoPlayer

    val audioSessionId: Int
        get() = exoPlayer?.audioSessionId ?: C.AUDIO_SESSION_ID_UNSET

    private fun ensureServiceRunning() {
        try {
            val intent = Intent(appContext, MusicService::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                appContext.startForegroundService(intent)
            } else {
                appContext.startService(intent)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to start MusicService: ${e.message}")
        }
    }

    fun playSong(song: SongEntity, newQueue: List<SongEntity> = listOf(song)) {
        ensureServiceRunning()

        val index = newQueue.indexOfFirst { it.id == song.id }.let { if (it >= 0) it else 0 }
        _queue.value = newQueue
        _currentQueueIndex.value = index
        _currentSong.value = song
        _durationMs.value = song.durationMs
        _currentPositionMs.value = 0L

        prepareAndPlay(song)
        checkAndExpandSmartMoodQueue()
    }

    fun playPause() {
        val player = exoPlayer ?: return
        if (player.isPlaying) {
            player.pause()
        } else {
            if (player.playbackState == Player.STATE_ENDED) {
                player.seekTo(0)
            }
            player.play()
        }
    }

    fun playNext() {
        val currentIdx = _currentQueueIndex.value
        val currentList = _queue.value
        if (currentList.isEmpty()) return

        if (_isShuffleEnabled.value) {
            val nextIdx = (currentList.indices).filter { it != currentIdx }.randomOrNull() ?: 0
            playAtIndex(nextIdx)
            return
        }

        if (currentIdx + 1 < currentList.size) {
            playAtIndex(currentIdx + 1)
        } else if (_repeatMode.value == RepeatMode.ALL) {
            playAtIndex(0)
        }
    }

    fun playPrevious() {
        val player = exoPlayer
        if (player != null && player.currentPosition > 3000L) {
            // Samsung Music standard: if song played > 3 seconds, previous rewinds to start
            player.seekTo(0)
            _currentPositionMs.value = 0L
            return
        }

        val currentIdx = _currentQueueIndex.value
        val currentList = _queue.value
        if (currentList.isEmpty()) return

        if (currentIdx - 1 >= 0) {
            playAtIndex(currentIdx - 1)
        } else if (_repeatMode.value == RepeatMode.ALL) {
            playAtIndex(currentList.size - 1)
        }
    }

    fun playAtIndex(index: Int) {
        val list = _queue.value
        if (index in list.indices) {
            _currentQueueIndex.value = index
            val song = list[index]
            _currentSong.value = song
            _durationMs.value = song.durationMs
            _currentPositionMs.value = 0L
            prepareAndPlay(song)
            checkAndExpandSmartMoodQueue()
        }
    }

    fun seekTo(positionMs: Long) {
        val player = exoPlayer ?: return
        val clamped = positionMs.coerceIn(0L, _durationMs.value.coerceAtLeast(1L))
        player.seekTo(clamped)
        _currentPositionMs.value = clamped
    }

    fun toggleShuffle() {
        _isShuffleEnabled.value = !_isShuffleEnabled.value
    }

    fun toggleRepeat() {
        _repeatMode.value = when (_repeatMode.value) {
            RepeatMode.OFF -> RepeatMode.ALL
            RepeatMode.ALL -> RepeatMode.ONE
            RepeatMode.ONE -> RepeatMode.OFF
        }
    }

    fun toggleSmartMoodQueue() {
        _smartMoodQueueEnabled.value = !_smartMoodQueueEnabled.value
        if (_smartMoodQueueEnabled.value) {
            checkAndExpandSmartMoodQueue()
        }
    }

    fun addToQueue(song: SongEntity) {
        val updated = _queue.value.toMutableList()
        updated.add(song)
        _queue.value = updated
    }

    fun reorderQueue(fromIndex: Int, toIndex: Int) {
        val list = _queue.value.toMutableList()
        if (fromIndex in list.indices && toIndex in list.indices) {
            val moved = list.removeAt(fromIndex)
            list.add(toIndex, moved)
            _queue.value = list

            // Adjust current index if affected
            val currentIdx = _currentQueueIndex.value
            if (currentIdx == fromIndex) {
                _currentQueueIndex.value = toIndex
            } else if (fromIndex < currentIdx && toIndex >= currentIdx) {
                _currentQueueIndex.value = currentIdx - 1
            } else if (fromIndex > currentIdx && toIndex <= currentIdx) {
                _currentQueueIndex.value = currentIdx + 1
            }
        }
    }

    fun removeFromQueue(index: Int) {
        val list = _queue.value.toMutableList()
        if (index in list.indices) {
            list.removeAt(index)
            _queue.value = list

            val currentIdx = _currentQueueIndex.value
            if (index < currentIdx) {
                _currentQueueIndex.value = currentIdx - 1
            } else if (index == currentIdx) {
                if (list.isNotEmpty()) {
                    val nextIdx = index.coerceAtMost(list.size - 1)
                    playAtIndex(nextIdx)
                } else {
                    _currentSong.value = null
                    _currentQueueIndex.value = -1
                    exoPlayer?.stop()
                }
            }
        }
    }

    private fun prepareAndPlay(song: SongEntity) {
        val player = exoPlayer ?: return
        val playUri = when {
            song.isDownloaded && !song.localPath.isNullOrBlank() && java.io.File(song.localPath).exists() -> {
                Uri.fromFile(java.io.File(song.localPath))
            }
            !song.streamUrl.isNullOrBlank() -> Uri.parse(song.streamUrl)
            else -> Uri.parse(song.contentUri)
        }

        val metadata = MediaMetadata.Builder()
            .setTitle(song.title)
            .setArtist(song.artist)
            .setAlbumTitle(song.album)
            .setArtworkUri(song.albumArtUri?.let { Uri.parse(it) })
            .build()

        val mediaItem = MediaItem.Builder()
            .setMediaId(song.id.toString())
            .setUri(playUri)
            .setMediaMetadata(metadata)
            .build()

        player.setMediaItem(mediaItem)
        player.prepare()
        player.play()
    }

    private fun handleSongCompletion() {
        if (_repeatMode.value == RepeatMode.ONE) {
            seekTo(0)
            exoPlayer?.play()
            return
        }
        playNext()
    }

    /**
     * Smart Mood Queue Engine:
     * When current queue nears completion (remaining <= 2 tracks),
     * automatically queries offline songs matching the mood score of the current track
     * and appends them dynamically!
     */
    private fun checkAndExpandSmartMoodQueue() {
        if (!_smartMoodQueueEnabled.value) return
        val currentSong = _currentSong.value ?: return
        val queueList = _queue.value
        val currentIndex = _currentQueueIndex.value

        val remainingTracks = (queueList.size - 1) - currentIndex
        if (remainingTracks <= 2) {
            scope.launch {
                val allSongs = repository.librarySongs.firstOrNull() ?: emptyList()
                val queueIds = queueList.map { it.id }.toSet()
                val recommendations = AudioMoodClassifier.findMatchingMoodSongs(
                    currentSong = currentSong,
                    allCandidates = allSongs,
                    currentQueueIds = queueIds,
                    limit = 4
                )
                if (recommendations.isNotEmpty()) {
                    val updated = _queue.value.toMutableList()
                    updated.addAll(recommendations)
                    _queue.value = updated
                    Log.d(TAG, "Smart Mood Queue added ${recommendations.size} matching tracks!")
                }
            }
        }
    }

    private fun startProgressTracker() {
        progressJob?.cancel()
        progressJob = scope.launch {
            while (isActive) {
                exoPlayer?.let { player ->
                    _currentPositionMs.value = player.currentPosition
                    if (player.duration > 0) {
                        _durationMs.value = player.duration
                    }
                }
                delay(100L) // 100ms refresh for ultra-smooth scrub and synced lyrics
            }
        }
    }

    private fun stopProgressTracker() {
        progressJob?.cancel()
        progressJob = null
    }
}
