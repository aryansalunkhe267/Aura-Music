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
     * Resolves the highest quality audio stream from stream details.
     * Strictly filters and prioritizes audio/mp4 (.m4a) streams to prevent ExoPlayer demuxer static/digital noise.
     */
    fun selectBestAudioStream(response: PipedStreamResponse): PipedAudioStream? {
        val streams = response.audioStreams?.filter { !it.url.isNullOrBlank() } ?: return null
        if (streams.isEmpty()) return null

        // 1. Strictly filter and prioritize audio/mp4 (.m4a) streams.
        // ExoPlayer's Mp4Extractor demuxes audio/mp4 (AAC/mp4a) cleanly, whereas WebM/Opus streams
        // without container headers or with mismatched sample rates cause severe digital noise and demuxer failure.
        val mp4Streams = streams.filter { stream ->
            val mime = stream.mimeType?.lowercase() ?: ""
            val format = stream.format?.lowercase() ?: ""
            val codec = stream.codec?.lowercase() ?: ""
            val url = stream.url?.lowercase() ?: ""

            val isDisallowedFormat = mime.contains("webm") ||
                    mime.contains("opus") ||
                    mime.contains("ogg") ||
                    format.contains("webm") ||
                    format.contains("opus") ||
                    format.contains("ogg") ||
                    codec.contains("opus")

            if (isDisallowedFormat) return@filter false

            mime.startsWith("audio/mp4") ||
                    mime.contains("audio/mp4") ||
                    format == "m4a" ||
                    codec.startsWith("mp4a") ||
                    codec.contains("mp4a") ||
                    url.contains("mime=audio%2fmp4") ||
                    url.contains("mime=audio/mp4")
        }

        if (mp4Streams.isNotEmpty()) {
            return mp4Streams.maxByOrNull { it.bitrate ?: 0 } ?: mp4Streams.first()
        }

        // 2. Strict secondary fallback: AAC streams that are not WebM/Opus/Ogg
        val safeAacStreams = streams.filter { stream ->
            val mime = stream.mimeType?.lowercase() ?: ""
            val format = stream.format?.lowercase() ?: ""
            val codec = stream.codec?.lowercase() ?: ""
            val isDisallowedFormat = mime.contains("webm") ||
                    mime.contains("opus") ||
                    mime.contains("ogg") ||
                    format.contains("webm") ||
                    format.contains("opus") ||
                    format.contains("ogg") ||
                    codec.contains("opus")

            !isDisallowedFormat && (mime.contains("aac") || format.contains("aac") || codec.contains("aac"))
        }

        if (safeAacStreams.isNotEmpty()) {
            return safeAacStreams.maxByOrNull { it.bitrate ?: 0 } ?: safeAacStreams.first()
        }

        // Strictly do not fall back to WebM/Opus streams that cause demuxer static
        return null
    }
}
