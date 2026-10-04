package com.example.playback

import android.content.Context
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.FileOutputStream
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Handles recording and offline saving of live radio streams to local app storage.
 */
object RadioRecorder {
    private const val TAG = "RadioRecorder"
    private val isRecording = AtomicBoolean(false)

    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .build()

    fun isCurrentlyRecording(): Boolean = isRecording.get()

    fun stopRecording() {
        isRecording.set(false)
    }

    /**
     * Records audio bytes from a live stream URL for the specified duration (seconds)
     * and saves to filesDir/radio_recordings/.
     */
    suspend fun recordStationStream(
        context: Context,
        stationName: String,
        streamUrl: String,
        durationSeconds: Int = 30,
        onProgress: (Int) -> Unit = {}
    ): File? = withContext(Dispatchers.IO) {
        if (!isRecording.compareAndSet(false, true)) {
            return@withContext null
        }

        val safeName = stationName.replace(Regex("[^a-zA-Z0-9_]"), "_").take(25)
        val recordingsDir = File(context.filesDir, "radio_recordings").apply { mkdirs() }
        val outputFile = File(recordingsDir, "${safeName}_${System.currentTimeMillis()}.mp3")

        var outputStream: FileOutputStream? = null
        try {
            val request = Request.Builder().url(streamUrl).build()
            val response = client.newCall(request).execute()
            if (!response.isSuccessful || response.body == null) {
                isRecording.set(false)
                return@withContext null
            }

            outputStream = FileOutputStream(outputFile)
            val inputStream = response.body!!.byteStream()
            val buffer = ByteArray(8192)
            val startTime = System.currentTimeMillis()
            val maxDurationMs = durationSeconds * 1000L

            var bytesRead: Int
            while (isRecording.get() && (System.currentTimeMillis() - startTime < maxDurationMs)) {
                bytesRead = inputStream.read(buffer)
                if (bytesRead == -1) break
                outputStream.write(buffer, 0, bytesRead)
                val elapsedSec = ((System.currentTimeMillis() - startTime) / 1000).toInt()
                onProgress(elapsedSec)
            }
            outputStream.flush()
            Log.i(TAG, "Recorded ${outputFile.length()} bytes to ${outputFile.absolutePath}")
            return@withContext outputFile
        } catch (e: Exception) {
            Log.e(TAG, "Recording failed: ${e.message}", e)
            return@withContext null
        } finally {
            try { outputStream?.close() } catch (_: Exception) {}
            isRecording.set(false)
        }
    }
}
