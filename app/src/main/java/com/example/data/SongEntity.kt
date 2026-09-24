package com.example.data

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Entity representing an audio track stored locally on the device.
 * Includes complete UTF-8 encoding support for Indic scripts (Hindi, Marathi, Punjabi)
 * as well as soft-hide flags for playlist-only containment.
 */
@Entity(tableName = "songs")
data class SongEntity(
    @PrimaryKey val id: Long, // MediaStore._ID
    val title: String,
    val artist: String,
    val album: String,
    val durationMs: Long,
    val contentUri: String,
    val albumArtUri: String? = null,
    val dataPath: String? = null,
    val dateAdded: Long = 0L,
    val size: Long = 0L,
    
    // Soft-hide engine: When true, song is hidden from general library screens
    // but remains preserved and playable within designated playlists.
    val isHiddenFromLibrary: Boolean = false,
    
    // Linguistic script categorization
    // "PUNJABI" (Gurmukhi), "HINDI_MARATHI" (Devanagari), "ENGLISH_OTHER"
    val languageScript: String = "UNKNOWN",
    
    // Acoustic Mood Profile calculated by on-device offline AI
    // "ENERGETIC", "CHILL", "MELANCHOLY", "UPBEAT", "CALM"
    val moodProfile: String = "CHILL",
    val moodScore: Float = 0.5f, // 0.0f (Calm/Acoustic) to 1.0f (High Energy/Punchy)
    
    // Custom embedded or user-supplied .lrc lyrics content
    val lrcLyrics: String? = null,

    // Hybrid Online + Offline fields
    val sourceType: String = "LOCAL", // "LOCAL" or "ONLINE"
    val streamUrl: String? = null,
    val localPath: String? = null,
    val isDownloaded: Boolean = true,
    val downloadProgress: Int = 100, // 0 to 100 percent

    // Favorites system
    val isFavorite: Boolean = false,

    // Enriched Online Metadata & Synced Lyrics
    val coverArtUrl: String? = null,
    val syncedLyrics: String? = null,
    val verifiedArtist: String? = null,
    val genre: String? = null
)
