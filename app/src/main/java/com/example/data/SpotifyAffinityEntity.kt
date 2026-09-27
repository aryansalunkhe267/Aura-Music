package com.example.data

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Stores personalized Spotify taste profile weights imported without Spotify Developer client IDs.
 * Represents top artists and genres used to seed the dual-engine taste vector.
 */
@Entity(tableName = "spotify_affinity")
data class SpotifyAffinityEntity(
    @PrimaryKey
    val artist: String,
    val weight: Float, // Normalized affinity score (e.g. 1.0 down to 0.1)
    val rank: Int = 1,
    val genres: String = "",
    val imageUrl: String? = null,
    val lastUpdated: Long = System.currentTimeMillis()
)
