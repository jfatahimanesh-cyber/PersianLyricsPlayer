package com.example.persianlyrics.ui.lyrics

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Remove
import androidx.compose.material.icons.rounded.RestartAlt
import androidx.compose.material.icons.rounded.Subtitles
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.persianlyrics.data.lrclib.LrcParser
import com.example.persianlyrics.data.lrclib.LyricLine
import kotlinx.coroutines.delay
import java.util.Locale

private const val OFFSET_STEP_MS = 500L

@Composable
fun SyncedLyricsView(
    lines: List<LyricLine>,
    currentPositionMs: Long,
    offsetMs: Long,
    onSeekTo: (Long) -> Unit,
    onOffsetChange: (Long) -> Unit,
    modifier: Modifier = Modifier,
    showOriginalText: Boolean = true,
    onToggleOriginalText: ((Boolean) -> Unit)? = null
) {
    val effectivePositionMs = remember(currentPositionMs, offsetMs) {
        (currentPositionMs - offsetMs).coerceAtLeast(0L)
    }

    val activeIndex = remember(lines, effectivePositionMs) {
        LrcParser.findActiveLineIndex(lines, effectivePositionMs)
    }

    val listState = rememberLazyListState()
    var isUserScrolling by remember { mutableStateOf(false) }

    LaunchedEffect(listState.isScrollInProgress) {
        if (listState.isScrollInProgress) {
            isUserScrolling = true
        } else if (isUserScrolling) {
            delay(3000L)
            isUserScrolling = false
        }
    }

    LaunchedEffect(activeIndex, isUserScrolling) {
        if (!isUserScrolling && activeIndex >= 0 && activeIndex < lines.size) {
            val viewportHeight = listState.layoutInfo.viewportSize.height
            val centerOffset = if (viewportHeight > 0) -(viewportHeight / 3) else 0
            listState.animateScrollToItem(
                index = activeIndex,
                scrollOffset = centerOffset
            )
        }
    }

    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
        Column(
            modifier = modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
        ) {
            SyncOffsetControlBar(
                offsetMs = offsetMs,
                showOriginalText = showOriginalText,
                onDecreaseOffset = { onOffsetChange(offsetMs - OFFSET_STEP_MS) },
                onIncreaseOffset = { onOffsetChange(offsetMs + OFFSET_STEP_MS) },
                onResetOffset = { onOffsetChange(0L) },
                onToggleOriginal = onToggleOriginalText?.let { callback ->
                    { callback(!showOriginalText) }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 10.dp)
            )

            if (lines.isEmpty()) {
                EmptyLyricsState(modifier = Modifier.weight(1f))
            } else {
                LazyColumn(
                    state = listState,
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    contentPadding = PaddingValues(
                        start = 20.dp,
                        end = 20.dp,
                        top = 36.dp,
                        bottom = 120.dp
                    ),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    itemsIndexed(
                        items = lines,
                        key = { index, line -> "${line.id}_${line.startTimeMs}_$index" }
                    ) { index, line ->
                        val isActive = index == activeIndex
                        val isPast = activeIndex >= 0 && index < activeIndex

                        LyricLineRow(
                            line = line,
                            isActive = isActive,
                            isPast = isPast,
                            showOriginalText = showOriginalText,
                            onClick = {
                                if (line.isSynced) {
                                    val targetSeekMs = (line.startTimeMs + offsetMs).coerceAtLeast(0L)
                                    onSeekTo(targetSeekMs)
                                    isUserScrolling = false
                                }
                            }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun SyncOffsetControlBar(
    offsetMs: Long,
    showOriginalText: Boolean,
    onDecreaseOffset: () -> Unit,
    onIncreaseOffset: () -> Unit,
    onResetOffset: () -> Unit,
    onToggleOriginal: (() -> Unit)?,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        tonalElevation = 2.dp
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OffsetStepButton(
                    icon = Icons.Rounded.Remove,
                    contentDescription = "عقب بردن لیریک ۰.۵ ثانیه",
                    onClick = onDecreaseOffset
                )

                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = "هماهنگی زمان",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = formatOffsetLabel(offsetMs),
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = if (offsetMs == 0L) {
                            MaterialTheme.colorScheme.onSurface
                        } else {
                            MaterialTheme.colorScheme.primary
                        }
                    )
                }

                OffsetStepButton(
                    icon = Icons.Rounded.Add,
                    contentDescription = "جلو بردن لیریک ۰.۵ ثانیه",
                    onClick = onIncreaseOffset
                )

                if (offsetMs != 0L) {
                    Surface(
                        modifier = Modifier
                            .size(32.dp)
                            .clip(CircleShape)
                            .clickable(onClick = onResetOffset),
                        shape = CircleShape,
                        color = MaterialTheme.colorScheme.secondaryContainer
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = Icons.Rounded.RestartAlt,
                                contentDescription = "ریست آفست زمانی",
                                modifier = Modifier.size(18.dp),
                                tint = MaterialTheme.colorScheme.onSecondaryContainer
                            )
                        }
                    }
                }
            }

            if (onToggleOriginal != null) {
                FilterChip(
                    selected = showOriginalText,
                    onClick = onToggleOriginal,
                    label = {
                        Text(
                            text = "متن اصلی",
                            style = MaterialTheme.typography.labelMedium
                        )
                    },
                    leadingIcon = {
                        Icon(
                            imageVector = Icons.Rounded.Subtitles,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                )
            }
        }
    }
}

@Composable
private fun OffsetStepButton(
    icon: ImageVector,
    contentDescription: String,
    onClick: () -> Unit
) {
    Surface(
        modifier = Modifier
            .size(36.dp)
            .clip(CircleShape)
            .clickable(onClick = onClick),
        shape = CircleShape,
        color = MaterialTheme.colorScheme.primaryContainer
    ) {
        Box(contentAlignment = Alignment.Center) {
            Icon(
                imageVector = icon,
                contentDescription = contentDescription,
                modifier = Modifier.size(20.dp),
                tint = MaterialTheme.colorScheme.onPrimaryContainer
            )
        }
    }
}

@Composable
private fun LyricLineRow(
    line: LyricLine,
    isActive: Boolean,
    isPast: Boolean,
    showOriginalText: Boolean,
    onClick: () -> Unit
) {
    val targetAlpha by animateFloatAsState(
        targetValue = when {
            isActive -> 1f
            isPast -> 0.55f
            else -> 0.72f
        },
        animationSpec = spring(stiffness = Spring.StiffnessMediumLow),
        label = "lineAlpha"
    )

    val containerColor by animateColorAsState(
        targetValue = if (isActive) {
            MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.45f)
        } else {
            Color.Transparent
        },
        label = "lineContainerColor"
    )

    val primaryTextColor by animateColorAsState(
        targetValue = if (isActive) {
            MaterialTheme.colorScheme.primary
        } else {
            MaterialTheme.colorScheme.onBackground
        },
        label = "primaryTextColor"
    )

    val persianLine = line.translation?.takeIf { it.isNotBlank() } ?: line.text
    val shouldShowOriginal = showOriginalText &&
        !line.translation.isNullOrBlank() &&
        line.translation != line.text

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .alpha(targetAlpha)
            .clip(RoundedCornerShape(14.dp))
            .clickable(enabled = line.isSynced, onClick = onClick),
        shape = RoundedCornerShape(14.dp),
        color = containerColor
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = if (isActive) 12.dp else 8.dp),
            horizontalAlignment = Alignment.Start
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = persianLine,
                    modifier = Modifier.weight(1f),
                    style = if (isActive) {
                        MaterialTheme.typography.titleMedium.copy(
                            fontSize = 20.sp,
                            lineHeight = 30.sp
                        )
                    } else {
                        MaterialTheme.typography.bodyLarge.copy(
                            fontSize = 16.sp,
                            lineHeight = 24.sp
                        )
                    },
                    fontWeight = if (isActive) FontWeight.ExtraBold else FontWeight.Medium,
                    color = primaryTextColor,
                    textAlign = TextAlign.Start
                )

                if (line.isSynced) {
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = formatTimestamp(line.startTimeMs),
                        style = MaterialTheme.typography.labelSmall,
                        color = if (isActive) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        }
                    )
                }
            }

            if (shouldShowOriginal) {
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = line.text,
                    modifier = Modifier.fillMaxWidth(),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Left
                )
            }
        }
    }
}

@Composable
private fun EmptyLyricsState(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .padding(24.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = "لیریک هماهنگی برای این آهنگ در دسترس نیست.",
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )
    }
}

private fun formatOffsetLabel(offsetMs: Long): String {
    val seconds = offsetMs / 1000.0
    return when {
        offsetMs > 0L -> String.format(Locale.US, "+%.1fs", seconds)
        offsetMs < 0L -> String.format(Locale.US, "%.1fs", seconds)
        else -> "0.0s"
    }
}

private fun formatTimestamp(timeMs: Long): String {
    val safeMs = timeMs.coerceAtLeast(0L)
    val totalSeconds = safeMs / 1000L
    val minutes = totalSeconds / 60L
    val seconds = totalSeconds % 60L
    return String.format(Locale.US, "%02d:%02d", minutes, seconds)
}
