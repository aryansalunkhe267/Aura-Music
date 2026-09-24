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
                val lyricText = trimmed.substring(lastMatchEnd).trim()
                for (time in timestamps) {
                    result.add(LyricLine(timeMs = time, text = lyricText))
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
     * Provides fallback demo lyrics with authentic bilingual lines when no .lrc file is loaded.
     */
    fun generateDemoLyrics(title: String, artist: String, durationMs: Long): String {
        val totalSec = (durationMs / 1000L).coerceAtLeast(30L)
        val step = (totalSec / 8).coerceIn(4, 15)

        return buildString {
            appendLine("[00:02.00]♪ Instrumental Intro ♪")
            appendLine("[00:${String.format("%02d", step)}.00] $title - $artist")
            appendLine("[00:${String.format("%02d", step * 2)}.00] ਤੂੰ ਹੀ ਮੇਰਾ ਪਿਆਰ, ਰੂਹ ਦਾ ਸਕੂਨ (Tujh Mein Rab Dikhta Hai)")
            appendLine("[00:${String.format("%02d", step * 3)}.00] दिल से सुनो ये धड़कन, सुरमई शाम का साया")
            appendLine("[00:${String.format("%02d", step * 4)}.00] सा रे गा मा पा धा नि सा... संगीत ही जीवन है")
            appendLine("[00:${String.format("%02d", step * 5)}.00] Every beat resonates through the soul")
            appendLine("[00:${String.format("%02d", step * 6)}.00] ਹਰ ਸਾਹ ਵਿੱਚ ਤੇਰਾ ਨਾਮ, ਬੇਪਰਵਾਹ ਸੁਰ")
            appendLine("[00:${String.format("%02d", step * 7)}.00] ♪ Outro Fade ♪")
        }
    }
}
