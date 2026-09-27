package com.example.api

import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Response
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.Path
import retrofit2.http.Query
import java.util.concurrent.TimeUnit

interface SpotifyWebAuthApi {
    @GET("get_access_token")
    suspend fun getAccessToken(
        @Header("Cookie") cookieHeader: String,
        @Header("User-Agent") userAgent: String = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/122.0.0.0 Safari/537.36",
        @Query("reason") reason: String = "transport",
        @Query("productType") productType: String = "web_player"
    ): Response<SpotifyWebTokenResponse>
}

interface SpotifyWebApi {
    @GET("v1/me/tracks")
    suspend fun getLikedTracks(
        @Header("Authorization") auth: String,
        @Query("limit") limit: Int = 50
    ): Response<SpotifyLikedTracksResponse>

    @GET("v1/me/playlists")
    suspend fun getUserPlaylists(
        @Header("Authorization") auth: String,
        @Query("limit") limit: Int = 50
    ): Response<SpotifyPlaylistsResponse>

    @GET("v1/playlists/{playlistId}/tracks")
    suspend fun getPlaylistTracks(
        @Header("Authorization") auth: String,
        @Path("playlistId") playlistId: String,
        @Query("limit") limit: Int = 50
    ): Response<SpotifyPlaylistTracksResponse>

    @GET("v1/me/top/artists")
    suspend fun getTopArtists(
        @Header("Authorization") auth: String,
        @Query("limit") limit: Int = 50,
        @Query("time_range") timeRange: String = "medium_term"
    ): Response<SpotifyTopArtistsResponse>

    @GET("v1/me/top/tracks")
    suspend fun getTopTracks(
        @Header("Authorization") auth: String,
        @Query("limit") limit: Int = 50,
        @Query("time_range") timeRange: String = "medium_term"
    ): Response<SpotifyTopTracksResponse>
}

object SpotifyApiClient {
    private val moshi = Moshi.Builder()
        .add(KotlinJsonAdapterFactory())
        .build()

    private val okHttpClient = OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(25, TimeUnit.SECONDS)
        .addInterceptor(HttpLoggingInterceptor().apply {
            level = HttpLoggingInterceptor.Level.BASIC
        })
        .build()

    val authApi: SpotifyWebAuthApi = Retrofit.Builder()
        .baseUrl("https://open.spotify.com/")
        .client(okHttpClient)
        .addConverterFactory(MoshiConverterFactory.create(moshi))
        .build()
        .create(SpotifyWebAuthApi::class.java)

    val api: SpotifyWebApi = Retrofit.Builder()
        .baseUrl("https://api.spotify.com/")
        .client(okHttpClient)
        .addConverterFactory(MoshiConverterFactory.create(moshi))
        .build()
        .create(SpotifyWebApi::class.java)
}
