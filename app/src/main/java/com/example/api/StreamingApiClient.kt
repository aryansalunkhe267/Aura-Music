package com.example.api

import android.content.Context
import android.util.Log
import com.example.data.OnlineMusicCatalog
import com.example.data.SongEntity
import com.example.network.NewPipeDownloader
import com.example.utils.CaptionToLrcConverter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.schabi.newpipe.extractor.MediaFormat
import org.schabi.newpipe.extractor.NewPipe
import org.schabi.newpipe.extractor.ServiceList
import org.schabi.newpipe.extractor.stream.StreamInfo
import org.schabi.newpipe.extractor.stream.StreamInfoItem
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

data class DirectStreamResult(
    val streamUrl: String,
    val coverArtUrl: String? = null,
    val lrcLyrics: String? = null,
    val title: String? = null,
    val artist: String? = null,
    val durationMs: Long? = null
)

/**
 * Direct In-App Media Extractor powered by NewPipeExtractor.
 * Entirely eliminates external Piped API dependencies. Performs local,
 * high-performance extraction of YouTube m4a/AAC audio streams and synchronized captions.
 */
object StreamingApiClient {

    private const val TAG = "StreamingApiClient"
    private val isInitialized = AtomicBoolean(false)

    val okHttpClient: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .build()

    fun init(context: Context? = null) {
        if (isInitialized.compareAndSet(false, true)) {
            try {
                NewPipe.init(NewPipeDownloader(okHttpClient))
                Log.i(TAG, "NewPipeExtractor initialized successfully with native downloader")
            } catch (e: Exception) {
                Log.e(TAG, "Failed initializing NewPipe: ${e.message}", e)
            }
        }
    }

    private fun ensureInit() {
        if (!isInitialized.get()) {
            init(null)
        }
    }

    /**
     * Extracts YouTube video ID from a URL or URI (e.g. piped://id, youtube://id, https://youtube.com/watch?v=id).
     */
    fun extractStreamId(urlOrId: String): String {
        return when {
            urlOrId.contains("v=") -> urlOrId.substringAfter("v=").substringBefore("&")
            urlOrId.contains("/watch/") -> urlOrId.substringAfter("/watch/").substringBefore("?")
            urlOrId.startsWith("/watch?v=") -> urlOrId.removePrefix("/watch?v=")
            urlOrId.startsWith("piped://") -> urlOrId.removePrefix("piped://")
            urlOrId.startsWith("youtube://") -> urlOrId.removePrefix("youtube://")
            urlOrId.startsWith("https://youtu.be/") -> urlOrId.removePrefix("https://youtu.be/").substringBefore("?")
            urlOrId.startsWith("/") -> urlOrId.removePrefix("/")
            else -> urlOrId
        }
    }

    fun toFullWatchUrl(urlOrId: String): String {
        val id = extractStreamId(urlOrId)
        return "https://www.youtube.com/watch?v=$id"
    }

