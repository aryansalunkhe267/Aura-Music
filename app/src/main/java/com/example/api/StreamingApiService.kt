package com.example.api

import android.util.Log
import com.example.data.SongEntity
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Response
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory
import retrofit2.http.GET
import retrofit2.http.Path
import retrofit2.http.Query
import java.util.concurrent.TimeUnit

interface StreamingApi {

    @GET("search")
    suspend fun searchSongs(
        @Query("q") query: String,
        @Query("filter") filter: String = "music_songs"
    ): Response<List<PipedSearchResultItem>>

    @GET("streams/{id}")
    suspend fun getStreamDetails(
        @Path("id") id: String
    ): Response<PipedStreamResponse>
}

object StreamingApiClient {

    private const val TAG = "StreamingApiClient"

    val DEFAULT_INSTANCES = listOf(
        "https://pipedapi.kavin.rocks/",
        "https://api.piped.privacydev.net/",
        "https://pipedapi.tokhmi.xyz/",
        "https://piped-api.lunar.icu/"
    )

    @Volatile
    var customBaseUrl: String = DEFAULT_INSTANCES.first()
        set(value) {
            val sanitized = if (!value.endsWith("/")) "$value/" else value
            field = sanitized
            rebuildRetrofit(sanitized)
        }

    private val moshi = Moshi.Builder()
        .add(KotlinJsonAdapterFactory())
        .build()

    // Host fallback interceptor for high-availability open-source instances
    private val hostFallbackInterceptor = Interceptor { chain ->
        val originalRequest = chain.request()
        try {
            val response = chain.proceed(originalRequest)
            if (response.isSuccessful) {
                return@Interceptor response
            }
            response.close()
        } catch (e: Exception) {
            Log.w(TAG, "Primary streaming instance failed (${originalRequest.url}), attempting fallback: ${e.message}")
        }

        // Try rotating through default instances if primary fails
        for (fallback in DEFAULT_INSTANCES) {
            if (!originalRequest.url.toString().startsWith(fallback)) {
                try {
                    val targetHost = java.net.URI.create(fallback).host ?: continue
                    val newHttpUrl = originalRequest.url.newBuilder()
                        .host(targetHost)
                        .build()
                    val fallbackRequest = originalRequest.newBuilder()
                        .url(newHttpUrl)
                        .build()
                    val res = chain.proceed(fallbackRequest)
                    if (res.isSuccessful) {
                        return@Interceptor res
                    }
                    res.close()
                } catch (e: Exception) {
                    Log.w(TAG, "Fallback instance $fallback also failed: ${e.message}")
                }
            }
        }

        // Re-attempt original if all fail
        chain.proceed(originalRequest)
    }

    private val okHttpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .addInterceptor(hostFallbackInterceptor)
        .addInterceptor(HttpLoggingInterceptor().apply {
            level = HttpLoggingInterceptor.Level.BASIC
        })
        .build()

    private var retrofit: Retrofit = Retrofit.Builder()
        .baseUrl(customBaseUrl)
        .client(okHttpClient)
        .addConverterFactory(MoshiConverterFactory.create(moshi))
        .build()

    @Volatile
    var api: StreamingApi = retrofit.create(StreamingApi::class.java)
        private set

    private fun rebuildRetrofit(baseUrl: String) {
        try {
            retrofit = Retrofit.Builder()
                .baseUrl(baseUrl)
                .client(okHttpClient)
                .addConverterFactory(MoshiConverterFactory.create(moshi))
                .build()
            api = retrofit.create(StreamingApi::class.java)
            Log.i(TAG, "Updated StreamingApi base URL to: $baseUrl")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to rebuild Retrofit with $baseUrl: ${e.message}")
        }
    }

    /**
     * Extracts video ID from a Piped stream URL (e.g. "/watch?v=dQw4w9WgXcQ" or raw ID).
     */
    fun extractStreamId(urlOrId: String): String {
        return when {
            urlOrId.contains("v=") -> urlOrId.substringAfter("v=").substringBefore("&")
            urlOrId.contains("/watch/") -> urlOrId.substringAfter("/watch/").substringBefore("?")
            urlOrId.startsWith("/watch?v=") -> urlOrId.removePrefix("/watch?v=")
            urlOrId.startsWith("/") -> urlOrId.removePrefix("/")
            else -> urlOrId
        }
    }

    /**
     * Maps a search result item to a SongEntity.
     */
    fun mapSearchResultToSong(item: PipedSearchResultItem): SongEntity {
        val streamId = extractStreamId(item.url ?: "")
        val numericId = (streamId.hashCode().toLong() and 0x7FFFFFFF) + 6_000_000L
        val durMs = (item.duration ?: 180L) * 1000L

        return SongEntity(
            id = numericId,
            title = item.title ?: "Unknown Track",
            artist = item.uploaderName ?: "Unknown Artist",
            album = "Single",
            durationMs = durMs,
            contentUri = "piped://$streamId",
            streamUrl = null, // Retrieved on demand via getStreamDetails()
            albumArtUri = item.thumbnail,
            coverArtUrl = item.thumbnail,
            sourceType = "ONLINE",
            isDownloaded = false,
            downloadProgress = 0,
            languageScript = "UNKNOWN",
            genre = "Online Stream"
        )
    }

    /**
     * Resolves the highest quality audio stream (M4A or Opus) from stream details.
     */
    fun selectBestAudioStream(response: PipedStreamResponse): PipedAudioStream? {
        val streams = response.audioStreams ?: return null
        return streams.firstOrNull { it.format?.uppercase() == "M4A" }
            ?: streams.firstOrNull { it.mimeType?.contains("mp4") == true }
            ?: streams.firstOrNull { it.format?.uppercase() == "OPUS" }
            ?: streams.firstOrNull()
    }
}
