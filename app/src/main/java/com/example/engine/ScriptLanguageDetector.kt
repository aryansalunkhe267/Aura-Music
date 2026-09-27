package com.example.engine

/**
 * Offline linguistic classifier inspecting UTF-8 Unicode codepoints
 * and comprehensive artist/keyword dictionaries for 600+ offline songs.
 *
 * Categorizes tracks into:
 * - PUNJABI (Gurmukhi script & Romanized Punjabi keywords/artists)
 * - HINDI (Devanagari script & Romanized Hindi keywords/artists)
 * - MARATHI (Romanized Marathi keywords & iconic Marathi artists)
 * - DEVOTIONAL (Bhakti / Spiritual / Gurbani / Mantras)
 * - ENGLISH_OTHER (International / English / Instrumental)
 */
object ScriptLanguageDetector {

    enum class ScriptType(val displayName: String, val tag: String) {
        PUNJABI("Punjabi", "PUNJABI"),
        HINDI("Hindi", "HINDI"),
        MARATHI("Marathi", "MARATHI"),
        DEVOTIONAL("Devotional", "DEVOTIONAL"),
        ENGLISH_OTHER("English / Other", "ENGLISH_OTHER")
    }

    // 1. Devotional / Spiritual Lexicon (Matched first across all languages)
    private val DEVOTIONAL_KEYWORDS = listOf(
        "aarti", "bhajan", "chalisa", "gurbani", "shabad", "kirtan", "ram", "shiva", 
        "krishna", "hanuman", "waheguru", "mantra", "stotram", "devotional",
        "anuradha paudwal", "hariharan", "jagjit singh", "gayatri", "ganpati", "ganesh", 
        "sai", "durga", "mata", "shlok", "stuti", "om namah", "hare krishna", "govind", 
        "bhagwan", "mahadev", "har har", "radhe", "radha", "satnam", "ardas", "jaap",
        "sukrit", "simran", "ik onkar", "vaheguru", "bhakti", "puja", "stotra"
    )

    // 2. Romanized Punjabi Lexicon (Artists + Slang / Cultural keywords)
    private val PUNJABI_ARTISTS = listOf(
        "sidhu moose wala", "sidhu moosewala", "karan aujla", "diljit dosanjh", "diljit", 
        "bilal saeed", "dr zeus", "amrit maan", "babbu maan", "sharry mann", "garry sandhu", 
        "ap dhillon", "b praak", "jordan sandhu", "parmish verma", "jasmine sandlas", 
        "sunanda sharma", "shubh", "mankirt aulakh", "hardy sandhu", "gurdas maan", 
        "jassi gill", "ammy virk", "tarsem jassar", "kulwinder billa", "nimrat khaira",
        "bohemia", "sukhe", "yo yo honey singh", "jazzy b", "gippy grewal", "prophec"
    )

    private val PUNJABI_KEYWORDS = listOf(
        "jatt", "pind", "sohneya", "taara", "bars", "yaar", "gabru", "patiala", 
        "bhangra", "akhiyan", "suit", "geet", "punjabi", "boliyan", "tappe", 
        "surma", "velli", "chobbar", "paranda", "kurti", "daaru", "mutiyaar", 
        "ghaint", "bamb", "gallan", "veham", "hathyaar", "asla", "jatti", "kudi", 
        "duawan", "chaahida", "rooh", "tere naal", "kise", "hor", "ditti"
    )

    // 3. Romanized Marathi Lexicon (Artists + Cultural keywords)
    private val MARATHI_ARTISTS = listOf(
        "ajay-atul", "ajay atul", "swapnil bandodkar", "suresh wadkar", "avadhoot gupte", 
        "bela shende", "adarsh shinde", "anand shinde", "vaishali samant", "mahesh kale", 
        "rahul deshpande", "sudhir phadke", "shridhar phadke", "prathamessh laghate",
        "arya ambekar", "kartiki gaikwad", "mangesh borgaonkar"
    )

    private val MARATHI_KEYWORDS = listOf(
        "prem", "raja", "pori", "deva", "junya", "dhaga", "koligeet", "bhetali", 
        "majhya", "tula", "marathi", "lavani", "abhang", "gavlan", "por", "porga", 
        "paoos", "chimb", "manat", "jeva", "aai", "zingaat", "yad lagla", "sairat", 
        "shantabai", "gondhal", "bharud", "maharashtra", "koli", "dhagala", "lagli",
        "dolby", "walwal", "pappi", "gulabachi", "rani", "kombdi"
    )

