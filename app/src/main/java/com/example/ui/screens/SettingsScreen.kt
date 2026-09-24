package com.example.ui.screens

import android.content.Context
import android.content.Intent
import android.media.audiofx.AudioEffect
import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AvTimer
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.CleaningServices
import androidx.compose.material.icons.filled.ColorLens
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.HighQuality
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material.icons.filled.Tab
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.ViewCarousel
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.RadioButtonDefaults
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.AccentPalette
import com.example.data.AppThemeMode
import com.example.data.PlayerUIStyle
import com.example.data.SettingsManager
import com.example.playback.PlaybackManager
import com.example.ui.theme.NeonMint
import com.example.ui.theme.OneUICardElevated
import com.example.ui.theme.OneUIDarkBackground
import com.example.ui.theme.OneUISurfaceDark
import com.example.ui.theme.OneUITextPrimary
import com.example.ui.theme.OneUITextSecondary
import com.example.ui.theme.SpotifyGreen

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    settingsManager: SettingsManager,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current

    val bitrate by settingsManager.bitrate.collectAsStateWithLifecycle()
    val crossfadeSec by settingsManager.crossfadeSeconds.collectAsStateWithLifecycle()
    val sleepTimer by settingsManager.sleepTimerRemainingMinutes.collectAsStateWithLifecycle()
    val themeMode by settingsManager.themeMode.collectAsStateWithLifecycle()
    val accentPalette by settingsManager.accentPalette.collectAsStateWithLifecycle()
    val playerStyle by settingsManager.playerStyle.collectAsStateWithLifecycle()
    val visibleTabs by settingsManager.visibleTabs.collectAsStateWithLifecycle()
    val downloadOverWifiOnly by settingsManager.downloadOverWifiOnly.collectAsStateWithLifecycle()

    var cacheSize by remember { mutableStateOf(settingsManager.calculateCacheSizeMb()) }

    // Dialog state controllers
    var showBitrateDialog by remember { mutableStateOf(false) }
    var showSleepTimerDialog by remember { mutableStateOf(false) }
    var showThemeDialog by remember { mutableStateOf(false) }
    var showAccentDialog by remember { mutableStateOf(false) }
    var showTabCustomizerDialog by remember { mutableStateOf(false) }

    val accentColor = Color(accentPalette.hexColor)

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(OneUIDarkBackground)
            .testTag("settings_screen")
    ) {
        // One UI Top App Bar
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(
                onClick = onBack,
                modifier = Modifier.testTag("settings_back_button")
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "Back",
                    tint = OneUITextPrimary
                )
            }
            Spacer(modifier = Modifier.width(8.dp))
            Column {
                Text(
                    text = "Settings & Customization",
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    color = OneUITextPrimary
                )
                Text(
                    text = "Audio, appearance & offline data",
                    fontSize = 12.sp,
                    color = OneUITextSecondary
                )
            }
        }

        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Group 1: Audio & Playback
            item {
                SettingsSectionHeader(title = "AUDIO & PLAYBACK", accentColor = accentColor)
                Card(
                    shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(containerColor = OneUICardElevated)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        // Bitrate Selector
                        SettingsRow(
                            icon = Icons.Default.HighQuality,
                            title = "Streaming & Download Quality",
                            subtitle = bitrate,
                            onClick = { showBitrateDialog = true },
                            accentColor = accentColor
                        )

                        Spacer(modifier = Modifier.height(14.dp))

                        // Equalizer Shortcut
                        SettingsRow(
                            icon = Icons.Default.GraphicEq,
                            title = "System Equalizer & SoundAlive",
                            subtitle = "Launch Dolby Atmos / Device sound effects",
                            onClick = {
                                openSystemEqualizer(context, PlaybackManager.audioSessionId)
                            },
                            accentColor = accentColor
                        )

                        Spacer(modifier = Modifier.height(14.dp))

                        // Sleep Timer
                        SettingsRow(
                            icon = Icons.Default.AvTimer,
                            title = "Sleep Timer",
                            subtitle = if (sleepTimer != null) "Stops in $sleepTimer min" else "Off",
                            onClick = { showSleepTimerDialog = true },
                            accentColor = accentColor
                        )

                        Spacer(modifier = Modifier.height(14.dp))

                        // Crossfade Duration Slider
                        Column {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(
                                        imageVector = Icons.Default.Tune,
                                        contentDescription = null,
                                        tint = accentColor,
                                        modifier = Modifier.size(22.dp)
                                    )
                                    Spacer(Modifier.width(12.dp))
                                    Text(
                                        text = "Crossfade & Gapless",
                                        fontSize = 15.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        color = OneUITextPrimary
                                    )
                                }
                                Text(
                                    text = if (crossfadeSec == 0) "Off" else "${crossfadeSec}s",
                                    fontSize = 13.sp,
                                    color = accentColor,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                            Spacer(Modifier.height(4.dp))
                            Slider(
                                value = crossfadeSec.toFloat(),
                                onValueChange = { settingsManager.setCrossfadeSeconds(it.toInt()) },
                                valueRange = 0f..12f,
                                steps = 11,
                                colors = SliderDefaults.colors(
                                    thumbColor = accentColor,
                                    activeTrackColor = accentColor,
                                    inactiveTrackColor = OneUISurfaceDark
                                )
                            )
                        }
                    }
                }
            }

            // Group 2: Personalization & Themes
            item {
                SettingsSectionHeader(title = "PERSONALIZATION & THEME", accentColor = accentColor)
                Card(
                    shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(containerColor = OneUICardElevated)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        // Theme Engine
                        SettingsRow(
                            icon = Icons.Default.Palette,
                            title = "Theme Engine",
                            subtitle = themeMode.name.replace("_", " "),
                            onClick = { showThemeDialog = true },
                            accentColor = accentColor
                        )

                        Spacer(modifier = Modifier.height(14.dp))

                        // Accent Color
                        SettingsRow(
                            icon = Icons.Default.ColorLens,
                            title = "Accent Color",
                            subtitle = accentPalette.displayName,
                            onClick = { showAccentDialog = true },
                            accentColor = accentColor
                        )

                        Spacer(modifier = Modifier.height(14.dp))

                        // Player UI Style
                        SettingsRow(
                            icon = Icons.Default.ViewCarousel,
                            title = "Player Interface Style",
                            subtitle = if (playerStyle == PlayerUIStyle.SPOTIFY_FLUID) "Spotify Fluid (Fluid blur & synced lyrics)" else "Samsung Minimalist (Clean One UI reachability)",
                            onClick = {
                                val nextStyle = if (playerStyle == PlayerUIStyle.SPOTIFY_FLUID) PlayerUIStyle.SAMSUNG_MINIMALIST else PlayerUIStyle.SPOTIFY_FLUID
                                settingsManager.setPlayerStyle(nextStyle)
                            },
                            accentColor = accentColor
                        )

                        Spacer(modifier = Modifier.height(14.dp))

                        // Home Tab Customizer
                        SettingsRow(
                            icon = Icons.Default.Tab,
                            title = "Home Tab Customizer",
                            subtitle = "${visibleTabs.size} of 5 tabs visible",
                            onClick = { showTabCustomizerDialog = true },
                            accentColor = accentColor
                        )
                    }
                }
            }

            // Group 3: Data & Storage
            item {
                SettingsSectionHeader(title = "DATA & STORAGE", accentColor = accentColor)
                Card(
                    shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(containerColor = OneUICardElevated)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        // Download over Wi-Fi Only Switch
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(
                                modifier = Modifier.weight(1f),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Wifi,
                                    contentDescription = null,
                                    tint = accentColor,
                                    modifier = Modifier.size(22.dp)
                                )
                                Spacer(Modifier.width(12.dp))
                                Column {
                                    Text(
                                        text = "Download Over Wi-Fi Only",
                                        fontSize = 15.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        color = OneUITextPrimary
                                    )
                                    Text(
                                        text = "Avoid cellular data for lyrics and metadata",
                                        fontSize = 12.sp,
                                        color = OneUITextSecondary
                                    )
                                }
                            }
                            Switch(
                                checked = downloadOverWifiOnly,
                                onCheckedChange = { settingsManager.setDownloadOverWifiOnly(it) },
                                colors = SwitchDefaults.colors(
                                    checkedThumbColor = Color.White,
                                    checkedTrackColor = accentColor
                                )
                            )
                        }

                        Spacer(modifier = Modifier.height(16.dp))

                        // Clear Cache
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    settingsManager.clearCache()
                                    cacheSize = settingsManager.calculateCacheSizeMb()
                                    Toast.makeText(context, "Image & lyrics cache cleared", Toast.LENGTH_SHORT).show()
                                },
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = Icons.Default.CleaningServices,
                                    contentDescription = null,
                                    tint = NeonMint,
                                    modifier = Modifier.size(22.dp)
                                )
                                Spacer(Modifier.width(12.dp))
                                Column {
                                    Text(
                                        text = "Clear Image & Lyrics Cache",
                                        fontSize = 15.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        color = OneUITextPrimary
                                    )
                                    Text(
                                        text = "Cached size: $cacheSize",
                                        fontSize = 12.sp,
                                        color = OneUITextSecondary
                                    )
                                }
                            }
                            Text(
                                text = "Clear",
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold,
                                color = SpotifyGreen
                            )
                        }
                    }
                }
            }

            // About & Credits
            item {
                Card(
                    shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(containerColor = OneUISurfaceDark),
                    modifier = Modifier.padding(bottom = 24.dp)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Info, contentDescription = null, tint = OneUITextSecondary, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(8.dp))
                            Text("Aura Music v1.2.0 • Offline Local Audio Player", fontSize = 12.sp, color = OneUITextSecondary)
                        }
                        Spacer(Modifier.height(4.dp))
                        Text(
                            text = "Metadata & Synced Lyrics enriched via LRCLIB and Deezer public open APIs. Audio playback powered by AndroidX Media3 ExoPlayer.",
                            fontSize = 11.sp,
                            color = OneUITextSecondary.copy(alpha = 0.8f)
                        )
                    }
                }
            }
        }
    }

    // Bitrate Selector Dialog
    if (showBitrateDialog) {
        val options = listOf("96 kbps (Data Saver)", "160 kbps (Standard)", "320 kbps (Lossless/High-Res)")
        Dialog(onDismissRequest = { showBitrateDialog = false }) {
            Surface(
                shape = RoundedCornerShape(20.dp),
                color = OneUICardElevated,
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(20.dp)) {
                    Text("Streaming & Download Quality", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = OneUITextPrimary)
                    Spacer(Modifier.height(12.dp))
                    options.forEach { opt ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    settingsManager.setBitrate(opt.substringBefore(" "))
                                    showBitrateDialog = false
                                }
                                .padding(vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(
                                selected = bitrate.startsWith(opt.take(3)),
                                onClick = {
                                    settingsManager.setBitrate(opt.substringBefore(" "))
                                    showBitrateDialog = false
                                },
                                colors = RadioButtonDefaults.colors(selectedColor = accentColor)
                            )
                            Spacer(Modifier.width(8.dp))
                            Text(opt, fontSize = 14.sp, color = OneUITextPrimary)
                        }
                    }
                }
            }
        }
    }

    // Sleep Timer Dialog
    if (showSleepTimerDialog) {
        val timerOptions = listOf(
            Pair("Off", null),
            Pair("15 minutes", 15),
            Pair("30 minutes", 30),
            Pair("45 minutes", 45),
            Pair("60 minutes", 60),
            Pair("End of Track", 4)
        )
        Dialog(onDismissRequest = { showSleepTimerDialog = false }) {
            Surface(
                shape = RoundedCornerShape(20.dp),
                color = OneUICardElevated,
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(20.dp)) {
                    Text("Sleep Timer", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = OneUITextPrimary)
                    Spacer(Modifier.height(12.dp))
                    timerOptions.forEach { (label, minutes) ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    settingsManager.setSleepTimer(minutes)
                                    showSleepTimerDialog = false
                                    val msg = if (minutes != null) "Music will stop in $minutes minutes" else "Sleep timer disabled"
                                    Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
                                }
                                .padding(vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(
                                selected = sleepTimer == minutes,
                                onClick = {
                                    settingsManager.setSleepTimer(minutes)
                                    showSleepTimerDialog = false
                                },
                                colors = RadioButtonDefaults.colors(selectedColor = accentColor)
                            )
                            Spacer(Modifier.width(8.dp))
                            Text(label, fontSize = 14.sp, color = OneUITextPrimary)
                        }
                    }
                }
            }
        }
    }

    // Theme Mode Dialog
    if (showThemeDialog) {
        Dialog(onDismissRequest = { showThemeDialog = false }) {
            Surface(
                shape = RoundedCornerShape(20.dp),
                color = OneUICardElevated,
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(20.dp)) {
                    Text("Theme Style", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = OneUITextPrimary)
                    Spacer(Modifier.height(12.dp))
                    AppThemeMode.entries.forEach { mode ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    settingsManager.setThemeMode(mode)
                                    showThemeDialog = false
                                }
                                .padding(vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(
                                selected = themeMode == mode,
                                onClick = {
                                    settingsManager.setThemeMode(mode)
                                    showThemeDialog = false
                                },
                                colors = RadioButtonDefaults.colors(selectedColor = accentColor)
                            )
                            Spacer(Modifier.width(8.dp))
                            Text(mode.name.replace("_", " "), fontSize = 14.sp, color = OneUITextPrimary)
                        }
                    }
                }
            }
        }
    }

    // Accent Palette Dialog
    if (showAccentDialog) {
        Dialog(onDismissRequest = { showAccentDialog = false }) {
            Surface(
                shape = RoundedCornerShape(20.dp),
                color = OneUICardElevated,
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(20.dp)) {
                    Text("Accent Color Palette", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = OneUITextPrimary)
                    Spacer(Modifier.height(14.dp))
                    AccentPalette.entries.forEach { palette ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    settingsManager.setAccentPalette(palette)
                                    showAccentDialog = false
                                }
                                .padding(vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(28.dp)
                                    .clip(CircleShape)
                                    .background(Color(palette.hexColor))
                            )
                            Spacer(Modifier.width(14.dp))
                            Text(palette.displayName, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = OneUITextPrimary)
                            if (accentPalette == palette) {
                                Spacer(Modifier.weight(1f))
                                Icon(Icons.Default.Check, contentDescription = "Selected", tint = Color(palette.hexColor))
                            }
                        }
                    }
                }
            }
        }
    }

    // Home Tab Customizer Dialog
    if (showTabCustomizerDialog) {
        val allTabs = listOf("Tracks", "Playlists", "Albums", "Artists", "Folders")
        Dialog(onDismissRequest = { showTabCustomizerDialog = false }) {
            Surface(
                shape = RoundedCornerShape(20.dp),
                color = OneUICardElevated,
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(20.dp)) {
                    Text("Home Tab Visibility", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = OneUITextPrimary)
                    Text("Choose which tabs appear on the home library bar", fontSize = 12.sp, color = OneUITextSecondary)
                    Spacer(Modifier.height(12.dp))
                    allTabs.forEach { tabName ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { settingsManager.toggleTabVisibility(tabName) }
                                .padding(vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Checkbox(
                                checked = visibleTabs.contains(tabName),
                                onCheckedChange = { settingsManager.toggleTabVisibility(tabName) },
                                colors = CheckboxDefaults.colors(checkedColor = accentColor)
                            )
                            Spacer(Modifier.width(8.dp))
                            Text(tabName, fontSize = 14.sp, color = OneUITextPrimary)
                        }
                    }
                    Spacer(Modifier.height(12.dp))
                    TextButton(
                        onClick = { showTabCustomizerDialog = false },
                        modifier = Modifier.align(Alignment.End)
                    ) {
                        Text("Done", color = accentColor, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}

@Composable
private fun SettingsSectionHeader(title: String, accentColor: Color) {
    Text(
        text = title,
        fontSize = 12.sp,
        fontWeight = FontWeight.Bold,
        letterSpacing = 1.2.sp,
        color = accentColor,
        modifier = Modifier.padding(start = 6.dp, bottom = 6.dp, top = 4.dp)
    )
}

@Composable
private fun SettingsRow(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    subtitle: String,
    onClick: () -> Unit,
    accentColor: Color
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Row(
            modifier = Modifier.weight(1f),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = accentColor,
                modifier = Modifier.size(22.dp)
            )
            Spacer(Modifier.width(14.dp))
            Column {
                Text(
                    text = title,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = OneUITextPrimary
                )
                Text(
                    text = subtitle,
                    fontSize = 12.sp,
                    color = OneUITextSecondary
                )
            }
        }
    }
}

private fun openSystemEqualizer(context: Context, audioSessionId: Int) {
    try {
        val intent = Intent(AudioEffect.ACTION_DISPLAY_AUDIO_EFFECT_CONTROL_PANEL).apply {
            putExtra(AudioEffect.EXTRA_AUDIO_SESSION, audioSessionId)
            putExtra(AudioEffect.EXTRA_PACKAGE_NAME, context.packageName)
            putExtra(AudioEffect.EXTRA_CONTENT_TYPE, AudioEffect.CONTENT_TYPE_MUSIC)
        }
        val resolve = context.packageManager.resolveActivity(intent, 0)
        if (resolve != null) {
            context.startActivity(intent)
        } else {
            // Fallback to Android Sound Settings
            val soundIntent = Intent(android.provider.Settings.ACTION_SOUND_SETTINGS)
            context.startActivity(soundIntent)
        }
    } catch (e: Exception) {
        Toast.makeText(context, "Opening device sound settings...", Toast.LENGTH_SHORT).show()
        try {
            context.startActivity(Intent(android.provider.Settings.ACTION_SOUND_SETTINGS))
        } catch (_: Exception) {}
    }
}
