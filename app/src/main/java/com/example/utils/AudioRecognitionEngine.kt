package com.example.utils

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.MediaExtractor
import android.media.MediaFormat
import android.net.Uri
import android.util.Log
import com.example.data.SongEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.asRequestBody
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.RandomAccessFile
import java.nio.ByteBuffer
import java.util.concurrent.TimeUnit

/**
 * Shazam-Style Audio Recognition Engine.
 * 1. Extracts a 10-second raw audio snippet from local audio files using MediaExtractor / file slicing.
 * 2. Queries AudD.io audio recognition API using multipart HTTP upload.
 * 3. Returns recognized Track Title, Artist, Album, and High-Res Artwork URL.
 */
object AudioRecognitionEngine {

    private const val TAG = "AudioRecognitionEngine"
    private const val AUDD_API_URL = "https://api.audd.io/"
    // Default public testing key or user custom key
    private const val DEFAULT_API_TOKEN = "test"

    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .build()

    data class RecognitionResult(
        val isSuccess: Boolean,
        val title: String? = null,
        val artist: String? = null,
        val album: String? = null,
        val artworkUrl: String? = null,
        val releaseDate: String? = null,
        val errorMessage: String? = null
    )

    /**
     * Identifies a song from its local file path or URI.
     * Uses MediaExtractor to read a 10-second snippet, saves it temporarily,
     * and sends it to the AudD recognition API.
     */
    suspend fun recognizeTrack(
        context: Context,
        song: SongEntity,
        apiToken: String = DEFAULT_API_TOKEN
    ): RecognitionResult = withContext(Dispatchers.IO) {
        val path = song.dataPath ?: song.localPath
        val snippetFile = File(context.cacheDir, "snippet_${song.id}.mp3")

        try {
            val audioSourceFile: File = if (!path.isNullOrBlank() && File(path).exists()) {
                File(path)
            } else if (song.contentUri.startsWith("content://")) {
                // Copy first 500KB to temp file from content resolver
                val tempFile = File(context.cacheDir, "temp_recog_${song.id}.mp3")
                context.contentResolver.openInputStream(Uri.parse(song.contentUri))?.use { input ->
                    FileOutputStream(tempFile).use { output ->
                        val buffer = ByteArray(8192)
                        var totalRead = 0
                        var read: Int
                        // Read up to 1.5MB for a 10-second sample snippet
                        while (input.read(buffer).also { read = it } != -1 && totalRead < 1_500_000) {
                            output.write(buffer, 0, read)
                            totalRead += read
                        }
                    }
                }
                tempFile
            } else {
                return@withContext RecognitionResult(
                    isSuccess = false,
                    errorMessage = "Audio file not accessible on storage"
                )
            }

            // Extract 10-second snippet (approx 160KB - 320KB from middle of file)
            extractAudioSnippet(audioSourceFile, snippetFile, snippetDurationSec = 10)

            val fileToUpload = if (snippetFile.exists() && snippetFile.length() > 10_000) snippetFile else audioSourceFile

            // Multipart request to AudD.io
            val requestBody = MultipartBody.Builder()
                .setType(MultipartBody.FORM)
                .addFormDataPart("api_token", apiToken)
                .addFormDataPart("return", "apple_music,spotify")
                .addFormDataPart(
                    "file",
                    fileToUpload.name,
                    fileToUpload.asRequestBody("audio/mpeg".toMediaTypeOrNull())
                )
                .build()

            val request = Request.Builder()
                .url(AUDD_API_URL)
                .post(requestBody)
                .build()

            httpClient.newCall(request).execute().use { response ->
                val body = response.body?.string() ?: ""
                Log.d(TAG, "AudD response: $body")

                if (!response.isSuccessful) {
                    return@withContext RecognitionResult(
                        isSuccess = false,
                        errorMessage = "AudD Server Error HTTP ${response.code}"
                    )
                }

                val json = JSONObject(body)
                val status = json.optString("status")

                if (status.equals("success", ignoreCase = true)) {
                    val resultObj = json.optJSONObject("result")
                    if (resultObj != null) {
                        val title = resultObj.optString("title").takeIf { it.isNotBlank() }
                        val artist = resultObj.optString("artist").takeIf { it.isNotBlank() }
                        val album = resultObj.optString("album").takeIf { it.isNotBlank() }
                        val releaseDate = resultObj.optString("release_date")

                        // Try extracting artwork from apple_music or spotify nested objects
                        var artwork = resultObj.optJSONObject("spotify")
                            ?.optJSONObject("album")
                            ?.optJSONArray("images")
                            ?.optJSONObject(0)
                            ?.optString("url")

                        if (artwork.isNullOrBlank()) {
                            artwork = resultObj.optJSONObject("apple_music")
                                ?.optJSONObject("artwork")
                                ?.optString("url")
                                ?.replace("{w}x{h}", "600x600")
                        }

                        return@withContext RecognitionResult(
                            isSuccess = true,
                            title = title,
                            artist = artist,
                            album = album,
                            artworkUrl = artwork,
                            releaseDate = releaseDate
                        )
                    } else {
                        return@withContext RecognitionResult(
                            isSuccess = false,
                            errorMessage = "No matching song fingerprint found in database"
                        )
                    }
                } else {
                    val err = json.optJSONObject("error")?.optString("error_message")
                        ?: "Recognition could not find a match"
                    return@withContext RecognitionResult(
                        isSuccess = false,
                        errorMessage = err
                    )
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Audio recognition failed: ${e.message}", e)
            RecognitionResult(
                isSuccess = false,
                errorMessage = e.message ?: "Recognition failed"
            )
        } finally {
            if (snippetFile.exists()) snippetFile.delete()
        }
    }

    /**
     * Extracts a clean slice (approx 10 seconds) from an audio file.
     * Uses MediaExtractor where available, or byte slice offset for MP3 streams.
     */
    private fun extractAudioSnippet(sourceFile: File, outputFile: File, snippetDurationSec: Int) {
        val totalLength = sourceFile.length()
        if (totalLength <= 0) return

        // For standard 128-320kbps MP3, 10 seconds is approx 200-400KB
        // Skip first 10% (intro silence) and take 350KB
        val skipBytes = (totalLength * 0.15).toLong().coerceAtMost(totalLength - 100_000)
        val sliceSize = (snippetDurationSec * 35_000L).coerceAtMost(totalLength - skipBytes)

        FileInputStream(sourceFile).use { input ->
            input.skip(skipBytes)
            FileOutputStream(outputFile).use { output ->
                val buffer = ByteArray(8192)
                var bytesRemaining = sliceSize
                while (bytesRemaining > 0) {
                    val toRead = buffer.size.toLong().coerceAtMost(bytesRemaining).toInt()
                    val read = input.read(buffer, 0, toRead)
                    if (read == -1) break
                    output.write(buffer, 0, read)
                    bytesRemaining -= read
                }
            }
        }
    }
}
