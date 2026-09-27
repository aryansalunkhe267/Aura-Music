package com.example.utils

import java.util.regex.Pattern

/**
 * High-performance, offline Indic Script Transliterator and Romanizer.
 * Strictly adheres to the ZERO TRANSLATION RULE:
 * - NEVER translates Hindi, Punjabi, or Marathi into English vocabulary.
 * - Transliterates original lyrics into phonetic Latin/Roman alphabet (e.g., "Kesariya tera ishq hai piya",
 *   NOT "Your love is saffron, my beloved").
 * - Strips any bracketed/parenthetical translation artifacts to preserve only the native language as sung.
 */
object IndicTransliterator {

    private val TRANSLATION_PAREN_REGEX = Regex("""(?i)\s*\((?:translation|meaning|eng|english|sub)?[:\s\-]*[A-Za-z\s,.'!?-]{3,}\)""")
    private val TRANSLATION_BRACKET_REGEX = Regex("""(?i)\s*\[(?:translation|meaning|eng|english|sub)[:\s\-]*[^\]]+\]""")
    private val TRANSLATION_LABEL_REGEX = Regex("""(?i)^\s*(?:translation|meaning|english|subtitles?)\s*[:\-].*""")

    // Common indicators of accidental English translation of Indic songs
    private val ENGLISH_TRANSLATION_INDICATORS = setOf(
        "your love is saffron", "my beloved", "god spent all treasures",
        "walking softly in the monsoon", "eternal longing", "cruising down the city",
        "saffron love", "your love", "beloved", "color me", "tell me someone"
    )

    /**
     * Checks if a character belongs to Devanagari (Hindi, Marathi, Sanskrit)
     */
    fun isDevanagari(c: Char): Boolean = c.code in 0x0900..0x097F

    /**
     * Checks if a character belongs to Gurmukhi (Punjabi)
     */
    fun isGurmukhi(c: Char): Boolean = c.code in 0x0A00..0x0A7F

    /**
     * Checks if the text contains any Indic script characters (Devanagari or Gurmukhi)
     */
    fun containsIndicScript(text: String): Boolean {
        for (ch in text) {
            if (isDevanagari(ch) || isGurmukhi(ch)) return true
        }
        return false
    }

    /**
     * Checks if a lyric line is exclusively an English translation or translation label,
     * which must be completely eliminated under the Zero Translation Rule.
     */
    fun isTranslationOnlyLine(text: String): Boolean {
        val trimmed = text.trim()
        if (trimmed.isBlank()) return false
        if (TRANSLATION_LABEL_REGEX.matches(trimmed)) return true
        val lower = trimmed.lowercase()
        for (indicator in ENGLISH_TRANSLATION_INDICATORS) {
            if (lower == indicator || lower.startsWith("$indicator,") || lower.startsWith("$indicator.")) {
                return true
            }
        }
        return false
    }

    /**
     * Checks if a block of LRC content is entirely translated into English words.
     */
    fun isPureEnglishTranslation(lrcContent: String): Boolean {
        val lower = lrcContent.lowercase()
        for (indicator in ENGLISH_TRANSLATION_INDICATORS) {
            if (lower.contains(indicator)) return true
        }
        if (lower.contains("[translation") || lower.contains("(translation") ||
            lower.contains("[meaning") || lower.contains("(meaning")) {
            return true
        }
        return false
    }

    /**
     * Strips translation separators like " // ", " | ", " - " if the right side is an English translation.
     */
    private fun stripTrailingTranslationSeparators(text: String): String {
        val separators = listOf(" // ", " | ", " — ", " - ")
        for (sep in separators) {
            val idx = text.indexOf(sep)
            if (idx > 0) {
                val left = text.substring(0, idx).trim()
                val right = text.substring(idx + sep.length).trim()
                val rightLower = right.lowercase()
                val isRightTranslation = TRANSLATION_LABEL_REGEX.matches(right) ||
                        ENGLISH_TRANSLATION_INDICATORS.any { rightLower.contains(it) } ||
                        rightLower.startsWith("meaning") || rightLower.startsWith("translation")
                if (isRightTranslation) {
                    return left
                }
            }
        }
        return text
    }

