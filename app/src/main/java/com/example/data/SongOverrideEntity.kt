package com.example.data

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Stores permanent metadata overrides for track tag & title editing (e.g. "J STAR | HULARA").
 * Overrides raw MediaStore or scanner values across app restarts and library rescans.
 */
@Entity(
    tableName = "song_overrides",
    indices = [Index(value = ["audioPath"])]
)
data class SongOverrideEntity(
    @PrimaryKey val songId: Long,
    val audioPath: String? = null,
    val customTitle: String? = null,
    val customArtist: String? = null,
    val customAlbum: String? = null,
    val customGenre: String? = null,
    val updatedAt: Long = System.currentTimeMillis()
)
