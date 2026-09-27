package com.example.network.api

import com.example.network.model.JioSaavnSearchResponse
import com.example.network.model.JioSaavnSongDetailResponse
import retrofit2.http.GET
import retrofit2.http.Path
import retrofit2.http.Query

interface JioSaavnApiService {

    @GET("search/songs")
    suspend fun searchSongs(
        @Query("query") query: String,
        @Query("page") page: Int = 1,
        @Query("limit") limit: Int = 30
    ): JioSaavnSearchResponse

    @GET("songs/{id}")
    suspend fun getSongById(
        @Path("id") id: String
    ): JioSaavnSongDetailResponse
}
