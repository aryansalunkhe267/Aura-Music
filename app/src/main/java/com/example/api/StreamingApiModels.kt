package com.example.api

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

@JsonClass(generateAdapter = true)
data class PipedSearchResultItem(
    @Json(name = "url") val url: String? = null,
    @Json(name = "title") val title: String? = null,
    @Json(name = "thumbnail") val thumbnail: String? = null,
    @Json(name = "uploaderName") val uploaderName: String? = null,
    @Json(name = "uploaderUrl") val uploaderUrl: String? = null,
    @Json(name = "uploaderAvatar") val uploaderAvatar: String? = null,
    @Json(name = "duration") val duration: Long? = 0L,
    @Json(name = "type") val type: String? = null
)

@JsonClass(generateAdapter = true)
data class PipedStreamResponse(
    @Json(name = "title") val title: String? = null,
    @Json(name = "description") val description: String? = null,
    @Json(name = "uploader") val uploader: String? = null,
    @Json(name = "uploaderAvatar") val uploaderAvatar: String? = null,
    @Json(name = "thumbnailUrl") val thumbnailUrl: String? = null,
    @Json(name = "duration") val duration: Long? = 0L,
    @Json(name = "audioStreams") val audioStreams: List<PipedAudioStream>? = emptyList(),
    @Json(name = "relatedStreams") val relatedStreams: List<PipedSearchResultItem>? = emptyList()
)

@JsonClass(generateAdapter = true)
data class PipedAudioStream(
    @Json(name = "url") val url: String,
    @Json(name = "format") val format: String? = "M4A",
    @Json(name = "quality") val quality: String? = "128 kbps",
    @Json(name = "mimeType") val mimeType: String? = "audio/mp4",
    @Json(name = "codec") val codec: String? = null,
    @Json(name = "bitrate") val bitrate: Int? = null,
    @Json(name = "contentLength") val contentLength: Long? = null
)