    /**
     * Cleans parenthetical translations and ensures the lyrics are strictly transliterated Roman text.
     * Removes parenthetical translations like "(Your love is saffron)" or "(Meaning: ...)",
     * strips trailing translation clauses, and transliterates Indic scripts phonetically.
     */
    fun cleanAndTransliterate(rawText: String): String {
        if (rawText.isBlank()) return ""

        if (isTranslationOnlyLine(rawText)) return ""

        // 1. Remove bracketed / parenthetical translations
        var cleaned = rawText
            .replace(TRANSLATION_PAREN_REGEX, "")
            .replace(TRANSLATION_BRACKET_REGEX, "")
            .trim()

        // 2. Remove trailing translation separators
        cleaned = stripTrailingTranslationSeparators(cleaned)

        if (isTranslationOnlyLine(cleaned)) return ""

        // 3. If it contains Devanagari or Gurmukhi scripts, convert them phonetically to Roman alphabet
        if (containsIndicScript(cleaned)) {
            cleaned = transliterateToLatin(cleaned)
        }

        return cleaned
    }

    /**
     * Transliterates Indic scripts (Devanagari and Gurmukhi) character by character
     * into natural Romanized Latin text.
     */
    fun transliterateToLatin(input: String): String {
        val sb = StringBuilder()
        var i = 0
        val len = input.length
        var addakActive = false

        while (i < len) {
            val ch = input[i]
            val code = ch.code

            // Handle Gurmukhi Addak (doubles next consonant)
            if (code == 0x0A71) {
                addakActive = true
                i++
                continue
            }

            when {
                isDevanagari(ch) -> {
                    val (roman, consumed) = transliterateDevanagariCluster(input, i)
                    sb.append(roman)
                    i += consumed
                }
                isGurmukhi(ch) -> {
                    val (roman, consumed) = transliterateGurmukhiCluster(input, i)
                    if (addakActive && roman.isNotEmpty()) {
                        sb.append(roman.first())
                        addakActive = false
                    }
                    sb.append(roman)
                    i += consumed
                }
                else -> {
                    sb.append(ch)
                    addakActive = false
                    i++
                }
            }
        }

        return sb.toString().trim()
    }

    /**
     * Transliterates a Devanagari character / consonant cluster starting at index [startIndex].
     */
    private fun transliterateDevanagariCluster(text: String, startIndex: Int): Pair<String, Int> {
        val ch = text[startIndex]
        val code = ch.code

        // 1. Independent Vowels
        DEVANAGARI_VOWELS[code]?.let { return Pair(it, 1) }

        // 2. Modifiers: Anusvara, Visarga, Chandrabindu
        when (code) {
            0x0901 -> return Pair("n", 1) // Chandrabindu
            0x0902 -> return Pair("n", 1) // Anusvara
            0x0903 -> return Pair("h", 1) // Visarga
            0x093C -> return Pair("", 1)  // Nukta standalone
        }

        // 3. Consonants
        val consonantBase = DEVANAGARI_CONSONANTS[code]
        if (consonantBase != null) {
            var consumed = 1
            var hasNukta = false

            // Check next char for Nukta (\u093C)
            if (startIndex + consumed < text.length && text[startIndex + consumed].code == 0x093C) {
                hasNukta = true
                consumed++
            }

            val basePhonetic = if (hasNukta || (consonantBase == "k" && startIndex >= 2 && text.substring(0, startIndex).endsWith("श्"))) {
                if (hasNukta) {
                    when (consonantBase) {
                        "k" -> "q"
                        "kh" -> "kh"
                        "g" -> "gh"
                        "j" -> "z"
                        "d" -> "r"
                        "dh" -> "rh"
                        "ph" -> "f"
                        else -> consonantBase
                    }
                } else "q"
            } else consonantBase

            // Check next char for Halant / Virama (\u094D) or Matra
            if (startIndex + consumed < text.length) {
                val nextCh = text[startIndex + consumed]
                val nextCode = nextCh.code

                // Virama: suppresses default 'a'
                if (nextCode == 0x094D) {
                    return Pair(basePhonetic, consumed + 1)
                }

                // Matra (dependent vowel)
                val matraVowel = DEVANAGARI_MATRAS[nextCode]
                if (matraVowel != null) {
                    val isEndOfWord = (startIndex + consumed + 1 >= text.length) ||
                            text[startIndex + consumed + 1].isWhitespace() ||
                            !isDevanagari(text[startIndex + consumed + 1])
                    val adjustedMatra = if (isEndOfWord && nextCode == 0x093E) {
                        "a"
                    } else if (isEndOfWord && nextCode == 0x0940) {
                        "i"
                    } else {
                        matraVowel
                    }
                    return Pair(basePhonetic + adjustedMatra, consumed + 1)
                }
            }

            // Word-final schwa deletion: if at end of word or string, do not append 'a'
            val isEndOfWord = (startIndex + consumed >= text.length) ||
                    text[startIndex + consumed].isWhitespace() ||
                    !isDevanagari(text[startIndex + consumed])

            val vowelToAppend = if (isEndOfWord) "" else "a"
            return Pair(basePhonetic + vowelToAppend, consumed)
        }

        // 4. Standalone Matras (if orphaned)
        DEVANAGARI_MATRAS[code]?.let { return Pair(it, 1) }

        return Pair(ch.toString(), 1)
    }

