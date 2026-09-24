package com.example.utils

import android.content.Context
import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import android.media.MediaMuxer
import android.media.MediaScannerConnection
import android.net.Uri
import android.os.Environment
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.nio.ByteBuffer

/**
 * Lossless native audio editor utilizing Android's MediaExtractor and MediaMuxer.
 * Trims audio files between exact millisecond intervals without re-encoding degradation.
 */
object AudioEditor {

    private const val TAG = "AudioEditor"

    data class TrimResult(
        val isSuccess: Boolean,
        val outputPath: String? = null,
        val outputUri: Uri? = null,
        val errorMessage: String? = null
    )

    /**
     * Losslessly trims an audio file from [startMs] to [endMs] and saves it
     * into the device's Music directory or external media directory.
     */
    suspend fun trimAudio(
        context: Context,
        sourceUri: Uri,
        outputFileName: String,
        startMs: Long,
        endMs: Long
    ): TrimResult = withContext(Dispatchers.IO) {
        if (startMs >= endMs) {
            return@withContext TrimResult(false, errorMessage = "Start time must be less than end time")
        }

        val extractor = MediaExtractor()
        var muxer: MediaMuxer? = null
        var outputFile: File? = null

        try {
            extractor.setDataSource(context, sourceUri, null)
            val trackCount = extractor.trackCount
            var audioTrackIndex = -1
            var audioFormat: MediaFormat? = null

            for (i in 0 until trackCount) {
                val format = extractor.getTrackFormat(i)
                val mime = format.getString(MediaFormat.KEY_MIME) ?: ""
                if (mime.startsWith("audio/")) {
                    audioTrackIndex = i
                    audioFormat = format
                    break
                }
            }

            if (audioTrackIndex == -1 || audioFormat == null) {
                extractor.release()
                return@withContext TrimResult(false, errorMessage = "No compatible audio track found in file")
            }

            extractor.selectTrack(audioTrackIndex)

            // Prepare destination file
            val musicDir = context.getExternalFilesDir(Environment.DIRECTORY_MUSIC)
                ?: File(context.filesDir, "Music").apply { mkdirs() }
            
            val sanitizedName = outputFileName.replace("[^a-zA-Z0-9_.-]".toRegex(), "_")
            val extension = if (sanitizedName.endsWith(".m4a") || sanitizedName.endsWith(".aac")) "" else ".m4a"
            outputFile = File(musicDir, "${sanitizedName}$extension")
            if (outputFile.exists()) {
                outputFile.delete()
            }

            // MediaMuxer configured for MPEG4 container
            muxer = MediaMuxer(outputFile.absolutePath, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)
            val muxerTrackIndex = muxer.addTrack(audioFormat)
            muxer.start()

            // Seek to start position
            val startUs = startMs * 1000L
            val endUs = endMs * 1000L
            extractor.seekTo(startUs, MediaExtractor.SEEK_TO_PREVIOUS_SYNC)

            // Allocate buffer
            val maxBufferSize = if (audioFormat.containsKey(MediaFormat.KEY_MAX_INPUT_SIZE)) {
                audioFormat.getInteger(MediaFormat.KEY_MAX_INPUT_SIZE).coerceAtLeast(64 * 1024)
            } else {
                256 * 1024
            }
            val buffer = ByteBuffer.allocateDirect(maxBufferSize)
            val bufferInfo = MediaCodec.BufferInfo()

            var presentationTimeOffsetUs: Long? = null

            while (true) {
                bufferInfo.offset = 0
                bufferInfo.size = extractor.readSampleData(buffer, 0)

                if (bufferInfo.size < 0) {
                    break // End of stream
                }

                val sampleTimeUs = extractor.sampleTime

                if (sampleTimeUs > endUs) {
                    break // Reached requested end boundary
                }

                if (sampleTimeUs >= startUs) {
                    if (presentationTimeOffsetUs == null) {
                        presentationTimeOffsetUs = sampleTimeUs
                    }

                    // Normalize timestamps so the trimmed file starts at t=0
                    bufferInfo.presentationTimeUs = sampleTimeUs - presentationTimeOffsetUs
                    bufferInfo.flags = extractor.sampleFlags

                    muxer.writeSampleData(muxerTrackIndex, buffer, bufferInfo)
                }

                extractor.advance()
            }

            muxer.stop()
            muxer.release()
            muxer = null
            extractor.release()

            // Notify MediaScanner so the trimmed audio appears in MediaStore
            MediaScannerConnection.scanFile(
                context,
                arrayOf(outputFile.absolutePath),
                arrayOf("audio/mp4", "audio/m4a")
            ) { path, uri ->
                Log.d(TAG, "MediaScanner finished: $path -> $uri")
            }

            TrimResult(
                isSuccess = true,
                outputPath = outputFile.absolutePath,
                outputUri = Uri.fromFile(outputFile)
            )
        } catch (e: Exception) {
            Log.e(TAG, "Failed trimming audio", e)
            try {
                muxer?.stop()
            } catch (_: Exception) {}
            try {
                muxer?.release()
            } catch (_: Exception) {}
            try {
                extractor.release()
            } catch (_: Exception) {}
            outputFile?.delete()

            TrimResult(isSuccess = false, errorMessage = e.localizedMessage ?: "Unknown error trimming audio")
        }
    }
}
