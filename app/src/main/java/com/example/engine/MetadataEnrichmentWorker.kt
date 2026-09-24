package com.example.engine

import android.content.Context
import android.media.MediaMetadataRetriever
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.net.Uri
import android.util.Log
import com.example.data.MusicDao
import com.example.data.SongEntity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.io.File
import java.net.URLEncoder
import java.util.concurrent.TimeUnit

/**
 * Background auto online metadata and synced lyrics enricher.
 * Monitors network connectivity and enriches local tracks with:
 * - High-resolution cover art (Deezer public API)
 * - Synced .lrc lyrics (LRCLIB open-source API)
 * - Verified artist and genre
 * Includes offline fallback to embedded ID3 frames and sidecar .lrc files.
 */
class MetadataEnrichmentWorker(
    private val context: Context,
    private val musicDao: MusicDao
) {
    private val TAG = "MetadataEnrichment"
    private val scope = CoroutineScope(Dispatchers.IO + Job())

    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .build()

    private var isNetworkAvailable = false
    private var isEnriching = false

    /**
     * Registers network connectivity callback to trigger enrichment when unmetered/active internet is present.
     */
    fun startNetworkMonitoring() {
        val connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
            ?: return

        val request = NetworkRequest.Builder()
            .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
            .build()

        try {
            connectivityManager.registerNetworkCallback(request, object : ConnectivityManager.NetworkCallback() {
                override fun onAvailable(network: Network) {
                    Log.d(TAG, "Network available, checking for tracks to enrich...")
                    isNetworkAvailable = true
                    triggerEnrichment()
                }

                override fun onLost(network: Network) {
                    isNetworkAvailable = false
                }
            })
        } catch (e: Exception) {
            Log.e(TAG, "Failed to register network callback: ${e.message}")
        }
    }

    /**
     * Enriches tracks that lack synced lyrics, cover art, or verified genre.
     */
    fun triggerEnrichment() {
        if (isEnriching) return
        scope.launch {
            isEnriching = true
            try {
                val songs = musicDao.getAllSongsUnlimited()
                for (song in songs) {
                    // Check if song already has both synced lyrics and cover art
                    val needsLyrics = song.syncedLyrics.isNullOrBlank() && song.lrcLyrics.isNullOrBlank()
                    val needsCover = song.coverArtUrl.isNullOrBlank() && song.albumArtUri.isNullOrBlank()
                    val needsGenre = song.genre.isNullOrBlank()

                    if (needsLyrics || needsCover || needsGenre) {
                        enrichSingleSong(song)
                        delay(500L) // Polite rate-limiting between API calls
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error during batch metadata enrichment: ${e.message}")
            } finally {
                isEnriching = false
            }
        }
    }

    /**
     * Enriches a single track via LRCLIB and Deezer public endpoints with offline ID3 fallbacks.
     */
    suspend fun enrichSingleSong(song: SongEntity) = withContext(Dispatchers.IO) {
        var enrichedLyrics: String? = song.syncedLyrics ?: song.lrcLyrics
        var enrichedCover: String? = song.coverArtUrl
        var verifiedArtist: String? = song.verifiedArtist
        var verifiedGenre: String? = song.genre

        // 1. Try offline sidecar .lrc file first if local file exists
        if (enrichedLyrics.isNullOrBlank() && !song.dataPath.isNullOrBlank()) {
            val localLrc = findLocalSidecarLrc(song.dataPath)
            if (localLrc != null) {
                enrichedLyrics = localLrc
                Log.d(TAG, "Found local sidecar .lrc for ${song.title}")
            }
        }

        // 2. Try ID3 embedded metadata fallback if local file exists
        if (!song.dataPath.isNullOrBlank()) {
            try {
                val retriever = MediaMetadataRetriever()
                retriever.setDataSource(song.dataPath)
                val genre = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_GENRE)
                if (!genre.isNullOrBlank() && verifiedGenre.isNullOrBlank()) {
                    verifiedGenre = genre
                }
                retriever.release()
            } catch (_: Exception) {}
        }

        // 3. Query online LRCLIB API for synced .lrc lyrics if online
        if (isNetworkAvailable && enrichedLyrics.isNullOrBlank()) {
            enrichedLyrics = fetchLyricsFromLrcLib(song.title, song.artist, song.durationMs / 1000)
        }

        // 4. Query Deezer public search for 500x500 high-resolution cover art & verified artist
        if (isNetworkAvailable && (enrichedCover.isNullOrBlank() || verifiedArtist.isNullOrBlank())) {
            val (cover, artist, genre) = fetchDeezerMetadata(song.title, song.artist)
            if (cover != null && enrichedCover.isNullOrBlank()) enrichedCover = cover
            if (artist != null && verifiedArtist.isNullOrBlank()) verifiedArtist = artist
            if (genre != null && verifiedGenre.isNullOrBlank()) verifiedGenre = genre
        }

        // 5. Update Room database if any new metadata was retrieved
        if (enrichedLyrics != song.syncedLyrics || enrichedCover != song.coverArtUrl ||
            verifiedArtist != song.verifiedArtist || verifiedGenre != song.genre) {
            musicDao.updateEnrichedMetadata(
                songId = song.id,
                coverArtUrl = enrichedCover,
                syncedLyrics = enrichedLyrics,
                verifiedArtist = verifiedArtist,
                genre = verifiedGenre
            )
            Log.d(TAG, "Successfully enriched metadata for: ${song.title}")
        }
    }

    /**
     * Checks if a .lrc file with matching filename exists in the same folder as the audio track.
     */
    private fun findLocalSidecarLrc(audioPath: String): String? {
        return try {
            val audioFile = File(audioPath)
            if (!audioFile.exists()) return null
            val baseName = audioFile.nameWithoutExtension
            val lrcFile = File(audioFile.parentFile, "$baseName.lrc")
            if (lrcFile.exists() && lrcFile.canRead()) {
                lrcFile.readText()
            } else {
                null
            }
        } catch (e: Exception) {
            null
        }
    }

    /**
     * Queries the open-source LRCLIB API for synced lyrics.
     * https://lrclib.net/api/get
     */
    private fun fetchLyricsFromLrcLib(title: String, artist: String, durationSecs: Long): String? {
        return try {
            val encodedTitle = URLEncoder.encode(title, "UTF-8")
            val encodedArtist = URLEncoder.encode(artist, "UTF-8")
            val url = "https://lrclib.net/api/get?track_name=$encodedTitle&artist_name=$encodedArtist&duration=$durationSecs"

            val request = Request.Builder()
                .url(url)
                .header("User-Agent", "AuraMusic/1.0 (Android Music Player)")
                .build()

            httpClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return null
                val body = response.body?.string() ?: return null
                val json = JSONObject(body)
                val synced = json.optString("syncedLyrics")
                if (synced.isNotBlank()) synced else json.optString("plainLyrics").takeIf { it.isNotBlank() }
            }
        } catch (e: Exception) {
            Log.d(TAG, "LRCLIB fetch error for $title: ${e.message}")
            null
        }
    }

    /**
     * Queries Deezer public API for 500x500 high-res album cover and verified artist/album.
     */
    private fun fetchDeezerMetadata(title: String, artist: String): Triple<String?, String?, String?> {
        return try {
            val query = if (artist.contains("Unknown", ignoreCase = true)) title else "$artist $title"
            val encodedQuery = URLEncoder.encode(query, "UTF-8")
            val url = "https://api.deezer.com/search?q=$encodedQuery&limit=1"

            val request = Request.Builder()
                .url(url)
                .header("User-Agent", "AuraMusic/1.0")
                .build()

            httpClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return Triple(null, null, null)
                val body = response.body?.string() ?: return Triple(null, null, null)
                val json = JSONObject(body)
                val data = json.optJSONArray("data")
                if (data != null && data.length() > 0) {
                    val item = data.getJSONObject(0)
                    val albumObj = item.optJSONObject("album")
                    val cover = albumObj?.optString("cover_big")?.takeIf { it.isNotBlank() }
                        ?: albumObj?.optString("cover_medium")
                    val artistObj = item.optJSONObject("artist")
                    val verifiedArtist = artistObj?.optString("name")
                    Triple(cover, verifiedArtist, null)
                } else {
                    Triple(null, null, null)
                }
            }
        } catch (e: Exception) {
            Log.d(TAG, "Deezer metadata fetch error: ${e.message}")
            Triple(null, null, null)
        }
    }
}
