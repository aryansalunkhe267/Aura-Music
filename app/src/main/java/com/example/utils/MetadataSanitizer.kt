package com.example.utils

import android.media.MediaMetadataRetriever
import java.util.Locale
import java.util.regex.Pattern

/**
 * Strips parenthetical translations and trailing tags:
 * - Removes any text inside parentheses: (Official Audio), (Translation), (Bonus Track)
 * - Removes text inside square brackets: [Official Audio], [Remastered]
 * Strictly preserves the original native script verbatim without any automated transliteration or translation.
 */
fun cleanMetadataString(raw: String): String {
    return raw
        .replace(Regex("""\s*\([^)]*\)"""), "")
        .replace(Regex("""\[[^\]]*\]"""), "")
        .replace(Regex("""\s+"""), " ")
        .trim()
}

/**
 * Metadata sanitizer and canonical consolidator for Pulse Music.
 * Normalizes artist names and albums so duplicate artist folders and split albums
 * merge into single canonical entities while preserving clean display metadata.
 */
object MetadataSanitizer {

    private val FILE_EXTENSION_PATTERN = Pattern.compile("""(?i)\.(mp3|m4a|flac|wav|aac|ogg|opus)$""")

    private val FEAT_DELIMITERS = Regex(
        """(?i)\s+(feat\.|ft\.|featuring|with|&|x|,|vs\.)\s+.*"""
    )

    private val ALBUM_VERSION_TAGS = Regex(
        """(?i)\s*(-|–|—)?\s*(\(|\[)?(deluxe|remastered|expanded|special edition|anniversary edition|original soundtrack|ost|soundtrack|single|ep)(\)|\])?"""
    )

    fun cleanMetadataString(raw: String): String = com.example.utils.cleanMetadataString(raw)

    /**
     * Sanitizes track title: strips file extensions and bracketed tags.
     */
    fun sanitizeTitle(rawTitle: String?): String {
        if (rawTitle.isNullOrBlank()) return "Unknown Title"
        var title = rawTitle.trim()
        title = FILE_EXTENSION_PATTERN.matcher(title).replaceAll("").trim()
        title = cleanMetadataString(title)
        return if (title.isBlank()) rawTitle.trim() else title
    }

    /**
     * Returns sanitized artist name with normalized whitespace and casing.
     */
    fun sanitizeArtist(rawArtist: String?): String {
        if (rawArtist.isNullOrBlank()) return "Unknown Artist"
        val trimmed = cleanMetadataString(rawArtist.trim())
        if (trimmed.isBlank()) return "Unknown Artist"
        return toCanonicalCasing(trimmed)
    }

    /**
     * Extracts the primary canonical artist name for grouping & deduplication.
     * E.g.: "Arijit Singh feat. Jasleen Royal" -> "Arijit Singh"
     * E.g.: "Sidhu Moose Wala & Bohemia" -> "Sidhu Moose Wala"
     * E.g.: "arijit singh" -> "Arijit Singh"
     */
    fun getCanonicalArtist(rawArtist: String?): String {
        val sanitized = sanitizeArtist(rawArtist)
        if (sanitized == "Unknown Artist") return sanitized

        // Split off guest artists to find the primary canonical artist entity
        val primary = sanitized.replace(FEAT_DELIMITERS, "").trim()
        return if (primary.isNotBlank()) toCanonicalCasing(primary) else sanitized
    }

    /**
     * Returns sanitized album name with normalized whitespace and casing.
     */
    fun sanitizeAlbum(rawAlbum: String?): String {
        if (rawAlbum.isNullOrBlank()) return "Unknown Album"
        val album = cleanMetadataString(rawAlbum.trim())
        return if (album.isBlank()) "Unknown Album" else toCanonicalCasing(album)
    }

    /**
     * Consolidates split albums into a single canonical entity.
     * Removes version suffixes like "- Deluxe Edition", "(Remastered 2021)", " - EP".
     */
    fun getCanonicalAlbum(rawAlbum: String?): String {
        val sanitized = sanitizeAlbum(rawAlbum)
        if (sanitized == "Unknown Album") return sanitized

        val stripped = sanitized.replace(ALBUM_VERSION_TAGS, "").trim()
        return if (stripped.isNotBlank()) toCanonicalCasing(stripped) else sanitized
    }

    /**
     * Converts ALL-CAPS or all-lowercase artist/album strings into canonical Title Case,
     * while preserving mixed-case names (e.g. "AP Dhillon", "AC/DC").
     */
    private fun toCanonicalCasing(input: String): String {
        val isAllUpper = input.length > 3 && input.all { !it.isLetter() || it.isUpperCase() }
        val isAllLower = input.length > 3 && input.all { !it.isLetter() || it.isLowerCase() }

        if (!isAllUpper && !isAllLower) {
            return input
        }

        return input.split(" ").joinToString(" ") { word ->
            if (word.equals("feat", ignoreCase = true) || word.equals("ft", ignoreCase = true)) {
                "feat."
            } else if (word.length <= 2 && word.all { it.isLetter() }) {
                word.uppercase(Locale.getDefault())
            } else {
                word.lowercase(Locale.getDefault())
                    .replaceFirstChar { if (it.isLowerCase()) it.titlecase(Locale.getDefault()) else it.toString() }
            }
        }
    }

    /**
     * Extracts raw TIT2 tag from local audio file using MediaMetadataRetriever
     * and sanitizes it preserving the original script.
     */
    fun extractAndSanitizeTitle(dataPath: String?, mediaStoreTitle: String?): String {
        var rawTitle = mediaStoreTitle
        if (!dataPath.isNullOrBlank()) {
            try {
                val retriever = MediaMetadataRetriever()
                retriever.setDataSource(dataPath)
                val id3Title = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_TITLE)
                retriever.release()
                if (!id3Title.isNullOrBlank()) {
                    rawTitle = id3Title
                }
            } catch (_: Exception) {}
        }
        return sanitizeTitle(rawTitle)
    }
}
