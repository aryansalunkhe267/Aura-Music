package com.example.network.api

import com.example.network.model.LrcLibResponse
import retrofit2.Response
import retrofit2.http.GET
import retrofit2.http.Query

interface LrcLibApiService {

    @GET("get")
    suspend fun getLyrics(
        @Query("track_name") trackName: String,
        @Query("artist_name") artistName: String,
        @Query("duration") durationSecs: Long? = null,
        @Query("album_name") albumName: String? = null
    ): Response<LrcLibResponse>
}
