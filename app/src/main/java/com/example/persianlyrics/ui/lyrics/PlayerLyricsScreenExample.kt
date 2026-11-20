package com.example.persianlyrics.ui.lyrics

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import com.example.persianlyrics.data.lrclib.LyricLine

@Composable
fun PlayerLyricsScreenExample(
    lines: List<LyricLine>,
    currentPositionMs: Long,
    onSeekPlayerTo: (Long) -> Unit,
    modifier: Modifier = Modifier
) {
    var offsetMs by remember { mutableLongStateOf(0L) }
    var showOriginal by remember { mutableStateOf(true) }

    SyncedLyricsView(
        lines = lines,
        currentPositionMs = currentPositionMs,
        offsetMs = offsetMs,
        onSeekTo = onSeekPlayerTo,
        onOffsetChange = { newOffset -> offsetMs = newOffset },
        showOriginalText = showOriginal,
        onToggleOriginalText = { showOriginal = it },
        modifier = modifier
    )
}
