package com.example.data

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.File

/**
 * High-contrast, premium color schemes required by Pulse Music.
 */
enum class AppThemePreset(
    val displayName: String,
    val backgroundColorHex: Long,
    val surfaceColorHex: Long,
    val cardColorHex: Long,
    val primaryAccentHex: Long,
    val secondaryAccentHex: Long,
    val textColorHex: Long
) {
    RADIOACTIVE_GREEN(
        "Radioactive / Neon Green",
        0xFF000000, // Pure OLED #000000
        0xFF080D08,
        0xFF101910,
        0xFF39FF14, // Intense #39FF14 accents
        0xFF00F5D4,
        0xFFFFFFFF
    ),
    CYBERPUNK_MAGENTA(
        "Cyberpunk Magenta / Cyan",
        0xFF0A0518,
        0xFF140D26,
        0xFF21153E,
        0xFFFF007F, // Neon Magenta
        0xFF00F0FF, // Neon Cyan
        0xFFFFFFFF
    ),
    NORDIC_SLATE(
        "Nordic Slate & Frost White",
        0xFF13171F,
        0xFF1C222D,
        0xFF262E3D,
        0xFFE2E8F0, // Frost White
        0xFF94A3B8,
        0xFFFFFFFF
    ),
    AMBER_GOLD(
        "Amber Gold & Deep Obsidian",
        0xFF0A0907, // Deep Obsidian
        0xFF16130E,
        0xFF231E15,
        0xFFFFB300, // Amber Gold
        0xFFFF8F00,
        0xFFFFFFFF
    ),
    SPOTIFY_OLED(
        "Pulse Classic OLED",
        0xFF000000,
        0xFF121212,
        0xFF1E1E1E,
        0xFF1DB954,
        0xFF00F5D4,
        0xFFFFFFFF
    )
}

enum class PlayerUIStyle {
    SPOTIFY_FLUID, SAMSUNG_MINIMALIST
}

class SettingsManager(private val context: Context) {

    private val prefs: SharedPreferences =
        context.getSharedPreferences("pulse_music_settings", Context.MODE_PRIVATE)

    // Audio & Playback
    private val _bitrate = MutableStateFlow(prefs.getString("bitrate", "320 kbps") ?: "320 kbps")
    val bitrate: StateFlow<String> = _bitrate.asStateFlow()

    private val _crossfadeSeconds = MutableStateFlow(prefs.getInt("crossfade_sec", 0))
    val crossfadeSeconds: StateFlow<Int> = _crossfadeSeconds.asStateFlow()

    private val _sleepTimerRemainingMinutes = MutableStateFlow<Int?>(null)
    val sleepTimerRemainingMinutes: StateFlow<Int?> = _sleepTimerRemainingMinutes.asStateFlow()

    // Personalization & Themes
    private val _themePreset = MutableStateFlow(
        try {
            AppThemePreset.valueOf(prefs.getString("theme_preset", AppThemePreset.RADIOACTIVE_GREEN.name)!!)
        } catch (_: Exception) {
            AppThemePreset.RADIOACTIVE_GREEN
        }
    )
    val themePreset: StateFlow<AppThemePreset> = _themePreset.asStateFlow()

    // Custom Wallpaper & Scrim
    private val _customWallpaperUri = MutableStateFlow(prefs.getString("wallpaper_uri", null))
    val customWallpaperUri: StateFlow<String?> = _customWallpaperUri.asStateFlow()

    private val _wallpaperScrimAlpha = MutableStateFlow(prefs.getFloat("wallpaper_scrim_alpha", 0.50f))
    val wallpaperScrimAlpha: StateFlow<Float> = _wallpaperScrimAlpha.asStateFlow()

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

    // Remote Streaming Provider (Custom Instance Configuration)
    private val _streamingInstanceUrl = MutableStateFlow(
        prefs.getString("streaming_instance_url", "https://pipedapi.kavin.rocks/") ?: "https://pipedapi.kavin.rocks/"
    )
    val streamingInstanceUrl: StateFlow<String> = _streamingInstanceUrl.asStateFlow()

    fun setStreamingInstanceUrl(url: String) {
        val sanitized = if (!url.endsWith("/")) "$url/" else url
        _streamingInstanceUrl.value = sanitized
        prefs.edit().putString("streaming_instance_url", sanitized).apply()
    }

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

    fun setThemePreset(preset: AppThemePreset) {
        _themePreset.value = preset
        prefs.edit().putString("theme_preset", preset.name).apply()
    }

    fun setCustomWallpaper(uriString: String?) {
        _customWallpaperUri.value = uriString
        prefs.edit().putString("wallpaper_uri", uriString).apply()
    }

    fun setWallpaperScrimAlpha(alpha: Float) {
        val clamped = alpha.coerceIn(0.40f, 0.60f)
        _wallpaperScrimAlpha.value = clamped
        prefs.edit().putFloat("wallpaper_scrim_alpha", clamped).apply()
    }

    fun setPlayerStyle(style: PlayerUIStyle) {
        _playerStyle.value = style
        prefs.edit().putString("player_style", style.name).apply()
    }

    fun toggleTabVisibility(tabName: String) {
        val current = _visibleTabs.value.toMutableSet()
        if (current.contains(tabName)) {
            if (current.size > 1) {
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
