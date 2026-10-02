package com.example.playback

import android.app.Notification
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import com.example.MainActivity
import com.example.PulseMusicApp
import com.example.R
import com.example.data.SongEntity
import com.example.widget.PulseMusicWidget
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.launch

/**
 * AndroidX MediaSessionService engineered for Samsung One UI & Android 14+ background persistence.
 * Prevents Samsung battery managers from killing the player via persistent foreground sessions and wake locks.
 */
class MusicService : MediaSessionService() {

    private val TAG = "MusicService"
    private val NOTIFICATION_ID = 2026

    private var exoPlayer: ExoPlayer? = null
    private var mediaSession: MediaSession? = null

    companion object {
        const val ACTION_PLAY = "com.pulse.music.ACTION_PLAY"
        const val ACTION_PAUSE = "com.pulse.music.ACTION_PAUSE"
        const val ACTION_PLAY_PAUSE = "com.pulse.music.ACTION_PLAY_PAUSE"
        const val ACTION_NEXT = "com.pulse.music.ACTION_NEXT"
        const val ACTION_PREV = "com.pulse.music.ACTION_PREV"
    }

    override fun onCreate() {
        super.onCreate()

        val audioAttributes = AudioAttributes.Builder()
            .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
            .setUsage(C.USAGE_MEDIA)
            .build()

        val player = ExoPlayer.Builder(this)
            .setAudioAttributes(audioAttributes, true)
            .setHandleAudioBecomingNoisy(true)
            .setWakeMode(C.WAKE_MODE_LOCAL)
            .build()

        exoPlayer = player

        val launchIntent = Intent(this, MainActivity::class.java)
        val sessionActivityPendingIntent = PendingIntent.getActivity(
            this,
            0,
            launchIntent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        mediaSession = MediaSession.Builder(this, player)
            .setSessionActivity(sessionActivityPendingIntent)
            .build()

        PlaybackManager.attachPlayer(player)

        // Player.Listener triggers widget update whenever onIsPlayingChanged fires
        player.addListener(object : Player.Listener {
            override fun onIsPlayingChanged(isPlaying: Boolean) {
                updateNotification()
                PulseMusicWidget.notifyWidgetUpdate(this@MusicService, isPlaying)
            }

            override fun onPlaybackStateChanged(playbackState: Int) {
                updateNotification()
                PulseMusicWidget.notifyWidgetUpdate(this@MusicService, player.isPlaying)
            }

            override fun onMediaItemTransition(mediaItem: androidx.media3.common.MediaItem?, reason: Int) {
                updateNotification()
                PulseMusicWidget.notifyWidgetUpdate(this@MusicService, player.isPlaying)
            }

            override fun onPlayerError(error: androidx.media3.common.PlaybackException) {
                Log.e(TAG, "MusicService ExoPlayer error: ${error.errorCodeName} - ${error.message}", error)
                updateNotification()
                PulseMusicWidget.notifyWidgetUpdate(this@MusicService, false)
            }
        })

        // Initial foreground notification to lock service against aggressive OS app sleep
        val initialNotification = buildNotification(PlaybackManager.currentSong.value, false)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(
                NOTIFICATION_ID,
                initialNotification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK
            )
        } else {
            startForeground(NOTIFICATION_ID, initialNotification)
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_PLAY -> handlePlayAction()
            ACTION_PAUSE -> handlePauseAction()
            ACTION_PLAY_PAUSE, Intent.ACTION_MEDIA_BUTTON -> handlePlayPauseAction()
            ACTION_NEXT -> PlaybackManager.playNext()
            ACTION_PREV -> PlaybackManager.playPrevious()
        }
        return super.onStartCommand(intent, flags, startId)
    }

    private fun handlePlayPauseAction() {
        val player = exoPlayer
        if (player != null && player.mediaItemCount > 0 && PlaybackManager.currentSong.value != null) {
            PlaybackManager.playPause()
        } else {
            startPlaybackFromLibrary()
        }
    }

