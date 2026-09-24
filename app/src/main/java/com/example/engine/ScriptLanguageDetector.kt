package com.example.engine

/**
 * Offline linguistic classifier inspecting UTF-8 Unicode codepoints
 * for Devanagari (Hindi, Marathi) and Gurmukhi (Punjabi) scripts.
 */
object ScriptLanguageDetector {

    enum class ScriptType(val displayName: String, val tag: String) {
        PUNJABI("Punjabi", "PUNJABI"),
        HINDI_MARATHI("Hindi & Marathi", "HINDI_MARATHI"),
        ENGLISH_OTHER("International / English", "ENGLISH_OTHER")
    }

    /**
     * Inspects track title, artist name, and album to determine script identity.
     */
    fun detectScript(title: String, artist: String, album: String = ""): ScriptType {
        val combinedText = "$title $artist $album"
        var gurmukhiCount = 0
        var devanagariCount = 0

        for (ch in combinedText) {
            val codePoint = ch.code
            // Gurmukhi Unicode Block: \u0A00 – \u0A7F
            if (codePoint in 0x0A00..0x0A7F) {
                gurmukhiCount++
            }
            // Devanagari Unicode Block: \u0900 – \u097F
            else if (codePoint in 0x0900..0x097F) {
                devanagariCount++
            }
        }

        // Also check common transliterated keywords if pure Unicode isn't present
        if (gurmukhiCount > 0 && gurmukhiCount >= devanagariCount) {
            return ScriptType.PUNJABI
        }
        if (devanagariCount > 0 && devanagariCount > gurmukhiCount) {
            return ScriptType.HINDI_MARATHI
        }

        // Secondary transliteration detection for Latin script Punjabi / Hindi keywords
        val lower = combinedText.lowercase()
        val punjabiKeywords = listOf(
            "sidhu", "moosewala", "ap dhillon", "diljit", "dosanjh", "karan aujla", 
            "shubh", "amrit maan", "jassi", "bhangra", "tappe", "boliyan", "punjabi"
        )
        val hindiKeywords = listOf(
            "arijit", "shreya", "sonu nigam", "lata", "kishore", "kumar sanu", 
            "alka yagnik", "pritam", "bollywood", "ghazal", "marathi", "abhang", 
            "ajay atul", "ar rahman", "hindi"
        )

        for (kw in punjabiKeywords) {
            if (lower.contains(kw)) return ScriptType.PUNJABI
        }
        for (kw in hindiKeywords) {
            if (lower.contains(kw)) return ScriptType.HINDI_MARATHI
        }

        return ScriptType.ENGLISH_OTHER
    }
}
