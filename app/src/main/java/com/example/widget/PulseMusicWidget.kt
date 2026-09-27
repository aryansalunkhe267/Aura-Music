package com.example.widget

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.Image
import androidx.glance.ImageProvider
import androidx.glance.appwidget.action.actionStartActivity
import androidx.glance.appwidget.action.actionStartService
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetManager
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.provideContent
import androidx.glance.background
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.layout.size
import androidx.glance.layout.width
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider
import com.example.MainActivity
import com.example.R
import com.example.playback.MusicService
import com.example.playback.PlaybackManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * Samsung One UI styled 4x1 & 4x2 Home Screen Widget built with Jetpack Glance.
 * Provides instant One UI rounded card aesthetic, artwork thumbnail, track metadata,
 * and direct service PendingIntent playback controls.
 */
class PulseMusicWidget : GlanceAppWidget() {

    companion object {
        fun notifyWidgetUpdate(context: Context) {
            CoroutineScope(Dispatchers.IO).launch {
                try {
                    val manager = GlanceAppWidgetManager(context)
                    val glanceIds = manager.getGlanceIds(PulseMusicWidget::class.java)
                    for (glanceId in glanceIds) {
                        PulseMusicWidget().update(context, glanceId)
                    }
                } catch (_: Exception) {}
            }
        }
    }

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        provideContent {
            val song = PlaybackManager.currentSong.value
            val isPlaying = PlaybackManager.isPlaying.value

            val title = song?.title ?: "Pulse Music"
            val artist = song?.artist ?: "Tap to play your library"

            val openAppIntent = Intent(context, MainActivity::class.java)

            val playPauseIntent = Intent(context, MusicService::class.java).apply {
                action = if (isPlaying) MusicService.ACTION_PAUSE else MusicService.ACTION_PLAY
            }
            val prevIntent = Intent(context, MusicService::class.java).apply {
                action = MusicService.ACTION_PREV
            }
            val nextIntent = Intent(context, MusicService::class.java).apply {
                action = MusicService.ACTION_NEXT
            }

            // Samsung One UI 24.dp rounded surface card
            Box(
                modifier = GlanceModifier
                    .fillMaxSize()
                    .cornerRadius(24.dp)
                    .background(ColorProvider(Color(0xFF14161C)))
                    .padding(12.dp)
                    .clickable(actionStartActivity(openAppIntent))
            ) {
                Row(
                    modifier = GlanceModifier.fillMaxSize(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Artwork Thumbnail or Pulse Logo
                    Box(
                        modifier = GlanceModifier
                            .size(54.dp)
                            .cornerRadius(14.dp)
                            .background(ColorProvider(Color(0xFF222834))),
                        contentAlignment = Alignment.Center
                    ) {
                        var loadedBitmap: Bitmap? = null
                        if (song != null && !song.albumArtUri.isNullOrBlank()) {
                            try {
                                val uri = Uri.parse(song.albumArtUri)
                                context.contentResolver.openInputStream(uri)?.use { stream ->
                                    loadedBitmap = BitmapFactory.decodeStream(stream)
                                }
                            } catch (_: Exception) {}
                        }

                        if (loadedBitmap != null) {
                            Image(
                                provider = ImageProvider(loadedBitmap!!),
                                contentDescription = "Artwork",
                                modifier = GlanceModifier.fillMaxSize().cornerRadius(14.dp)
                            )
                        } else {
                            Image(
                                provider = ImageProvider(R.drawable.ic_launcher_foreground),
                                contentDescription = "Pulse Music Icon",
                                modifier = GlanceModifier.size(42.dp)
                            )
                        }
                    }

                    Spacer(modifier = GlanceModifier.width(12.dp))

                    // Song Info
                    Column(
                        modifier = GlanceModifier.defaultWeight()
                    ) {
                        Text(
                            text = title,
                            style = TextStyle(
                                color = ColorProvider(Color.White),
                                fontSize = 15.sp,
                                fontWeight = FontWeight.Bold
                            ),
                            maxLines = 1
                        )
                        Spacer(modifier = GlanceModifier.height(2.dp))
                        Text(
                            text = artist,
                            style = TextStyle(
                                color = ColorProvider(Color(0xFF8E9BAE)),
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Normal
                            ),
                            maxLines = 1
                        )
                    }

                    Spacer(modifier = GlanceModifier.width(8.dp))

                    // One UI Transport Buttons
                    Row(
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Previous Button
                        Box(
                            modifier = GlanceModifier
                                .size(38.dp)
                                .cornerRadius(19.dp)
                                .clickable(actionStartService(prevIntent)),
                            contentAlignment = Alignment.Center
                        ) {
                            Image(
                                provider = ImageProvider(android.R.drawable.ic_media_previous),
                                contentDescription = "Previous",
                                modifier = GlanceModifier.size(24.dp)
                            )
                        }

                        Spacer(modifier = GlanceModifier.width(4.dp))

                        // Play/Pause Action
                        Box(
                            modifier = GlanceModifier
                                .size(44.dp)
                                .cornerRadius(22.dp)
                                .background(ColorProvider(Color(0xFF1DB954)))
                                .clickable(actionStartService(playPauseIntent)),
                            contentAlignment = Alignment.Center
                        ) {
                            Image(
                                provider = ImageProvider(
                                    if (isPlaying) android.R.drawable.ic_media_pause else android.R.drawable.ic_media_play
                                ),
                                contentDescription = if (isPlaying) "Pause" else "Play",
                                modifier = GlanceModifier.size(26.dp)
                            )
                        }

                        Spacer(modifier = GlanceModifier.width(4.dp))

                        // Next Button
                        Box(
                            modifier = GlanceModifier
                                .size(38.dp)
                                .cornerRadius(19.dp)
                                .clickable(actionStartService(nextIntent)),
                            contentAlignment = Alignment.Center
                        ) {
                            Image(
                                provider = ImageProvider(android.R.drawable.ic_media_next),
                                contentDescription = "Next",
                                modifier = GlanceModifier.size(24.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}
