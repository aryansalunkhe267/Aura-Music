package com.example

import com.example.utils.MetadataSanitizer
import com.example.utils.cleanMetadataString
import org.junit.Assert.assertEquals
import org.junit.Test

class MetadataSanitizerTest {

    @Test
    fun `preserves Punjabi Gurmukhi script title verbatim without translation`() {
        val raw = "ਤਿਤਲੀਆਂ.mp3"
        val cleaned = MetadataSanitizer.sanitizeTitle(raw)
        assertEquals("ਤਿਤਲੀਆਂ", cleaned)
    }

    @Test
    fun `preserves Hindi Marathi Devanagari script title verbatim without translation`() {
        val raw = "केसरिया तेरा इश्क है पिया"
        val cleaned = MetadataSanitizer.sanitizeTitle(raw)
        assertEquals("केसरिया तेरा इश्क है पिया", cleaned)
    }

    @Test
    fun `preserves bilingual titles verbatim without altering or translating`() {
        val raw = "Kesariya / केसरिया"
        val cleaned = MetadataSanitizer.sanitizeTitle(raw)
        assertEquals("Kesariya / केसरिया", cleaned)
    }

    @Test
    fun `strips file extension while keeping native title`() {
        val raw = "295 - ਸਿੱਧੂ ਮੂਸੇਵਾਲਾ.m4a"
        val cleaned = MetadataSanitizer.sanitizeTitle(raw)
        assertEquals("295 - ਸਿੱਧੂ ਮੂਸੇਵਾਲਾ", cleaned)
    }

    @Test
    fun `cleanMetadataString strips parenthetical translations`() {
        val raw1 = "295 (ਟਵੰਟੀ ਨਾਈਨ ਫਾਈਵ)"
        val raw2 = "Excuses (ਕਹਿਣੇ ਦੀ ਲੋੜ ਨਹੀਂ)"
        assertEquals("295", cleanMetadataString(raw1))
        assertEquals("Excuses", cleanMetadataString(raw2))
    }

    @Test
    fun `cleanMetadataString strips square bracket tags`() {
        val raw = "Song Title [Official Audio] [Remix]"
        assertEquals("Song Title", cleanMetadataString(raw))
    }
}
