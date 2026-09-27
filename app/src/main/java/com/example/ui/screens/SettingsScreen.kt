package com.example.ui.screens

import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.Wallpaper
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.example.data.AppThemePreset
import com.example.data.SettingsManager
import com.example.ui.theme.OneUICardElevated
import com.example.ui.theme.OneUIDarkBackground
import com.example.ui.theme.OneUISurfaceDark
import com.example.ui.theme.OneUITextPrimary
import com.example.ui.theme.OneUITextSecondary

@Composable
fun SettingsScreen(
    settingsManager: SettingsManager,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val scrollState = rememberScrollState()

    val currentThemePreset by settingsManager.themePreset.collectAsStateWithLifecycle()
    val customWallpaperUri by settingsManager.customWallpaperUri.collectAsStateWithLifecycle()
    val wallpaperScrimAlpha by settingsManager.wallpaperScrimAlpha.collectAsStateWithLifecycle()
    val bitrate by settingsManager.bitrate.collectAsStateWithLifecycle()
    val crossfade by settingsManager.crossfadeSeconds.collectAsStateWithLifecycle()
    val wifiOnly by settingsManager.downloadOverWifiOnly.collectAsStateWithLifecycle()

    var cacheSize by remember { mutableStateOf(settingsManager.calculateCacheSizeMb()) }

    // Android zero-permission Photo Picker for Samsung Gallery wallpaper selection
    val galleryPickerLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        if (uri != null) {
            settingsManager.setCustomWallpaper(uri.toString())
            Toast.makeText(context, "Gallery background applied with 24dp blur", Toast.LENGTH_SHORT).show()
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(OneUIDarkBackground)
            .verticalScroll(scrollState)
            .padding(bottom = 120.dp)
            .testTag("settings_screen")
    ) {
        // One UI Header
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "Back",
                    tint = OneUITextPrimary
                )
            }
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = "Settings & Personalization",
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
                color = OneUITextPrimary
            )
        }

        // Section 1: Extended Preset Themes
        Column(modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Palette, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                Spacer(Modifier.width(8.dp))
                Text(
                    text = "Extended Preset Themes",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    color = OneUITextPrimary
                )
            }
            Spacer(Modifier.height(12.dp))

            AppThemePreset.values().forEach { preset ->
                val isSelected = currentThemePreset == preset
                Card(
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = if (isSelected) Color(preset.cardColorHex) else OneUICardElevated
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp)
                        .clickable { settingsManager.setThemePreset(preset) }
                        .then(
                            if (isSelected) Modifier.border(2.dp, Color(preset.primaryAccentHex), RoundedCornerShape(16.dp))
                            else Modifier
                        )
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            // Swatch previews
                            Box(
                                modifier = Modifier
                                    .size(36.dp)
                                    .clip(CircleShape)
                                    .background(Color(preset.backgroundColorHex))
                                    .border(1.dp, Color.White.copy(alpha = 0.3f), CircleShape),
                                contentAlignment = Alignment.Center
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(18.dp)
                                        .clip(CircleShape)
                                        .background(Color(preset.primaryAccentHex))
                                )
                            }
                            Spacer(Modifier.width(14.dp))
                            Column {
                                Text(
                                    text = preset.displayName,
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = OneUITextPrimary
                                )
                                Text(
                                    text = "Accent: #${preset.primaryAccentHex.toString(16).takeLast(6).uppercase()}",
                                    fontSize = 11.sp,
                                    color = OneUITextSecondary
                                )
                            }
                        }

                        if (isSelected) {
                            Icon(
                                imageVector = Icons.Default.Check,
                                contentDescription = "Active",
                                tint = Color(preset.primaryAccentHex)
                            )
                        }
                    }
                }
            }
        }

        Spacer(Modifier.height(16.dp))

        // Section 2: Custom Gallery Background
        Column(modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Wallpaper, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                Spacer(Modifier.width(8.dp))
                Text(
                    text = "Custom Gallery Background",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    color = OneUITextPrimary
                )
            }
            Spacer(Modifier.height(12.dp))

            Card(
                shape = RoundedCornerShape(18.dp),
                colors = CardDefaults.cardColors(containerColor = OneUICardElevated),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "Choose any photo from Samsung Gallery. Pulse Music applies a 24dp Gaussian blur and 40%-60% dark scrim overlay to maintain crisp UI contrast.",
                        fontSize = 13.sp,
                        color = OneUITextSecondary
                    )

                    Spacer(Modifier.height(14.dp))

                    if (customWallpaperUri != null) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            AsyncImage(
                                model = customWallpaperUri,
                                contentDescription = "Current wallpaper",
                                contentScale = ContentScale.Crop,
                                modifier = Modifier
                                    .size(60.dp)
                                    .clip(RoundedCornerShape(12.dp))
                            )
                            Spacer(Modifier.width(14.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text("Custom Wallpaper Active", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = OneUITextPrimary)
                                Text("Dynamic blur (24dp) enabled", fontSize = 12.sp, color = MaterialTheme.colorScheme.primary)
                            }
                            IconButton(onClick = { settingsManager.setCustomWallpaper(null) }) {
                                Icon(Icons.Default.Delete, contentDescription = "Remove Wallpaper", tint = Color(0xFFFF453A))
                            }
                        }

                        Spacer(Modifier.height(16.dp))

                        // Configurable Scrim Slider (40% to 60%)
                        Text(
                            text = "Dark Scrim Overlay: ${(wallpaperScrimAlpha * 100).toInt()}%",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = OneUITextPrimary
                        )
                        Slider(
                            value = wallpaperScrimAlpha,
                            onValueChange = { settingsManager.setWallpaperScrimAlpha(it) },
                            valueRange = 0.40f..0.60f,
                            colors = SliderDefaults.colors(
                                thumbColor = MaterialTheme.colorScheme.primary,
                                activeTrackColor = MaterialTheme.colorScheme.primary
                            )
                        )
                    }

                    Spacer(Modifier.height(10.dp))

                    Button(
                        onClick = {
                            galleryPickerLauncher.launch(
                                PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                            )
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Default.PhotoLibrary, contentDescription = null, tint = Color.Black)
                        Spacer(Modifier.width(8.dp))
                        Text(
                            text = if (customWallpaperUri == null) "Select Gallery Photo" else "Change Wallpaper",
                            color = Color.Black,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }

        Spacer(Modifier.height(16.dp))

        // Section 3: Audio Playback Quality & Cache
        Column(modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Tune, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                Spacer(Modifier.width(8.dp))
                Text(
                    text = "Audio & Storage",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    color = OneUITextPrimary
                )
            }
            Spacer(Modifier.height(12.dp))

            Card(
                shape = RoundedCornerShape(18.dp),
                colors = CardDefaults.cardColors(containerColor = OneUICardElevated),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text("Audio Bitrate", fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = OneUITextPrimary)
                            Text("Streaming & export resolution", fontSize = 12.sp, color = OneUITextSecondary)
                        }
                        Text(bitrate, fontSize = 14.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                    }

                    Spacer(Modifier.height(16.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text("Storage Cache", fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = OneUITextPrimary)
                            Text("Cached cover art and online queries", fontSize = 12.sp, color = OneUITextSecondary)
                        }
                        TextButton(
                            onClick = {
                                settingsManager.clearCache()
                                cacheSize = settingsManager.calculateCacheSizeMb()
                                Toast.makeText(context, "Cache cleared", Toast.LENGTH_SHORT).show()
                            }
                        ) {
                            Text("Clear ($cacheSize)", color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }
    }
}
