package com.example.data

import android.content.Context
import android.net.Uri
import android.os.Environment
import android.util.Log
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
 * Native offline music downloader that downloads online audio streams directly into
 * device storage and indexes them into the local Room database for offline playback.
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
     * Downloads an online track in the background and saves it to offline storage.
     */
    fun downloadTrack(song: SongEntity, onCompleted: ((SongEntity) -> Unit)? = null) {
        if (song.isDownloaded && !song.localPath.isNullOrBlank() && File(song.localPath).exists()) {
            Log.d(TAG, "Track ${song.title} is already downloaded.")
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
                val targetDir = context.getExternalFilesDir(Environment.DIRECTORY_MUSIC)
                    ?: File(context.filesDir, "Music")
                if (!targetDir.exists()) {
                    targetDir.mkdirs()
                }

                // Clean filename from title
                val safeFileName = "${song.id}_" + song.title.replace(Regex("[^a-zA-Z0-9.-]"), "_") + ".mp3"
                val destinationFile = File(targetDir, safeFileName)

                val url = URL(streamUrl)
                val connection = url.openConnection() as HttpURLConnection
                connection.connectTimeout = 15000
                connection.readTimeout = 20000
                connection.instanceFollowRedirects = true
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
                        val progress = ((downloadedBytes * 100) / totalLength).toInt().coerceIn(5, 99)
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

                val localFilePath = destinationFile.absolutePath
                val localContentUri = Uri.fromFile(destinationFile).toString()

                // Update Room database with local offline path
                musicDao.updateDownloadStatus(
                    songId = song.id,
                    progress = 100,
                    isDownloaded = true,
                    localPath = localFilePath,
                    contentUri = localContentUri
                )

                val updatedSong = song.copy(
                    localPath = localFilePath,
                    contentUri = localContentUri,
                    isDownloaded = true,
                    downloadProgress = 100
                )

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
