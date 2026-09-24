package com.example

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build
import com.example.data.MusicDatabase
import com.example.data.MusicRepository
import com.example.playback.PlaybackManager

class AuraMusicApp : Application() {

    companion object {
        const val PLAYBACK_CHANNEL_ID = "aura_playback_channel"
        lateinit var instance: AuraMusicApp
            private set
    }

    lateinit var database: MusicDatabase
        private set
    lateinit var repository: MusicRepository
        private set
    lateinit var settingsManager: com.example.data.SettingsManager
        private set

    override fun onCreate() {
        super.onCreate()
        instance = this

        database = MusicDatabase.getDatabase(this)
        repository = MusicRepository(this, database.musicDao())
        settingsManager = com.example.data.SettingsManager(this)

        // Create persistent notification channel for background media playback
        createNotificationChannel()

        // Initialize PlaybackManager singleton
        PlaybackManager.initialize(this, repository)

        // Auto background metadata and synced lyrics enricher on network connection
        repository.enrichmentWorker.startNetworkMonitoring()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val name = "Aura Music Playback"
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
