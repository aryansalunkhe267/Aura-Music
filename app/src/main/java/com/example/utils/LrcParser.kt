package com.example.utils

import java.util.regex.Pattern

data class LyricLine(
    val timeMs: Long,
    val text: String
)

object LrcParser {

    private val LRC_TIME_PATTERN = Pattern.compile("\\[(\\d{1,2}):(\\d{2})(?:[.:](\\d{2,3}))?]")

    /**
     * Parses a .lrc formatted string into ordered LyricLine objects.
     * Enforces the ZERO TRANSLATION RULE & TRANSLITERATION REQUIREMENT:
     * - Discards parenthetical / bracketed English translations (e.g. "(Your love is saffron)").
     * - Transliterates original Hindi, Punjabi, or Marathi words into standard Latin/English alphabet
     *   without altering the native tongue (e.g., "Kesariya tera ishq hai piya").
     */
    fun parse(lrcContent: String?): List<LyricLine> {
        if (lrcContent.isNullOrBlank()) return emptyList()

        val lines = lrcContent.lines()
        val result = mutableListOf<LyricLine>()

        for (line in lines) {
            val trimmed = line.trim()
            if (trimmed.isEmpty()) continue

            val matcher = LRC_TIME_PATTERN.matcher(trimmed)
            val timestamps = mutableListOf<Long>()
            var lastMatchEnd = 0

            while (matcher.find()) {
                val minStr = matcher.group(1) ?: "0"
                val secStr = matcher.group(2) ?: "0"
                val fracStr = matcher.group(3) ?: "0"

                val minutes = minStr.toLongOrNull() ?: 0L
                val seconds = secStr.toLongOrNull() ?: 0L
                val millis = when (fracStr.length) {
                    2 -> (fracStr.toLongOrNull() ?: 0L) * 10
                    3 -> fracStr.toLongOrNull() ?: 0L
                    else -> 0L
                }

                val totalMs = minutes * 60_000L + seconds * 1000L + millis
                timestamps.add(totalMs)
                lastMatchEnd = matcher.end()
            }

            if (timestamps.isNotEmpty()) {
                val rawLyricText = trimmed.substring(lastMatchEnd).trim()
                // Apply strict transliteration & translation-stripping
                val sanitizedLyric = IndicTransliterator.cleanAndTransliterate(rawLyricText)
                if (sanitizedLyric.isNotBlank()) {
                    for (time in timestamps) {
                        result.add(LyricLine(timeMs = time, text = sanitizedLyric))
                    }
                }
            }
        }

        return result.sortedBy { it.timeMs }
    }

    /**
     * Returns the index of the currently active lyric line matching [currentMs].
     */
    fun findActiveIndex(lyrics: List<LyricLine>, currentMs: Long): Int {
        if (lyrics.isEmpty()) return -1
        if (currentMs < lyrics.first().timeMs) return 0

        var activeIndex = 0
        for (i in lyrics.indices) {
            if (lyrics[i].timeMs <= currentMs) {
                activeIndex = i
            } else {
                break
            }
        }
        return activeIndex
    }

    /**
     * Provides fallback demo lyrics with strict phonetic transliteration (no English translation).
     * Preserves original Hindi, Punjabi, and Marathi lyrics in the Latin alphabet.
     */
    fun generateDemoLyrics(title: String, artist: String, durationMs: Long): String {
        val totalSec = (durationMs / 1000L).coerceAtLeast(30L)
        val step = (totalSec / 8).coerceIn(4, 15)

        if (title.contains("295", ignoreCase = true)) {
            return buildString {
                appendLine("[00:02.00]♪ Bass & Dhol Intro ♪")
                appendLine("[00:${String.format("%02d", step)}.00] Nit controversy create milugi")
                appendLine("[00:${String.format("%02d", step * 2)}.00] Dharma de naam te debate milugi")
                appendLine("[00:${String.format("%02d", step * 3)}.00] Sach bolenga taan milu 295")
                appendLine("[00:${String.format("%02d", step * 4)}.00] Je karenga tarakki putt hate milugi")
                appendLine("[00:${String.format("%02d", step * 5)}.00] Nit controversy create milugi")
                appendLine("[00:${String.format("%02d", step * 6)}.00] Sach bolenga taan milu 295")
                appendLine("[00:${String.format("%02d", step * 7)}.00] ♪ Outro Fade ♪")
            }
        }

        return buildString {
            appendLine("[00:02.00]♪ Instrumental Intro ♪")
            appendLine("[00:${String.format("%02d", step)}.00] $title - $artist")
            appendLine("[00:${String.format("%02d", step * 2)}.00] Tu hi mera pyaar, rooh da sukoon")
            appendLine("[00:${String.format("%02d", step * 3)}.00] Dil se suno ye dhadkan, surmayi shaam ka saaya")
            appendLine("[00:${String.format("%02d", step * 4)}.00] Sa re ga ma pa dha ni sa... sangeet hi jeevan hai")
            appendLine("[00:${String.format("%02d", step * 5)}.00] Kesariya tera ishq hai piya, rang jaaun jo main haath lagaun")
            appendLine("[00:${String.format("%02d", step * 6)}.00] Har saah vich tera naam, beparwah sur")
            appendLine("[00:${String.format("%02d", step * 7)}.00] ♪ Outro Fade ♪")
        }
    }
}
