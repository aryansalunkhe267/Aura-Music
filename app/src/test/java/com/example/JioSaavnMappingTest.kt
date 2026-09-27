package com.example

import com.example.network.model.JioSaavnAlbum
import com.example.network.model.JioSaavnArtist
import com.example.network.model.JioSaavnArtists
import com.example.network.model.JioSaavnMediaLink
import com.example.network.model.JioSaavnSong
import com.example.network.model.toSongEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class JioSaavnMappingTest {

    @Test
    fun `maps JioSaavn song to unified SongEntity with 320kbps stream and 500x500 art`() {
        val song = JioSaavnSong(
            id = "test_song_123",
            name = "295 (Official Audio)",
            duration = 270,
            language = "punjabi",
            album = JioSaavnAlbum(id = "alb_1", name = "Moosetape"),
            artists = JioSaavnArtists(
                primary = listOf(JioSaavnArtist(id = "art_1", name = "Sidhu Moose Wala"))
            ),
            image = listOf(
                JioSaavnMediaLink(quality = "50x50", url = "http://art/50.jpg"),
                JioSaavnMediaLink(quality = "500x500", url = "http://art/500.jpg")
            ),
            downloadUrl = listOf(
                JioSaavnMediaLink(quality = "160kbps", url = "http://stream/160.mp3"),
                JioSaavnMediaLink(quality = "320kbps", url = "http://stream/320.mp3")
            )
        )

        val entity = song.toSongEntity()

        assertEquals("295", entity.title)
        assertEquals("Sidhu Moose Wala", entity.artist)
        assertEquals("Moosetape", entity.album)
        assertEquals(270_000L, entity.durationMs)
        assertEquals("http://stream/320.mp3", entity.streamUrl)
        assertEquals("http://art/500.jpg", entity.albumArtUri)
        assertEquals("ONLINE", entity.sourceType)
        assertEquals("PUNJABI", entity.languageScript)
        assertTrue(entity.id != 0L)
    }

    @Test
    fun `detects Marathi language correctly`() {
        val song = JioSaavnSong(
            id = "marathi_song_456",
            name = "Zingaat",
            duration = 210,
            language = "marathi",
            album = JioSaavnAlbum(id = "alb_2", name = "Sairat"),
            artists = JioSaavnArtists(
                primary = listOf(JioSaavnArtist(id = "art_2", name = "Ajay-Atul"))
            ),
            image = emptyList(),
            downloadUrl = listOf(JioSaavnMediaLink(quality = "320kbps", url = "http://stream/zingaat.mp3"))
        )

        val entity = song.toSongEntity()
        assertEquals("MARATHI", entity.languageScript)
        assertEquals("Ajay-Atul", entity.artist)
    }
}
