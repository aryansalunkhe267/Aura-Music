package com.example.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import com.example.data.AppThemePreset

@Composable
fun PulseMusicTheme(
    preset: AppThemePreset = AppThemePreset.RADIOACTIVE_GREEN,
    content: @Composable () -> Unit
) {
    val colorScheme = darkColorScheme(
        primary = Color(preset.primaryAccentHex),
        onPrimary = Color.Black,
        primaryContainer = Color(preset.cardColorHex),
        onPrimaryContainer = Color(preset.primaryAccentHex),
        secondary = Color(preset.secondaryAccentHex),
        onSecondary = Color.Black,
        surface = Color(preset.surfaceColorHex),
        onSurface = Color(preset.textColorHex),
        background = Color(preset.backgroundColorHex),
        onBackground = Color(preset.textColorHex),
        surfaceVariant = Color(preset.cardColorHex),
        onSurfaceVariant = Color(0xFFA0AEC0),
        outline = Color(preset.primaryAccentHex).copy(alpha = 0.3f)
    )

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}

@Composable
fun MyApplicationTheme(
    content: @Composable () -> Unit
) {
    PulseMusicTheme(preset = AppThemePreset.RADIOACTIVE_GREEN, content = content)
}