    /**
     * Transliterates a Gurmukhi character / consonant cluster starting at index [startIndex].
     */
    private fun transliterateGurmukhiCluster(text: String, startIndex: Int): Pair<String, Int> {
        val ch = text[startIndex]
        val code = ch.code

        // 1. Independent Vowels
        GURMUKHI_VOWELS[code]?.let { return Pair(it, 1) }

        // 2. Modifiers: Bindi, Tippi, Visarga
        when (code) {
            0x0A01, 0x0A02 -> return Pair("n", 1) // Bindi
            0x0A03 -> return Pair("h", 1)         // Visarga
            0x0A70 -> return Pair("n", 1)         // Tippi
        }

        // 3. Consonants
        val consonantBase = GURMUKHI_CONSONANTS[code]
        if (consonantBase != null) {
            var consumed = 1

            // Check next char for Halant / Virama (\u0A4D) or Matra
            if (startIndex + consumed < text.length) {
                val nextCh = text[startIndex + consumed]
                val nextCode = nextCh.code

                // Virama suppresses default 'a'
                if (nextCode == 0x0A4D) {
                    return Pair(consonantBase, consumed + 1)
                }

                // Matra
                val matraVowel = GURMUKHI_MATRAS[nextCode]
                if (matraVowel != null) {
                    val isEndOfWord = (startIndex + consumed + 1 >= text.length) ||
                            text[startIndex + consumed + 1].isWhitespace() ||
                            !isGurmukhi(text[startIndex + consumed + 1])
                    val adjustedMatra = if (isEndOfWord && nextCode == 0x0A3E) {
                        "a"
                    } else if (isEndOfWord && nextCode == 0x0A40) {
                        "i"
                    } else {
                        matraVowel
                    }
                    return Pair(consonantBase + adjustedMatra, consumed + 1)
                }
            }

            // Word-final schwa deletion
            val isEndOfWord = (startIndex + consumed >= text.length) ||
                    text[startIndex + consumed].isWhitespace() ||
                    !isGurmukhi(text[startIndex + consumed])

            val vowelToAppend = if (isEndOfWord) "" else "a"
            return Pair(consonantBase + vowelToAppend, consumed)
        }

        // 4. Standalone Matras
        GURMUKHI_MATRAS[code]?.let { return Pair(it, 1) }

        return Pair(ch.toString(), 1)
    }

    /**
     * Transliterates an entire .lrc content string line-by-line while preserving timestamps.
     * Strictly enforces the Zero Translation Rule by discarding translation lines
     * and transliterating Indic scripts into Latin alphabet.
     */
    fun transliterateLrc(lrcContent: String?): String {
        if (lrcContent.isNullOrBlank()) return ""

        val timePattern = Pattern.compile("^\\[\\d{1,2}:\\d{2}(?:[.:]\\d{2,3})?]")

        return lrcContent.lineSequence()
            .mapNotNull { line ->
                val trimmed = line.trim()
                if (trimmed.isEmpty()) return@mapNotNull null

                val matcher = timePattern.matcher(trimmed)
                if (matcher.find()) {
                    val timestamp = matcher.group()
                    val lyricPart = trimmed.substring(matcher.end()).trim()
                    val transliterated = cleanAndTransliterate(lyricPart)
                    if (transliterated.isNotBlank()) {
                        "$timestamp $transliterated"
                    } else {
                        null
                    }
                } else {
                    val cleaned = cleanAndTransliterate(trimmed)
                    if (cleaned.isNotBlank()) cleaned else null
                }
            }
            .joinToString("\n")
    }

