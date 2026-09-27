package com.example.engine

import android.content.Context
import android.net.Uri
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.example.data.MusicDatabase
import com.example.data.SongEntity
import com.example.utils.ArtworkHelper
import com.example.utils.Id3TagWriter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.net.URLEncoder
import java.util.concurrent.TimeUnit

/**
 * Background WorkManager task detecting offline songs missing album art.
 * Queries iTunes Search API and JioSaavn API for high-resolution 600x600/1000x1000 artwork,
 * downloads and caches it locally, updates Room DB, and physically injects APIC frames into local MP3s.
 */
class ArtworkAutoFetchWorker(
    appContext: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(appContext, workerParams) {

    private val TAG = "ArtworkAutoFetch"

    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .build()

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        val database = MusicDatabase.getDatabase(applicationContext)
        val musicDao = database.musicDao()

        val artCacheDir = File(applicationContext.cacheDir, "artwork").apply { mkdirs() }

        try {
            val songs = musicDao.getAllSongsUnlimited()
            var enrichedCount = 0

            for (song in songs) {
                // Check if already has album art
                val cachedFile = File(artCacheDir, "${song.id}.jpg")
                val hasArt = (cachedFile.exists() && cachedFile.length() > 1000) ||
                        (!song.albumArtUri.isNullOrBlank() && !song.albumArtUri.contains("default", ignoreCase = true)) ||
                        (!song.coverArtUrl.isNullOrBlank())

                if (!hasArt) {
                    val cleanTitle = song.title.replace(Regex("""\s*\([^)]*\)"""), "").trim()
                    val cleanArtist = if (song.artist.contains("Unknown", ignoreCase = true)) "" else song.artist.trim()

                    // 1. Try iTunes Search API (Best for Bollywood, Punjabi, Pop high-res 600x600 artwork)
                    var highResArtUrl = fetchItunesCoverArt(cleanTitle, cleanArtist)

                    // 2. Fallback to Deezer
                    if (highResArtUrl.isNullOrBlank()) {
                        highResArtUrl = fetchDeezerCoverArt(cleanTitle, cleanArtist)
                    }

                    if (!highResArtUrl.isNullOrBlank()) {
                        val downloadedBytes = downloadImageBytes(highResArtUrl)
                        if (downloadedBytes != null && downloadedBytes.isNotEmpty()) {
                            // Save to local cache
                            FileOutputStream(cachedFile).use { out ->
                                out.write(downloadedBytes)
                                out.flush()
                            }
                            val cachedUri = Uri.fromFile(cachedFile).toString()

                            // Update Room DB
                            musicDao.updateCoverArt(song.id, cachedUri)

                            // Also write APIC physical tag into local MP3 if accessible
                            val localPath = song.dataPath ?: song.localPath
                            if (!localPath.isNullOrBlank()) {
                                val mp3File = File(localPath)
                                if (mp3File.exists() && mp3File.canWrite()) {
                                    Id3TagWriter.writeTags(
                                        mp3File = mp3File,
                                        title = song.title,
                                        artist = song.artist,
                                        album = song.album,
                                        coverArtBytes = downloadedBytes
                                    )
                                }
                            }

                            enrichedCount++
                            Log.i(TAG, "Successfully auto-fetched cover art for '${song.title}' -> $cachedUri")
                            delay(350L) // Rate limiting
                        }
                    }
                }
            }

            Log.i(TAG, "ArtworkAutoFetchWorker finished. Enriched $enrichedCount tracks.")
            Result.success()
        } catch (e: Exception) {
            Log.e(TAG, "ArtworkAutoFetchWorker error: ${e.message}", e)
            Result.retry()
        }
    }

    private fun fetchItunesCoverArt(title: String, artist: String): String? {
        return try {
            val query = if (artist.isBlank()) title else "$artist $title"
            val encoded = URLEncoder.encode(query, "UTF-8")
            val url = "https://itunes.apple.com/search?term=$encoded&entity=song&limit=1"

            val request = Request.Builder()
                .url(url)
                .header("User-Agent", "PulseMusic/1.0")
                .build()

            httpClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return null
                val body = response.body?.string() ?: return null
                val json = JSONObject(body)
                val results = json.optJSONArray("results")
                if (results != null && results.length() > 0) {
                    val first = results.getJSONObject(0)
                    val art100 = first.optString("artworkUrl100")
                    if (art100.isNotBlank()) {
                        // Replace 100x100 with 600x600 for crisp high-resolution artwork
                        art100.replace("100x100bb.jpg", "600x600bb.jpg")
                    } else null
                } else null
            }
        } catch (e: Exception) {
            null
        }
    }

    private fun fetchDeezerCoverArt(title: String, artist: String): String? {
        return try {
            val query = if (artist.isBlank()) title else "$artist $title"
            val encoded = URLEncoder.encode(query, "UTF-8")
            val url = "https://api.deezer.com/search?q=$encoded&limit=1"

            val request = Request.Builder()
                .url(url)
                .header("User-Agent", "PulseMusic/1.0")
                .build()

            httpClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return null
                val body = response.body?.string() ?: return null
                val json = JSONObject(body)
                val data = json.optJSONArray("data")
                if (data != null && data.length() > 0) {
                    val item = data.getJSONObject(0)
                    val album = item.optJSONObject("album")
                    album?.optString("cover_xl")?.takeIf { it.isNotBlank() }
                        ?: album?.optString("cover_big")?.takeIf { it.isNotBlank() }
                } else null
            }
        } catch (e: Exception) {
            null
        }
    }

    private fun downloadImageBytes(imageUrl: String): ByteArray? {
        return try {
            val request = Request.Builder().url(imageUrl).build()
            httpClient.newCall(request).execute().use { response ->
                if (response.isSuccessful) {
                    response.body?.bytes()
                } else null
            }
        } catch (e: Exception) {
            null
        }
    }
}