    private fun handlePlayAction() {
        val player = exoPlayer
        if (player != null && player.mediaItemCount > 0 && PlaybackManager.currentSong.value != null) {
            if (!player.isPlaying) {
                PlaybackManager.playPause()
            }
        } else {
            startPlaybackFromLibrary()
        }
    }

    private fun handlePauseAction() {
        val player = exoPlayer
        if (player != null && player.isPlaying) {
            PlaybackManager.playPause()
        }
    }

    private fun startPlaybackFromLibrary() {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val app = applicationContext as? PulseMusicApp ?: PulseMusicApp.instance
                var library = app.repository.librarySongs.firstOrNull()?.ifEmpty { null }
                    ?: app.database.musicDao().getAllSongsUnlimited()

                if (library.isEmpty()) {
                    app.repository.scanLocalAudioLibrary()
                    library = app.database.musicDao().getAllSongsUnlimited()
                }

                if (library.isNotEmpty()) {
                    val prefs = getSharedPreferences(PulseMusicWidget.PREFS_NAME, Context.MODE_PRIVATE)
                    val lastSongId = prefs.getLong(PulseMusicWidget.KEY_SONG_ID, -1L)
                    val targetSong = if (lastSongId != -1L) {
                        library.firstOrNull { it.id == lastSongId } ?: library.first()
                    } else {
                        library.first()
                    }
                    PlaybackManager.playSong(targetSong, library)
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error starting playback from library when waking app: ${e.message}", e)
            }
        }
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? {
        return mediaSession
    }

    override fun onTaskRemoved(rootIntent: Intent?) {
        val player = exoPlayer
        if (player == null || !player.playWhenReady || player.mediaItemCount == 0) {
            stopSelf()
        }
    }

    override fun onDestroy() {
        PulseMusicWidget.notifyWidgetUpdate(this, isPlayingOverride = false)
        PlaybackManager.detachPlayer()
        mediaSession?.run {
            player.release()
            release()
            mediaSession = null
        }
        exoPlayer = null
        super.onDestroy()
    }

    private fun updateNotification() {
        val song = PlaybackManager.currentSong.value
        val isPlaying = exoPlayer?.isPlaying ?: false
        val notification = buildNotification(song, isPlaying)
        val notificationManager = getSystemService(NOTIFICATION_SERVICE) as android.app.NotificationManager
        notificationManager.notify(NOTIFICATION_ID, notification)
    }

    private fun buildNotification(song: SongEntity?, isPlaying: Boolean): Notification {
        val title = song?.title ?: "Pulse Music"
        val artist = song?.artist ?: "Offline High-Fidelity Audio"

        val openAppIntent = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val prevIntent = PendingIntent.getService(
            this,
            1,
            Intent(this, MusicService::class.java).apply { action = ACTION_PREV },
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val playPauseIntent = PendingIntent.getService(
            this,
            2,
            Intent(this, MusicService::class.java).apply {
                action = if (isPlaying) ACTION_PAUSE else ACTION_PLAY
            },
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val nextIntent = PendingIntent.getService(
            this,
            3,
            Intent(this, MusicService::class.java).apply { action = ACTION_NEXT },
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val playPauseIcon = if (isPlaying) {
            android.R.drawable.ic_media_pause
        } else {
            android.R.drawable.ic_media_play
        }

        val builder = NotificationCompat.Builder(this, PulseMusicApp.PLAYBACK_CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle(title)
            .setContentText(artist)
            .setSubText(song?.languageScript?.replace("_", " ") ?: "PULSE")
            .setContentIntent(openAppIntent)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setOngoing(isPlaying)
            .setShowWhen(false)
            .addAction(android.R.drawable.ic_media_previous, "Previous", prevIntent)
            .addAction(playPauseIcon, if (isPlaying) "Pause" else "Play", playPauseIntent)
            .addAction(android.R.drawable.ic_media_next, "Next", nextIntent)

        mediaSession?.let { session ->
            builder.setStyle(
                androidx.media3.session.MediaStyleNotificationHelper.MediaStyle(session)
                    .setShowActionsInCompactView(0, 1, 2)
            )
        }

        return builder.build()
    }
}
