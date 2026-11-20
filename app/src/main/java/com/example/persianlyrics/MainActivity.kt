package com.example.persianlyrics

import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.media3.common.MediaItem
import androidx.media3.exoplayer.ExoPlayer
import com.example.persianlyrics.data.gemini.GeminiLyricsTranslatorService
import com.example.persianlyrics.data.gemini.PersianLyricStyle
import com.example.persianlyrics.data.lrclib.LrclibLyricsService
import com.example.persianlyrics.data.lrclib.LyricLine
import com.example.persianlyrics.ui.lyrics.PlayerLyricsScreenExample
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.Locale

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    PersianLyricsAppScreen()
                }
            }
        }
    }
}

@Composable
private fun PersianLyricsAppScreen() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    val exoPlayer = remember {
        ExoPlayer.Builder(context).build()
    }

    DisposableEffect(Unit) {
        onDispose { exoPlayer.release() }
    }

    var trackTitle by remember { mutableStateOf("Bohemian Rhapsody") }
    var artistName by remember { mutableStateOf("Queen") }
    var geminiApiKey by remember { mutableStateOf("") }
    var statusMessage by remember { mutableStateOf("یک فایل صوتی انتخاب کنید یا عنوان آهنگ را بزنید.") }
    var isPlaying by remember { mutableStateOf(false) }
    var currentPosMs by remember { mutableLongStateOf(0L) }
    var durationMs by remember { mutableLongStateOf(1L) }
    var lyricsLines by remember { mutableStateOf<List<LyricLine>>(emptyList()) }

    val lrclibService = remember { LrclibLyricsService() }
    val translatorService = remember { GeminiLyricsTranslatorService() }

    val audioPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        if (uri != null) {
            exoPlayer.setMediaItem(MediaItem.fromUri(uri))
            exoPlayer.prepare()
            exoPlayer.play()
            isPlaying = true
            statusMessage = "فایل صوتی بارگذاری شد؛ لیریک را جستجو کنید."
        }
    }

    LaunchedEffect(exoPlayer) {
        while (true) {
            currentPosMs = exoPlayer.currentPosition.coerceAtLeast(0L)
            durationMs = exoPlayer.duration.takeIf { it > 0L } ?: 1L
            isPlaying = exoPlayer.isPlaying
            delay(200L)
        }
    }

    fun fetchAndTranslate() {
        scope.launch {
            statusMessage = "در حال جستجو در LRCLIB..."
            val lrclibResult = lrclibService.fetchLyricsForTrack(
                trackName = trackTitle,
                artistName = artistName
            ).getOrElse {
                statusMessage = "خطا در LRCLIB: ${it.message}"
                return@launch
            }

            val baseLines = when {
                lrclibResult != null && lrclibResult.syncedLines.isNotEmpty() -> lrclibResult.syncedLines
                lrclibResult != null && lrclibResult.plainLines.isNotEmpty() -> lrclibResult.plainLines
                else -> {
                    statusMessage = "لیریکی در LRCLIB یافت نشد."
                    return@launch
                }
            }

            if (geminiApiKey.isBlank()) {
                lyricsLines = baseLines
                statusMessage = "لیریک آماده از LRCLIB بارگذاری شد (بدون کلید Gemini)."
                return@launch
            }

            statusMessage = "در حال ترجمه فارسی با Gemini..."
            translatorService.translateLinesKeepTimestamps(
                lines = baseLines,
                apiKey = geminiApiKey,
                model = "gemini-2.5-flash",
                style = PersianLyricStyle.FLUENT_TRANSLATION
            ).onSuccess { translated ->
                lyricsLines = translated
                statusMessage = "لیریک فارسی هماهنگ آماده است."
            }.onFailure {
                lyricsLines = baseLines
                statusMessage = "خطا در ترجمه Gemini: ${it.message}"
            }
        }
    }

    Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedTextField(
                    value = trackTitle,
                    onValueChange = { trackTitle = it },
                    label = { Text("نام آهنگ") },
                    singleLine = true,
                    modifier = Modifier.weight(1f)
                )
                OutlinedTextField(
                    value = artistName,
                    onValueChange = { artistName = it },
                    label = { Text("خواننده") },
                    singleLine = true,
                    modifier = Modifier.weight(1f)
                )
            }

            OutlinedTextField(
                value = geminiApiKey,
                onValueChange = { geminiApiKey = it },
                label = { Text("Gemini API Key (اختیاری برای ترجمه)") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Button(onClick = { audioPicker.launch("audio/*") }) {
                    Text("انتخاب آهنگ")
                }
                OutlinedButton(
                    onClick = {
                        if (exoPlayer.isPlaying) exoPlayer.pause() else exoPlayer.play()
                    }
                ) {
                    Text(if (isPlaying) "توقف" else "پخش")
                }
                Button(onClick = { fetchAndTranslate() }) {
                    Text("دریافت لیریک")
                }
            }

            Slider(
                value = (currentPosMs.toFloat() / durationMs.toFloat()).coerceIn(0f, 1f),
                onValueChange = { fraction ->
                    exoPlayer.seekTo((fraction * durationMs).toLong())
                }
            )

            Text(
                text = "$statusMessage (${formatMs(currentPosMs)} / ${formatMs(durationMs)})",
                style = MaterialTheme.typography.bodySmall
            )

            PlayerLyricsScreenExample(
                lines = lyricsLines,
                currentPositionMs = currentPosMs,
                onSeekPlayerTo = { targetMs -> exoPlayer.seekTo(targetMs) },
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
            )
        }
    }
}

private fun formatMs(ms: Long): String {
    val totalSec = (ms.coerceAtLeast(0L)) / 1000L
    return String.format(Locale.US, "%02d:%02d", totalSec / 60L, totalSec % 60L)
}
