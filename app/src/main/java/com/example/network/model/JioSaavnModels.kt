package com.example.network.model

import com.example.data.SongEntity
import com.example.engine.ScriptLanguageDetector
import com.example.utils.cleanMetadataString
import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

@JsonClass(generateAdapter = true)
data class JioSaavnSearchResponse(
    @Json(name = "success") val success: Boolean? = false,
    @Json(name = "data") val data: JioSaavnSearchData? = null
)

@JsonClass(generateAdapter = true)
data class JioSaavnSearchData(
    @Json(name = "total") val total: Int? = 0,
    @Json(name = "start") val start: Int? = 0,
    @Json(name = "results") val results: List<JioSaavnSong>? = emptyList()
)

@JsonClass(generateAdapter = true)
data class JioSaavnSongDetailResponse(
    @Json(name = "success") val success: Boolean? = false,
    @Json(name = "data") val data: List<JioSaavnSong>? = emptyList()
)

@JsonClass(generateAdapter = true)
data class JioSaavnSong(
    @Json(name = "id") val id: String,
    @Json(name = "name") val name: String,
    @Json(name = "type") val type: String? = "song",
    @Json(name = "year") val year: String? = null,
    @Json(name = "releaseDate") val releaseDate: String? = null,
    @Json(name = "duration") val duration: Any? = null, // can be Int or String
    @Json(name = "label") val label: String? = null,
    @Json(name = "explicitContent") val explicitContent: Boolean? = false,
    @Json(name = "playCount") val playCount: Any? = null,
    @Json(name = "language") val language: String? = null,
    @Json(name = "hasLyrics") val hasLyrics: Boolean? = false,
    @Json(name = "lyricsId") val lyricsId: String? = null,
    @Json(name = "url") val url: String? = null,
    @Json(name = "album") val album: JioSaavnAlbum? = null,
    @Json(name = "artists") val artists: JioSaavnArtists? = null,
    @Json(name = "image") val image: List<JioSaavnMediaLink>? = emptyList(),
    @Json(name = "downloadUrl") val downloadUrl: List<JioSaavnMediaLink>? = emptyList()
)

@JsonClass(generateAdapter = true)
data class JioSaavnAlbum(
    @Json(name = "id") val id: String? = null,
    @Json(name = "name") val name: String? = null,
    @Json(name = "url") val url: String? = null
)

@JsonClass(generateAdapter = true)
data class JioSaavnArtists(
    @Json(name = "primary") val primary: List<JioSaavnArtist>? = emptyList(),
    @Json(name = "featured") val featured: List<JioSaavnArtist>? = emptyList(),
    @Json(name = "all") val all: List<JioSaavnArtist>? = emptyList()
)

@JsonClass(generateAdapter = true)
data class JioSaavnArtist(
    @Json(name = "id") val id: String? = null,
    @Json(name = "name") val name: String? = null,
    @Json(name = "role") val role: String? = null,
    @Json(name = "type") val type: String? = null,
    @Json(name = "url") val url: String? = null
)

@JsonClass(generateAdapter = true)
data class JioSaavnMediaLink(
    @Json(name = "quality") val quality: String? = null,
    @Json(name = "url") val url: String? = null
)

/**
 * Maps JioSaavn API DTO to unified [SongEntity] model.
 * Prioritizes 320kbps pristine audio stream and 500x500 high-res cover art.
 */
fun JioSaavnSong.toSongEntity(): SongEntity {
    // 1. Choose highest audio quality stream URL (320kbps -> 160kbps -> 96kbps -> best available)
    val bestStream = downloadUrl?.firstOrNull { it.quality == "320kbps" }?.url
        ?: downloadUrl?.firstOrNull { it.quality == "160kbps" }?.url
        ?: downloadUrl?.firstOrNull { it.quality == "96kbps" }?.url
        ?: downloadUrl?.lastOrNull()?.url
        ?: ""

    // 2. Choose highest resolution cover image (500x500 -> 150x150 -> best available)
    val bestArt = image?.firstOrNull { it.quality == "500x500" }?.url
        ?: image?.firstOrNull { it.quality == "150x150" }?.url
        ?: image?.lastOrNull()?.url
        ?: ""

    // 3. Extract primary artist names
    val artistName = artists?.primary?.mapNotNull { it.name }?.takeIf { it.isNotEmpty() }?.joinToString(", ")
        ?: artists?.all?.mapNotNull { it.name }?.takeIf { it.isNotEmpty() }?.joinToString(", ")
        ?: "Unknown Artist"

    val albumName = album?.name ?: "Single"

    // 4. Safe duration parse (can be seconds)
    val durationSecs = when (val d = duration) {
        is Number -> d.toLong()
        is String -> d.toLongOrNull() ?: 180L
        else -> 180L
    }
    val durationMillis = (durationSecs * 1000L).coerceAtLeast(10_000L)

    // 5. Generate stable positive Long ID for Room DB without clashing with MediaStore IDs
    val stableId = (id.hashCode().toLong() and 0x7FFFFFFF) or 0x4000000000000000L

    // 6. Clean metadata & detect script/language
    val cleanTitle = cleanMetadataString(name)
    val cleanArtist = cleanMetadataString(artistName)
    val scriptType = ScriptLanguageDetector.detectScript(cleanTitle, cleanArtist, albumName)

    val languageCategory = when {
        language?.equals("punjabi", ignoreCase = true) == true || scriptType == ScriptLanguageDetector.ScriptType.PUNJABI -> "PUNJABI"
        language?.equals("marathi", ignoreCase = true) == true || scriptType == ScriptLanguageDetector.ScriptType.MARATHI -> "MARATHI"
        scriptType == ScriptLanguageDetector.ScriptType.DEVOTIONAL -> "DEVOTIONAL"
        language?.equals("hindi", ignoreCase = true) == true || scriptType == ScriptLanguageDetector.ScriptType.HINDI -> "HINDI"
        else -> "HINDI"
    }

    return SongEntity(
        id = stableId,
        title = cleanTitle,
        artist = cleanArtist,
        album = cleanMetadataString(albumName),
        durationMs = durationMillis,
        contentUri = bestStream,
        dataPath = null,
        albumArtUri = bestArt.ifBlank { null },
        coverArtUrl = bestArt.ifBlank { null },
        streamUrl = bestStream,
        sourceType = "ONLINE",
        isDownloaded = false,
        downloadProgress = 0,
        languageScript = languageCategory,
        verifiedArtist = cleanArtist,
        genre = languageCategory,
        moodProfile = "UPBEAT",
        moodScore = 0.8f
    )
}
