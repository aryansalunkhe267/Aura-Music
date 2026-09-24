package com.example.utils

import java.util.regex.Pattern

/**
 * Sanitization function to strip parenthetical translations and trailing tags:
 * - Removes any text inside parentheses: (Translation)
 * - Removes text inside square brackets: [Official Audio]
 */
fun cleanMetadataString(raw: String): String {
    return raw
        .replace(Regex("""\s*\([^)]*\)"""), "")
        .replace(Regex("""\[[^\]]*\]"""), "")
        .trim()
}

/**
 * Raw metadata extractor and sanitizer that strictly preserves the original native tags verbatim.
 * Completely disables any translation, transliteration, or script-replacement logic,
 * trimming whitespace and removing trailing audio file extension artifacts.
 */
object MetadataSanitizer {

    // Audio file extensions commonly left over in filename-based metadata
    private val FILE_EXTENSION_PATTERN = Pattern.compile("""(?i)\.(mp3|m4a|flac|wav|aac|ogg|opus)$""")

    fun cleanMetadataString(raw: String): String = com.example.utils.cleanMetadataString(raw)

    /**
     * Returns the raw original title verbatim.
     * Only trims whitespace and strips trailing file extensions.
     */
    fun sanitizeTitle(rawTitle: String?): String {
        if (rawTitle.isNullOrBlank()) return "Unknown Title"
        var title = rawTitle.trim()
        title = FILE_EXTENSION_PATTERN.matcher(title).replaceAll("").trim()
        return if (title.isBlank()) rawTitle.trim() else title
    }

    /**
     * Returns the raw artist verbatim, strictly preserving native script without modification.
     */
    fun sanitizeArtist(rawArtist: String?): String {
        if (rawArtist.isNullOrBlank()) return "Unknown Artist"
        val artist = rawArtist.trim()
        return if (artist.isBlank()) "Unknown Artist" else artist
    }

    /**
     * Returns the raw album name verbatim, strictly preserving native script without modification.
     */
    fun sanitizeAlbum(rawAlbum: String?): String {
        if (rawAlbum.isNullOrBlank()) return "Unknown Album"
        val album = rawAlbum.trim()
        return if (album.isBlank()) "Unknown Album" else album
    }
}
