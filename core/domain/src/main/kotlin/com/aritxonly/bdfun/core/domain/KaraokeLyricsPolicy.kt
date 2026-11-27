package com.aritxonly.bdfun.core.domain

import kotlin.math.abs

/** 未唱到的字保留的亮度系数，和 BiliPai 的默认值一致。 */
const val KaraokeDimAlphaFactor = 0.36f

/** 采样与界面时钟差得这么多，就当成拖动进度条／卡住了：重设锚点。 */
const val LyricClockSnapMs = 700L

/** 播放中还连续这么久没拿到新采样，说明播放器卡住了，界面时钟跟着冻结。 */
const val LyricClockStallMs = 1_600L

/**
 * 逐字扫亮用的界面时钟。
 *
 * 播放器每秒才发布一次位置，直接拿它刷进度是一秒一跳。这里不逐帧累加增量，
 * 而是记住一个锚点（位置 + 当时的帧时间），位置一律由单调时钟线性外推：
 * 只有偏差大到像 seek 或卡顿时才换锚点。锚点不动，进度就只会往前走，不会原地抽搐。
 */
data class LyricClock(
    val anchorMs: Long,
    val anchorFrameNs: Long,
    val sampleMs: Long,
    val sampleFrameNs: Long,
) {
    /** 锚点位置 + 到这一帧为止走过的播放时间。 */
    fun positionAt(frameNs: Long, speed: Float): Long {
        val rate = speed.takeIf { it.isFinite() && it > 0f } ?: 1f
        val elapsedMs = (frameNs - anchorFrameNs).coerceAtLeast(0L) / 1_000_000f
        return anchorMs + (elapsedMs * rate).toLong()
    }

    /**
     * 用新采样校正锚点：
     * - 暂停 → 直接停在采样值；
     * - 采样与预测差得远（seek）或长时间不推进（卡住）→ 重设锚点；
     * - 其余情况锚点原样保留，界面时钟继续按帧线性外推。
     */
    fun advance(
        sampledMs: Long,
        frameNs: Long,
        playing: Boolean,
        speed: Float,
        snapThresholdMs: Long = LyricClockSnapMs,
        stallMs: Long = LyricClockStallMs,
    ): LyricClock {
        if (!playing) return LyricClock(sampledMs, frameNs, sampledMs, frameNs)
        if (anchorFrameNs <= 0L) return LyricClock(sampledMs, frameNs, sampledMs, frameNs)
        val fresh = sampledMs != sampleMs
        val nextSampleMs = if (fresh) sampledMs else sampleMs
        val nextSampleFrameNs = if (fresh) frameNs else sampleFrameNs
        val stalled = !fresh && frameNs - nextSampleFrameNs > stallMs * 1_000_000L
        if (stalled) return LyricClock(sampledMs, frameNs, nextSampleMs, nextSampleFrameNs)
        val drift = sampledMs - positionAt(frameNs, speed)
        if (abs(drift) > snapThresholdMs) {
            return LyricClock(sampledMs, frameNs, nextSampleMs, nextSampleFrameNs)
        }
        return LyricClock(anchorMs, anchorFrameNs, nextSampleMs, nextSampleFrameNs)
    }

    companion object {
        /** 起始锚点：位置取首帧采样，帧时间为 0 表示下一帧再正式落地。 */
        fun of(sampledMs: Long): LyricClock = LyricClock(sampledMs, 0L, sampledMs, 0L)
    }
}

/** 一行歌词的扫亮进度：字幕只有整句时间，行内线性插值近似「唱到哪儿」。 */
fun karaokeLineProgress(positionMs: Long, fromMs: Long, toMs: Long): Float {
    val span = (toMs - fromMs).coerceAtLeast(1L)
    return ((positionMs - fromMs).toFloat() / span).coerceIn(0f, 1f)
}

/**
 * 已唱到的字数（可以是小数：整数部分整字点亮，小数部分把过渡字画一半）。
 *
 * 按字数而不是像素算边界，扫描边才会正好落在字与字之间——这正是「逐字」和
 * 「整行慢慢变亮」的区别。
 */
fun karaokeLitGlyphCount(progress: Float, glyphCount: Int): Float {
    if (glyphCount <= 0) return 0f
    return progress.coerceIn(0f, 1f) * glyphCount
}

/**
 * 非当前行的亮度：离当前行越远越暗，但暗到基线的 [floor] 倍就停住，
 * 免得屏幕两端的歌词彻底看不见。
 */
fun karaokeInactiveAlpha(
    distance: Int,
    base: Float = KaraokeDimAlphaFactor,
    step: Float = 0.12f,
    floor: Float = 0.45f,
): Float {
    val steps = (distance.coerceAtLeast(1) - 1).toFloat()
    return (base * (1f - steps * step)).coerceAtLeast(base * floor)
}
