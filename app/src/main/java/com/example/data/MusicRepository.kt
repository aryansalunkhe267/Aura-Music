package com.example.data

import android.content.ContentUris
import android.content.Context
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import android.util.Log
import com.example.engine.AudioMoodClassifier
import com.example.engine.ScriptLanguageDetector
import com.example.utils.MetadataSanitizer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext

class MusicRepository(
    private val context: Context,
    private val musicDao: MusicDao
) {
    private val TAG = "MusicRepository"

    val downloadManager = MusicDownloadManager(context, musicDao)
    val enrichmentWorker = com.example.engine.MetadataEnrichmentWorker(context, musicDao)

    // Exposed reactive flows
    val librarySongs: Flow<List<SongEntity>> = musicDao.getLibrarySongs()
    val hiddenVaultSongs: Flow<List<SongEntity>> = musicDao.getHiddenVaultSongs()
    val playlists: Flow<List<PlaylistEntity>> = musicDao.getAllPlaylists()
    val onlineSongs: Flow<List<SongEntity>> = musicDao.getOnlineSongs()
    val favoriteSongs: Flow<List<SongEntity>> = musicDao.getFavoriteSongs()

    suspend fun toggleFavorite(songId: Long) {
        musicDao.toggleFavorite(songId)
    }

    fun getSongsForPlaylist(playlistId: Long): Flow<List<SongEntity>> =
        musicDao.getSongsForPlaylist(playlistId)

    fun getSongsByScript(script: String): Flow<List<SongEntity>> =
        musicDao.getSongsByScript(script)

    fun getSongsByMood(mood: String): Flow<List<SongEntity>> =
        musicDao.getSongsByMoodFlow(mood)

    suspend fun setSongHidden(songId: Long, isHidden: Boolean) {
        musicDao.setSongHidden(songId, isHidden)
    }

    suspend fun updateLyrics(songId: Long, lyrics: String) {
        musicDao.updateLyrics(songId, lyrics)
    }

    suspend fun createPlaylist(name: String, description: String = "", type: String = "USER"): Long {
        return musicDao.insertPlaylist(
            PlaylistEntity(name = name, description = description, playlistType = type)
        )
    }

    suspend fun addSongToPlaylist(playlistId: Long, songId: Long) {
        musicDao.addSongToPlaylist(PlaylistSongCrossRef(playlistId = playlistId, songId = songId))
    }

    suspend fun removeSongFromPlaylist(playlistId: Long, songId: Long) {
        musicDao.removeSongFromPlaylist(playlistId, songId)
    }

    suspend fun deletePlaylist(playlistId: Long) {
        musicDao.deletePlaylist(playlistId)
    }

    /**
     * Scans MediaStore with ZERO CAP (no LIMIT clause) to load the entire offline library.
     * Extracts full UTF-8 metadata and runs offline linguistic & acoustic classifiers.
     */
    suspend fun scanLocalAudioLibrary(): Int = withContext(Dispatchers.IO) {
        val resolver = context.contentResolver
        val audioUri = MediaStore.Audio.Media.EXTERNAL_CONTENT_URI

        val projection = arrayOf(
            MediaStore.Audio.Media._ID,
            MediaStore.Audio.Media.TITLE,
            MediaStore.Audio.Media.ARTIST,
            MediaStore.Audio.Media.ALBUM,
            MediaStore.Audio.Media.DURATION,
            MediaStore.Audio.Media.DATA,
            MediaStore.Audio.Media.DATE_ADDED,
            MediaStore.Audio.Media.SIZE,
            MediaStore.Audio.Media.ALBUM_ID
        )

        // Only query valid music tracks with positive duration
        val selection = "${MediaStore.Audio.Media.IS_MUSIC} != 0 AND ${MediaStore.Audio.Media.DURATION} > 5000"
        val sortOrder = "${MediaStore.Audio.Media.TITLE} COLLATE NOCASE ASC"

        val scannedSongs = mutableListOf<SongEntity>()

        try {
            resolver.query(audioUri, projection, selection, null, sortOrder)?.use { cursor ->
                val idCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media._ID)
                val titleCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.TITLE)
                val artistCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ARTIST)
                val albumCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ALBUM)
                val durationCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DURATION)
                val dataCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DATA)
                val dateCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DATE_ADDED)
                val sizeCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.SIZE)
                val albumIdCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ALBUM_ID)

                while (cursor.moveToNext()) {
                    val id = cursor.getLong(idCol)
                    val rawTitle = cursor.getString(titleCol) ?: "Unknown Title"
                    val rawArtist = cursor.getString(artistCol) ?: "Unknown Artist"
                    val rawAlbum = cursor.getString(albumCol) ?: "Unknown Album"
                    val duration = cursor.getLong(durationCol)
                    val dataPath = cursor.getString(dataCol)
                    val dateAdded = cursor.getLong(dateCol)
                    val size = cursor.getLong(sizeCol)
                    val albumId = cursor.getLong(albumIdCol)

                    val cleanTitle = MetadataSanitizer.sanitizeTitle(rawTitle)
                    val cleanArtist = MetadataSanitizer.sanitizeArtist(rawArtist)
                    val cleanAlbum = MetadataSanitizer.sanitizeAlbum(rawAlbum)

                    val contentUri = ContentUris.withAppendedId(audioUri, id).toString()
                    val albumArtUri = "content://media/external/audio/albumart/$albumId"

                    // Script linguistic detection (Devanagari, Gurmukhi, English)
                    val scriptType = ScriptLanguageDetector.detectScript(cleanTitle, cleanArtist, cleanAlbum)

                    scannedSongs.add(
                        SongEntity(
                            id = id,
                            title = cleanTitle,
                            artist = cleanArtist,
                            album = cleanAlbum,
                            durationMs = duration,
                            contentUri = contentUri,
                            albumArtUri = albumArtUri,
                            dataPath = dataPath,
                            dateAdded = dateAdded,
                            size = size,
                            isHiddenFromLibrary = false,
                            languageScript = scriptType.tag,
                            moodProfile = "CHILL",
                            moodScore = 0.5f,
                            sourceType = "LOCAL",
                            isDownloaded = true,
                            downloadProgress = 100
                        )
                    )
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error querying MediaStore: ${e.message}", e)
        }

        // If local device has 0 media files (e.g. fresh emulator), seed authentic demo offline tracks
        // so the user can immediately experience Samsung One UI playback, Synced Lyrics, and Mood Queues
        if (scannedSongs.isEmpty()) {
            val sampleTracks = seedDemonstrationSongs()
            scannedSongs.addAll(sampleTracks)
        }

        // Fetch existing songs to preserve custom user lyrics and soft-hide states
        val existingSongs = musicDao.getAllSongsUnlimited().associateBy { it.id }
        val mergedSongs = scannedSongs.map { scanned ->
            val existing = existingSongs[scanned.id]
            if (existing != null) {
                scanned.copy(
                    isHiddenFromLibrary = existing.isHiddenFromLibrary,
                    isFavorite = existing.isFavorite,
                    coverArtUrl = existing.coverArtUrl ?: scanned.coverArtUrl,
                    syncedLyrics = existing.syncedLyrics ?: scanned.syncedLyrics,
                    lrcLyrics = existing.lrcLyrics ?: scanned.lrcLyrics,
                    verifiedArtist = existing.verifiedArtist ?: scanned.verifiedArtist,
                    genre = existing.genre ?: scanned.genre,
                    moodProfile = existing.moodProfile,
                    moodScore = existing.moodScore
                )
            } else {
                scanned
            }
        }

        musicDao.insertSongs(mergedSongs)
        ensureAutoGeneratedPlaylists(mergedSongs)

        return@withContext mergedSongs.size
    }

    private fun seedDemonstrationSongs(): List<SongEntity> {
        return listOf(
            SongEntity(
                id = 1001L,
                title = "295",
                artist = "Sidhu Moose Wala",
                album = "Moosetape",
                durationMs = 270_000L,
                contentUri = "android.resource://${context.packageName}/raw/demo_track_1",
                albumArtUri = null,
                languageScript = "PUNJABI",
                moodProfile = "ENERGETIC",
                moodScore = 0.92f,
                lrcLyrics = """
                    [00:00.00]♪ Bass & Dhol Intro ♪
                    [00:06.50]Tell me who can block the path ahead
                    [00:14.20]Standing tall through every controversy
                    [00:22.00]When you speak truth, they press 295
                    [00:30.80]Crowds follow like storming winds
                    [00:38.00]The voice of truth echoes forever
                    [00:46.50]Sidhu's legacy shines forever
                """.trimIndent()
            ),
            SongEntity(
                id = 1002L,
                title = "Kesariya",
                artist = "Arijit Singh",
                album = "Brahmastra Classics",
                durationMs = 268_000L,
                contentUri = "android.resource://${context.packageName}/raw/demo_track_2",
                albumArtUri = null,
                languageScript = "HINDI_MARATHI",
                moodProfile = "CHILL",
                moodScore = 0.48f,
                lrcLyrics = """
                    [00:00.00]♪ Acoustic Guitar Arpeggio ♪
                    [00:07.00]Tell me how someone could not fall for you
                    [00:15.50]God spent all treasures of beauty making you
                    [00:24.00]Your love is saffron, my beloved
                    [00:32.40]I am dyed the moment I touch it
                    [00:40.00]Spending days dreaming and nights praying for you
                    [00:48.00]♪ Flute & Strings Interlude ♪
                """.trimIndent()
            ),
            SongEntity(
                id = 1003L,
                title = "Deva Shree Ganesha",
                artist = "Ajay-Atul",
                album = "Agneepath",
                durationMs = 336_000L,
                contentUri = "android.resource://${context.packageName}/raw/demo_track_3",
                albumArtUri = null,
                languageScript = "HINDI_MARATHI",
                moodProfile = "ENERGETIC",
                moodScore = 0.95f,
                lrcLyrics = """
                    [00:00.00]♪ Shankh Naad & Powerful Dhol-Tasha ♪
                    [00:10.00]Deva Shree Ganesha, Deva Shree Ganesha!
                    [00:20.50]In the eyes of the devoted burns divine fire
                    [00:31.00]Ganpati Bappa Morya! Mangal Murti Morya!
                    [00:42.00]With divine strength every chain is broken
                    [00:54.00]♪ Climax Percussion Roll ♪
                """.trimIndent()
            ),
            SongEntity(
                id = 1004L,
                title = "Excuses",
                artist = "AP Dhillon, Gurinder Gill",
                album = "Hidden Gems",
                durationMs = 176_000L,
                contentUri = "android.resource://${context.packageName}/raw/demo_track_4",
                albumArtUri = null,
                languageScript = "PUNJABI",
                moodProfile = "UPBEAT",
                moodScore = 0.72f,
                lrcLyrics = """
                    [00:00.00]♪ Synth Wave Groove ♪
                    [00:08.00]No need to say a word, you know my heart
                    [00:15.50]Let me tell you something, girl
                    [00:23.00]When our eyes met, we talked all through the night
                    [00:31.20]Brown Munde vibes in the city night
                    [00:39.00]♪ Smooth Bass Drop ♪
                """.trimIndent()
            ),
            SongEntity(
                id = 1005L,
                title = "Sham Savere",
                artist = "Aura Acoustics",
                album = "Monsoon Reverie",
                durationMs = 210_000L,
                contentUri = "android.resource://${context.packageName}/raw/demo_track_5",
                albumArtUri = null,
                languageScript = "HINDI_MARATHI",
                moodProfile = "CALM",
                moodScore = 0.18f,
                lrcLyrics = """
                    [00:00.00]♪ Gentle Rain Sounds & Rhodes Piano ♪
                    [00:12.00]Morning and evening, your memories linger
                    [00:25.00]The gentle winds whisper a sweet melody
                    [00:38.00]Heart full of peace, surrounded by calm
                    [00:52.00]♪ Soft Acoustic Vinyl Crackle ♪
                """.trimIndent()
            )
        )
    }

    private suspend fun ensureAutoGeneratedPlaylists(songs: List<SongEntity>) {
        val favSongs = songs.filter { it.isFavorite }
        createOrPopulatePlaylist("❤️ Favorites", "Your pinned favorite tracks", "FAVORITES", favSongs)

        val punjabiSongs = songs.filter { it.languageScript == "PUNJABI" }
        val hindiSongs = songs.filter { it.languageScript == "HINDI_MARATHI" }
        val energeticSongs = songs.filter { it.moodProfile == "ENERGETIC" }
        val chillSongs = songs.filter { it.moodProfile == "CHILL" || it.moodProfile == "CALM" }

        createOrPopulatePlaylist("Punjabi Hits", "Top Punjabi hits", "PUNJABI", punjabiSongs)
        createOrPopulatePlaylist("Hindi & Marathi", "Classics & Soundtracks", "HINDI_MARATHI", hindiSongs)
        createOrPopulatePlaylist("⚡ Gym & High Energy", "Fast-paced punchy workout tracks", "MOOD_ENERGETIC", energeticSongs)
        createOrPopulatePlaylist("☕ Chill & Acoustic", "Peaceful vibes and soothing melodies", "MOOD_CHILL", chillSongs)
    }

    private suspend fun createOrPopulatePlaylist(
        name: String,
        desc: String,
        type: String,
        songs: List<SongEntity>
    ) {
        var playlist = musicDao.getPlaylistByType(type)
        val playlistId = if (playlist == null) {
            musicDao.insertPlaylist(
                PlaylistEntity(name = name, description = desc, isAutoGenerated = true, playlistType = type)
            )
        } else {
            playlist.playlistId
        }

        songs.forEachIndexed { index, song ->
            musicDao.addSongToPlaylist(
                PlaylistSongCrossRef(playlistId = playlistId, songId = song.id, sortOrder = index)
            )
        }
    }

    suspend fun insertOnlineTrack(track: SongEntity) {
        musicDao.insertSong(track)
    }

    suspend fun downloadTrack(track: SongEntity, onComplete: ((SongEntity) -> Unit)? = null) {
        musicDao.insertSong(track)
        downloadManager.downloadTrack(track, onComplete)
    }

    suspend fun importSafUris(uris: List<android.net.Uri>): Int = withContext(Dispatchers.IO) {
        val resolver = context.contentResolver
        var count = 0
        for (uri in uris) {
            try {
                try {
                    resolver.takePersistableUriPermission(uri, android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION)
                } catch (_: Exception) {}

                var displayName = "Imported Audio"
                var size = 0L
                resolver.query(uri, null, null, null, null)?.use { cursor ->
                    val nameIdx = cursor.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
                    val sizeIdx = cursor.getColumnIndex(android.provider.OpenableColumns.SIZE)
                    if (cursor.moveToFirst()) {
                        if (nameIdx != -1) displayName = cursor.getString(nameIdx) ?: displayName
                        if (sizeIdx != -1) size = cursor.getLong(sizeIdx)
                    }
                }

                val cleanTitle = MetadataSanitizer.sanitizeTitle(displayName)
                val cleanArtist = "Imported Track"
                val scriptType = ScriptLanguageDetector.detectScript(cleanTitle, cleanArtist, "")
                val uniqueId = System.currentTimeMillis() + (0..9999).random()

                val importedSong = SongEntity(
                    id = uniqueId,
                    title = cleanTitle,
                    artist = cleanArtist,
                    album = "Imported Audio",
                    durationMs = 180000L,
                    contentUri = uri.toString(),
                    dataPath = uri.path,
                    size = size,
                    dateAdded = System.currentTimeMillis() / 1000L,
                    isHiddenFromLibrary = false,
                    languageScript = scriptType.tag,
                    moodProfile = "CHILL",
                    moodScore = 0.5f,
                    sourceType = "LOCAL",
                    isDownloaded = true,
                    downloadProgress = 100
                )
                musicDao.insertSong(importedSong)
                count++
            } catch (e: Exception) {
                Log.e(TAG, "Error importing SAF URI $uri: ${e.message}", e)
            }
        }
        return@withContext count
    }
}
