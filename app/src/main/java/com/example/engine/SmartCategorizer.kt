package com.example.engine

import java.util.regex.Pattern

/**
 * Intelligent classifier for Indic and Indian music genres.
 * Categorizes tracks into:
 * - Punjabi
 * - Hindi
 * - Marathi
 * - Bollywood
 * - 90s Hindi
 * - Devotional
 *
 * Utilizes Unicode codepoint ranges (Gurmukhi \u0A00-\u0A7F, Devanagari \u0900-\u097F)
 * and comprehensive tag/artist/title keyword regex matching.
 */
object SmartCategorizer {

    enum class Category(val displayName: String, val tag: String) {
        ALL("All", "ALL"),
        PUNJABI("Punjabi", "PUNJABI"),
        HINDI("Hindi", "HINDI"),
        MARATHI("Marathi", "MARATHI"),
        BOLLYWOOD("Bollywood", "BOLLYWOOD"),
        NINETIES_HINDI("90s Hindi", "NINETIES_HINDI"),
        DEVOTIONAL("Devotional", "DEVOTIONAL")
    }

    private val GURMUKHI_PATTERN = Pattern.compile("[\u0A00-\u0A7F]")
    private val DEVANAGARI_PATTERN = Pattern.compile("[\u0900-\u097F]")

    // Devotional / Spiritual keywords
    private val DEVOTIONAL_KEYWORDS = Regex(
        """(?i)\b(bhajan|aarti|arti|kirtan|devotional|chalisa|shlok|shloka|mantra|stotra|stuti|ganesh|ganpati|bappa|morya|shiva|shiv|bholenath|mahadev|krishna|radha|ram|shree ram|hanuman|waheguru|ardas|simran|gurbani|vitthal|pandurang|mauli|sai baba|bhakti|devi|mata|amritvela|prayer|temple)\b"""
    )

    // 90s Hindi golden era keywords
    private val NINETIES_KEYWORDS = Regex(
        """(?i)\b(90s|1990s|nineties|kumar sanu|alka yagnik|udit narayan|anu malik|nadeem shravan|jatin lalit|kavita krishnamurthy|sadhana sargam|abhijeet|bappi lahiri|lata mangeshkar|kishore kumar|mohd rafi|mukesh|anuradha paudwal|sonu nigam 90s|tip tip|main koi aisa geet)\b"""
    )

    // Marathi linguistic & cultural keywords
    private val MARATHI_KEYWORDS = Regex(
        """(?i)\b(marathi|abhang|lavani|ajay atul|ajay-atul|natarang|swapnil|mangesh|prahlad shinde|anand shinde|adityaraj|zeemarathi|maharashtra|vitthala|pandharpur|dhurala|sairat|chhatrapati|shivaji)\b"""
    )

    // Bollywood soundtrack & contemporary Hindi cinema keywords
    private val BOLLYWOOD_KEYWORDS = Regex(
        """(?i)\b(bollywood|soundtrack|filmi|movie|t-series|tseries|yrf|dharma|zeemusic|pritam|vishal shekhar|amit trivedi|badshah|honey singh|neha kakkar|armaan malik|arijit singh|shreya ghoshal|sunidhi chauhan|atif aslam|remix|club mix|reprise)\b"""
    )

    // Punjabi artists and musical terminology
    private val PUNJABI_KEYWORDS = Regex(
        """(?i)\b(punjabi|bhangra|sidhu|moose\s*wala|diljit|dosanjh|ap dhillon|karan aujla|shubh|amrit maan|jassi|tappe|boliyan|gurinder gill|b praak|jaani|hardy sandhu|ammy virk|maninder butter|sunanda|parmish|gippy|mankirt|kulwinder|speed records|white hill)\b"""
    )

    // General Hindi keywords
    private val HINDI_KEYWORDS = Regex(
        """(?i)\b(hindi|ghazal|sufi|qawwali|arijit|shreya|sonu nigam|jubin nautiyal|kk|mohit chauhan|darshan raval|jagjit singh|rahat fateh|pankaj udhas)\b"""
    )

    /**
     * Determines the primary category for a track.
     */
    fun classify(
        title: String,
        artist: String,
        album: String,
        genre: String = ""
    ): Category {
        val combined = "$title $artist $album $genre"

        // 1. Devotional takes highest thematic precedence
        if (DEVOTIONAL_KEYWORDS.containsMatchIn(combined)) {
            return Category.DEVOTIONAL
        }

        // 2. 90s Hindi classics
        if (NINETIES_KEYWORDS.containsMatchIn(combined)) {
            return Category.NINETIES_HINDI
        }

        // 3. Marathi
        if (MARATHI_KEYWORDS.containsMatchIn(combined)) {
            return Category.MARATHI
        }

        // 4. Gurmukhi script detection or Punjabi keywords
        if (GURMUKHI_PATTERN.matcher(combined).find() || PUNJABI_KEYWORDS.containsMatchIn(combined)) {
            return Category.PUNJABI
        }

        // 5. Bollywood
        if (BOLLYWOOD_KEYWORDS.containsMatchIn(combined) || genre.contains("Soundtrack", ignoreCase = true) || genre.contains("Bollywood", ignoreCase = true)) {
            return Category.BOLLYWOOD
        }

        // 6. Devanagari script detection or Hindi keywords
        if (DEVANAGARI_PATTERN.matcher(combined).find() || HINDI_KEYWORDS.containsMatchIn(combined)) {
            return Category.HINDI
        }

        return Category.ALL
    }

    /**
     * Checks if two songs match the same linguistic/genre category for language-locked auto-queue.
     */
    fun isSameCategory(songA: com.example.data.SongEntity, songB: com.example.data.SongEntity): Boolean {
        val catA = classify(songA.title, songA.artist, songA.album, songA.genre ?: "")
        val catB = classify(songB.title, songB.artist, songB.album, songB.genre ?: "")
        if (catA == Category.ALL || catB == Category.ALL) {
            return true
        }
        return catA == catB
    }
}
