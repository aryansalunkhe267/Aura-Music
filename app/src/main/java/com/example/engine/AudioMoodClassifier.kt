package com.example.engine

import android.content.Context
import android.media.MediaExtractor
import android.media.MediaFormat
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.util.Log
import com.example.data.SongEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.nio.ByteBuffer
import kotlin.math.abs
import kotlin.math.sqrt

/**
 * On-device offline acoustic engine that inspects audio tracks to extract
 * energy, spectral dynamics, and duration heuristics to categorize into mood profiles.
 */
object AudioMoodClassifier {

    private const val TAG = "AudioMoodClassifier"

    enum class Mood(val displayName: String, val tag: String, val colorHex: Long) {
        ENERGETIC("⚡ High Energy / Gym", "ENERGETIC", 0xFFFF5722),
        UPBEAT("🎉 Upbeat & Vibrant", "UPBEAT", 0xFFFFB300),
        CHILL("☕ Chill & Relaxed", "CHILL", 0xFF00B0FF),
        MELANCHOLY("🌧️ Melancholy & Deep", "MELANCHOLY", 0xFF7E57C2),
        CALM("🍃 Calm & Acoustic", "CALM", 0xFF26A69A)
    }

    data class MoodResult(
        val mood: Mood,
        val energyScore: Float // 0.0 to 1.0
    )

    /**
     * Performs lightweight on-device feature extraction on local audio.
     * Uses MediaExtractor / MediaMetadataRetriever to sample audio frames and analyze peak energy.
     */
    suspend fun analyzeSongMood(context: Context, song: SongEntity): MoodResult = withContext(Dispatchers.IO) {
        try {
            val uri = Uri.parse(song.contentUri)
            val retriever = MediaMetadataRetriever()
            try {
                retriever.setDataSource(context, uri)
            } catch (e: Exception) {
                // Fallback to title/artist heuristic if file is temporarily inaccessible
                return@withContext heuristicMoodFromMetadata(song)
            }

            val bitrateStr = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_BITRATE)
            val durationStr = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)
            retriever.release()

            val bitrate = bitrateStr?.toLongOrNull() ?: 192000L
            val duration = durationStr?.toLongOrNull() ?: song.durationMs

            // Sample raw compressed packet sizes via MediaExtractor for energy distribution
            val extractor = MediaExtractor()
            var audioTrackIndex = -1
            try {
                extractor.setDataSource(context, uri, null)
                for (i in 0 until extractor.trackCount) {
                    val format = extractor.getTrackFormat(i)
                    val mime = format.getString(MediaFormat.KEY_MIME) ?: ""
                    if (mime.startsWith("audio/")) {
                        audioTrackIndex = i
                        break
                    }
                }
            } catch (e: Exception) {
                extractor.release()
                return@withContext heuristicMoodFromMetadata(song)
            }

            if (audioTrackIndex == -1) {
                extractor.release()
                return@withContext heuristicMoodFromMetadata(song)
            }

            extractor.selectTrack(audioTrackIndex)

            val buffer = ByteBuffer.allocate(8192)
            var sampleCount = 0
            var totalSampleSize = 0L
            var maxSampleSize = 0
            var varianceAcc = 0.0

            val sampleSizes = mutableListOf<Int>()

            // Sample up to 60 packets evenly spread across the file
            while (sampleCount < 60) {
                val sampleSize = extractor.readSampleData(buffer, 0)
                if (sampleSize < 0) break
                sampleSizes.add(sampleSize)
                totalSampleSize += sampleSize
                if (sampleSize > maxSampleSize) maxSampleSize = sampleSize
                sampleCount++
                // Seek ahead 3 seconds to sample throughout track
                extractor.seekTo(extractor.sampleTime + 3_000_000L, MediaExtractor.SEEK_TO_CLOSEST_SYNC)
            }
            extractor.release()

