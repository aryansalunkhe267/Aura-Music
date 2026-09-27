package com.example.api

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

@JsonClass(generateAdapter = true)
data class JioSaavnSearchResponse(
    @Json(name = "status") val status: String?,
    @Json(name = "data") val data: JioSaavnSearchData?
)

@JsonClass(generateAdapter = true)
data class JioSaavnSearchData(
    @Json(name = "total") val total: Int? = 0,
    @Json(name = "start") val start: Int? = 0,
    @Json(name = "results") val results: List<JioSaavnSongItem>? = emptyList()
)

@JsonClass(generateAdapter = true)
data class JioSaavnSongItem(
    @Json(name = "id") val id: String,
    @Json(name = "name") val name: String?,
    @Json(name = "type") val type: String? = "song",
    @Json(name = "year") val year: String? = null,
    @Json(name = "releaseDate") val releaseDate: String? = null,
    @Json(name = "duration") val duration: Any? = null, // can be Int or String
    @Json(name = "label") val label: String? = null,
    @Json(name = "primaryArtists") val primaryArtists: String? = null,
    @Json(name = "featuredArtists") val featuredArtists: String? = null,
    @Json(name = "artists") val artists: JioSaavnArtistsContainer? = null,
    @Json(name = "album") val album: JioSaavnAlbumInfo? = null,
    @Json(name = "image") val image: List<JioSaavnImage>? = null,
    @Json(name = "downloadUrl") val downloadUrl: List<JioSaavnDownloadUrl>? = null,
    @Json(name = "url") val url: String? = null
)

@JsonClass(generateAdapter = true)
data class JioSaavnArtistsContainer(
    @Json(name = "primary") val primary: List<JioSaavnArtistItem>? = null,
    @Json(name = "featured") val featured: List<JioSaavnArtistItem>? = null,
    @Json(name = "all") val all: List<JioSaavnArtistItem>? = null
)

@JsonClass(generateAdapter = true)
data class JioSaavnArtistItem(
    @Json(name = "id") val id: String? = null,
    @Json(name = "name") val name: String? = null,
    @Json(name = "role") val role: String? = null,
    @Json(name = "image") val image: List<JioSaavnImage>? = null
)

@JsonClass(generateAdapter = true)
data class JioSaavnAlbumInfo(
    @Json(name = "id") val id: String? = null,
    @Json(name = "name") val name: String? = null,
    @Json(name = "url") val url: String? = null
)

@JsonClass(generateAdapter = true)
data class JioSaavnImage(
    @Json(name = "quality") val quality: String? = null,
    @Json(name = "url") val url: String? = null
)

@JsonClass(generateAdapter = true)
data class JioSaavnDownloadUrl(
    @Json(name = "quality") val quality: String? = null,
    @Json(name = "url") val url: String? = null
)

@JsonClass(generateAdapter = true)
data class JioSaavnPlaylistSearchResponse(
    @Json(name = "status") val status: String?,
    @Json(name = "data") val data: JioSaavnPlaylistSearchData?
)

@JsonClass(generateAdapter = true)
data class JioSaavnPlaylistSearchData(
    @Json(name = "results") val results: List<JioSaavnPlaylistItem>? = emptyList()
)

@JsonClass(generateAdapter = true)
data class JioSaavnPlaylistItem(
    @Json(name = "id") val id: String,
    @Json(name = "title") val title: String?,
    @Json(name = "subtitle") val subtitle: String? = null,
    @Json(name = "type") val type: String? = "playlist",
    @Json(name = "image") val image: List<JioSaavnImage>? = null,
    @Json(name = "songCount") val songCount: String? = null,
    @Json(name = "firstname") val firstname: String? = null
)
