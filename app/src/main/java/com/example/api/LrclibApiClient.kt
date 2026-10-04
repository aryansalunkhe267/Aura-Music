package com.example.api

import android.net.Uri
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit

data class LrcResult(
    val syncedLyrics: String?,
    val plainLyrics: String?,
    val trackName: String? = null,
    val artistName: String? = null
)

/**
 * Client for LRCLIB (https://lrclib.net) - free, open-source, community-driven
 * synchronized lyrics database. Eliminates flawed YouTube ASR captions ([sangeet]/[hansi]).
 */
object LrclibApiClient {

    private const val TAG = "LrclibApiClient"
    private const val BASE_URL = "https://lrclib.net/api"

    private val httpClient: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .build()

    suspend fun fetchLyrics(
        title: String,
        artist: String,
        durationSeconds: Long? = null
    ): LrcResult? = withContext(Dispatchers.IO) {
        val cleanTitle = cleanSearchTitle(title)
        val cleanArtist = cleanSearchArtist(artist)

        if (cleanTitle.isBlank()) return@withContext null

        // 1. Direct get endpoint with artist_name and track_name
        try {
            val encodedTitle = Uri.encode(cleanTitle)
            val encodedArtist = Uri.encode(cleanArtist)
            var url = "$BASE_URL/get?artist_name=$encodedArtist&track_name=$encodedTitle"
            if (durationSeconds != null && durationSeconds > 10) {
                url += "&duration=$durationSeconds"
            }

            val request = Request.Builder()
                .url(url)
                .header("User-Agent", "PulseMusic/2.0 (Android; OpenSource Music Player)")
                .build()

            httpClient.newCall(request).execute().use { response ->
                if (response.isSuccessful) {
                    val body = response.body?.string()
                    if (!body.isNullOrBlank()) {
                        val json = JSONObject(body)
                        val synced = json.optString("syncedLyrics").takeIf { it.isNotBlank() && it != "null" }
                        val plain = json.optString("plainLyrics").takeIf { it.isNotBlank() && it != "null" }
                        if (synced != null || plain != null) {
                            Log.i(TAG, "LRCLIB direct match found for '$cleanTitle' by '$cleanArtist'")
                            return@withContext LrcResult(
                                syncedLyrics = synced,
                                plainLyrics = plain,
                                trackName = json.optString("trackName"),
                                artistName = json.optString("artistName")
                            )
                        }
                    }
                }
            }
        } catch (e: Exception) {
            Log.d(TAG, "Direct get failed for '$cleanTitle': ${e.message}")
        }

        // 2. Search endpoint fallback: /api/search?q=...
        try {
            val query = if (cleanArtist.isNotBlank() && !cleanArtist.equals("Unknown Artist", ignoreCase = true)) {
                "$cleanTitle $cleanArtist"
            } else {
                cleanTitle
            }

            val searchUrl = "$BASE_URL/search?q=${Uri.encode(query)}"
            val searchRequest = Request.Builder()
                .url(searchUrl)
                .header("User-Agent", "PulseMusic/2.0 (Android; OpenSource Music Player)")
                .build()

            httpClient.newCall(searchRequest).execute().use { response ->
                if (response.isSuccessful) {
                    val body = response.body?.string()
                    if (!body.isNullOrBlank()) {
                        val array = JSONArray(body)
                        for (i in 0 until array.length()) {
                            val item = array.getJSONObject(i)
                            val synced = item.optString("syncedLyrics").takeIf { it.isNotBlank() && it != "null" }
                            val plain = item.optString("plainLyrics").takeIf { it.isNotBlank() && it != "null" }
                            if (synced != null) {
                                Log.i(TAG, "LRCLIB search match found for '$query'")
                                return@withContext LrcResult(
                                    syncedLyrics = synced,
                                    plainLyrics = plain,
                                    trackName = item.optString("trackName"),
                                    artistName = item.optString("artistName")
                                )
                            }
                        }
                    }
                }
            }
        } catch (e: Exception) {
            Log.d(TAG, "Search fallback failed for '$cleanTitle': ${e.message}")
        }

        return@withContext null
    }

    private fun cleanSearchTitle(title: String): String {
        return title
            .replace(Regex("\\[.*?\\]"), "")
            .replace(Regex("\\(Official.*?\\)", RegexOption.IGNORE_CASE), "")
            .replace(Regex("\\(Music Video.*?\\)", RegexOption.IGNORE_CASE), "")
            .replace(Regex("\\(Audio.*?\\)", RegexOption.IGNORE_CASE), "")
            .replace(Regex("\\(Lyric Video.*?\\)", RegexOption.IGNORE_CASE), "")
            .replace(Regex("\\(Visualizer.*?\\)", RegexOption.IGNORE_CASE), "")
            .replace(Regex("\\(Full Song.*?\\)", RegexOption.IGNORE_CASE), "")
            .replace(Regex("\\(Remastered.*?\\)", RegexOption.IGNORE_CASE), "")
            .replace(Regex("ft\\..*|feat\\..*", RegexOption.IGNORE_CASE), "")
            .replace(Regex("\\|.*"), "")
            .replace(Regex(" - .*"), "")
            .trim()
    }

    private fun cleanSearchArtist(artist: String): String {
        return artist
            .replace(Regex(" - Topic$", RegexOption.IGNORE_CASE), "")
            .replace(Regex("VEVO$", RegexOption.IGNORE_CASE), "")
            .trim()
    }
}
