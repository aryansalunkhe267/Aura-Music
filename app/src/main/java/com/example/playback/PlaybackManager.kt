package com.example.playback

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.util.Log
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import com.example.api.JioSaavnApiClient
import com.example.data.MusicRepository
import com.example.data.PersonalizationRepository
import com.example.data.SongEntity
import com.example.engine.AudioMoodClassifier
import com.example.engine.SmartCategorizer
import com.example.widget.PulseMusicWidget
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
import java.io.File

enum class RepeatMode {
    OFF, ALL, ONE
}

object PlaybackManager {

    private const val TAG = "PlaybackManager"

    private lateinit var appContext: Context
    private lateinit var repository: MusicRepository
    private var personalizationRepository: PersonalizationRepository? = null

    private val mainScope = CoroutineScope(Dispatchers.Main + Job())
    private val mainHandler = Handler(Looper.getMainLooper())

    // Internal ExoPlayer reference
    private var exoPlayer: ExoPlayer? = null
    private var progressJob: Job? = null
    private var pendingPlaySong: SongEntity? = null
    private var pendingPlayQueue: List<SongEntity>? = null

    // Listen tracking for personalization (>= 30 seconds logged)
    private var trackPlayStartTimeMs = 0L
    private var trackAccumulatedPlayMs = 0L
    private var trackedSong: SongEntity? = null

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

    private val _languageLockedQueueEnabled = MutableStateFlow(true)
    val languageLockedQueueEnabled: StateFlow<Boolean> = _languageLockedQueueEnabled.asStateFlow()

    fun initialize(
        context: Context,
        repo: MusicRepository,
        personalizationRepo: PersonalizationRepository? = null
    ) {
        appContext = context.applicationContext
        repository = repo
        personalizationRepository = personalizationRepo
    }

