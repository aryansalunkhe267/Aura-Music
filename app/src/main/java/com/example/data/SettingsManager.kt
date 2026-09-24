package com.example.data

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.File

enum class AppThemeMode {
    AMOLED_BLACK, DARK, LIGHT, SYSTEM
}

enum class PlayerUIStyle {
    SPOTIFY_FLUID, SAMSUNG_MINIMALIST
}

enum class AccentPalette(val displayName: String, val hexColor: Long) {
    SPOTIFY_GREEN("Spotify Green", 0xFF1DB954),
    SAMSUNG_BLUE("Samsung Blue", 0xFF2D68C4),
    VIBRANT_PURPLE("Vibrant Purple", 0xFF9C27B0),
    NEON_MINT("Neon Mint", 0xFF00F5A0),
    MONOCHROMATIC("Minimalist Mono", 0xFFE0E0E0)
}

class SettingsManager(private val context: Context) {

    private val prefs: SharedPreferences =
        context.getSharedPreferences("aura_music_settings", Context.MODE_PRIVATE)

    // Audio & Playback
    private val _bitrate = MutableStateFlow(prefs.getString("bitrate", "320 kbps") ?: "320 kbps")
    val bitrate: StateFlow<String> = _bitrate.asStateFlow()

    private val _crossfadeSeconds = MutableStateFlow(prefs.getInt("crossfade_sec", 0))
    val crossfadeSeconds: StateFlow<Int> = _crossfadeSeconds.asStateFlow()

    private val _sleepTimerRemainingMinutes = MutableStateFlow<Int?>(null)
    val sleepTimerRemainingMinutes: StateFlow<Int?> = _sleepTimerRemainingMinutes.asStateFlow()

    // Personalization & Theming
    private val _themeMode = MutableStateFlow(
        try {
            AppThemeMode.valueOf(prefs.getString("theme_mode", AppThemeMode.AMOLED_BLACK.name)!!)
        } catch (_: Exception) {
            AppThemeMode.AMOLED_BLACK
        }
    )
    val themeMode: StateFlow<AppThemeMode> = _themeMode.asStateFlow()

    private val _accentPalette = MutableStateFlow(
        try {
            AccentPalette.valueOf(prefs.getString("accent_palette", AccentPalette.SPOTIFY_GREEN.name)!!)
        } catch (_: Exception) {
            AccentPalette.SPOTIFY_GREEN
        }
    )
    val accentPalette: StateFlow<AccentPalette> = _accentPalette.asStateFlow()

    private val _playerStyle = MutableStateFlow(
        try {
            PlayerUIStyle.valueOf(prefs.getString("player_style", PlayerUIStyle.SPOTIFY_FLUID.name)!!)
        } catch (_: Exception) {
            PlayerUIStyle.SPOTIFY_FLUID
        }
    )
    val playerStyle: StateFlow<PlayerUIStyle> = _playerStyle.asStateFlow()

    private val _visibleTabs = MutableStateFlow(
        prefs.getStringSet("visible_tabs", setOf("Tracks", "Playlists", "Albums", "Artists", "Folders"))
            ?: setOf("Tracks", "Playlists", "Albums", "Artists", "Folders")
    )
    val visibleTabs: StateFlow<Set<String>> = _visibleTabs.asStateFlow()

    // Data & Storage
    private val _downloadOverWifiOnly = MutableStateFlow(prefs.getBoolean("wifi_only", false))
    val downloadOverWifiOnly: StateFlow<Boolean> = _downloadOverWifiOnly.asStateFlow()

    fun setBitrate(value: String) {
        _bitrate.value = value
        prefs.edit().putString("bitrate", value).apply()
    }

    fun setCrossfadeSeconds(sec: Int) {
        _crossfadeSeconds.value = sec
        prefs.edit().putInt("crossfade_sec", sec).apply()
    }

    fun setSleepTimer(minutes: Int?) {
        _sleepTimerRemainingMinutes.value = minutes
    }

    fun setThemeMode(mode: AppThemeMode) {
        _themeMode.value = mode
        prefs.edit().putString("theme_mode", mode.name).apply()
    }

    fun setAccentPalette(palette: AccentPalette) {
        _accentPalette.value = palette
        prefs.edit().putString("accent_palette", palette.name).apply()
    }

    fun setPlayerStyle(style: PlayerUIStyle) {
        _playerStyle.value = style
        prefs.edit().putString("player_style", style.name).apply()
    }

    fun toggleTabVisibility(tabName: String) {
        val current = _visibleTabs.value.toMutableSet()
        if (current.contains(tabName)) {
            if (current.size > 1) { // Keep at least one tab visible
                current.remove(tabName)
            }
        } else {
            current.add(tabName)
        }
        _visibleTabs.value = current
        prefs.edit().putStringSet("visible_tabs", current).apply()
    }

    fun setDownloadOverWifiOnly(enabled: Boolean) {
        _downloadOverWifiOnly.value = enabled
        prefs.edit().putBoolean("wifi_only", enabled).apply()
    }

    /**
     * Calculates cache footprint in Megabytes.
     */
    fun calculateCacheSizeMb(): String {
        return try {
            val cacheDir = context.cacheDir
            val sizeBytes = getFolderSize(cacheDir)
            val mb = sizeBytes.toDouble() / (1024 * 1024)
            String.format("%.1f MB", mb)
        } catch (e: Exception) {
            "0.0 MB"
        }
    }

    /**
     * Clears image & HTTP cache directory.
     */
    fun clearCache(): Boolean {
        return try {
            val cacheDir = context.cacheDir
            cacheDir.deleteRecursively()
            cacheDir.mkdirs()
            true
        } catch (e: Exception) {
            false
        }
    }

    private fun getFolderSize(dir: File): Long {
        var size = 0L
        dir.listFiles()?.forEach { file ->
            size += if (file.isDirectory) getFolderSize(file) else file.length()
        }
        return size
    }
}