    // --- Unicode Mapping Tables ---

    private val DEVANAGARI_VOWELS = mapOf(
        0x0905 to "a",
        0x0906 to "aa",
        0x0907 to "i",
        0x0908 to "ee",
        0x0909 to "u",
        0x090A to "oo",
        0x090B to "ri",
        0x090F to "e",
        0x0910 to "ai",
        0x0913 to "o",
        0x0914 to "au",
        0x0904 to "e",
        0x090D to "e",
        0x0911 to "o",
        0x0912 to "o"
    )

    private val DEVANAGARI_MATRAS = mapOf(
        0x093E to "aa",
        0x093F to "i",
        0x0940 to "ee",
        0x0941 to "u",
        0x0942 to "oo",
        0x0943 to "ri",
        0x0947 to "e",
        0x0948 to "ai",
        0x094B to "o",
        0x094C to "au",
        0x0945 to "e",
        0x0946 to "e",
        0x0949 to "o"
    )

    private val DEVANAGARI_CONSONANTS = mapOf(
        0x0915 to "k", 0x0916 to "kh", 0x0917 to "g", 0x0918 to "gh", 0x0919 to "ng",
        0x091A to "ch", 0x091B to "chh", 0x091C to "j", 0x091D to "jh", 0x091E to "ny",
        0x091F to "t", 0x0920 to "th", 0x0921 to "d", 0x0922 to "dh", 0x0923 to "n",
        0x0924 to "t", 0x0925 to "th", 0x0926 to "d", 0x0927 to "dh", 0x0928 to "n",
        0x092A to "p", 0x092B to "ph", 0x092C to "b", 0x092D to "bh", 0x092E to "m",
        0x092F to "y", 0x0930 to "r", 0x0932 to "l", 0x0933 to "l", 0x0935 to "v",
        0x0936 to "sh", 0x0937 to "sh", 0x0938 to "s", 0x0939 to "h",
        // Additional Marathi / Sanskrit letters
        0x0958 to "q", 0x0959 to "kh", 0x095A to "gh", 0x095B to "z",
        0x095C to "r", 0x095D to "rh", 0x095E to "f"
    )

    private val GURMUKHI_VOWELS = mapOf(
        0x0A05 to "a",
        0x0A06 to "aa",
        0x0A07 to "i",
        0x0A08 to "ee",
        0x0A09 to "u",
        0x0A0A to "oo",
        0x0A0F to "e",
        0x0A10 to "ai",
        0x0A13 to "o",
        0x0A14 to "au"
    )

    private val GURMUKHI_MATRAS = mapOf(
        0x0A3E to "aa",
        0x0A3F to "i",
        0x0A40 to "ee",
        0x0A41 to "u",
        0x0A42 to "oo",
        0x0A47 to "e",
        0x0A48 to "ai",
        0x0A4B to "o",
        0x0A4C to "au"
    )

    private val GURMUKHI_CONSONANTS = mapOf(
        0x0A15 to "k", 0x0A16 to "kh", 0x0A17 to "g", 0x0A18 to "gh", 0x0A19 to "ng",
        0x0A1A to "ch", 0x0A1B to "chh", 0x0A1C to "j", 0x0A1D to "jh", 0x0A1E to "ny",
        0x0A1F to "t", 0x0A20 to "th", 0x0A21 to "d", 0x0A22 to "dh", 0x0A23 to "n",
        0x0A24 to "t", 0x0A25 to "th", 0x0A26 to "d", 0x0A27 to "dh", 0x0A28 to "n",
        0x0A2A to "p", 0x0A2B to "ph", 0x0A2C to "b", 0x0A2D to "bh", 0x0A2E to "m",
        0x0A2F to "y", 0x0A30 to "r", 0x0A32 to "l", 0x0A33 to "l", 0x0A35 to "v",
        0x0A36 to "sh", 0x0A38 to "s", 0x0A39 to "h",
        0x0A59 to "kh", 0x0A5A to "gh", 0x0A5B to "z", 0x0A5C to "r", 0x0A5E to "f"
    )
}