            if (sampleSizes.isNotEmpty()) {
                val avgSampleSize = totalSampleSize.toDouble() / sampleSizes.size
                for (size in sampleSizes) {
                    val diff = size - avgSampleSize
                    varianceAcc += diff * diff
                }
                val stdDev = sqrt(varianceAcc / sampleSizes.size)
                
                // Energy calculation based on packet dynamics and bitrate
                val dynamicsRatio = (stdDev / (avgSampleSize.coerceAtLeast(1.0))).toFloat()
                val bitrateFactor = (bitrate.toFloat() / 320000f).coerceIn(0.2f, 1.0f)
                
                // Normalize energy score between 0.0 and 1.0
                val rawScore = (dynamicsRatio * 0.6f + bitrateFactor * 0.4f).coerceIn(0.1f, 0.95f)
                
                return@withContext classifyScoreToMood(rawScore, song)
            }

            return@withContext heuristicMoodFromMetadata(song)
        } catch (e: Exception) {
            Log.w(TAG, "Mood analysis exception: ${e.message}")
            return@withContext heuristicMoodFromMetadata(song)
        }
    }

    private fun classifyScoreToMood(score: Float, song: SongEntity): MoodResult {
        val lowerText = "${song.title} ${song.artist}".lowercase()
        // Bias slightly based on musical sentiment keywords
        val adjustedScore = when {
            lowerText.contains("remix") || lowerText.contains("party") || lowerText.contains("beat") -> (score + 0.15f).coerceAtMost(0.99f)
            lowerText.contains("sad") || lowerText.contains("slow") || lowerText.contains("acoustic") || lowerText.contains("unplugged") -> (score - 0.15f).coerceAtLeast(0.05f)
            else -> score
        }

        val mood = when {
            adjustedScore >= 0.75f -> Mood.ENERGETIC
            adjustedScore >= 0.58f -> Mood.UPBEAT
            adjustedScore >= 0.42f -> Mood.CHILL
            adjustedScore >= 0.25f -> Mood.MELANCHOLY
            else -> Mood.CALM
        }

        return MoodResult(mood, adjustedScore)
    }

    private fun heuristicMoodFromMetadata(song: SongEntity): MoodResult {
        val text = "${song.title} ${song.artist} ${song.album}".lowercase()
        return when {
            text.contains("dance") || text.contains("bhangra") || text.contains("remix") || text.contains("edm") || text.contains("rock") ->
                MoodResult(Mood.ENERGETIC, 0.85f)
            text.contains("pop") || text.contains("upbeat") || text.contains("dhol") ->
                MoodResult(Mood.UPBEAT, 0.65f)
            text.contains("sad") || text.contains("dard") || text.contains("broken") || text.contains("cry") || text.contains("judai") ->
                MoodResult(Mood.MELANCHOLY, 0.28f)
            text.contains("sleep") || text.contains("lofi") || text.contains("ambient") || text.contains("peace") || text.contains("meditation") ->
                MoodResult(Mood.CALM, 0.15f)
            else ->
                MoodResult(Mood.CHILL, 0.50f)
        }
    }

    /**
     * Smart Mood Queue Recommendation:
     * When current queue has 1 or fewer remaining songs, finds songs in the offline library
     * with the nearest mood scores to provide continuous seamless playback.
     */
    fun findMatchingMoodSongs(
        currentSong: SongEntity,
        allCandidates: List<SongEntity>,
        currentQueueIds: Set<Long>,
        limit: Int = 5
    ): List<SongEntity> {
        val targetScore = currentSong.moodScore
        val targetMood = currentSong.moodProfile

        return allCandidates
            .filter { it.id != currentSong.id && !currentQueueIds.contains(it.id) }
            .sortedBy { candidate ->
                var distance = abs(candidate.moodScore - targetScore)
                // Bonus for exact mood profile match
                if (candidate.moodProfile == targetMood) {
                    distance *= 0.5f
                }
                distance
            }
            .take(limit)
    }
}
