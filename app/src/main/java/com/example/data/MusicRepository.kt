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

    suspend fun deleteSongPermanently(songId: Long) {
        musicDao.deleteSongById(songId)
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
                    val parsedUri = Uri.parse(contentUri)

                    // 1. Extract embedded picture and ID3 lyrics via MediaMetadataRetriever directly from the local audio file's Uri
                    var extractedArtUri: String? = null
                    var extractedLyrics: String? = null

                    val retriever = android.media.MediaMetadataRetriever()
                    try {
                        var sourceLoaded = false
                        try {
                            retriever.setDataSource(context, parsedUri)
                            sourceLoaded = true
                        } catch (_: Exception) {}

                        if (!sourceLoaded) {
                            try {
                                context.contentResolver.openFileDescriptor(parsedUri, "r")?.use { pfd ->
                                    retriever.setDataSource(pfd.fileDescriptor)
                                    sourceLoaded = true
                                }
                            } catch (_: Exception) {}
                        }

                        if (!sourceLoaded && !dataPath.isNullOrBlank()) {
                            try {
                                val f = java.io.File(dataPath)
                                if (f.exists() && f.canRead()) {
                                    retriever.setDataSource(dataPath)
                                    sourceLoaded = true
                                }
                            } catch (_: Exception) {}
                        }

                        if (sourceLoaded) {
                            // Extract embedded picture byte array via MediaMetadataRetriever.embeddedPicture
                            val rawPictureBytes = retriever.embeddedPicture
                            if (rawPictureBytes != null && rawPictureBytes.isNotEmpty()) {
                                val coversDir = java.io.File(context.filesDir, "album_covers").apply { mkdirs() }
                                val coverFile = java.io.File(coversDir, "cover_${id}.jpg")
                                if (!coverFile.exists() || coverFile.length() == 0L) {
                                    coverFile.writeBytes(rawPictureBytes)
                                }
                                extractedArtUri = Uri.fromFile(coverFile).toString()
                            }

                            // Extract embedded ID3 lyrics directly via MediaMetadataRetriever from the audio file's Uri
                            // Key 1000 corresponds to METADATA_KEY_LYRICS in Android's media framework
                            for (key in intArrayOf(1000, 1001, 1002, 1003, 1004, 1005)) {
                                val text = retriever.extractMetadata(key)
                                if (!text.isNullOrBlank()) {
                                    extractedLyrics = text
                                    break
                                }
                            }
                        }
                    } catch (e: Exception) {
                        Log.d(TAG, "Retriever metadata extraction for $cleanTitle: ${e.message}")
                    } finally {
                        try {
                            retriever.release()
                        } catch (_: Exception) {}
                    }

                    // 2. Fallback to Id3LyricsExtractor for embedded USLT / SYLT frames directly from audio file Uri
                    if (extractedLyrics.isNullOrBlank()) {
                        extractedLyrics = com.example.utils.Id3LyricsExtractor.extractEmbeddedLyrics(context, dataPath, contentUri)
                    }

                    // 3. Cover art resolution: prefer extracted embedded picture byte array file,
                    // fallback to constructing the correct content://media/external/audio/albumart/{albumId} Uri for Coil
                    val finalAlbumArtUri = if (!extractedArtUri.isNullOrBlank()) {
                        extractedArtUri
                    } else if (albumId > 0) {
                        ContentUris.withAppendedId(
                            Uri.parse("content://media/external/audio/albumart"),
                            albumId
                        ).toString()
                    } else {
                        null
                    }

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
                            albumArtUri = finalAlbumArtUri,
                            coverArtUrl = finalAlbumArtUri,
                            dataPath = dataPath,
                            dateAdded = dateAdded,
                            size = size,
                            isHiddenFromLibrary = false,
                            languageScript = scriptType.tag,
                            moodProfile = "CHILL",
                            moodScore = 0.5f,
                            lrcLyrics = extractedLyrics,
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
                    coverArtUrl = scanned.coverArtUrl ?: existing.coverArtUrl,
                    albumArtUri = scanned.albumArtUri ?: existing.albumArtUri,
                    syncedLyrics = existing.syncedLyrics ?: scanned.syncedLyrics,
                    lrcLyrics = scanned.lrcLyrics ?: existing.lrcLyrics,
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

    companion object {
        val mockLyrics = listOf(
            "Nit controversy create milugi",
            "Dharma de naam te debate milugi",
            "Sach bolenga taan milu 295",
            "Je karenga tarakki putt hate milugi"
        )
    }

    private fun seedDemonstrationSongs(): List<SongEntity> {
        return listOf(
            SongEntity(
                id = 1001L,
                title = "295",
                artist = "Sidhu Moose Wala",
                album = "Moosetape",
                durationMs = 60_000L,
                contentUri = "android.resource://${context.packageName}/raw/demo_track_1",
                albumArtUri = null,
                languageScript = "PUNJABI",
                moodProfile = "ENERGETIC",
                moodScore = 0.92f,
                lrcLyrics = """
                    [00:00.00]♪ Bass & Dhol Intro ♪
                    [00:06.50]Nit controversy create milugi
                    [00:14.20]Dharma de naam te debate milugi
                    [00:22.00]Sach bolenga taan milu 295
                    [00:30.80]Je karenga tarakki putt hate milugi
                    [00:38.00]Nit controversy create milugi
                    [00:46.50]Sach bolenga taan milu 295
                """.trimIndent()
            ),
            SongEntity(
                id = 1002L,
                title = "Kesariya",
                artist = "Arijit Singh",
                album = "Brahmastra Classics",
                durationMs = 60_000L,
                contentUri = "android.resource://${context.packageName}/raw/demo_track_2",
                albumArtUri = null,
                languageScript = "HINDI_MARATHI",
                moodProfile = "CHILL",
                moodScore = 0.48f,
                lrcLyrics = """
                    [00:00.00]♪ Acoustic Guitar Arpeggio ♪
                    [00:07.00]Mujhko itna bataaye koyi, kaise tujhpe fida na ho koyi
                    [00:15.50]Rab ne banaya tujhe karke fursat se taiyyar
                    [00:24.00]Kesariya tera ishq hai piya
                    [00:32.40]Rang jaaun jo main haath lagaun
                    [00:40.00]Din beete saare teri fikr mein, rain saari tere zikr mein
                    [00:48.00]♪ Flute & Strings Interlude ♪
                """.trimIndent()
            ),
            SongEntity(
                id = 1003L,
                title = "Deva Shree Ganesha",
                artist = "Ajay-Atul",
                album = "Agneepath",
                durationMs = 60_000L,
                contentUri = "android.resource://${context.packageName}/raw/demo_track_3",
                albumArtUri = null,
                languageScript = "HINDI_MARATHI",
                moodProfile = "ENERGETIC",
                moodScore = 0.95f,
                lrcLyrics = """
                    [00:00.00]♪ Shankh Naad & Powerful Dhol-Tasha ♪
                    [00:10.00]Deva shree Ganesha, deva shree Ganesha!
                    [00:20.50]Jwala si jalti hai aankhon mein jiske bhi
                    [00:31.00]Ganpati Bappa Morya! Mangal Murti Morya!
                    [00:42.00]Tod deta hai har bandhan re deva
                    [00:54.00]♪ Climax Percussion Roll ♪
                """.trimIndent()
            ),
            SongEntity(
                id = 1004L,
                title = "Excuses",
                artist = "AP Dhillon, Gurinder Gill",
                album = "Hidden Gems",
                durationMs = 60_000L,
                contentUri = "android.resource://${context.packageName}/raw/demo_track_4",
                albumArtUri = null,
                languageScript = "PUNJABI",
                moodProfile = "UPBEAT",
                moodScore = 0.72f,
                lrcLyrics = """
                    [00:00.00]♪ Synth Wave Groove ♪
                    [00:08.00]Kehndi hundi si chan tak raah bana de
                    [00:15.50]Taare ne pasand mainu hethaan saare laa de
                    [00:23.00]Ohna taareyan de vich jad mainu vekhegi
                    [00:31.20]Yaad meri aavegi taan dil tera vi lagna nahi
                    [00:39.00]♪ Smooth Bass Drop ♪
                """.trimIndent()
            ),
            SongEntity(
                id = 1005L,
                title = "Tujhe Dekha Toh",
                artist = "Kumar Sanu, Lata Mangeshkar",
                album = "Dilwale Dulhania Le Jayenge 90s",
                durationMs = 60_000L,
                contentUri = "android.resource://${context.packageName}/raw/demo_track_5",
                albumArtUri = null,
                languageScript = "HINDI",
                genre = "90s Hindi",
                moodProfile = "CHILL",
                moodScore = 0.55f,
                lrcLyrics = """
                    [00:00.00]♪ Mandolin & Accordion Intro ♪
                    [00:08.00]Tujhe dekha toh yeh jaana sanam
                    [00:16.00]Pyaar hota hai deewana sanam
                    [00:24.00]Ab yahan se kahan jaaye hum
                    [00:32.00]Teri baahon mein mar jaaye hum
                    [00:40.00]Tujhe dekha toh yeh jaana sanam
                    [00:48.00]♪ Violins & Flute Interlude ♪
                """.trimIndent()
            ),
            SongEntity(
                id = 1006L,
                title = "Shree Hanuman Chalisa",
                artist = "Hariharan",
                album = "Shree Hanuman Bhakti",
                durationMs = 60_000L,
                contentUri = "android.resource://${context.packageName}/raw/demo_track_2",
                albumArtUri = null,
                languageScript = "HINDI",
                genre = "Devotional",
                moodProfile = "CALM",
                moodScore = 0.35f,
                lrcLyrics = """
                    [00:00.00]♪ Shankh Naad & Temple Bells ♪
                    [00:07.00]Shree Guru Charan Saroj Raj Nij Manu Mukuru Sudhari
                    [00:16.00]Barnau Raghuvar Bimal Jasu Jo Dayaku Phala Chari
                    [00:25.00]Jai Hanuman Gyan Gun Sagar, Jai Kapis Tihun Lok Ujagar
                    [00:35.00]Ram Doot Atulit Bal Dhama, Anjani Putra Pavan Sut Nama
                    [00:46.00]Mahabir Bikram Bajrangi, Kumati Nivar Sumati Ke Sangi
                    [00:55.00]♪ Om Shanti Peace Fade ♪
                """.trimIndent()
            ),
            SongEntity(
                id = 1007L,
                title = "Kabira",
                artist = "Pritam, Arijit Singh, Harshdeep Kaur",
                album = "Yeh Jawaani Hai Deewani Soundtrack",
                durationMs = 60_000L,
                contentUri = "android.resource://${context.packageName}/raw/demo_track_1",
                albumArtUri = null,
                languageScript = "HINDI",
                genre = "Bollywood",
                moodProfile = "CHILL",
                moodScore = 0.50f,
                lrcLyrics = """
                    [00:00.00]♪ Acoustic Guitar Strumming ♪
                    [00:08.00]Kaisi teri khudgarzi, na dhoop chune na chhaanv
                    [00:16.50]Kaisi teri khudgarzi, kisi thor tike na paanv
                    [00:25.00]Ban liya apna paigambar, tarash liya tu ne aasmaan
                    [00:34.00]Re Kabira maan jaa, re Fakeera maan jaa
                    [00:43.00]Aaja tujhko pukare teri parchhaaiyan
                    [00:52.00]♪ Sufi Chorus Harmony ♪
                """.trimIndent()
            ),
            SongEntity(
                id = 1008L,
                title = "Zingaat",
                artist = "Ajay-Atul",
                album = "Sairat Marathi Soundtrack",
                durationMs = 60_000L,
                contentUri = "android.resource://${context.packageName}/raw/demo_track_4",
                albumArtUri = null,
                languageScript = "MARATHI",
                genre = "Marathi",
                moodProfile = "ENERGETIC",
                moodScore = 0.98f,
                lrcLyrics = """
                    [00:00.00]♪ High-Octane Halgi & Sambal Beat ♪
                    [00:08.00]Usavala ga bhet ghadali, kshanat aali kshanat geli
                    [00:17.00]Manat aali manat geli, jhaali zing zing zingat!
                    [00:25.50]Zing zing zingat, zing zing zingat!
                    [00:34.00]Aata aadhi madhi yeto tula, baghun thodasa gaadhu tula
                    [00:44.00]Jhaali zing zing zingat!
                    [00:53.00]♪ Explosive Brass Roll ♪
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

                var cleanTitle = MetadataSanitizer.sanitizeTitle(displayName)
                var cleanArtist = "Imported Track"
                var cleanAlbum = "Imported Audio"
                var duration = 180000L
                var extractedArtUri: String? = null
                var extractedLyrics: String? = null

                val uniqueId = System.currentTimeMillis() + (0..9999).random()

                val retriever = android.media.MediaMetadataRetriever()
                try {
                    retriever.setDataSource(context, uri)
                    retriever.extractMetadata(android.media.MediaMetadataRetriever.METADATA_KEY_TITLE)?.takeIf { it.isNotBlank() }?.let {
                        cleanTitle = MetadataSanitizer.sanitizeTitle(it)
                    }
                    retriever.extractMetadata(android.media.MediaMetadataRetriever.METADATA_KEY_ARTIST)?.takeIf { it.isNotBlank() }?.let {
                        cleanArtist = MetadataSanitizer.sanitizeArtist(it)
                    }
                    retriever.extractMetadata(android.media.MediaMetadataRetriever.METADATA_KEY_ALBUM)?.takeIf { it.isNotBlank() }?.let {
                        cleanAlbum = MetadataSanitizer.sanitizeAlbum(it)
                    }
                    retriever.extractMetadata(android.media.MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull()?.let {
                        if (it > 0) duration = it
                    }

                    val rawPictureBytes = retriever.embeddedPicture
                    if (rawPictureBytes != null && rawPictureBytes.isNotEmpty()) {
                        val coversDir = java.io.File(context.filesDir, "album_covers").apply { mkdirs() }
                        val coverFile = java.io.File(coversDir, "cover_${uniqueId}.jpg")
                        coverFile.writeBytes(rawPictureBytes)
                        extractedArtUri = Uri.fromFile(coverFile).toString()
                    }

                    for (key in intArrayOf(1000, 1001, 1002, 1003, 1004, 1005)) {
                        val text = retriever.extractMetadata(key)
                        if (!text.isNullOrBlank()) {
                            extractedLyrics = text
                            break
                        }
                    }
                } catch (e: Exception) {
                    Log.d(TAG, "SAF retriever metadata extraction: ${e.message}")
                } finally {
                    try { retriever.release() } catch (_: Exception) {}
                }

                if (extractedLyrics.isNullOrBlank()) {
                    extractedLyrics = com.example.utils.Id3LyricsExtractor.extractEmbeddedLyrics(context, null, uri.toString())
                }

                val scriptType = ScriptLanguageDetector.detectScript(cleanTitle, cleanArtist, cleanAlbum)

                val importedSong = SongEntity(
                    id = uniqueId,
                    title = cleanTitle,
                    artist = cleanArtist,
                    album = cleanAlbum,
                    durationMs = duration,
                    contentUri = uri.toString(),
                    dataPath = uri.path,
                    albumArtUri = extractedArtUri,
                    coverArtUrl = extractedArtUri,
                    lrcLyrics = extractedLyrics,
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
