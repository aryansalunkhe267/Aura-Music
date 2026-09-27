package com.example.data

import android.content.ContentValues
import android.content.Context
import android.media.MediaScannerConnection
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.util.Log
import com.example.utils.ArtworkHelper
import com.example.utils.Id3TagWriter
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.URL

/**
 * Native offline music downloader that downloads online audio streams (320kbps MP3) directly into
 * the device's public Music directory, embeds physical ID3 tags & APIC cover art,
 * inserts the track into MediaStore.Audio.Media, and updates the local Room database for instant offline access.
 */
class MusicDownloadManager(
    private val context: Context,
    private val musicDao: MusicDao
) {
    private val TAG = "MusicDownloadManager"
    private val scope = CoroutineScope(Dispatchers.IO + Job())

    // Tracks download progress per songId: 0 to 100
    private val _downloadProgressMap = MutableStateFlow<Map<Long, Int>>(emptyMap())
    val downloadProgressMap: StateFlow<Map<Long, Int>> = _downloadProgressMap.asStateFlow()

    // Active downloading jobs
    private val activeJobs = mutableMapOf<Long, Job>()

    /**
     * Downloads an online track in the background directly into public Music directory.
     */
    fun downloadTrack(song: SongEntity, onCompleted: ((SongEntity) -> Unit)? = null) {
        if (song.isDownloaded && !song.localPath.isNullOrBlank() && File(song.localPath).exists()) {
            Log.d(TAG, "Track ${song.title} is already downloaded.")
            onCompleted?.invoke(song)
            return
        }

        val streamUrl = song.streamUrl ?: song.contentUri
        if (!streamUrl.startsWith("http://") && !streamUrl.startsWith("https://")) {
            Log.w(TAG, "Cannot download non-http stream URL: $streamUrl")
            return
        }

        // Cancel previous job for this song if any
        activeJobs[song.id]?.cancel()

        val job = scope.launch {
            try {
                updateProgress(song.id, 5)

                // 1. Determine public Music directory
                val publicMusicDir = try {
                    val baseDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MUSIC)
                    val pulseDir = File(baseDir, "PulseMusic")
                    if (!pulseDir.exists()) pulseDir.mkdirs()
                    if (pulseDir.exists() && pulseDir.canWrite()) pulseDir else baseDir
                } catch (_: Exception) {
                    null
                } ?: context.getExternalFilesDir(Environment.DIRECTORY_MUSIC)
                ?: File(context.filesDir, "Music")

                if (!publicMusicDir.exists()) {
                    publicMusicDir.mkdirs()
                }

                // Clean filename from title and artist
                val cleanTitle = song.title.replace(Regex("[^a-zA-Z0-9.-]"), "_")
                val cleanArtist = song.artist.replace(Regex("[^a-zA-Z0-9.-]"), "_")
                val safeFileName = "${cleanTitle}_${cleanArtist}_320kbps.mp3"
                val destinationFile = File(publicMusicDir, safeFileName)

                // 2. Download 320kbps stream with chunked progress reporting
                val url = URL(streamUrl)
                val connection = url.openConnection() as HttpURLConnection
                connection.connectTimeout = 15000
                connection.readTimeout = 25000
                connection.instanceFollowRedirects = true
                connection.setRequestProperty("User-Agent", "PulseMusic/2.0")
                connection.connect()

                if (connection.responseCode !in 200..299) {
                    throw IllegalStateException("Server returned HTTP ${connection.responseCode}")
                }

                val totalLength = connection.contentLength.toLong()
                var downloadedBytes = 0L

                val inputStream: InputStream = connection.inputStream
                val outputStream = FileOutputStream(destinationFile)

                val buffer = ByteArray(8 * 1024)
                var bytesRead: Int
                var lastProgress = 5

                while (inputStream.read(buffer).also { bytesRead = it } != -1) {
                    outputStream.write(buffer, 0, bytesRead)
                    downloadedBytes += bytesRead

                    if (totalLength > 0) {
                        val progress = ((downloadedBytes * 85) / totalLength).toInt().coerceIn(5, 85)
                        if (progress > lastProgress) {
                            lastProgress = progress
                            updateProgress(song.id, progress)
                        }
                    }
                }

                outputStream.flush()
                outputStream.close()
                inputStream.close()
                connection.disconnect()

                updateProgress(song.id, 90)

                // 3. Physical ID3 Tag & APIC Album Art writing
                try {
                    val artUrl = song.coverArtUrl ?: song.albumArtUri
                    val artBytes = if (!artUrl.isNullOrBlank() && artUrl.startsWith("http")) {
                        try {
                            val artConn = URL(artUrl).openConnection() as HttpURLConnection
                            artConn.connectTimeout = 10000
                            artConn.readTimeout = 10000
                            if (artConn.responseCode in 200..299) {
                                artConn.inputStream.use { it.readBytes() }
                            } else null
                        } catch (_: Exception) {
                            null
                        }
                    } else null

                    Id3TagWriter.writeTags(
                        mp3File = destinationFile,
                        title = song.title,
                        artist = song.artist,
                        album = song.album,
                        coverArtBytes = artBytes
                    )
                } catch (e: Exception) {
                    Log.w(TAG, "Could not write ID3 tags to downloaded file: ${e.message}")
                }

                updateProgress(song.id, 95)

                // 4. Auto-Sync: Insert file into MediaStore.Audio.Media & trigger system media scan
                var mediaStoreContentUri: Uri? = null
                try {
                    val values = ContentValues().apply {
                        put(MediaStore.Audio.Media.TITLE, song.title)
                        put(MediaStore.Audio.Media.ARTIST, song.artist)
                        put(MediaStore.Audio.Media.ALBUM, song.album)
                        put(MediaStore.Audio.Media.MIME_TYPE, "audio/mpeg")
                        put(MediaStore.Audio.Media.DATA, destinationFile.absolutePath)
                        put(MediaStore.Audio.Media.IS_MUSIC, 1)
                        put(MediaStore.Audio.Media.DATE_ADDED, System.currentTimeMillis() / 1000)
                        put(MediaStore.Audio.Media.SIZE, destinationFile.length())
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                            put(MediaStore.Audio.Media.RELATIVE_PATH, "Music/PulseMusic")
                        }
                    }
                    mediaStoreContentUri = context.contentResolver.insert(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, values)
                } catch (e: Exception) {
                    Log.w(TAG, "MediaStore insert fallback to MediaScanner: ${e.message}")
                }

                // Scan with MediaScannerConnection for universal Android MediaStore integration
                MediaScannerConnection.scanFile(
                    context,
                    arrayOf(destinationFile.absolutePath),
                    arrayOf("audio/mpeg")
                ) { path, uri ->
                    Log.d(TAG, "MediaScanner finished: $path -> $uri")
                }

                val finalContentUri = mediaStoreContentUri?.toString() ?: Uri.fromFile(destinationFile).toString()
                val localFilePath = destinationFile.absolutePath

                // 5. Update Room database with offline path and status
                musicDao.updateDownloadStatus(
                    songId = song.id,
                    progress = 100,
                    isDownloaded = true,
                    localPath = localFilePath,
                    contentUri = finalContentUri
                )

                val updatedSong = song.copy(
                    localPath = localFilePath,
                    contentUri = finalContentUri,
                    sourceType = "LOCAL",
                    isDownloaded = true,
                    downloadProgress = 100
                )
                musicDao.insertSong(updatedSong)

                updateProgress(song.id, 100)
                Log.d(TAG, "Successfully downloaded: ${song.title} to $localFilePath")

                withContext(Dispatchers.Main) {
                    onCompleted?.invoke(updatedSong)
                }

            } catch (e: Exception) {
                Log.e(TAG, "Error downloading ${song.title}: ${e.message}", e)
                updateProgress(song.id, 0)
                musicDao.updateDownloadStatus(
                    songId = song.id,
                    progress = 0,
                    isDownloaded = false,
                    localPath = null,
                    contentUri = song.contentUri
                )
            } finally {
                activeJobs.remove(song.id)
            }
        }

        activeJobs[song.id] = job
    }

    private suspend fun updateProgress(songId: Long, progress: Int) {
        val current = _downloadProgressMap.value.toMutableMap()
        current[songId] = progress
        _downloadProgressMap.value = current
    }

    fun isDownloading(songId: Long): Boolean {
        return activeJobs.containsKey(songId)
    }

    fun getProgress(songId: Long): Int {
        return _downloadProgressMap.value[songId] ?: 0
    }
}
