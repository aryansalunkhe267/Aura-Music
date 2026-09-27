package com.example.data

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Unified playback history tracking for both offline and online audio streams.
 * Records every track played for at least 30 seconds to feed the hybrid taste vector.
 */
@Entity(
    tableName = "playback_history",
    indices = [
        Index(value = ["timestamp"]),
        Index(value = ["artist"]),
        Index(value = ["language"])
    ]
)
data class PlaybackHistoryEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0L,
    val songId: Long?,
    val title: String,
    val artist: String,
    val album: String = "",
    val source: String, // "OFFLINE" or "ONLINE"
    val language: String = "UNKNOWN", // "PUNJABI", "HINDI", "MARATHI", etc.
    val durationPlayedMs: Long = 0L,
    val timestamp: Long = System.currentTimeMillis()
)
