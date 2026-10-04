package com.example.data

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Stores persistent user-imported or edited synchronized .LRC lyrics
 * keyed by song ID or audio file path.
 */
@Entity(
    tableName = "custom_lyrics",
    indices = [Index(value = ["audioPath"])]
)
data class CustomLyricsEntity(
    @PrimaryKey val songId: Long,
    val audioPath: String? = null,
    val lrcLyrics: String,
    val source: String = "MANUAL", // "MANUAL", "LRCLIB", "EMBEDDED"
    val updatedAt: Long = System.currentTimeMillis()
)
