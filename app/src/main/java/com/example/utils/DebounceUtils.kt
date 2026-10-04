package com.example.utils

import android.os.SystemClock
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember

/**
 * Throttles rapid button clicks (300ms threshold) to prevent ExoPlayer / MediaSession
 * desync, race conditions, and animation glitches when tapped rapidly.
 */
class DebouncedClickHandler(private val debounceMs: Long = 300L) {
    private var lastClickTime = 0L

    fun processClick(action: () -> Unit) {
        val now = SystemClock.elapsedRealtime()
        if (now - lastClickTime >= debounceMs) {
            lastClickTime = now
            action()
        }
    }
}

@Composable
fun rememberDebouncedClick(debounceMs: Long = 300L, onClick: () -> Unit): () -> Unit {
    val debouncer = remember { DebouncedClickHandler(debounceMs) }
    return { debouncer.processClick(onClick) }
}
