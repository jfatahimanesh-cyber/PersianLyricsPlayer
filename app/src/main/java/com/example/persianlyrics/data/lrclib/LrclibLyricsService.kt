package com.example.persianlyrics.data.lrclib

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class LrclibLyricsService(
    private val apiService: LrclibApiService = LrclibClientFactory.create()
) {

    suspend fun fetchLyricsForTrack(
        trackName: String,
        artistName: String,
        albumName: String? = null,
        durationSec: Int? = null,
        offsetMs: Long = 0L
    ): Result<LrclibLyricsResult?> = withContext(Dispatchers.IO) {
        runCatching {
            val cleanTitle = trackName.trim()
            val cleanArtist = artistName.trim()
            if (cleanTitle.isEmpty()) return@runCatching null

            val exactResponse = if (cleanArtist.isNotEmpty()) {
                apiService.getLyrics(
                    trackName = cleanTitle,
                    artistName = cleanArtist,
                    albumName = albumName?.takeIf { it.isNotBlank() },
                    durationSec = durationSec
                )
            } else {
                null
            }

            val bestDto = if (exactResponse != null && exactResponse.isSuccessful && exactResponse.body() != null) {
                exactResponse.body()
            } else {
                val searchResponse = apiService.searchLyrics(
                    query = "$cleanTitle $cleanArtist".trim(),
                    trackName = cleanTitle,
                    artistName = cleanArtist.takeIf { it.isNotEmpty() },
                    albumName = albumName?.takeIf { it.isNotBlank() }
                )

                if (!searchResponse.isSuccessful) {
                    if (searchResponse.code() == 404) return@runCatching null
                    error("LRCLIB search failed with HTTP ${searchResponse.code()}")
                }

                val candidates = searchResponse.body().orEmpty()
                selectBestCandidate(candidates, durationSec)
            } ?: return@runCatching null

            mapToResult(bestDto, offsetMs)
        }
    }

    private fun selectBestCandidate(
        candidates: List<LrclibTrackDto>,
        targetDurationSec: Int?
    ): LrclibTrackDto? {
        if (candidates.isEmpty()) return null

        return candidates.maxByOrNull { dto ->
            var score = 0
            if (!dto.syncedLyrics.isNullOrBlank()) score += 100
            if (!dto.plainLyrics.isNullOrBlank()) score += 30
            if (dto.instrumental) score += 10

            if (targetDurationSec != null && dto.duration != null) {
                val diff = kotlin.math.abs(dto.duration - targetDurationSec)
                score -= (diff * 2).toInt().coerceAtMost(60)
            }
            score
        }
    }

    private fun mapToResult(dto: LrclibTrackDto, offsetMs: Long): LrclibLyricsResult {
        val synced = LrcParser.parseSyncedLrc(dto.syncedLyrics, offsetMs)
        val plain = LrcParser.parsePlainLyrics(dto.plainLyrics)

        return LrclibLyricsResult(
            trackId = dto.id,
            trackTitle = dto.resolvedTrackTitle,
            artistName = dto.artistName.orEmpty(),
            albumName = dto.albumName,
            durationSec = dto.duration,
            isInstrumental = dto.instrumental,
            syncedLines = synced,
            plainLines = plain,
            rawSyncedLrc = dto.syncedLyrics,
            rawPlainLyrics = dto.plainLyrics
        )
    }
}
