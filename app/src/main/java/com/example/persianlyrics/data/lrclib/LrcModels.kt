package com.example.persianlyrics.data.lrclib

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class LrclibTrackDto(
    val id: Long? = null,
    val trackName: String? = null,
    val artistName: String? = null,
    val albumName: String? = null,
    val duration: Double? = null,
    val instrumental: Boolean = false,
    val plainLyrics: String? = null,
    val syncedLyrics: String? = null,
    @SerialName("name") val legacyName: String? = null
) {
    val resolvedTrackTitle: String
        get() = trackName ?: legacyName ?: ""
}

data class LyricLine(
    val id: Int,
    val startTimeMs: Long,
    val text: String,
    val translation: String? = null
) {
    val isSynced: Boolean
        get() = startTimeMs >= 0L
}

data class LrclibLyricsResult(
    val trackId: Long?,
    val trackTitle: String,
    val artistName: String,
    val albumName: String?,
    val durationSec: Double?,
    val isInstrumental: Boolean,
    val syncedLines: List<LyricLine>,
    val plainLines: List<LyricLine>,
    val rawSyncedLrc: String?,
    val rawPlainLyrics: String?
) {
    val hasSyncedLyrics: Boolean
        get() = syncedLines.isNotEmpty()

    val hasAnyLyrics: Boolean
        get() = syncedLines.isNotEmpty() || plainLines.isNotEmpty()
}
