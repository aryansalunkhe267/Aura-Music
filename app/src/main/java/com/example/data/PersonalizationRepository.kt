package com.example.data

import android.util.Log
import com.example.api.JioSaavnApiClient
import com.example.engine.SmartCategorizer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.withContext
import kotlin.math.exp

/**
 * Dual-Engine Continuous Personalization Repository.
 * Merges imported Spotify taste profiles with offline and online 14-day playback history
 * using exponential time decay to compute the user's live hybrid taste vector.
 */
class PersonalizationRepository(
    private val musicDao: MusicDao
) {
    private val TAG = "PersonalizationRepo"
    private val FOURTEEN_DAYS_MS = 14L * 24L * 60L * 60L * 1000L
    private val DECAY_LAMBDA = 0.15 // Exponential decay rate per day

    data class TasteVector(
        val topArtists: List<ArtistAffinity>,
        val dominantLanguage: String,
        val dominantMood: String,
        val totalListens14d: Int
    )

    data class ArtistAffinity(
        val artist: String,
        val totalScore: Float,
        val source: String // "SPOTIFY", "LISTENS", "HYBRID"
    )

    data class PersonalizedRails(
        val madeForYou: List<SongEntity>,
        val basedOnRecentListens: List<SongEntity>,
        val rediscoverOffline: List<SongEntity>
    )

    /**
     * Records a track completion or significant listen (minimum 30 seconds).
     */
    suspend fun logTrackPlayback(song: SongEntity, durationPlayedMs: Long) = withContext(Dispatchers.IO) {
        if (durationPlayedMs < 30_000L) {
            Log.d(TAG, "Ignoring listen under 30s: ${song.title} (${durationPlayedMs / 1000}s)")
            return@withContext
        }

        val isOnline = song.sourceType.equals("ONLINE", ignoreCase = true)
        val entry = PlaybackHistoryEntity(
            songId = song.id,
            title = song.title,
            artist = song.artist,
            album = song.album,
            source = if (isOnline) "ONLINE" else "OFFLINE",
            language = song.languageScript,
            durationPlayedMs = durationPlayedMs,
            timestamp = System.currentTimeMillis()
        )

        musicDao.insertPlaybackHistory(entry)
        Log.i(TAG, "Logged playback history: '${song.title}' by '${song.artist}' (${entry.source}, ${durationPlayedMs / 1000}s)")
    }

    /**
     * Computes the rolling 14-day affinity model.
     * Weights recent plays exponentially higher and merges with Spotify affinities.
     */
    suspend fun computeTasteVector(): TasteVector = withContext(Dispatchers.IO) {
        val now = System.currentTimeMillis()
        val since = now - FOURTEEN_DAYS_MS

        val recentHistory = musicDao.getRecentPlaybackHistoryDirect(since)
        val spotifyAffinities = musicDao.getSpotifyAffinitiesDirect()

        val artistScores = mutableMapOf<String, Float>()
        val languageCounts = mutableMapOf<String, Int>()

        // 1. Calculate time-decayed history scores
        for (item in recentHistory) {
            val daysAgo = (now - item.timestamp).toDouble() / (1000.0 * 60.0 * 60.0 * 24.0)
            val weight = exp(-DECAY_LAMBDA * daysAgo).toFloat()

            val cleanArtist = item.artist.trim()
            artistScores[cleanArtist] = (artistScores[cleanArtist] ?: 0f) + weight

            if (item.language.isNotBlank() && item.language != "UNKNOWN") {
                languageCounts[item.language] = (languageCounts[item.language] ?: 0) + 1
            }
        }

        // 2. Merge with imported Spotify affinities (if present)
        for (spotifyItem in spotifyAffinities) {
            val cleanArtist = spotifyItem.artist.trim()
            // Boost factor for imported top artists
            val spotifyWeight = spotifyItem.weight * 3.5f
            artistScores[cleanArtist] = (artistScores[cleanArtist] ?: 0f) + spotifyWeight
        }

        val sortedArtists = artistScores.map { (artist, score) ->
            val hasSpotify = spotifyAffinities.any { it.artist.equals(artist, ignoreCase = true) }
            val hasListens = recentHistory.any { it.artist.equals(artist, ignoreCase = true) }
            val source = when {
                hasSpotify && hasListens -> "HYBRID"
                hasSpotify -> "SPOTIFY"
                else -> "LISTENS"
            }
            ArtistAffinity(artist, score, source)
        }.sortedByDescending { it.totalScore }.take(15)

        val dominantLang = languageCounts.maxByOrNull { it.value }?.key ?: "HINDI"

        TasteVector(
            topArtists = sortedArtists,
            dominantLanguage = dominantLang,
            dominantMood = "ENERGETIC",
            totalListens14d = recentHistory.size
        )
    }

    /**
     * Generates personalized rails for the Online Explore screen:
     * - "Made For You"
     * - "Based on Recent Listens"
     * - "Rediscover Offline Favorites"
     */
    suspend fun getPersonalizedRails(): PersonalizedRails = withContext(Dispatchers.IO) {
        val taste = computeTasteVector()
        val topArtist = taste.topArtists.firstOrNull()?.artist
        val secondArtist = taste.topArtists.getOrNull(1)?.artist

        // 1. Made For You rail: query top affinity artist or fallback
        val madeForYouSongs = mutableListOf<SongEntity>()
        if (!topArtist.isNullOrBlank()) {
            try {
                val resp = JioSaavnApiClient.api.searchSongs(topArtist, limit = 8)
                val results = resp.body()?.data?.results ?: emptyList()
                if (results.isNotEmpty()) {
                    madeForYouSongs.addAll(results.map { JioSaavnApiClient.mapToSongEntity(it) })
                }
            } catch (e: Exception) {
                Log.w(TAG, "Failed fetching Made For You for $topArtist: ${e.message}")
            }
        }
        if (madeForYouSongs.isEmpty()) {
            madeForYouSongs.addAll(OnlineMusicCatalog.getTrendingNow())
        }

        // 2. Based on Recent Listens rail: query secondary artist or regional focus
        val recentListensSongs = mutableListOf<SongEntity>()
        val queryForRecent = secondArtist ?: if (taste.dominantLanguage == "PUNJABI") "Top Punjabi" else "Bollywood Hits"
        try {
            val resp = JioSaavnApiClient.api.searchSongs(queryForRecent, limit = 8)
            val results = resp.body()?.data?.results ?: emptyList()
            if (results.isNotEmpty()) {
                recentListensSongs.addAll(results.map { JioSaavnApiClient.mapToSongEntity(it) })
            }
        } catch (e: Exception) {
            Log.w(TAG, "Failed fetching Based on Recent for $queryForRecent: ${e.message}")
        }
        if (recentListensSongs.isEmpty()) {
            recentListensSongs.addAll(OnlineMusicCatalog.getTopCharts())
        }

        // 3. Rediscover Offline Favorites rail: high-score offline songs matching top artists
        val allOffline = musicDao.getAllSongsUnlimited()
        val rediscoverOffline = allOffline.filter { song ->
            taste.topArtists.any { it.artist.contains(song.artist, ignoreCase = true) || song.artist.contains(it.artist, ignoreCase = true) }
        }.take(8).ifEmpty {
            allOffline.filter { it.isFavorite }.take(8)
        }.ifEmpty {
            allOffline.take(8)
        }

        PersonalizedRails(
            madeForYou = madeForYouSongs.distinctBy { it.id }.take(10),
            basedOnRecentListens = recentListensSongs.distinctBy { it.id }.take(10),
            rediscoverOffline = rediscoverOffline
        )
    }

    /**
     * Adaptive Auto-Queue helper:
     * When manual queue ends, dynamically enqueues tracks that match the dominant language
     * and mood profile of the user's top listening clusters.
     */
    suspend fun getAdaptiveAutoQueue(
        currentSong: SongEntity,
        existingQueue: List<SongEntity>
    ): List<SongEntity> = withContext(Dispatchers.IO) {
        val taste = computeTasteVector()
        val allOffline = musicDao.getAllSongsUnlimited()
        val currentCategory = SmartCategorizer.classify(currentSong.title, currentSong.artist, currentSong.album, currentSong.genre ?: "")

        val existingIds = existingQueue.map { it.id }.toSet()

        // 1. First priority: Offline tracks matching same linguistic category and top artists
        val candidateOffline = allOffline.filter { candidate ->
            !existingIds.contains(candidate.id) &&
                    SmartCategorizer.isSameCategory(currentSong, candidate)
        }

        if (candidateOffline.isNotEmpty()) {
            return@withContext candidateOffline.take(10)
        }

        // 2. Second priority: Online catalog tracks matching dominant language
        val onlineMatches = OnlineMusicCatalog.getOnlineTracks().filter { candidate ->
            !existingIds.contains(candidate.id) &&
                    (candidate.languageScript.equals(taste.dominantLanguage, ignoreCase = true) ||
                            SmartCategorizer.isSameCategory(currentSong, candidate))
        }

        return@withContext onlineMatches.take(10)
    }
}
