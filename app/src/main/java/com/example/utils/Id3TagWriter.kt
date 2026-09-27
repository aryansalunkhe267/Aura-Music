package com.example.utils

import android.graphics.Bitmap
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.RandomAccessFile
import java.nio.charset.StandardCharsets

/**
 * Native ID3v2.3 Physical Tag Writer for MP3 files.
 * Writes ID3v2.3 frames directly to disk:
 * - TIT2: Song Title
 * - TPE1: Lead Performer / Soloist / Artist
 * - TALB: Album / Movie / Show title
 * - APIC: Attached Picture (Cover Front Art)
 *
 * Strips obsolete ID3v2 headers and prepends freshly serialized ID3v2.3 tag block
 * preserving all underlying MPEG audio frames intact.
 */
object Id3TagWriter {

    private const val TAG = "Id3TagWriter"

    /**
     * Physically writes ID3 tags and APIC cover artwork into the specified MP3 file.
     */
    suspend fun writeTags(
        mp3File: File,
        title: String?,
        artist: String?,
        album: String?,
        coverArtBytes: ByteArray? = null
    ): Boolean = withContext(Dispatchers.IO) {
        if (!mp3File.exists() || !mp3File.canWrite()) {
            Log.e(TAG, "File does not exist or is not writable: ${mp3File.absolutePath}")
            return@withContext false
        }

        val tempFile = File(mp3File.parentFile, "${mp3File.name}.tagtmp")

        try {
            val framesStream = ByteArrayOutputStream()

            // 1. Write TIT2 (Title)
            if (!title.isNullOrBlank()) {
                writeTextFrame(framesStream, "TIT2", title)
            }

            // 2. Write TPE1 (Artist)
            if (!artist.isNullOrBlank()) {
                writeTextFrame(framesStream, "TPE1", artist)
            }

            // 3. Write TALB (Album)
            if (!album.isNullOrBlank()) {
                writeTextFrame(framesStream, "TALB", album)
            }

            // 4. Write APIC (Cover Front Art)
            if (coverArtBytes != null && coverArtBytes.isNotEmpty()) {
                writeApicFrame(framesStream, coverArtBytes)
            }

            val framesData = framesStream.toByteArray()
            if (framesData.isEmpty()) {
                return@withContext true
            }

            // Build ID3v2.3 Header (10 bytes)
            val header = ByteArray(10)
            header[0] = 'I'.code.toByte()
            header[1] = 'D'.code.toByte()
            header[2] = '3'.code.toByte()
            header[3] = 0x03 // Major version 2.3
            header[4] = 0x00 // Revision 0
            header[5] = 0x00 // Flags

            // Synchsafe integer size (7 bits per byte)
            val tagSize = framesData.size
            header[6] = ((tagSize shr 21) and 0x7F).toByte()
            header[7] = ((tagSize shr 14) and 0x7F).toByte()
            header[8] = ((tagSize shr 7) and 0x7F).toByte()
            header[9] = (tagSize and 0x7F).toByte()

            // Read original file, skipping existing ID3v2 header if present
            var audioDataOffset = 0L
            RandomAccessFile(mp3File, "r").use { raf ->
                if (raf.length() >= 10) {
                    val magic = ByteArray(3)
                    raf.readFully(magic)
                    if (magic[0] == 'I'.code.toByte() && magic[1] == 'D'.code.toByte() && magic[2] == '3'.code.toByte()) {
                        raf.seek(6)
                        val b0 = raf.read()
                        val b1 = raf.read()
                        val b2 = raf.read()
                        val b3 = raf.read()
                        val existingTagSize = ((b0 and 0x7F) shl 21) or
                                ((b1 and 0x7F) shl 14) or
                                ((b2 and 0x7F) shl 7) or
                                (b3 and 0x7F)
                        audioDataOffset = (10 + existingTagSize).toLong()
                    }
                }
            }

            // Write new file: ID3v2 Header + Frames + Audio Data
            FileOutputStream(tempFile).use { out ->
                out.write(header)
                out.write(framesData)

                FileInputStream(mp3File).use { input ->
                    if (audioDataOffset > 0) {
                        input.skip(audioDataOffset)
                    }
                    val buffer = ByteArray(32768)
                    var read: Int
                    while (input.read(buffer).also { read = it } != -1) {
                        out.write(buffer, 0, read)
                    }
                }
                out.flush()
            }

            // Replace original file atomically
            if (tempFile.exists() && tempFile.length() > 0) {
                if (mp3File.delete()) {
                    tempFile.renameTo(mp3File)
                    Log.i(TAG, "Successfully updated physical ID3 tags in ${mp3File.name}")
                    return@withContext true
                } else {
                    tempFile.copyTo(mp3File, overwrite = true)
                    tempFile.delete()
                    return@withContext true
                }
            }
            false
        } catch (e: Exception) {
            Log.e(TAG, "Failed writing physical ID3 tags: ${e.message}", e)
            if (tempFile.exists()) tempFile.delete()
            false
        }
    }

    /**
     * Serializes standard ID3v2.3 Text Frame (TIT2, TPE1, TALB).
     */
    private fun writeTextFrame(out: ByteArrayOutputStream, frameId: String, text: String) {
        val encodedBytes = text.toByteArray(StandardCharsets.UTF_8)
        // 1 byte encoding flag (0x03 for UTF-8) + payload
        val framePayload = ByteArray(1 + encodedBytes.size)
        framePayload[0] = 0x03
        System.arraycopy(encodedBytes, 0, framePayload, 1, encodedBytes.size)

        writeFrame(out, frameId, framePayload)
    }

    /**
     * Serializes ID3v2.3 APIC (Attached Picture) Frame.
     */
    private fun writeApicFrame(out: ByteArrayOutputStream, imageBytes: ByteArray) {
        val mimeType = "image/jpeg".toByteArray(StandardCharsets.ISO_8859_1)
        val description = "".toByteArray(StandardCharsets.ISO_8859_1)

        val payload = ByteArrayOutputStream()
        payload.write(0x00) // Encoding: ISO-8859-1
        payload.write(mimeType)
        payload.write(0x00) // MIME null terminator
        payload.write(0x03) // Picture Type: Cover (front)
        payload.write(description)
        payload.write(0x00) // Description null terminator
        payload.write(imageBytes)

        writeFrame(out, "APIC", payload.toByteArray())
    }

    private fun writeFrame(out: ByteArrayOutputStream, frameId: String, payload: ByteArray) {
        val idBytes = frameId.take(4).toByteArray(StandardCharsets.ISO_8859_1)
        out.write(idBytes)

        val size = payload.size
        // 4-byte big-endian size
        out.write((size shr 24) and 0xFF)
        out.write((size shr 16) and 0xFF)
        out.write((size shr 8) and 0xFF)
        out.write(size and 0xFF)

        // 2 bytes flags
        out.write(0x00)
        out.write(0x00)

        out.write(payload)
    }
}