    /**
     * Attaches the persistent ExoPlayer instance from MusicService.
     */
    fun attachPlayer(player: ExoPlayer) {
        exoPlayer = player

        player.addListener(object : Player.Listener {
            override fun onIsPlayingChanged(isPlayingNow: Boolean) {
                _isPlaying.value = isPlayingNow
                if (isPlayingNow) {
                    trackPlayStartTimeMs = System.currentTimeMillis()
                    startProgressTracker()
                } else {
                    recordCurrentTrackListenTime()
                    stopProgressTracker()
                }
                PulseMusicWidget.notifyWidgetUpdate(appContext)
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
                        recordCurrentTrackListenTime()
                        handleSongCompletion()
                    }
                    Player.STATE_IDLE -> {
                        _isBuffering.value = false
                    }
                }
                PulseMusicWidget.notifyWidgetUpdate(appContext)
            }

            override fun onPlayerError(error: PlaybackException) {
                Log.e(TAG, "ExoPlayer playback error: [${error.errorCodeName}] ${error.message}", error)
                _isBuffering.value = false
                _isPlaying.value = false
                stopProgressTracker()
            }

            override fun onPositionDiscontinuity(
                oldPosition: Player.PositionInfo,
                newPosition: Player.PositionInfo,
                reason: Int
            ) {
                _currentPositionMs.value = newPosition.positionMs
            }
        })

        // If there was a track clicked while service was binding, execute playback now
        pendingPlaySong?.let { song ->
            val q = pendingPlayQueue ?: listOf(song)
            pendingPlaySong = null
            pendingPlayQueue = null
            playSong(song, q)
        }
    }

    fun detachPlayer() {
        recordCurrentTrackListenTime()
        stopProgressTracker()
        exoPlayer = null
    }

    fun getPlayer(): ExoPlayer? = exoPlayer

    val audioSessionId: Int
        get() = exoPlayer?.audioSessionId ?: C.AUDIO_SESSION_ID_UNSET

    private fun recordCurrentTrackListenTime() {
        if (trackPlayStartTimeMs > 0) {
            val delta = System.currentTimeMillis() - trackPlayStartTimeMs
            trackAccumulatedPlayMs += delta
            trackPlayStartTimeMs = 0L
        }

        val song = trackedSong
        if (song != null && trackAccumulatedPlayMs >= 30_000L) {
            val totalPlayed = trackAccumulatedPlayMs
            mainScope.launch(Dispatchers.IO) {
                personalizationRepository?.logTrackPlayback(song, totalPlayed)
            }
        }
    }

    private fun ensureServiceRunning() {
        try {
            val intent = Intent(appContext, MusicService::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                appContext.startForegroundService(intent)
            } else {
                appContext.startService(intent)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed starting MusicService: ${e.message}")
        }
    }

    /**
     * Centralized play method for track clicks.
     * Guaranteed main-thread execution with fallback queue expansion.
     */
    fun playSong(song: SongEntity, initialQueue: List<SongEntity> = listOf(song)) {
        ensureServiceRunning()

        if (exoPlayer == null) {
            // Buffer request until service finishes attaching ExoPlayer
            pendingPlaySong = song
            pendingPlayQueue = initialQueue
            return
        }

        // Flush listen log of previous track if listened >= 30s
        recordCurrentTrackListenTime()
        trackedSong = song
        trackAccumulatedPlayMs = 0L
        trackPlayStartTimeMs = System.currentTimeMillis()

        mainScope.launch {
            var fullQueue = initialQueue
            // Language-Locked Auto-Queue: if single song selected or auto-queue enabled,
            // query matching category songs to create a cohesive linguistic & mood stream
            if (_languageLockedQueueEnabled.value && initialQueue.size <= 1) {
                val allSongs = repository.librarySongs.firstOrNull() ?: emptyList()
                val matchingSongs = allSongs.filter { candidate ->
                    candidate.id != song.id && SmartCategorizer.isSameCategory(song, candidate)
                }
                fullQueue = listOf(song) + matchingSongs
            }

            val index = fullQueue.indexOfFirst { it.id == song.id }.let { if (it >= 0) it else 0 }
            _queue.value = fullQueue
            _currentQueueIndex.value = index
            _currentSong.value = song
            _durationMs.value = song.durationMs
            _currentPositionMs.value = 0L

            executePlayback(song)
            PulseMusicWidget.notifyWidgetUpdate(appContext)
        }
    }

    fun playPause() {
        mainHandler.post {
            val player = exoPlayer ?: return@post
            if (player.isPlaying) {
                player.pause()
            } else {
                if (player.playbackState == Player.STATE_ENDED) {
                    player.seekTo(0)
                }
                player.play()
            }
            PulseMusicWidget.notifyWidgetUpdate(appContext)
        }
    }

    fun playNext() {
        mainHandler.post {
            val currentIdx = _currentQueueIndex.value
            val currentList = _queue.value
            if (currentList.isEmpty()) return@post

            if (_isShuffleEnabled.value) {
                val nextIdx = currentList.indices.filter { it != currentIdx }.randomOrNull() ?: 0
                playAtIndex(nextIdx)
                return@post
            }

            if (currentIdx + 1 < currentList.size) {
                playAtIndex(currentIdx + 1)
            } else if (_repeatMode.value == RepeatMode.ALL) {
                playAtIndex(0)
            } else {
                // Adaptive Auto-Queue triggered when queue runs dry
                checkAndExpandSmartQueue(forceAdaptive = true)
            }
        }
    }

    fun playPrevious() {
        mainHandler.post {
            val player = exoPlayer
            if (player != null && player.currentPosition > 3000L) {
                // Rewinds to 0 if played > 3 seconds
                player.seekTo(0)
                _currentPositionMs.value = 0L
                return@post
            }

            val currentIdx = _currentQueueIndex.value
            val currentList = _queue.value
            if (currentList.isEmpty()) return@post

            if (currentIdx > 0) {
                playAtIndex(currentIdx - 1)
            } else {
                playAtIndex(0)
            }
        }
    }

    fun playAtIndex(index: Int) {
        val list = _queue.value
        if (index in list.indices) {
            val song = list[index]
            _currentQueueIndex.value = index
            _currentSong.value = song
            _durationMs.value = song.durationMs
            _currentPositionMs.value = 0L

            recordCurrentTrackListenTime()
            trackedSong = song
            trackAccumulatedPlayMs = 0L
            trackPlayStartTimeMs = System.currentTimeMillis()

            executePlayback(song)
            PulseMusicWidget.notifyWidgetUpdate(appContext)

            checkAndExpandSmartQueue()
        }
    }

    fun seekTo(positionMs: Long) {
        mainHandler.post {
            exoPlayer?.seekTo(positionMs)
            _currentPositionMs.value = positionMs
        }
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

    fun toggleLanguageLockedQueue() {
        _languageLockedQueueEnabled.value = !_languageLockedQueueEnabled.value
    }

    fun reorderQueue(fromIndex: Int, toIndex: Int) {
        val list = _queue.value.toMutableList()
        if (fromIndex in list.indices && toIndex in list.indices) {
            val moved = list.removeAt(fromIndex)
            list.add(toIndex, moved)
            _queue.value = list

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

    /**
     * Prepares and plays media item reliably using MediaItem.fromUri().
     * Resolves Spotify imported tracks to JioSaavn streams on the fly.
     */
    private fun executePlayback(song: SongEntity) {
        val player = exoPlayer ?: return

        mainScope.launch {
            try {
                var playSong = song

                // Bridge Spotify imported tracks or Piped streams to playable audio stream
                if (song.contentUri.startsWith("piped://") && song.streamUrl.isNullOrBlank()) {
                    val streamId = song.contentUri.removePrefix("piped://")
                    try {
                        val streamResp = com.example.api.StreamingApiClient.api.getStreamDetails(streamId)
                        val bestStream = streamResp.body()?.let { com.example.api.StreamingApiClient.selectBestAudioStream(it) }
                        if (bestStream != null) {
                            playSong = song.copy(
                                streamUrl = bestStream.url,
                                albumArtUri = song.albumArtUri ?: streamResp.body()?.thumbnailUrl
                            )
                            repository.insertOnlineTrack(playSong)
                            _currentSong.value = playSong
                        }
                    } catch (e: Exception) {
                        Log.w(TAG, "StreamingApiClient stream resolution failed for ${song.title}: ${e.message}")
                    }
                } else if (song.contentUri.startsWith("spotify://") || (song.sourceType == "ONLINE" && song.streamUrl.isNullOrBlank())) {
                    Log.d(TAG, "Bridging track '${song.title}' to audio stream...")
                    var resolved = false
                    try {
                        val searchResp = com.example.api.StreamingApiClient.api.searchSongs("${song.title} ${song.artist}")
                        val firstResult = searchResp.body()?.firstOrNull()
                        if (firstResult != null) {
                            val sId = com.example.api.StreamingApiClient.extractStreamId(firstResult.url ?: "")
                            val streamDetails = com.example.api.StreamingApiClient.api.getStreamDetails(sId)
                            val audioStream = streamDetails.body()?.let { com.example.api.StreamingApiClient.selectBestAudioStream(it) }
                            if (audioStream != null) {
                                playSong = song.copy(
                                    contentUri = "piped://$sId",
                                    streamUrl = audioStream.url,
                                    albumArtUri = song.albumArtUri ?: firstResult.thumbnail,
                                    coverArtUrl = song.coverArtUrl ?: firstResult.thumbnail
                                )
                                repository.insertOnlineTrack(playSong)
                                _currentSong.value = playSong
                                resolved = true
                            }
                        }
                    } catch (e: Exception) {
                        Log.w(TAG, "StreamingApiClient bridge failed for ${song.title}: ${e.message}")
                    }

                    if (!resolved) {
                        try {
                            val searchResp = JioSaavnApiClient.api.searchSongs("${song.title} ${song.artist}", limit = 1)
                            val best = searchResp.body()?.data?.results?.firstOrNull()
                            if (best != null) {
                                val mapped = JioSaavnApiClient.mapToSongEntity(best)
                                playSong = song.copy(
                                    streamUrl = mapped.streamUrl,
                                    contentUri = mapped.contentUri,
                                    albumArtUri = song.albumArtUri ?: mapped.albumArtUri,
                                    coverArtUrl = song.coverArtUrl ?: mapped.coverArtUrl
                                )
                                repository.insertOnlineTrack(playSong)
                                _currentSong.value = playSong
                            }
                        } catch (e: Exception) {
                            Log.w(TAG, "JioSaavn fallback bridge failed for ${song.title}: ${e.message}")
                        }
                    }
                }

                val playUri = resolvePlayableUri(playSong)

                val metadata = MediaMetadata.Builder()
                    .setTitle(playSong.title)
                    .setArtist(playSong.artist)
                    .setAlbumTitle(playSong.album)
                    .setArtworkUri(playSong.albumArtUri?.let { Uri.parse(it) } ?: playSong.coverArtUrl?.let { Uri.parse(it) })
                    .build()

                val mediaItem = MediaItem.Builder()
                    .setMediaId(playSong.id.toString())
                    .setUri(playUri)
                    .setMediaMetadata(metadata)
                    .build()

                player.setMediaItem(mediaItem)
                player.prepare()
                player.play()
                Log.d(TAG, "Playing track ${playSong.id} via URI: $playUri")
            } catch (e: Exception) {
                Log.e(TAG, "Failed executing playback for ${song.title}: ${e.message}", e)
            }
        }
    }

    private fun resolvePlayableUri(song: SongEntity): Uri {
        return when {
            song.isDownloaded && !song.localPath.isNullOrBlank() && File(song.localPath).exists() -> {
                Uri.fromFile(File(song.localPath))
            }
            !song.dataPath.isNullOrBlank() && File(song.dataPath).exists() -> {
                Uri.fromFile(File(song.dataPath))
            }
            !song.streamUrl.isNullOrBlank() -> Uri.parse(song.streamUrl)
            song.contentUri.startsWith("android.resource://") -> {
                resolveRawResourceUri(song.contentUri)
            }
            else -> Uri.parse(song.contentUri)
        }
    }

    private fun resolveRawResourceUri(rawUriString: String): Uri {
        return try {
            val uri = Uri.parse(rawUriString)
            val segment = uri.lastPathSegment ?: ""
            val resId = segment.toIntOrNull() ?: appContext.resources.getIdentifier(segment, "raw", appContext.packageName)
            if (resId != 0) {
                androidx.media3.datasource.RawResourceDataSource.buildRawResourceUri(resId)
            } else {
                uri
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error resolving resource URI: $rawUriString", e)
            Uri.parse(rawUriString)
        }
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
     * Dual-Engine Adaptive Auto-Queue:
     * When manual queue reaches the end or has <= 2 songs, dynamically enqueues tracks matching
     * the dominant language and mood profile of the user's hybrid taste vector.
     */
    private fun checkAndExpandSmartQueue(forceAdaptive: Boolean = false) {
        val current = _currentSong.value ?: return
        val currentQueue = _queue.value
        val currentIndex = _currentQueueIndex.value

        val remaining = (currentQueue.size - 1) - currentIndex
        if (remaining <= 2 || forceAdaptive) {
            mainScope.launch(Dispatchers.IO) {
                val adaptiveList = personalizationRepository?.getAdaptiveAutoQueue(current, currentQueue)
                    ?: emptyList()

                val toAppend = if (adaptiveList.isNotEmpty()) {
                    adaptiveList.take(6)
                } else {
                    val allSongs = repository.librarySongs.firstOrNull() ?: emptyList()
                    val existingIds = currentQueue.map { it.id }.toSet()
                    val matching = allSongs.filter { candidate ->
                        !existingIds.contains(candidate.id) && SmartCategorizer.isSameCategory(current, candidate)
                    }
                    if (matching.isNotEmpty()) matching.take(4) else AudioMoodClassifier.findMatchingMoodSongs(current, allSongs, existingIds, 4)
                }

                if (toAppend.isNotEmpty()) {
                    mainHandler.post {
                        val updated = _queue.value.toMutableList()
                        val previousSize = updated.size
                        updated.addAll(toAppend)
                        _queue.value = updated
                        Log.d(TAG, "Adaptive Auto-Queue appended ${toAppend.size} tracks from hybrid taste vector")

                        if (forceAdaptive && previousSize > 0) {
                            playAtIndex(previousSize)
                        }
                    }
                }
            }
        }
    }

    private fun startProgressTracker() {
        progressJob?.cancel()
        progressJob = mainScope.launch {
            while (isActive) {
                exoPlayer?.let { player ->
                    _currentPositionMs.value = player.currentPosition
                    if (player.duration > 0) {
                        _durationMs.value = player.duration
                    }
                }
                delay(100L) // 100ms ultra-smooth scrub and synced lyrics updates
            }
        }
    }

    private fun stopProgressTracker() {
        progressJob?.cancel()
        progressJob = null
    }
}
