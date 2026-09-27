package com.example.network

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.util.Log
import com.example.data.OnlineMusicCatalog
import com.example.data.SongEntity
import com.example.network.model.toSongEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.withContext

/**
 * Repository orchestrating online music search via JioSaavn API
 * and synchronized lyrics fetching via LRCLIB API.
 * Provides seamless offline fallbacks and Result<T> error encapsulation.
 */
class OnlineMusicRepository(
    private val context: Context? = null
) {
    private val TAG = "OnlineMusicRepository"

    /**
     * Checks if active internet connectivity is present.
     */
    fun isOnline(): Boolean {
        if (context == null) return true
        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
            ?: return false
        val activeNetwork = cm.activeNetwork ?: return false
        val capabilities = cm.getNetworkCapabilities(activeNetwork) ?: return false
        return capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
    }

    /**
     * Searches tracks online using JioSaavn API.
     * Emits [Result.success] with list of mapped [SongEntity] on success.
     * Falls back to curated catalog if network is unavailable or returns an error.
     */
    fun searchTracks(query: String): Flow<Result<List<SongEntity>>> = flow {
        val trimmedQuery = query.trim()
        if (trimmedQuery.isEmpty()) {
            emit(Result.success(OnlineMusicCatalog.getOnlineTracks()))
            return@flow
        }

        if (!isOnline()) {
            val localResults = OnlineMusicCatalog.searchTracks(trimmedQuery)
            emit(Result.success(localResults))
            return@flow
        }

        try {
            val response = NetworkClient.jioSaavnApi.searchSongs(query = trimmedQuery, page = 1, limit = 25)
            val apiSongs = response.data?.results?.map { it.toSongEntity() } ?: emptyList()

            if (apiSongs.isNotEmpty()) {
                emit(Result.success(apiSongs))
            } else {
                // Fallback to local curated catalog
                val fallbackResults = OnlineMusicCatalog.searchTracks(trimmedQuery)
                emit(Result.success(fallbackResults))
            }
        } catch (e: Exception) {
            Log.w(TAG, "Online search failed for '$trimmedQuery': ${e.message}. Using curated fallback.")
            val fallbackResults = OnlineMusicCatalog.searchTracks(trimmedQuery)
            if (fallbackResults.isNotEmpty()) {
                emit(Result.success(fallbackResults))
            } else {
                emit(Result.failure(e))
            }
        }
    }.flowOn(Dispatchers.IO)

    /**
     * Fetches real-time synchronized lyrics from LRCLIB.
     */
    suspend fun fetchLrcLibLyrics(
        title: String,
        artist: String,
        durationSecs: Long
    ): Result<String?> = withContext(Dispatchers.IO) {
        if (!isOnline()) {
            return@withContext Result.failure(IllegalStateException("Device is offline"))
        }

        try {
            val cleanTitle = title.replace(Regex("\\(.*\\)|\\[.*\\]"), "").trim()
            val cleanArtist = artist.replace(Regex("\\(.*\\)|\\[.*\\]"), "").trim()

            val response = NetworkClient.lrcLibApi.getLyrics(
                trackName = cleanTitle,
                artistName = cleanArtist,
                durationSecs = durationSecs
            )

            if (response.isSuccessful) {
                val body = response.body()
                val synced = body?.syncedLyrics?.takeIf { it.isNotBlank() }
                val plain = body?.plainLyrics?.takeIf { it.isNotBlank() }
                Result.success(synced ?: plain)
            } else if (response.code() == 404) {
                Result.success(null) // Not found
            } else {
                Result.failure(RuntimeException("LRCLIB returned HTTP ${response.code()}"))
            }
        } catch (e: Exception) {
            Log.w(TAG, "Failed to fetch LRCLIB lyrics: ${e.message}")
            Result.failure(e)
        }
    }
}