    /**
     * Searches YouTube natively using NewPipeExtractor without any external Piped instances.
     */
    suspend fun searchSongs(query: String): List<SongEntity> = withContext(Dispatchers.IO) {
        ensureInit()
        val results = mutableListOf<SongEntity>()
        try {
            val searchExtractor = ServiceList.YouTube.getSearchExtractor(query)
            searchExtractor.fetchPage()

            val items = searchExtractor.initialPage?.items ?: emptyList()
            for (item in items) {
                if (item is StreamInfoItem) {
                    val videoId = extractStreamId(item.url)
                    val numericId = (videoId.hashCode().toLong() and 0x7FFFFFFF) + 6_000_000L
                    val durSec = if (item.duration > 0) item.duration else 180L
                    val bestThumb = item.thumbnails.maxByOrNull { it.width }?.url
                        ?: item.thumbnails.firstOrNull()?.url

                    results.add(
                        SongEntity(
                            id = numericId,
                            title = item.name ?: "Unknown Track",
                            artist = item.uploaderName ?: "Unknown Artist",
                            album = "Online Stream",
                            durationMs = durSec * 1000L,
                            contentUri = "youtube://$videoId",
                            streamUrl = null, // Extracted on demand
                            albumArtUri = bestThumb,
                            coverArtUrl = bestThumb,
                            sourceType = "ONLINE",
                            isDownloaded = false,
                            downloadProgress = 0,
                            languageScript = "UNKNOWN",
                            genre = "Direct Stream"
                        )
                    )
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "NewPipeExtractor native search failed for '$query': ${e.message}", e)
        }
        return@withContext results
    }

    /**
     * Extracts direct playable high-quality M4A/AAC audio stream and synchronized .lrc lyrics.
     */
    suspend fun extractDirectStream(songOrUrl: String, fallbackTitle: String = "", fallbackArtist: String = ""): DirectStreamResult? = withContext(Dispatchers.IO) {
        ensureInit()
        try {
            val watchUrl = toFullWatchUrl(songOrUrl)
            val streamInfo = StreamInfo.getInfo(ServiceList.YouTube, watchUrl)

            // Strictly filter and prioritize high-quality audio/mp4 (M4A) streams
            val audioStreams = streamInfo.audioStreams ?: emptyList()
            val m4aStream = audioStreams.filter { audio ->
                audio.format == MediaFormat.M4A ||
                        audio.format?.name.equals("M4A", ignoreCase = true) ||
                        audio.codec?.contains("mp4a", ignoreCase = true) == true ||
                        audio.codec?.contains("aac", ignoreCase = true) == true
            }.maxByOrNull { it.averageBitrate } ?: audioStreams.maxByOrNull { it.averageBitrate }

            val directUrl = m4aStream?.content
            if (directUrl.isNullOrBlank()) {
                Log.w(TAG, "No valid audio stream extracted for $watchUrl")
                return@withContext null
            }

            val bestThumb = streamInfo.thumbnails.maxByOrNull { it.width }?.url
                ?: streamInfo.thumbnails.firstOrNull()?.url

            // Extract synchronized lyrics from YouTube captions / subtitles
            var extractedLrc: String? = null
            val subtitles = streamInfo.subtitles ?: emptyList()

            // Prioritize English, local language or auto-generated subtitle tracks
            val preferredSub = subtitles.firstOrNull { !it.isAutoGenerated && (it.languageTag?.startsWith("en") == true || it.languageTag?.startsWith("hi") == true || it.languageTag?.startsWith("pa") == true) }
                ?: subtitles.firstOrNull { !it.isAutoGenerated }
                ?: subtitles.firstOrNull { it.languageTag?.startsWith("en") == true }
                ?: subtitles.firstOrNull()

            if (preferredSub != null && !preferredSub.content.isNullOrBlank()) {
                try {
                    val subReq = Request.Builder().url(preferredSub.content).build()
                    okHttpClient.newCall(subReq).execute().use { subResp ->
                        if (subResp.isSuccessful) {
                            val rawSubText = subResp.body?.string()
                            extractedLrc = CaptionToLrcConverter.convertToLrc(rawSubText)
                            if (!extractedLrc.isNullOrBlank()) {
                                Log.i(TAG, "Successfully extracted synchronized LRC captions from YouTube (${preferredSub.languageTag})")
                            }
                        }
                    }
                } catch (e: Exception) {
                    Log.w(TAG, "Failed downloading caption content from ${preferredSub.content}: ${e.message}")
                }
            }

            // Fallback: Query OnlineMusicCatalog for synchronized LRC lyrics if YouTube captions absent
            if (extractedLrc.isNullOrBlank()) {
                val qTitle = if (streamInfo.name.isNotBlank()) streamInfo.name else fallbackTitle
                val qArtist = if (streamInfo.uploaderName.isNotBlank()) streamInfo.uploaderName else fallbackArtist
                if (qTitle.isNotBlank()) {
                    extractedLrc = OnlineMusicCatalog.fetchSyncedLyrics(qTitle, qArtist)
                }
            }

            return@withContext DirectStreamResult(
                streamUrl = directUrl,
                coverArtUrl = bestThumb,
                lrcLyrics = extractedLrc,
                title = streamInfo.name,
                artist = streamInfo.uploaderName,
                durationMs = (streamInfo.duration * 1000L).takeIf { it > 0 }
            )
        } catch (e: Exception) {
            Log.e(TAG, "Direct audio extraction failed for '$songOrUrl': ${e.message}", e)
            return@withContext null
        }
    }
}
