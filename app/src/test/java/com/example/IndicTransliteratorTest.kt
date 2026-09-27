package com.example

import com.example.utils.IndicTransliterator
import com.example.utils.LrcParser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class IndicTransliteratorTest {

    @Test
    fun `transliterates Devanagari Hindi lyrics phonetically into Latin alphabet`() {
        val devanagari = "केसरिया तेरा इश्क है पिया"
        val transliterated = IndicTransliterator.transliterateToLatin(devanagari)
        // Checks that Devanagari characters are completely Romanized into Latin alphabet
        assertFalse(IndicTransliterator.containsIndicScript(transliterated))
        assertTrue(transliterated.contains("kesariya", ignoreCase = true))
        assertTrue(transliterated.contains("tera", ignoreCase = true))
    }

    @Test
    fun `transliterates Gurmukhi Punjabi lyrics phonetically into Latin alphabet`() {
        val gurmukhi = "ਤੂੰ ਹੀ ਮੇਰਾ ਪਿਆਰ ਰੂਹ ਦਾ ਸਕੂਨ"
        val transliterated = IndicTransliterator.transliterateToLatin(gurmukhi)
        assertFalse(IndicTransliterator.containsIndicScript(transliterated))
        assertTrue(transliterated.contains("mera", ignoreCase = true))
    }

    @Test
    fun `enforces Zero Translation Rule by stripping parenthetical English translations`() {
        val raw = "Kesariya tera ishq hai piya (Your love is saffron, my beloved)"
        val cleaned = IndicTransliterator.cleanAndTransliterate(raw)
        assertEquals("Kesariya tera ishq hai piya", cleaned)
    }

    @Test
    fun `enforces Zero Translation Rule by discarding standalone English translation lines`() {
        val translationLine = "Your love is saffron, my beloved"
        val cleaned = IndicTransliterator.cleanAndTransliterate(translationLine)
        assertEquals("", cleaned)
    }

    @Test
    fun `strips trailing translation separators`() {
        val lineWithSeparator = "Kesariya tera ishq hai piya // Your love is saffron"
        val cleaned = IndicTransliterator.cleanAndTransliterate(lineWithSeparator)
        assertEquals("Kesariya tera ishq hai piya", cleaned)
    }

    @Test
    fun `LrcParser parses and romanizes Devanagari while preserving timestamps`() {
        val lrc = """
            [00:10.50] केसरिया तेरा इश्क है पिया (Your love is saffron)
            [00:15.00] (Your love is saffron, my beloved)
            [00:20.00] तूं ही मेरा यार
        """.trimIndent()

        val parsed = LrcParser.parse(lrc)
        // Standalone translation line at 00:15.00 is filtered out
        assertEquals(2, parsed.size)
        assertEquals(10500L, parsed[0].timeMs)
        assertFalse(IndicTransliterator.containsIndicScript(parsed[0].text))
        assertFalse(parsed[0].text.contains("saffron", ignoreCase = true))
        assertTrue(parsed[0].text.contains("kesariya", ignoreCase = true))
    }
}
