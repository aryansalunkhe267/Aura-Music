package com.example.api

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

@JsonClass(generateAdapter = true)
data class SpotifyWebTokenResponse(
    @Json(name = "clientId") val clientId: String? = null,
    @Json(name = "accessToken") val accessToken: String? = null,
    @Json(name = "accessTokenExpirationTimestampMs") val accessTokenExpirationTimestampMs: Long? = null,
    @Json(name = "isAnonymous") val isAnonymous: Boolean? = false
)

@JsonClass(generateAdapter = true)
data class SpotifyLikedTracksResponse(
    @Json(name = "items") val items: List<SpotifySavedTrackItem>? = emptyList(),
    @Json(name = "total") val total: Int? = 0,
    @Json(name = "next") val next: String? = null
)

@JsonClass(generateAdapter = true)
data class SpotifySavedTrackItem(
    @Json(name = "added_at") val addedAt: String? = null,
    @Json(name = "track") val track: SpotifyTrackObject? = null
)

@JsonClass(generateAdapter = true)
data class SpotifyPlaylistsResponse(
    @Json(name = "items") val items: List<SpotifyPlaylistItem>? = emptyList(),
    @Json(name = "total") val total: Int? = 0
)

@JsonClass(generateAdapter = true)
data class SpotifyPlaylistItem(
    @Json(name = "id") val id: String,
    @Json(name = "name") val name: String,
    @Json(name = "description") val description: String? = null,
    @Json(name = "images") val images: List<SpotifyImageObject>? = emptyList(),
    @Json(name = "tracks") val tracks: SpotifyTracksHref? = null
)

@JsonClass(generateAdapter = true)
data class SpotifyTracksHref(
    @Json(name = "total") val total: Int? = 0,
    @Json(name = "href") val href: String? = null
)

@JsonClass(generateAdapter = true)
data class SpotifyPlaylistTracksResponse(
    @Json(name = "items") val items: List<SpotifyPlaylistTrackItem>? = emptyList()
)

@JsonClass(generateAdapter = true)
data class SpotifyPlaylistTrackItem(
    @Json(name = "track") val track: SpotifyTrackObject? = null
)

@JsonClass(generateAdapter = true)
data class SpotifyTopArtistsResponse(
    @Json(name = "items") val items: List<SpotifyArtistObject>? = emptyList(),
    @Json(name = "total") val total: Int? = 0
)

@JsonClass(generateAdapter = true)
data class SpotifyTopTracksResponse(
    @Json(name = "items") val items: List<SpotifyTrackObject>? = emptyList(),
    @Json(name = "total") val total: Int? = 0
)

@JsonClass(generateAdapter = true)
data class SpotifyTrackObject(
    @Json(name = "id") val id: String,
    @Json(name = "name") val name: String,
    @Json(name = "artists") val artists: List<SpotifyArtistSimple>? = emptyList(),
    @Json(name = "album") val album: SpotifyAlbumObject? = null,
    @Json(name = "duration_ms") val durationMs: Long? = 180000L,
    @Json(name = "popularity") val popularity: Int? = 0
)

@JsonClass(generateAdapter = true)
data class SpotifyArtistSimple(
    @Json(name = "id") val id: String? = null,
    @Json(name = "name") val name: String
)

@JsonClass(generateAdapter = true)
data class SpotifyArtistObject(
    @Json(name = "id") val id: String,
    @Json(name = "name") val name: String,
    @Json(name = "genres") val genres: List<String>? = emptyList(),
    @Json(name = "popularity") val popularity: Int? = 0,
    @Json(name = "images") val images: List<SpotifyImageObject>? = emptyList()
)

@JsonClass(generateAdapter = true)
data class SpotifyAlbumObject(
    @Json(name = "id") val id: String? = null,
    @Json(name = "name") val name: String? = "Single",
    @Json(name = "images") val images: List<SpotifyImageObject>? = emptyList()
)

@JsonClass(generateAdapter = true)
data class SpotifyImageObject(
    @Json(name = "url") val url: String,
    @Json(name = "height") val height: Int? = null,
    @Json(name = "width") val width: Int? = null
)
