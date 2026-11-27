package com.aritxonly.bdfun.feature.video.presentation.listen

import com.aritxonly.bdfun.core.domain.karaokeLineProgress
import com.aritxonly.bdfun.core.model.video.SubtitleCue

/** 歌词一行的纯数据投影：解析、对齐、偏移都在这层算完，界面只负责画。 */
data class LyricLine(
    val fromMs: Long,
    val toMs: Long,
    val text: String,
    val translation: String? = null,
)

data class Lyrics(val lines: List<LyricLine>) {

    fun indexAt(positionMs: Long): Int {
        if (lines.isEmpty()) return -1
        var found = -1
        for (i in lines.indices) {
            if (lines[i].fromMs <= positionMs) found = i else break
        }
        return found
    }

    /** 当前行内的进度 0..1；用于逐字填充的近似插值（字幕本身只有整句时间）。 */
    fun progressAt(index: Int, positionMs: Long): Float {
        val line = lines.getOrNull(index) ?: return 0f
        return karaokeLineProgress(positionMs, line.fromMs, line.toMs)
    }

    fun withOffset(offsetMs: Long): Lyrics {
        if (offsetMs == 0L) return this
        return copy(lines = lines.map {
            it.copy(
                fromMs = (it.fromMs + offsetMs).coerceAtLeast(0L),
                toMs = (it.toMs + offsetMs).coerceAtLeast(0L),
            )
        }.sortedBy { it.fromMs })
    }

    /** 副字幕按时间重叠贴到主字幕行上；不重叠就留空，不做模糊配对。 */
    fun withTranslation(other: Lyrics): Lyrics {
        if (other.lines.isEmpty() || lines.isEmpty()) return this
        return copy(lines = lines.map { line ->
            val hit = other.lines.firstOrNull { it.overlaps(line) }
            if (hit == null) line else line.copy(translation = hit.text)
        })
    }

    private fun LyricLine.overlaps(other: LyricLine): Boolean =
        other.fromMs < toMs && fromMs < other.toMs

    companion object {
        val Empty = Lyrics(emptyList())
    }
}

/** 解析字幕时只需要时间与文本，避免让纯解析器依赖 B 站模型。 */
data class SubtitleCueInput(val fromMs: Long, val toMs: Long, val text: String)

private const val LAST_LINE_HOLD_MS = 6_000L

fun lyricsFromCues(cues: List<SubtitleCueInput>): Lyrics {
    if (cues.isEmpty()) return Lyrics.Empty
    val sorted = cues.filter { it.text.isNotBlank() }.sortedBy { it.fromMs }
    if (sorted.isEmpty()) return Lyrics.Empty
    val lines = sorted.mapIndexed { index, cue ->
        // 收尾时间取「本句结束」和「下一句开始」的较小值：字幕的 toMs 常常被写长，
        // 不夹住会让最后一句一直高亮。
        val next = sorted.getOrNull(index + 1)
        val end = if (next != null) cue.toMs.coerceAtMost(next.fromMs).coerceAtLeast(cue.fromMs)
        else cue.toMs.coerceAtLeast(cue.fromMs + LAST_LINE_HOLD_MS)
        LyricLine(fromMs = cue.fromMs, toMs = end, text = cue.text.trim())
    }
    return Lyrics(lines)
}

fun lyricsFromSubtitles(cues: List<SubtitleCue>): Lyrics =
    lyricsFromCues(cues.map { SubtitleCueInput(it.fromMs, it.toMs, it.text) })

private val TIMESTAMP = Regex("\\[(\\d{1,3}):(\\d{1,2})(?:[.:](\\d{1,3}))?]")
private val OFFSET_TAG = Regex("^\\[offset:\\s*(-?\\d+)]$", RegexOption.IGNORE_CASE)

/**
 * 只认 [mm:ss.xx] 时间戳，其余 [ti:] / [ar:] 等元信息整行丢弃。
 * 一行多个时间戳会被拆成多行——LRC 的副歌常这么写。
 */
fun parseLrc(raw: String): Lyrics {
    if (raw.isBlank()) return Lyrics.Empty
    val parsed = mutableListOf<LyricLine>()
    var offset = 0L
    raw.lineSequence().forEach { line ->
        val trimmed = line.trim()
        if (trimmed.isEmpty()) return@forEach
        OFFSET_TAG.find(trimmed)?.let {
            offset = it.groupValues[1].toLongOrNull() ?: 0L
            return@forEach
        }
        val stamps = TIMESTAMP.findAll(trimmed).toList()
        if (stamps.isEmpty()) return@forEach
        val text = trimmed.substring(stamps.last().range.last + 1).trim()
        if (text.isEmpty()) return@forEach
        stamps.forEach { stamp ->
            parsed += LyricLine(
                fromMs = stamp.toMillis() + offset,
                toMs = 0L,
                text = text,
            )
        }
    }
    if (parsed.isEmpty()) return Lyrics.Empty
    val sorted = parsed.sortedBy { it.fromMs }
    val lines = sorted.mapIndexed { index, line ->
        val next = sorted.getOrNull(index + 1)
        line.copy(toMs = next?.fromMs ?: (line.fromMs + LAST_LINE_HOLD_MS))
    }
    return Lyrics(lines)
}

private fun MatchResult.toMillis(): Long {
    val minutes = groupValues[1].toLong()
    val seconds = groupValues[2].toLong()
    val fractionText = groupValues.getOrNull(3).orEmpty()
    // 两位是百分秒、三位是毫秒，都按比例折成毫秒。
    val fraction = when (fractionText.length) {
        0 -> 0L
        1 -> fractionText.toLong() * 100L
        2 -> fractionText.toLong() * 10L
        else -> fractionText.take(3).toLong()
    }
    return minutes * 60_000L + seconds * 1_000L + fraction
}