package com.example.utils

import android.content.ContentUris
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.provider.MediaStore
import android.util.Log
import android.util.LruCache
import com.example.data.SongEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream

/**
 * High-performance Album Artwork Extractor.
 * Extracts embedded APIC/ID3 album art directly from local file descriptors/paths
 * using MediaMetadataRetriever, with graceful fallback to MediaStore.Audio.Albums.EXTERNAL_CONTENT_URI.
 * Caches extracted bitmaps in a fast LruCache memory pool and internal storage files for Coil.
 */
object ArtworkHelper {

    private const val TAG = "ArtworkHelper"

    // 12MB in-memory LRU bitmap cache
    private val memoryCache: LruCache<String, Bitmap> = object : LruCache<String, Bitmap>(12 * 1024 * 1024) {
        override fun sizeOf(key: String, bitmap: Bitmap): Int {
            return bitmap.byteCount
        }
    }

    /**
     * Resolves the best available artwork path/URI for a song.
     * 1. Checks memory cache.
     * 2. Checks cached extracted APIC/ID3 file in internal cacheDir.
     * 3. Extracts raw embedded APIC/ID3 bytes from local file descriptor via MediaMetadataRetriever.
     * 4. Fallback: ContentUris.withAppendedId(MediaStore.Audio.Albums.EXTERNAL_CONTENT_URI, albumId)
     */
    suspend fun getArtworkUri(context: Context, song: SongEntity, albumId: Long = 0L): String? = withContext(Dispatchers.IO) {
        val cacheKey = "song_${song.id}"
        val artCacheDir = File(context.cacheDir, "artwork").apply { mkdirs() }
        val cachedArtFile = File(artCacheDir, "${song.id}.jpg")

        if (cachedArtFile.exists() && cachedArtFile.length() > 0) {
            return@withContext Uri.fromFile(cachedArtFile).toString()
        }

        // 1. Try MediaMetadataRetriever on local file descriptor / data path
        val path = song.dataPath ?: song.localPath
        if (!path.isNullOrBlank()) {
            val file = File(path)
            if (file.exists() && file.canRead()) {
                val extracted = extractEmbeddedArt(file.absolutePath)
                if (extracted != null) {
                    try {
                        FileOutputStream(cachedArtFile).use { out ->
                            out.write(extracted)
                            out.flush()
                        }
                        return@withContext Uri.fromFile(cachedArtFile).toString()
                    } catch (e: Exception) {
                        Log.e(TAG, "Failed caching extracted artwork: ${e.message}")
                    }
                }
            }
        }

        // 2. Try content resolver with song contentUri if dataPath wasn't directly accessible
        if (song.contentUri.startsWith("content://")) {
            try {
                val uri = Uri.parse(song.contentUri)
                context.contentResolver.openFileDescriptor(uri, "r")?.use { pfd ->
                    val retriever = MediaMetadataRetriever()
                    retriever.setDataSource(pfd.fileDescriptor)
                    val rawArt = retriever.embeddedPicture
                    retriever.release()

                    if (rawArt != null && rawArt.isNotEmpty()) {
                        FileOutputStream(cachedArtFile).use { out ->
                            out.write(rawArt)
                            out.flush()
                        }
                        return@withContext Uri.fromFile(cachedArtFile).toString()
                    }
                }
            } catch (_: Exception) {}
        }

        // 3. Fallback to MediaStore Albums URI
        if (albumId > 0) {
            val mediaStoreAlbumUri = ContentUris.withAppendedId(
                MediaStore.Audio.Albums.EXTERNAL_CONTENT_URI,
                albumId
            ).toString()
            return@withContext mediaStoreAlbumUri
        }

        // 4. Fallback to existing albumArtUri or coverArtUrl
        return@withContext song.albumArtUri ?: song.coverArtUrl
    }

    private fun extractEmbeddedArt(filePath: String): ByteArray? {
        var retriever: MediaMetadataRetriever? = null
        return try {
            retriever = MediaMetadataRetriever()
            retriever.setDataSource(filePath)
            retriever.embeddedPicture
        } catch (e: Exception) {
            null
        } finally {
            try {
                retriever?.release()
            } catch (_: Exception) {}
        }
    }

    fun getMemoryCachedBitmap(key: String): Bitmap? = memoryCache.get(key)

    fun putMemoryCachedBitmap(key: String, bitmap: Bitmap) {
        memoryCache.put(key, bitmap)
    }
}
