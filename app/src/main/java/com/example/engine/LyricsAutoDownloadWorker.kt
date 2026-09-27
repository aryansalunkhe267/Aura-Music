package com.example.engine

import android.content.Context
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.example.data.MusicDatabase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.io.File
import java.net.URLEncoder
import java.util.concurrent.TimeUnit

/**
 * Automated WorkManager task that runs when an active network connection is present.
 * Scans offline tracks lacking embedded lyrics, queries the LrcLib API (https://lrclib.net/api/get),
 * and caches downloaded .lrc files locally in the app's internal storage for future offline access.
 */
class LyricsAutoDownloadWorker(
    appContext: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(appContext, workerParams) {

    private val TAG = "LyricsAutoDownload"

    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .build()

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        val database = MusicDatabase.getDatabase(applicationContext)
        val musicDao = database.musicDao()

        val lyricsDir = File(applicationContext.filesDir, "lyrics").apply { mkdirs() }

        try {
            val songs = musicDao.getAllSongsUnlimited()
            var downloadedCount = 0

            for (song in songs) {
                // If song already has synced lyrics in DB, ensure cached on disk
                val cachedFile = File(lyricsDir, "${song.id}.lrc")

                if (song.syncedLyrics.isNullOrBlank() && song.lrcLyrics.isNullOrBlank()) {
                    // Check if already cached on disk from previous run
                    if (cachedFile.exists() && cachedFile.length() > 0) {
                        val diskLyrics = cachedFile.readText()
                        musicDao.updateLyrics(song.id, diskLyrics)
                        continue
                    }

                    // Query LrcLib API
                    val lyrics = fetchFromLrcLib(song.title, song.artist, song.durationMs / 1000)
                    if (!lyrics.isNullOrBlank()) {
                        // Cache locally on disk in app's internal storage
                        try {
                            cachedFile.writeText(lyrics)
                        } catch (e: Exception) {
                            Log.e(TAG, "Failed writing lyrics to internal storage: ${e.message}")
                        }

                        // Persist to Room
                        musicDao.updateLyrics(song.id, lyrics)
                        downloadedCount++
                        Log.d(TAG, "Successfully auto-downloaded & cached .lrc for '${song.title}'")

                        // Polite rate-limiting between API calls
                        delay(400L)
                    }
                } else {
                    // Cache existing DB lyrics to disk if not yet saved
                    if (!cachedFile.exists()) {
                        val currentLyrics = song.syncedLyrics ?: song.lrcLyrics
                        if (!currentLyrics.isNullOrBlank()) {
                            try {
                                cachedFile.writeText(currentLyrics)
                            } catch (_: Exception) {}
                        }
                    }
                }
            }

            Log.i(TAG, "WorkManager completed lyrics sync. Downloaded $downloadedCount new track lyrics.")
            Result.success()
        } catch (e: Exception) {
            Log.e(TAG, "LyricsAutoDownloadWorker failed: ${e.message}", e)
            Result.retry()
        }
    }

    private fun fetchFromLrcLib(title: String, artist: String, durationSecs: Long): String? {
        return try {
            val encodedTitle = URLEncoder.encode(title.trim(), "UTF-8")
            val cleanArtist = if (artist.contains("Unknown", ignoreCase = true)) "" else artist.trim()
            val encodedArtist = URLEncoder.encode(cleanArtist, "UTF-8")
            val url = "https://lrclib.net/api/get?track_name=$encodedTitle&artist_name=$encodedArtist&duration=$durationSecs"

            val request = Request.Builder()
                .url(url)
                .header("User-Agent", "PulseMusic/1.0 (Android Music Player; support@pulsemusic.app)")
                .build()

            httpClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return null
                val body = response.body?.string() ?: return null
                val json = JSONObject(body)
                val synced = json.optString("syncedLyrics")
                if (synced.isNotBlank()) synced else json.optString("plainLyrics").takeIf { it.isNotBlank() }
            }
        } catch (e: Exception) {
            Log.d(TAG, "LrcLib query failed for $title: ${e.message}")
            null
        }
    }
}
