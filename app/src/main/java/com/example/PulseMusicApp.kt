package com.example

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import com.example.data.MusicDatabase
import com.example.data.MusicRepository
import com.example.data.SettingsManager
import com.example.engine.LyricsAutoDownloadWorker
import com.example.playback.PlaybackManager
import java.util.concurrent.TimeUnit

class PulseMusicApp : Application() {

    companion object {
        const val PLAYBACK_CHANNEL_ID = "pulse_playback_channel"
        lateinit var instance: PulseMusicApp
            private set
    }

    lateinit var database: MusicDatabase
        private set
    lateinit var repository: MusicRepository
        private set
    lateinit var settingsManager: SettingsManager
        private set
    lateinit var spotifyRepository: com.example.data.SpotifyWebRepository
        private set
    lateinit var personalizationRepository: com.example.data.PersonalizationRepository
        private set

    override fun onCreate() {
        super.onCreate()
        instance = this

        database = MusicDatabase.getDatabase(this)
        repository = MusicRepository(this, database.musicDao())
        settingsManager = SettingsManager(this)
        com.example.api.StreamingApiClient.init(this)
        spotifyRepository = com.example.data.SpotifyWebRepository(database.musicDao())
        personalizationRepository = com.example.data.PersonalizationRepository(database.musicDao())

        // Create persistent notification channel for background media playback
        createNotificationChannel()

        // Initialize PlaybackManager singleton
        PlaybackManager.initialize(this, repository, personalizationRepository)

        // Auto background metadata enricher
        repository.enrichmentWorker.startNetworkMonitoring()

        // Schedule WorkManager background auto-download for missing synced lyrics & cover art
        scheduleLyricsWorkManager()
        scheduleArtworkWorkManager()
    }

    private fun scheduleArtworkWorkManager() {
        try {
            val constraints = Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .build()

            val artworkWorkRequest = PeriodicWorkRequestBuilder<com.example.engine.ArtworkAutoFetchWorker>(
                repeatInterval = 6,
                repeatIntervalTimeUnit = TimeUnit.HOURS
            ).setConstraints(constraints).build()

            WorkManager.getInstance(this).enqueueUniquePeriodicWork(
                "ArtworkAutoFetchWork",
                ExistingPeriodicWorkPolicy.KEEP,
                artworkWorkRequest
            )
        } catch (_: Exception) {}
    }

    private fun scheduleLyricsWorkManager() {
        try {
            val constraints = Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .build()

            val lyricsWorkRequest = PeriodicWorkRequestBuilder<LyricsAutoDownloadWorker>(
                repeatInterval = 6,
                repeatIntervalTimeUnit = TimeUnit.HOURS
            ).setConstraints(constraints).build()

            WorkManager.getInstance(this).enqueueUniquePeriodicWork(
                "LyricsAutoDownloadWork",
                ExistingPeriodicWorkPolicy.KEEP,
                lyricsWorkRequest
            )
        } catch (_: Exception) {}
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val name = "Pulse Music Playback"
            val descriptionText = "Persistent background media playback controls"
            val importance = NotificationManager.IMPORTANCE_LOW
            val channel = NotificationChannel(PLAYBACK_CHANNEL_ID, name, importance).apply {
                description = descriptionText
                setShowBadge(false)
                lockscreenVisibility = android.app.Notification.VISIBILITY_PUBLIC
            }
            val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.createNotificationChannel(channel)
        }
    }
}