    // 4. Romanized Hindi Lexicon (Artists + Bollywood / Ghazal keywords)
    private val HINDI_ARTISTS = listOf(
        "arijit singh", "arijit", "neha kakkar", "atif aslam", "shreya ghoshal", "shreya", 
        "kishore kumar", "kishore", "lata mangeshkar", "lata", "udit narayan", "alka yagnik", 
        "kumar sanu", "sonu nigam", "badshah", "pritam", "vishal-shekhar", "vishal shekhar", 
        "sajid-wajid", "sajid wajid", "mohit chauhan", "kk", "armaan malik", "jubin nautiyal", 
        "shankar mahadevan", "ar rahman", "sunidhi chauhan", "mika singh", "darshan raval",
        "sachin-jigar", "mithoon", "shankar-ehsaan-loy", "amit trivedi", "himesh reshammiya"
    )

    private val HINDI_KEYWORDS = listOf(
        "pyaar", "dil", "aankhein", "tere", "mera", "hawa", "zindagi", "ishq", 
        "tum", "hum", "mohabbat", "khuda", "sanam", "deewana", "chand", "raat", 
        "saath", "dhadkan", "aashiqui", "kasam", "humsafar", "tujhe", "meri", 
        "bewafa", "dard", "pal", "muskurane", "tumse", "jaane", "zara", "dua",
        "kesariya", "channa mereya", "tum hi ho", " Shayar", "shayari", "bollywood"
    )

    /**
     * Inspects track title, artist name, and album to determine high-accuracy script and language identity.
     */
    fun detectScript(title: String, artist: String, album: String = ""): ScriptType {
        val combinedText = "$title $artist $album".trim()
        val lower = combinedText.lowercase()

        // Step 1: Check Devotional first (Spiritual tracks take highest precedence)
        for (kw in DEVOTIONAL_KEYWORDS) {
            if (containsWord(lower, kw)) {
                return ScriptType.DEVOTIONAL
            }
        }

        // Step 2: Unicode Codepoint Frequency Analysis
        var gurmukhiCount = 0
        var devanagariCount = 0

        for (ch in combinedText) {
            val codePoint = ch.code
            // Gurmukhi Unicode Block: \u0A00 – \u0A7F (Punjabi)
            if (codePoint in 0x0A00..0x0A7F) {
                gurmukhiCount++
            }
            // Devanagari Unicode Block: \u0900 – \u097F (Hindi / Marathi)
            else if (codePoint in 0x0900..0x097F) {
                devanagariCount++
            }
        }

        // Strong Unicode Gurmukhi presence
        if (gurmukhiCount > 0 && gurmukhiCount >= devanagariCount) {
            return ScriptType.PUNJABI
        }

        // Step 3: Romanized Marathi Identification (Artists take precedence over Devanagari default)
        for (artistName in MARATHI_ARTISTS) {
            if (lower.contains(artistName)) return ScriptType.MARATHI
        }
        for (kw in MARATHI_KEYWORDS) {
            if (containsWord(lower, kw)) {
                return ScriptType.MARATHI
            }
        }

        // Strong Unicode Devanagari presence (if not identified as Marathi above)
        if (devanagariCount > 0 && devanagariCount > gurmukhiCount) {
            return ScriptType.HINDI
        }

        // Step 4: Romanized Punjabi Identification
        for (artistName in PUNJABI_ARTISTS) {
            if (lower.contains(artistName)) return ScriptType.PUNJABI
        }
        for (kw in PUNJABI_KEYWORDS) {
            if (containsWord(lower, kw)) {
                return ScriptType.PUNJABI
            }
        }

        // Step 5: Romanized Hindi Identification
        for (artistName in HINDI_ARTISTS) {
            if (lower.contains(artistName)) return ScriptType.HINDI
        }
        for (kw in HINDI_KEYWORDS) {
            if (containsWord(lower, kw)) {
                return ScriptType.HINDI
            }
        }

        return ScriptType.ENGLISH_OTHER
    }

    private fun containsWord(haystack: String, needle: String): Boolean {
        if (!haystack.contains(needle)) return false
        // Word boundary matching to prevent false subword positives
        val regex = Regex("""(?i)(?:^|[\s\-_,.:;()\[\]])\Q$needle\E(?:$|[\s\-_,.:;()\[\]])""")
        return regex.containsMatchIn(haystack)
    }
}
