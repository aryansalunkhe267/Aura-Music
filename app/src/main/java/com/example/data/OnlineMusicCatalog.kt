package com.example.data

import com.example.engine.ScriptLanguageDetector

/**
 * Curated catalog of legal, free royalty-free and Creative Commons audio streams
 * spanning Punjabi, Hindi/Bollywood, Marathi, and Lo-Fi Indie genres.
 */
object OnlineMusicCatalog {

    fun getOnlineTracks(): List<SongEntity> = listOf(
        SongEntity(
            id = 900001L,
            title = "Pendu Vibe",
            artist = "Aman Sandhu",
            album = "Desi Beats Punjab",
            durationMs = 184000L,
            contentUri = "https://www.soundhelix.com/examples/mp3/SoundHelix-Song-1.mp3",
            streamUrl = "https://www.soundhelix.com/examples/mp3/SoundHelix-Song-1.mp3",
            albumArtUri = "https://images.unsplash.com/photo-1514525253161-7a46d19cd819?w=600&auto=format&fit=crop&q=80",
            sourceType = "ONLINE",
            isDownloaded = false,
            downloadProgress = 0,
            languageScript = "PUNJABI",
            moodProfile = "ENERGETIC",
            moodScore = 0.85f,
            lrcLyrics = """
                [00:00.00]Pendu Vibe - Aman Sandhu
                [00:05.50]Fresh morning breeze over golden open fields
                [00:15.20]Living free where love and kindness yield
                [00:25.80]Our voice, our pride, shining bright
                [00:35.00]Warm hearts welcome friends with pure delight
                [00:48.30]Pendu Vibe going non-stop!
            """.trimIndent()
        ),
        SongEntity(
            id = 900002L,
            title = "Tum Mile Dil Khila",
            artist = "Rohan Sharma, Shreya N",
            album = "Monsoon Melodies",
            durationMs = 212000L,
            contentUri = "https://www.soundhelix.com/examples/mp3/SoundHelix-Song-2.mp3",
            streamUrl = "https://www.soundhelix.com/examples/mp3/SoundHelix-Song-2.mp3",
            albumArtUri = "https://images.unsplash.com/photo-1470225620780-dba8ba36b745?w=600&auto=format&fit=crop&q=80",
            sourceType = "ONLINE",
            isDownloaded = false,
            downloadProgress = 0,
            languageScript = "HINDI_MARATHI",
            moodProfile = "CHILL",
            moodScore = 0.40f,
            lrcLyrics = """
                [00:00.00]Tum Mile Dil Khila - Acoustic Lofi
                [00:08.00]Walking softly in the monsoon shower
                [00:18.50]Sweet memories blooming like a flower
                [00:29.00]When you arrived the whole world felt brand new
                [00:40.00]Every single heartbeat calls out for you
                [00:52.00]Gazing at stars under quiet midnight skies
            """.trimIndent()
        ),
        SongEntity(
            id = 900003L,
            title = "Malhari Dhun",
            artist = "Swapnil Bandekar",
            album = "Maharashtra Beats",
            durationMs = 195000L,
            contentUri = "https://www.soundhelix.com/examples/mp3/SoundHelix-Song-3.mp3",
            streamUrl = "https://www.soundhelix.com/examples/mp3/SoundHelix-Song-3.mp3",
            albumArtUri = "https://images.unsplash.com/photo-1493225457124-a3eb161ffa5f?w=600&auto=format&fit=crop&q=80",
            sourceType = "ONLINE",
            isDownloaded = false,
            downloadProgress = 0,
            languageScript = "HINDI_MARATHI",
            moodProfile = "UPBEAT",
            moodScore = 0.90f,
            lrcLyrics = """
                [00:00.00]Malhari Dhun - Dhol Tasha Mix
                [00:06.00]Rhythm on the beat, dancing in colors
                [00:16.00]Echoes across the Sahyadri mountains rise
                [00:28.00]The tune of celebration touches the soul
                [00:40.00]Gulal colors shower into morning skies
            """.trimIndent()
        ),
        SongEntity(
            id = 900004L,
            title = "Gedi Route",
            artist = "Karan Dhillon",
            album = "Urban Jatt",
            durationMs = 175000L,
            contentUri = "https://www.soundhelix.com/examples/mp3/SoundHelix-Song-4.mp3",
            streamUrl = "https://www.soundhelix.com/examples/mp3/SoundHelix-Song-4.mp3",
            albumArtUri = "https://images.unsplash.com/photo-1511671782779-c97d3d27a1d4?w=600&auto=format&fit=crop&q=80",
            sourceType = "ONLINE",
            isDownloaded = false,
            downloadProgress = 0,
            languageScript = "PUNJABI",
            moodProfile = "ENERGETIC",
            moodScore = 0.88f,
            lrcLyrics = """
                [00:00.00]Gedi Route - Karan Dhillon
                [00:07.00]Cruising down the city avenue
                [00:17.50]Speakers turned up playing through
                [00:27.00]Your name is all that echoes tonight
                [00:38.00]Rolling with the crew till morning light
            """.trimIndent()
        ),
        SongEntity(
            id = 900005L,
            title = "Sufi Raag - Bismillah",
            artist = "Nusrat Studio Ensemble",
            album = "Mystic Soul",
            durationMs = 240000L,
            contentUri = "https://www.soundhelix.com/examples/mp3/SoundHelix-Song-8.mp3",
            streamUrl = "https://www.soundhelix.com/examples/mp3/SoundHelix-Song-8.mp3",
            albumArtUri = "https://images.unsplash.com/photo-1465847899084-d164df4dedc6?w=600&auto=format&fit=crop&q=80",
            sourceType = "ONLINE",
            isDownloaded = false,
            downloadProgress = 0,
            languageScript = "HINDI_MARATHI",
            moodProfile = "CALM",
            moodScore = 0.30f,
            lrcLyrics = """
                [00:00.00]Sufi Raag - Mystic Devotion
                [00:10.00]Eternal longing of the soulful prayer
                [00:22.00]In every gentle breath, devotion is there
                [00:34.00]Chants of praise rising to the sky
                [00:46.00]Illuminating peace that never says goodbye
            """.trimIndent()
        ),
        SongEntity(
            id = 900006L,
            title = "Midnight Coffee Lo-Fi",
            artist = "Aura Chill Project",
            album = "Late Night Study Sessions",
            durationMs = 160000L,
            contentUri = "https://www.soundhelix.com/examples/mp3/SoundHelix-Song-9.mp3",
            streamUrl = "https://www.soundhelix.com/examples/mp3/SoundHelix-Song-9.mp3",
            albumArtUri = "https://images.unsplash.com/photo-1501386761578-eac5c94b800a?w=600&auto=format&fit=crop&q=80",
            sourceType = "ONLINE",
            isDownloaded = false,
            downloadProgress = 0,
            languageScript = "ENGLISH_OTHER",
            moodProfile = "CHILL",
            moodScore = 0.35f,
            lrcLyrics = """
                [00:00.00]Midnight Coffee Lo-Fi
                [00:15.00]Gentle rain tapping on the windowpane
                [00:30.00]Warm brew, peaceful mind, no stress today
                [00:45.00]Lost inside rhythmic tape hiss memories
            """.trimIndent()
        )
    )

    fun searchTracks(query: String): List<SongEntity> {
        val q = query.trim().lowercase()
        if (q.isEmpty()) return getOnlineTracks()

        return getOnlineTracks().filter { track ->
            track.title.lowercase().contains(q) ||
            track.artist.lowercase().contains(q) ||
            track.album.lowercase().contains(q) ||
            track.languageScript.lowercase().contains(q) ||
            track.moodProfile.lowercase().contains(q)
        }
    }
}
