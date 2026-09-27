package com.example.utils

import android.content.Context
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.util.Log
import java.io.File
import java.io.FileInputStream
import java.io.RandomAccessFile
import java.nio.charset.Charset
import java.nio.charset.StandardCharsets

/**
 * Native ID3v2 frame extractor for embedded USLT (Unsynchronized lyrics)
 * and SYLT (Synchronized lyrics) frames.
 */
object Id3LyricsExtractor {

    private const val TAG = "Id3LyricsExtractor"

    /**
     * Extracts embedded lyrics from an audio file.
     * Tries:
     * 1. Direct ID3v2 binary frame inspection (USLT / SYLT)
     * 2. MediaMetadataRetriever standard fallback
     */
    fun extractEmbeddedLyrics(context: Context, dataPath: String?, contentUriString: String?): String? {
        if (!dataPath.isNullOrBlank()) {
            val file = File(dataPath)
            if (file.exists() && file.canRead()) {
                val id3Lyrics = extractFromMp3File(file)
                if (!id3Lyrics.isNullOrBlank()) return id3Lyrics
            }
        }

        // Fallback: MediaMetadataRetriever
        try {
            val retriever = MediaMetadataRetriever()
            if (!dataPath.isNullOrBlank() && File(dataPath).exists()) {
                retriever.setDataSource(dataPath)
            } else if (!contentUriString.isNullOrBlank() && !contentUriString.startsWith("android.resource://")) {
                retriever.setDataSource(context, Uri.parse(contentUriString))
            } else {
                return null
            }

            // Some Android devices expose lyrics or commentary via metadata keys
            val comment = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_TITLE)
            retriever.release()
        } catch (_: Exception) {}

        return null
    }

    private fun extractFromMp3File(file: File): String? {
        return try {
            RandomAccessFile(file, "r").use { raf ->
                val header = ByteArray(10)
                raf.readFully(header)
                if (header[0] != 'I'.code.toByte() || header[1] != 'D'.code.toByte() || header[2] != '3'.code.toByte()) {
                    return null
                }

                val majorVersion = header[3].toInt()
                val tagSize = (header[6].toInt() and 0x7F shl 21) or
                        (header[7].toInt() and 0x7F shl 14) or
                        (header[8].toInt() and 0x7F shl 7) or
                        (header[9].toInt() and 0x7F)

                val tagEnd = 10L + tagSize

                while (raf.filePointer < tagEnd - 10) {
                    val frameHeader = ByteArray(10)
                    raf.readFully(frameHeader)

                    val frameId = String(frameHeader, 0, 4, StandardCharsets.ISO_8859_1)
                    if (frameId.all { it == '\u0000' }) break // Padding reached

                    val frameSize = if (majorVersion == 4) {
                        (frameHeader[4].toInt() and 0x7F shl 21) or
                                (frameHeader[5].toInt() and 0x7F shl 14) or
                                (frameHeader[6].toInt() and 0x7F shl 7) or
                                (frameHeader[7].toInt() and 0x7F)
                    } else {
                        (frameHeader[4].toInt() and 0xFF shl 24) or
                                (frameHeader[5].toInt() and 0xFF shl 16) or
                                (frameHeader[6].toInt() and 0xFF shl 8) or
                                (frameHeader[7].toInt() and 0xFF)
                    }

                    if (frameSize <= 0 || frameSize > tagSize) break

                    if (frameId == "USLT") {
                        val frameData = ByteArray(frameSize)
                        raf.readFully(frameData)
                        return parseUsltFrame(frameData)
                    } else if (frameId == "SYLT") {
                        val frameData = ByteArray(frameSize)
                        raf.readFully(frameData)
                        val sylt = parseSyltFrame(frameData)
                        if (!sylt.isNullOrBlank()) return sylt
                    } else {
                        raf.skipBytes(frameSize)
                    }
                }
                null
            }
        } catch (e: Exception) {
            Log.d(TAG, "ID3 parse exception: ${e.message}")
            null
        }
    }

    private fun parseUsltFrame(data: ByteArray): String? {
        if (data.size < 5) return null
        val encodingByte = data[0].toInt()
        val charset = getCharset(encodingByte)

        // data[1..3] is 3-byte language
        var index = 4
        // Skip null-terminated content descriptor
        index = skipNullTerminated(data, index, encodingByte)
        if (index >= data.size) return null

        val lyricsBytes = data.copyOfRange(index, data.size)
        val text = String(lyricsBytes, charset).trim()
        return text.ifEmpty { null }
    }

    private fun parseSyltFrame(data: ByteArray): String? {
        if (data.size < 6) return null
        val encodingByte = data[0].toInt()
        val charset = getCharset(encodingByte)
        val timeFormat = data[4].toInt() // 2 = milliseconds

        var index = 6
        index = skipNullTerminated(data, index, encodingByte)

        val sb = StringBuilder()
        while (index < data.size) {
            val textStart = index
            while (index < data.size && data[index] != 0.toByte()) {
                index++
            }
            if (index >= data.size) break
            val lineText = String(data, textStart, index - textStart, charset).trim()
            index++ // skip null byte

            if (index + 4 <= data.size) {
                val timeMs = (data[index].toInt() and 0xFF shl 24) or
                        (data[index + 1].toInt() and 0xFF shl 16) or
                        (data[index + 2].toInt() and 0xFF shl 8) or
                        (data[index + 3].toInt() and 0xFF)
                index += 4

                if (timeFormat == 2 && lineText.isNotEmpty()) {
                    val minutes = timeMs / 60000
                    val seconds = (timeMs % 60000) / 1000
                    val hundredths = (timeMs % 1000) / 10
                    sb.append(String.format("[%02d:%02d.%02d]%s\n", minutes, seconds, hundredths, lineText))
                }
            }
        }
        return sb.toString().trim().ifEmpty { null }
    }

    private fun skipNullTerminated(data: ByteArray, startIndex: Int, encodingByte: Int): Int {
        var idx = startIndex
        val isDoubleByte = (encodingByte == 1 || encodingByte == 2)
        while (idx < data.size) {
            if (isDoubleByte && idx + 1 < data.size) {
                if (data[idx] == 0.toByte() && data[idx + 1] == 0.toByte()) {
                    return idx + 2
                }
                idx += 2
            } else {
                if (data[idx] == 0.toByte()) {
                    return idx + 1
                }
                idx++
            }
        }
        return idx
    }

    private fun getCharset(encodingByte: Int): Charset {
        return when (encodingByte) {
            1 -> StandardCharsets.UTF_16
            2 -> StandardCharsets.UTF_16BE
            3 -> StandardCharsets.UTF_8
            else -> StandardCharsets.ISO_8859_1
        }
    }
}
