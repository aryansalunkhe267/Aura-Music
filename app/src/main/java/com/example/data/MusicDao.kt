package com.example.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface MusicDao {

    // Permanent deletion query
    @Query("DELETE FROM songs WHERE id = :songId")
    suspend fun deleteSongById(songId: Long)

    // Main library query: Soft-hidden tracks are omitted from general view
    @Query("SELECT * FROM songs WHERE isHiddenFromLibrary = 0 ORDER BY title COLLATE NOCASE ASC")
    fun getLibrarySongs(): Flow<List<SongEntity>>

    // Query for all songs regardless of soft-hide state (for scanning & queue algorithms)
    @Query("SELECT * FROM songs ORDER BY title COLLATE NOCASE ASC")
    suspend fun getAllSongsUnlimited(): List<SongEntity>

    @Query("SELECT * FROM songs WHERE id = :id LIMIT 1")
    suspend fun getSongById(id: Long): SongEntity?

    // Dedicated query for softly-hidden songs (kept exclusively in playlists or hidden vault)
    @Query("SELECT * FROM songs WHERE isHiddenFromLibrary = 1 ORDER BY title COLLATE NOCASE ASC")
    fun getHiddenVaultSongs(): Flow<List<SongEntity>>

    // Language script queries (Punjabi / Hindi / Marathi)
    @Query("SELECT * FROM songs WHERE languageScript = :script AND isHiddenFromLibrary = 0 ORDER BY title COLLATE NOCASE ASC")
    fun getSongsByScript(script: String): Flow<List<SongEntity>>

    // Mood-based queries for smart recommendation engine
    @Query("SELECT * FROM songs WHERE moodProfile = :mood ORDER BY moodScore DESC")
    suspend fun getSongsByMoodDirect(mood: String): List<SongEntity>

    @Query("SELECT * FROM songs WHERE moodProfile = :mood AND isHiddenFromLibrary = 0 ORDER BY moodScore DESC")
    fun getSongsByMoodFlow(mood: String): Flow<List<SongEntity>>

    // Insert or update
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSongs(songs: List<SongEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSong(song: SongEntity)

    @Update
    suspend fun updateSong(song: SongEntity)

    // Soft-hide toggle engine
    @Query("UPDATE songs SET isHiddenFromLibrary = :isHidden WHERE id = :songId")
    suspend fun setSongHidden(songId: Long, isHidden: Boolean)

    // Update synchronized .lrc lyrics
    @Query("UPDATE songs SET lrcLyrics = :lyrics WHERE id = :songId")
    suspend fun updateLyrics(songId: Long, lyrics: String)

    // Update acoustic mood profile
    @Query("UPDATE songs SET moodProfile = :mood, moodScore = :score WHERE id = :songId")
    suspend fun updateMood(songId: Long, mood: String, score: Float)

    // Update download progress & offline status
    @Query("UPDATE songs SET downloadProgress = :progress, isDownloaded = :isDownloaded, localPath = :localPath, contentUri = :contentUri WHERE id = :songId")
    suspend fun updateDownloadStatus(songId: Long, progress: Int, isDownloaded: Boolean, localPath: String?, contentUri: String)

    @Query("SELECT * FROM songs WHERE sourceType = 'ONLINE' ORDER BY dateAdded DESC")
    fun getOnlineSongs(): Flow<List<SongEntity>>

    // Favorites System
    @Query("UPDATE songs SET isFavorite = CASE WHEN isFavorite = 1 THEN 0 ELSE 1 END WHERE id = :songId")
    suspend fun toggleFavorite(songId: Long)

    @Query("UPDATE songs SET isFavorite = :isFavorite WHERE id = :songId")
    suspend fun setFavorite(songId: Long, isFavorite: Boolean)

    @Query("UPDATE songs SET coverArtUrl = :coverArtUrl, albumArtUri = :coverArtUrl WHERE id = :songId")
    suspend fun updateCoverArt(songId: Long, coverArtUrl: String)

    @Query("SELECT * FROM songs WHERE isFavorite = 1 AND isHiddenFromLibrary = 0 ORDER BY title COLLATE NOCASE ASC")
    fun getFavoriteSongs(): Flow<List<SongEntity>>

    // Online Metadata & Synced Lyrics Enrichment
    @Query("""
        UPDATE songs 
        SET coverArtUrl = COALESCE(:coverArtUrl, coverArtUrl),
            syncedLyrics = COALESCE(:syncedLyrics, syncedLyrics),
            lrcLyrics = COALESCE(:syncedLyrics, lrcLyrics),
            verifiedArtist = COALESCE(:verifiedArtist, verifiedArtist),
            genre = COALESCE(:genre, genre)
        WHERE id = :songId
    """)
    suspend fun updateEnrichedMetadata(
        songId: Long,
        coverArtUrl: String?,
        syncedLyrics: String?,
        verifiedArtist: String?,
        genre: String?
    )

    // Playlists
    @Query("SELECT * FROM playlists ORDER BY createdAt DESC")
    fun getAllPlaylists(): Flow<List<PlaylistEntity>>

    @Query("SELECT * FROM playlists WHERE playlistId = :id LIMIT 1")
    suspend fun getPlaylistById(id: Long): PlaylistEntity?

    @Query("SELECT * FROM playlists WHERE playlistType = :type LIMIT 1")
    suspend fun getPlaylistByType(type: String): PlaylistEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPlaylist(playlist: PlaylistEntity): Long

    @Query("DELETE FROM playlists WHERE playlistId = :id")
    suspend fun deletePlaylist(id: Long)

    // Cross-ref Playlist Songs
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun addSongToPlaylist(ref: PlaylistSongCrossRef)

    @Query("DELETE FROM playlist_song_cross_ref WHERE playlistId = :playlistId AND songId = :songId")
    suspend fun removeSongFromPlaylist(playlistId: Long, songId: Long)

    @Query("""
        SELECT s.* FROM songs s
        INNER JOIN playlist_song_cross_ref ps ON s.id = ps.songId
        WHERE ps.playlistId = :playlistId
        ORDER BY ps.sortOrder ASC, ps.addedAt DESC
    """)
    fun getSongsForPlaylist(playlistId: Long): Flow<List<SongEntity>>

    @Query("""
        SELECT p.* FROM playlists p
        INNER JOIN playlist_song_cross_ref ps ON p.playlistId = ps.playlistId
        WHERE ps.songId = :songId
    """)
    fun getPlaylistsForSong(songId: Long): Flow<List<PlaylistEntity>>

    // Playback History Tracking (Minimum 30s listen)
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPlaybackHistory(entry: PlaybackHistoryEntity): Long

    @Query("SELECT * FROM playback_history WHERE timestamp >= :sinceTimestamp ORDER BY timestamp DESC")
    fun getRecentPlaybackHistory(sinceTimestamp: Long): Flow<List<PlaybackHistoryEntity>>

    @Query("SELECT * FROM playback_history WHERE timestamp >= :sinceTimestamp ORDER BY timestamp DESC")
    suspend fun getRecentPlaybackHistoryDirect(sinceTimestamp: Long): List<PlaybackHistoryEntity>

    // Spotify Taste Profile Affinity
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSpotifyAffinities(affinities: List<SpotifyAffinityEntity>)

    @Query("SELECT * FROM spotify_affinity ORDER BY weight DESC")
    fun getSpotifyAffinities(): Flow<List<SpotifyAffinityEntity>>

    @Query("SELECT * FROM spotify_affinity ORDER BY weight DESC")
    suspend fun getSpotifyAffinitiesDirect(): List<SpotifyAffinityEntity>

    @Query("DELETE FROM spotify_affinity")
    suspend fun clearSpotifyAffinities()

    // Custom Lyrics Persistence (Room)
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun saveCustomLyrics(lyrics: CustomLyricsEntity)

    @Query("SELECT * FROM custom_lyrics WHERE songId = :songId LIMIT 1")
    suspend fun getCustomLyrics(songId: Long): CustomLyricsEntity?

    @Query("SELECT * FROM custom_lyrics WHERE audioPath = :audioPath LIMIT 1")
    suspend fun getCustomLyricsByPath(audioPath: String): CustomLyricsEntity?

    @Query("DELETE FROM custom_lyrics WHERE songId = :songId")
    suspend fun deleteCustomLyrics(songId: Long)

    // Permanent Tag & Title Metadata Overrides (Room)
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun saveSongOverride(override: SongOverrideEntity)

    @Query("SELECT * FROM song_overrides WHERE songId = :songId LIMIT 1")
    suspend fun getSongOverride(songId: Long): SongOverrideEntity?

    @Query("SELECT * FROM song_overrides WHERE audioPath = :audioPath LIMIT 1")
    suspend fun getSongOverrideByPath(audioPath: String): SongOverrideEntity?

    @Query("SELECT * FROM song_overrides")
    fun getAllSongOverrides(): Flow<List<SongOverrideEntity>>

    @Query("SELECT * FROM song_overrides")
    suspend fun getAllSongOverridesDirect(): List<SongOverrideEntity>

    @Query("DELETE FROM song_overrides WHERE songId = :songId")
    suspend fun deleteSongOverride(songId: Long)

    // Playlist Reordering & Clear
    @Query("UPDATE playlist_song_cross_ref SET sortOrder = :sortOrder WHERE playlistId = :playlistId AND songId = :songId")
    suspend fun updatePlaylistSortOrder(playlistId: Long, songId: Long, sortOrder: Int)

    @Query("DELETE FROM playlist_song_cross_ref WHERE playlistId = :playlistId")
    suspend fun clearSongsFromPlaylist(playlistId: Long)
}
