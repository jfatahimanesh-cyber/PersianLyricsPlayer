package com.example.persianlyrics.data.lrclib

object LrcParser {

    private val timestampRegex = Regex("""\[(\d{1,2}):(\d{2})(?:[.:](\d{1,3}))?]""")
    private val metadataTagRegex = Regex("""^\[(ti|ar|al|by|offset|re|ve|length):.*]$""", RegexOption.IGNORE_CASE)

    fun parseSyncedLrc(
        lrcContent: String?,
        offsetMs: Long = 0L
    ): List<LyricLine> {
        if (lrcContent.isNullOrBlank()) return emptyList()

        val rawEntries = mutableListOf<Pair<Long, String>>()

        lrcContent
            .replace("\r\n", "\n")
            .replace("\r", "\n")
            .lineSequence()
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .forEach { line ->
                if (metadataTagRegex.matches(line)) return@forEach

                val matches = timestampRegex.findAll(line).toList()
                if (matches.isEmpty()) return@forEach

                val lyricText = line.replace(timestampRegex, "").trim()
                if (lyricText.isEmpty()) return@forEach

                for (match in matches) {
                    val timeMs = matchToMillis(match)
                    if (timeMs != null) {
                        val adjustedTime = (timeMs + offsetMs).coerceAtLeast(0L)
                        rawEntries.add(adjustedTime to lyricText)
                    }
                }
            }

        return rawEntries
            .sortedBy { it.first }
            .mapIndexed { index, (timeMs, text) ->
                LyricLine(
                    id = index,
                    startTimeMs = timeMs,
                    text = text
                )
            }
    }

    fun parsePlainLyrics(plainText: String?): List<LyricLine> {
        if (plainText.isNullOrBlank()) return emptyList()

        return plainText
            .replace("\r\n", "\n")
            .replace("\r", "\n")
            .lineSequence()
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .mapIndexed { index, line ->
                LyricLine(
                    id = index,
                    startTimeMs = -1L,
                    text = line
                )
            }
            .toList()
    }

    fun findActiveLineIndex(lines: List<LyricLine>, currentPositionMs: Long): Int {
        if (lines.isEmpty()) return -1
        if (currentPositionMs < lines.first().startTimeMs) return -1

        var low = 0
        var high = lines.lastIndex
        var activeIndex = 0

        while (low <= high) {
            val mid = low + (high - low) / 2
            val midTime = lines[mid].startTimeMs
            if (midTime <= currentPositionMs) {
                activeIndex = mid
                low = mid + 1
            } else {
                high = mid - 1
            }
        }
        return activeIndex
    }

    private fun matchToMillis(match: MatchResult): Long? {
        val minutes = match.groupValues[1].toLongOrNull() ?: return null
        val seconds = match.groupValues[2].toLongOrNull() ?: return null
        val fractionStr = match.groupValues.getOrElse(3) { "0" }

        if (seconds >= 60L) return null

        val fractionMs = when (fractionStr.length) {
            0 -> 0L
            1 -> (fractionStr.toLongOrNull() ?: 0L) * 100L
            2 -> (fractionStr.toLongOrNull() ?: 0L) * 10L
            else -> fractionStr.take(3).padEnd(3, '0').toLongOrNull() ?: 0L
        }

        return (minutes * 60_000L) + (seconds * 1_000L) + fractionMs
    }
}
