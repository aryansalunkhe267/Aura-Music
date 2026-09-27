package com.example.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.SongEntity
import kotlinx.coroutines.launch

/**
 * Interactive right-side Alphabetical Fast-Scroll Slider for Pulse Music.
 *
 * Requirements:
 * - Uses the EXACT same LazyListState instance as the parent LazyColumn.
 * - Computes a letter-to-index map Map<Char, Int> wrapped in remember(songs) to capture
 *   the exact first item position of each character (A-Z, #).
 * - Inside pointer input handlers (detectDragGestures and detectTapGestures), looks up the targeted
 *   letter in the map and invokes coroutineScope.launch { listState.scrollToItem(targetIndex) }.
 */
@Composable
fun FastScrollAlphabetIndexer(
    tracks: List<SongEntity>,
    listState: LazyListState,
    modifier: Modifier = Modifier
) {
    AlphabetFastScroller(
        songs = tracks,
        listState = listState,
        modifier = modifier
    )
}

@Composable
fun AlphabetFastScroller(
    songs: List<SongEntity>,
    listState: LazyListState,
    modifier: Modifier = Modifier
) {
    val haptic = LocalHapticFeedback.current
    val coroutineScope = rememberCoroutineScope()

    // Fixed alphabet set: special numbers/symbols # followed by A-Z
    val alphabet: List<Char> = remember {
        listOf('#') + ('A'..'Z').toList()
    }

    // Precompute an exact index map using remember(songs) to find the first song index for each character (A-Z, #)
    val letterIndexMap: Map<Char, Int> = remember(songs) {
        val map = mutableMapOf<Char, Int>()
        songs.forEachIndexed { index, song ->
            val trimmedTitle = song.title.trim()
            val firstChar = trimmedTitle.firstOrNull()?.uppercaseChar() ?: '#'
            val key = if (firstChar in 'A'..'Z') firstChar else '#'
            if (!map.containsKey(key)) {
                map[key] = index
            }
        }
        map
    }

    var isTouching by remember { mutableStateOf(false) }
    var selectedLetter by remember { mutableStateOf<Char?>(null) }
    var touchYRatio by remember { mutableFloatStateOf(0f) }
    var columnHeightPx by remember { mutableFloatStateOf(1f) }

    fun jumpToLetter(char: Char, force: Boolean = false) {
        if (force || selectedLetter != char) {
            selectedLetter = char
            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)

            // Resolve target index from precomputed map or nearest subsequent character
            var targetIndex = letterIndexMap[char]
            if (targetIndex == null) {
                if (char == '#') {
                    targetIndex = 0
                } else {
                    val charIndex = alphabet.indexOf(char)
                    if (charIndex >= 0) {
                        for (i in charIndex + 1 until alphabet.size) {
                            val nextChar = alphabet[i]
                            if (letterIndexMap.containsKey(nextChar)) {
                                targetIndex = letterIndexMap[nextChar]
                                break
                            }
                        }
                        if (targetIndex == null) {
                            for (i in charIndex - 1 downTo 0) {
                                val prevChar = alphabet[i]
                                if (letterIndexMap.containsKey(prevChar)) {
                                    targetIndex = letterIndexMap[prevChar]
                                    break
                                }
                            }
                        }
                    }
                }
            }

            if (targetIndex != null && targetIndex in songs.indices) {
                coroutineScope.launch {
                    // Call scrollToItem for instant jumping without animation conflicts
                    listState.scrollToItem(targetIndex)
                }
            }
        }
    }

    fun resolveCharFromY(y: Float): Char {
        val ratio = (y / columnHeightPx).coerceIn(0f, 1f)
        touchYRatio = ratio
        val idx = (ratio * alphabet.size).toInt().coerceIn(0, alphabet.size - 1)
        return alphabet[idx]
    }

    Box(
        modifier = modifier
            .fillMaxHeight()
            .width(42.dp)
            .padding(vertical = 8.dp)
            .testTag("alphabet_fast_scroller"),
        contentAlignment = Alignment.CenterEnd
    ) {
        // Floating large preview bubble showing current letter when dragging
        AnimatedVisibility(
            visible = isTouching && selectedLetter != null,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier.align(Alignment.CenterStart)
        ) {
            Box(
                modifier = Modifier
                    .offset {
                        val yOffset = ((touchYRatio - 0.5f) * columnHeightPx * 0.85f).toInt()
                        IntOffset(x = -48.dp.roundToPx(), y = yOffset)
                    }
                    .size(48.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primary),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = selectedLetter?.toString() ?: "",
                    fontSize = 22.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = Color.Black
                )
            }
        }

        // Pinned scroller bar with combined gesture detection
        Column(
            modifier = Modifier
                .width(26.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(Color.Black.copy(alpha = 0.50f))
                .padding(vertical = 4.dp)
                .onGloballyPositioned { coordinates ->
                    columnHeightPx = coordinates.size.height.toFloat().coerceAtLeast(1f)
                }
                .pointerInput(songs, alphabet, letterIndexMap) {
                    detectTapGestures(
                        onPress = { offset ->
                            isTouching = true
                            val char = resolveCharFromY(offset.y)
                            jumpToLetter(char, force = true)
                            tryAwaitRelease()
                            isTouching = false
                        },
                        onTap = { offset ->
                            val char = resolveCharFromY(offset.y)
                            jumpToLetter(char, force = true)
                        }
                    )
                }
                .pointerInput(songs, alphabet, letterIndexMap) {
                    detectDragGestures(
                        onDragStart = { offset ->
                            isTouching = true
                            val char = resolveCharFromY(offset.y)
                            jumpToLetter(char, force = true)
                        },
                        onDragEnd = { isTouching = false },
                        onDragCancel = { isTouching = false },
                        onDrag = { change, _ ->
                            change.consume()
                            val char = resolveCharFromY(change.position.y)
                            jumpToLetter(char)
                        }
                    )
                },
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            alphabet.forEach { char ->
                val hasSongs = letterIndexMap.containsKey(char)
                val isSelected = selectedLetter == char && isTouching

                Text(
                    text = char.toString(),
                    fontSize = 9.sp,
                    fontWeight = if (isSelected) FontWeight.ExtraBold else if (hasSongs) FontWeight.Bold else FontWeight.Normal,
                    color = when {
                        isSelected -> MaterialTheme.colorScheme.primary
                        hasSongs -> Color.White
                        else -> Color.White.copy(alpha = 0.35f)
                    },
                    modifier = Modifier.padding(vertical = 0.5.dp)
                )
            }
        }
    }
}
