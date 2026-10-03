package com.example.utils

import java.util.regex.Pattern

/**
 * Universal Caption to .LRC synchronizer.
 * Converts WebVTT, TTML, and YouTube XML (srv1/srv3) timed captions
 * into synchronized standard .lrc format ([mm:ss.xx]Text).
 */
object CaptionToLrcConverter {

    private val VTT_TIME_PATTERN = Pattern.compile("(\\d{2}:)?(\\d{2}):(\\d{2})[.,](\\d{2,3})\\s*-->")
    private val TTML_TIME_PATTERN = Pattern.compile("begin=[\"'](\\d{2}:)?(\\d{2}):(\\d{2})[.,](\\d{2,3})[\"'][^>]*>(.*?)<")
    private val YOUTUBE_XML_PATTERN = Pattern.compile("<text\\s+start=[\"']([0-9.]+)[\"'][^>]*>(.*?)</text>")

    fun convertToLrc(rawCaptions: String?): String? {
        if (rawCaptions.isNullOrBlank()) return null

        val sb = StringBuilder()

        // 1. Try YouTube XML format (<text start="12.34" ...>text</text>)
        val xmlMatcher = YOUTUBE_XML_PATTERN.matcher(rawCaptions)
        var xmlFound = false
        while (xmlMatcher.find()) {
            val startSeconds = xmlMatcher.group(1)?.toDoubleOrNull() ?: continue
            val rawText = xmlMatcher.group(2) ?: continue
            val cleanText = unescapeXml(rawText).trim()
            if (cleanText.isBlank()) continue

            val totalMillis = (startSeconds * 1000).toLong()
            val min = totalMillis / 60000
            val sec = (totalMillis % 60000) / 1000
            val hundredths = (totalMillis % 1000) / 10
            sb.append(String.format("[%02d:%02d.%02d]%s\n", min, sec, hundredths, cleanText))
            xmlFound = true
        }
        if (xmlFound && sb.isNotEmpty()) return sb.toString().trim()

        // 2. Try TTML format (<p begin="00:01:23.450" ...>text</p>)
        val ttmlMatcher = TTML_TIME_PATTERN.matcher(rawCaptions)
        var ttmlFound = false
        while (ttmlMatcher.find()) {
            val hoursStr = ttmlMatcher.group(1)?.removeSuffix(":")
            val minStr = ttmlMatcher.group(2) ?: "0"
            val secStr = ttmlMatcher.group(3) ?: "0"
            val msStr = ttmlMatcher.group(4) ?: "0"
            val text = unescapeXml(ttmlMatcher.group(5) ?: "").trim()
            if (text.isBlank()) continue

            val hours = hoursStr?.toLongOrNull() ?: 0L
            val minutes = minStr.toLongOrNull() ?: 0L
            val seconds = secStr.toLongOrNull() ?: 0L
            val millis = when (msStr.length) {
                2 -> (msStr.toLongOrNull() ?: 0L) * 10
                3 -> msStr.toLongOrNull() ?: 0L
                else -> 0L
            }
            val totalMinutes = hours * 60 + minutes
            val hundredths = (millis % 1000) / 10
            sb.append(String.format("[%02d:%02d.%02d]%s\n", totalMinutes, seconds, hundredths, text))
            ttmlFound = true
        }
        if (ttmlFound && sb.isNotEmpty()) return sb.toString().trim()

        // 3. Try WebVTT format
        val lines = rawCaptions.lines()
        var i = 0
        while (i < lines.size) {
            val line = lines[i].trim()
            val matcher = VTT_TIME_PATTERN.matcher(line)
            if (matcher.find()) {
                val hoursStr = matcher.group(1)?.removeSuffix(":")
                val minStr = matcher.group(2) ?: "0"
                val secStr = matcher.group(3) ?: "0"
                val msStr = matcher.group(4) ?: "0"

                val hours = hoursStr?.toLongOrNull() ?: 0L
                val minutes = minStr.toLongOrNull() ?: 0L
                val seconds = secStr.toLongOrNull() ?: 0L
                val millis = when (msStr.length) {
                    2 -> (msStr.toLongOrNull() ?: 0L) * 10
                    3 -> msStr.toLongOrNull() ?: 0L
                    else -> 0L
                }
                val totalMinutes = hours * 60 + minutes
                val hundredths = (millis % 1000) / 10

                // Read following lyric text line
                i++
                val textBuilder = StringBuilder()
                while (i < lines.size && lines[i].isNotBlank() && !lines[i].contains("-->")) {
                    textBuilder.append(lines[i].trim()).append(" ")
                    i++
                }
                val text = cleanVttText(textBuilder.toString().trim())
                if (text.isNotBlank()) {
                    sb.append(String.format("[%02d:%02d.%02d]%s\n", totalMinutes, seconds, hundredths, text))
                }
            } else {
                i++
            }
        }

        return if (sb.isNotEmpty()) sb.toString().trim() else null
    }

    private fun unescapeXml(text: String): String {
        return text.replace("&amp;", "&")
            .replace("&lt;", "<")
            .replace("&gt;", ">")
            .replace("&quot;", "\"")
            .replace("&apos;", "'")
            .replace("&#39;", "'")
            .replace(Regex("<[^>]*>"), "")
    }

    private fun cleanVttText(text: String): String {
        return text.replace(Regex("<[^>]*>"), "")
            .replace(Regex("\\{.*?\\}"), "")
            .trim()
    }
}
