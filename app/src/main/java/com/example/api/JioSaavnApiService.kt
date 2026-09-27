package com.example.api

import android.util.Log
import com.example.data.SongEntity
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.Response
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory
import retrofit2.http.GET
import retrofit2.http.Query
import java.util.concurrent.TimeUnit

interface JioSaavnApi {

    @GET("api/search/songs")
    suspend fun searchSongs(
        @Query("query") query: String,
        @Query("page") page: Int = 1,
        @Query("limit") limit: Int = 20,
        @Query("languages") languages: String = "punjabi,hindi,marathi"
    ): retrofit2.Response<JioSaavnSearchResponse>

    @GET("api/search/playlists")
    suspend fun searchPlaylists(
        @Query("query") query: String,
        @Query("page") page: Int = 1,
        @Query("limit") limit: Int = 10,
        @Query("languages") languages: String = "punjabi,hindi,marathi"
    ): retrofit2.Response<JioSaavnPlaylistSearchResponse>
}

object JioSaavnApiClient {

    private const val TAG = "JioSaavnApiClient"
    private const val PRIMARY_BASE_URL = "https://saavn.dev/"
    private const val FALLBACK_BASE_URL = "https://saavn.me/"

    private val moshi = Moshi.Builder()
        .add(KotlinJsonAdapterFactory())
        .build()

    // Host fallback interceptor: redirects failed requests to alternative JioSaavn server
    private val hostFallbackInterceptor = Interceptor { chain ->
        val originalRequest = chain.request()
        try {
            val response = chain.proceed(originalRequest)
            if (response.isSuccessful) {
                return@Interceptor response
            }
            response.close()
        } catch (e: Exception) {
            Log.w(TAG, "Primary JioSaavn host failed (${originalRequest.url}), attempting fallback: ${e.message}")
        }

        // Try fallback URL
        val originalUrl = originalRequest.url
        val newHost = if (originalUrl.host.contains("saavn.dev")) "saavn.me" else "saavn.dev"
        val fallbackUrl = originalUrl.newBuilder()
            .host(newHost)
            .build()
        val fallbackRequest = originalRequest.newBuilder()
            .url(fallbackUrl)
            .build()

        chain.proceed(fallbackRequest)
    }

    private val okHttpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .addInterceptor(hostFallbackInterceptor)
        .addInterceptor(HttpLoggingInterceptor().apply {
            level = HttpLoggingInterceptor.Level.BASIC
        })
        .build()

    val api: JioSaavnApi = Retrofit.Builder()
        .baseUrl(PRIMARY_BASE_URL)
        .client(okHttpClient)
        .addConverterFactory(MoshiConverterFactory.create(moshi))
        .build()
        .create(JioSaavnApi::class.java)

    /**
     * Converts a JioSaavn API song item into a clean Pulse Music SongEntity.
     * Selects the highest available 320kbps audio stream and 500x500 album art.
     */
    fun mapToSongEntity(item: JioSaavnSongItem): SongEntity {
        // Find best stream URL (prefer 320kbps or 160kbps)
        val bestAudioUrl = item.downloadUrl?.firstOrNull { it.quality == "320kbps" }?.url
            ?: item.downloadUrl?.firstOrNull { it.quality == "160kbps" }?.url
            ?: item.downloadUrl?.lastOrNull()?.url
            ?: item.url
            ?: ""

        // Find best image URL (prefer 500x500)
        val bestArtUrl = item.image?.firstOrNull { it.quality == "500x500" }?.url
            ?: item.image?.lastOrNull()?.url
            ?: ""

        // Artist extraction
        val artistName = when {
            !item.primaryArtists.isNullOrBlank() -> item.primaryArtists
            item.artists?.primary?.isNotEmpty() == true -> item.artists.primary.joinToString(", ") { it.name ?: "" }
            else -> "Unknown Artist"
        }

        val albumName = item.album?.name ?: "Single"

        val durationMillis = when (val d = item.duration) {
            is Number -> d.toLong() * 1000L
            is String -> (d.toLongOrNull() ?: 180L) * 1000L
            else -> 180_000L
        }

        // Numeric unique ID from string hash or digit parse
        val numericId = item.id.filter { it.isDigit() }.toLongOrNull()
            ?: (item.id.hashCode().toLong() and 0x7FFFFFFF) + 5_000_000L

        return SongEntity(
            id = numericId,
            title = decodeHtmlEntities(item.name ?: "Unknown Song"),
            artist = decodeHtmlEntities(artistName),
            album = decodeHtmlEntities(albumName),
            durationMs = durationMillis,
            contentUri = bestAudioUrl,
            streamUrl = bestAudioUrl,
            albumArtUri = bestArtUrl,
            coverArtUrl = bestArtUrl,
            sourceType = "ONLINE",
            isDownloaded = false,
            downloadProgress = 0,
            languageScript = inferLanguage(item.name ?: "", artistName),
            genre = "Online Stream",
            moodProfile = "CHILL",
            moodScore = 0.5f,
            lrcLyrics = null,
            syncedLyrics = null
        )
    }

    private fun decodeHtmlEntities(input: String): String {
        return input
            .replace("&quot;", "\"")
            .replace("&amp;", "&")
            .replace("&#039;", "'")
            .replace("&lt;", "<")
            .replace("&gt;", ">")
    }

    private fun inferLanguage(title: String, artist: String): String {
        val combined = "$title $artist".lowercase()
        return when {
            combined.any { it.code in 0x0A00..0x0A7F } || combined.contains("punjabi") || combined.contains("sidhu") || combined.contains("diljit") || combined.contains("aujla") -> "PUNJABI"
            combined.any { it.code in 0x0900..0x097F } || combined.contains("marathi") || combined.contains("ajay atul") -> "MARATHI"
            else -> "HINDI"
        }
    }
}
