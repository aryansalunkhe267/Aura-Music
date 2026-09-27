package com.example.data

/**
 * Curated catalog of online audio streams and featured rails for Pulse Music
 * spanning Punjabi, Hindi/Bollywood, Marathi, and Lo-Fi Indie genres.
 */
object OnlineMusicCatalog {

    data class OnlinePlaylist(
        val id: String,
        val title: String,
        val subtitle: String,
        val coverUrl: String,
        val songCount: Int,
        val songs: List<SongEntity>
    )

    fun getTrendingNow(): List<SongEntity> = listOf(
        SongEntity(
            id = 900001L,
            title = "Pendu Vibe",
            artist = "Aman Sandhu",
            album = "Desi Beats Punjab",
            durationMs = 184000L,
            contentUri = "https://www.soundhelix.com/examples/mp3/SoundHelix-Song-1.mp3",
            streamUrl = "https://www.soundhelix.com/examples/mp3/SoundHelix-Song-1.mp3",
            albumArtUri = "https://images.unsplash.com/photo-1514525253161-7a46d19cd819?w=600&auto=format&fit=crop&q=80",
            coverArtUrl = "https://images.unsplash.com/photo-1514525253161-7a46d19cd819?w=600&auto=format&fit=crop&q=80",
            sourceType = "ONLINE",
            isDownloaded = false,
            downloadProgress = 0,
            languageScript = "PUNJABI",
            moodProfile = "ENERGETIC",
            moodScore = 0.85f,
            lrcLyrics = """
                [00:00.00]Pendu Vibe - Aman Sandhu
                [00:05.50]Savere savere khule khetaan vich thandi hawa
                [00:15.20]Yaari piche jaan vaar de munde beparwah
                [00:25.80]Saadi wakhri pehchaan, uchi saddi shaan
                [00:35.00]Dil khol ke miliye yaaran nu hamesha
                [00:48.30]Pendu Vibe challe non-stop jatta!
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
            coverArtUrl = "https://images.unsplash.com/photo-1470225620780-dba8ba36b745?w=600&auto=format&fit=crop&q=80",
            sourceType = "ONLINE",
            isDownloaded = false,
            downloadProgress = 0,
            languageScript = "HINDI_MARATHI",
            moodProfile = "CHILL",
            moodScore = 0.40f,
            lrcLyrics = """
                [00:00.00]Tum Mile Dil Khila - Acoustic Lofi
                [00:08.00]Barse re sawan ki meethi fuhaar
                [00:18.50]Khil gaye man ke phool pehle pehle pyar
                [00:29.00]Tum mile dil khile aur jeene ko kya chahiye
                [00:40.00]Har ek dhadkan pukare tera naam re piya
                [00:52.00]Taareyan di chhaanve baith kitiyan salaahan
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
            coverArtUrl = "https://images.unsplash.com/photo-1493225457124-a3eb161ffa5f?w=600&auto=format&fit=crop&q=80",
            sourceType = "ONLINE",
            isDownloaded = false,
            downloadProgress = 0,
            languageScript = "HINDI_MARATHI",
            moodProfile = "UPBEAT",
            moodScore = 0.90f,
            lrcLyrics = """
                [00:00.00]Malhari Dhun - Dhol Tasha Mix
                [00:06.00]Garjati sahyadri shikhare dhol tasha chya taalat
                [00:16.00]Ude ga amba bai cha gondhal aamchya darat
                [00:28.00]Bhandara udhala re aabhalat bhala motha
                [00:40.00]Malhari martand jai malhar bolta
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
            coverArtUrl = "https://images.unsplash.com/photo-1511671782779-c97d3d27a1d4?w=600&auto=format&fit=crop&q=80",
            sourceType = "ONLINE",
            isDownloaded = false,
            downloadProgress = 0,
            languageScript = "PUNJABI",
            moodProfile = "ENERGETIC",
            moodScore = 0.88f,
            lrcLyrics = """
                [00:00.00]Gedi Route - Karan Dhillon
                [00:07.00]Gaddi chaddi gedi route te morni wangu
                [00:17.50]Bass chhadti speaker khadkave shishe wangu
                [00:27.00]Charche shehar ch poore jatt de
                [00:38.00]Yaar beli naal baith mehfilan sajaande
            """.trimIndent()
        )
    )

    fun getTopCharts(): List<SongEntity> = listOf(
        SongEntity(
            id = 900010L,
            title = "Jatt Life Anthem",
            artist = "Varinder Brar",
            album = "Top 50 Punjab",
            durationMs = 192000L,
            contentUri = "https://www.soundhelix.com/examples/mp3/SoundHelix-Song-5.mp3",
            streamUrl = "https://www.soundhelix.com/examples/mp3/SoundHelix-Song-5.mp3",
            albumArtUri = "https://images.unsplash.com/photo-1508700115892-45ecd05ae2ad?w=600&auto=format&fit=crop&q=80",
            coverArtUrl = "https://images.unsplash.com/photo-1508700115892-45ecd05ae2ad?w=600&auto=format&fit=crop&q=80",
            sourceType = "ONLINE",
            isDownloaded = false,
            downloadProgress = 0,
            languageScript = "PUNJABI",
            genre = "Top Chart #1"
        ),
        SongEntity(
            id = 900011L,
            title = "Sajna Ve",
            artist = "Arijit Style Ensemble",
            album = "Bollywood Top Hits",
            durationMs = 230000L,
            contentUri = "https://www.soundhelix.com/examples/mp3/SoundHelix-Song-6.mp3",
            streamUrl = "https://www.soundhelix.com/examples/mp3/SoundHelix-Song-6.mp3",
            albumArtUri = "https://images.unsplash.com/photo-1518609878373-06d740f60d8b?w=600&auto=format&fit=crop&q=80",
            coverArtUrl = "https://images.unsplash.com/photo-1518609878373-06d740f60d8b?w=600&auto=format&fit=crop&q=80",
            sourceType = "ONLINE",
            isDownloaded = false,
            downloadProgress = 0,
            languageScript = "HINDI_MARATHI",
            genre = "Top Chart #2"
        ),
        SongEntity(
            id = 900012L,
            title = "Aai Bhavani Gondhal",
            artist = "Ajay-Atul Tribute",
            album = "Maharashtra Top 20",
            durationMs = 210000L,
            contentUri = "https://www.soundhelix.com/examples/mp3/SoundHelix-Song-7.mp3",
            streamUrl = "https://www.soundhelix.com/examples/mp3/SoundHelix-Song-7.mp3",
            albumArtUri = "https://images.unsplash.com/photo-1465847899084-d164df4dedc6?w=600&auto=format&fit=crop&q=80",
            coverArtUrl = "https://images.unsplash.com/photo-1465847899084-d164df4dedc6?w=600&auto=format&fit=crop&q=80",
            sourceType = "ONLINE",
            isDownloaded = false,
            downloadProgress = 0,
            languageScript = "HINDI_MARATHI",
            genre = "Top Chart #3"
        ),
        SongEntity(
            id = 900005L,
            title = "Sufi Raag - Bismillah",
            artist = "Nusrat Studio Ensemble",
            album = "Mystic Soul",
            durationMs = 240000L,
            contentUri = "https://www.soundhelix.com/examples/mp3/SoundHelix-Song-8.mp3",
            streamUrl = "https://www.soundhelix.com/examples/mp3/SoundHelix-Song-8.mp3",
            albumArtUri = "https://images.unsplash.com/photo-1516450360452-9312f5e86fc7?w=600&auto=format&fit=crop&q=80",
            coverArtUrl = "https://images.unsplash.com/photo-1516450360452-9312f5e86fc7?w=600&auto=format&fit=crop&q=80",
            sourceType = "ONLINE",
            isDownloaded = false,
            downloadProgress = 0,
            languageScript = "HINDI_MARATHI",
            moodProfile = "CALM",
            moodScore = 0.30f,
            genre = "Top Chart #4"
        )
    )

    fun getNewReleases(): List<SongEntity> = listOf(
        SongEntity(
            id = 900020L,
            title = "Midnight Coffee Lo-Fi",
            artist = "Aura Chill Project",
            album = "Late Night Study Sessions",
            durationMs = 160000L,
            contentUri = "https://www.soundhelix.com/examples/mp3/SoundHelix-Song-9.mp3",
            streamUrl = "https://www.soundhelix.com/examples/mp3/SoundHelix-Song-9.mp3",
            albumArtUri = "https://images.unsplash.com/photo-1501386761578-eac5c94b800a?w=600&auto=format&fit=crop&q=80",
            coverArtUrl = "https://images.unsplash.com/photo-1501386761578-eac5c94b800a?w=600&auto=format&fit=crop&q=80",
            sourceType = "ONLINE",
            isDownloaded = false,
            downloadProgress = 0,
            languageScript = "ENGLISH_OTHER",
            genre = "Lo-Fi Beats"
        ),
        SongEntity(
            id = 900021L,
            title = "Chandigarh Di Hawa",
            artist = "Harman Gill",
            album = "Urban Punjab 2026",
            durationMs = 178000L,
            contentUri = "https://www.soundhelix.com/examples/mp3/SoundHelix-Song-10.mp3",
            streamUrl = "https://www.soundhelix.com/examples/mp3/SoundHelix-Song-10.mp3",
            albumArtUri = "https://images.unsplash.com/photo-1514525253161-7a46d19cd819?w=600&auto=format&fit=crop&q=80",
            coverArtUrl = "https://images.unsplash.com/photo-1514525253161-7a46d19cd819?w=600&auto=format&fit=crop&q=80",
            sourceType = "ONLINE",
            isDownloaded = false,
            downloadProgress = 0,
            languageScript = "PUNJABI",
            genre = "New Release"
        ),
        SongEntity(
            id = 900022L,
            title = "Dil Ki Dhadkan",
            artist = "Kabir S & Priya Sen",
            album = "Dhadkan Remastered",
            durationMs = 215000L,
            contentUri = "https://www.soundhelix.com/examples/mp3/SoundHelix-Song-11.mp3",
            streamUrl = "https://www.soundhelix.com/examples/mp3/SoundHelix-Song-11.mp3",
            albumArtUri = "https://images.unsplash.com/photo-1470225620780-dba8ba36b745?w=600&auto=format&fit=crop&q=80",
            coverArtUrl = "https://images.unsplash.com/photo-1470225620780-dba8ba36b745?w=600&auto=format&fit=crop&q=80",
            sourceType = "ONLINE",
            isDownloaded = false,
            downloadProgress = 0,
            languageScript = "HINDI_MARATHI",
            genre = "New Release"
        )
    )

    fun getFeaturedPlaylists(): List<OnlinePlaylist> = listOf(
        OnlinePlaylist(
            id = "pl_punjabi_top",
            title = "Punjabi Power Hits",
            subtitle = "Sidhu, AP Dhillon, Diljit & Karan Aujla",
            coverUrl = "https://images.unsplash.com/photo-1511671782779-c97d3d27a1d4?w=600&auto=format&fit=crop&q=80",
            songCount = 25,
            songs = listOf(getTrendingNow()[0], getTrendingNow()[3], getTopCharts()[0])
        ),
        OnlinePlaylist(
            id = "pl_bollywood_romance",
            title = "Bollywood Romance 2026",
            subtitle = "Arijit Singh, Shreya Ghoshal, Atif Aslam",
            coverUrl = "https://images.unsplash.com/photo-1470225620780-dba8ba36b745?w=600&auto=format&fit=crop&q=80",
            songCount = 30,
            songs = listOf(getTrendingNow()[1], getTopCharts()[1])
        ),
        OnlinePlaylist(
            id = "pl_marathi_energetic",
            title = "Dhol Tasha & Marathi Beats",
            subtitle = "Ajay-Atul, Zingaat, Malhari Energy",
            coverUrl = "https://images.unsplash.com/photo-1493225457124-a3eb161ffa5f?w=600&auto=format&fit=crop&q=80",
            songCount = 18,
            songs = listOf(getTrendingNow()[2], getTopCharts()[2])
        ),
        OnlinePlaylist(
            id = "pl_devotional_bhakti",
            title = "Devotional Bhakti & Aarti",
            subtitle = "Ganesh Aarti, Hanuman Chalisa, Kirtan",
            coverUrl = "https://images.unsplash.com/photo-1465847899084-d164df4dedc6?w=600&auto=format&fit=crop&q=80",
            songCount = 20,
            songs = listOf(getTopCharts()[3])
        ),
        OnlinePlaylist(
            id = "pl_lofi_chill",
            title = "Late Night Study & Lo-Fi",
            subtitle = "Calm beats, soothing rain, relaxing focus",
            coverUrl = "https://images.unsplash.com/photo-1501386761578-eac5c94b800a?w=600&auto=format&fit=crop&q=80",
            songCount = 22,
            songs = listOf(getNewReleases()[0])
        )
    )

    fun getOnlineTracks(): List<SongEntity> {
        return (getTrendingNow() + getTopCharts() + getNewReleases()).distinctBy { it.id }
    }

    fun searchTracks(query: String): List<SongEntity> {
        val q = query.trim().lowercase()
        if (q.isEmpty()) return getOnlineTracks()

        return getOnlineTracks().filter { track ->
            track.title.lowercase().contains(q) ||
            track.artist.lowercase().contains(q) ||
            track.album.lowercase().contains(q) ||
            track.languageScript.lowercase().contains(q) ||
            (track.genre?.lowercase()?.contains(q) == true)
        }
    }
}
