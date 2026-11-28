package com.aritxonly.bdfun.core.domain

import kotlin.math.roundToLong

/** 未唱到的字保留的亮度系数，和 BiliPai 的默认值一致。 */
const val KaraokeDimAlphaFactor = 0.36f

/** 采样与逐帧预测的偏差超过这个量，判定为 seek（拖进度条、换集、循环重播），位置直接跟采样。 */
const val LyricSeekThresholdMs = 1_500L

/** 单帧最多补多少毫秒的偏差：保证一帧的推进远小于一个字，不会一下亮过好几个字。 */
const val LyricCatchUpStepLimitMs = 28L

/** 每帧把剩余偏差的 1/4 补上，几帧内平滑收敛到采样，而不是当帧跳到位。 */
const val LyricCatchUpDivisor = 4L

/** 采样一直不更新超过这个时长，说明播放器没在推进（缓冲），位置原地冻住等声音回来。 */
const val LyricStallHoldMs = 1_200L

/** 帧间隔超过这个值（掉帧、切后台回来）就不插值了，直接对到采样。 */
const val LyricFrameGapLimitMs = 1_500L

/**
 * 逐字扫亮用的界面时钟。
 *
 * 播放器每秒才发布一次位置，直接拿它刷进度是一秒一跳。这里每帧把这一帧真实走过的播放
 * 时间加上去（[step]），采样只用来校正：偏差小就每帧补一点慢慢收敛，偏差大到像 seek 才
 * 直接跟过去。
 *
 * 跟上一版的差别在于**单帧位移有上限**。上一版要求输出单调、锚点只在采样时移动，结果
 * 同一秒内的每一帧都被压回采样值，位置整秒不动、下一秒再跳一千毫秒——就是「卡顿、好几个
 * 词一起亮」。现在正常播放时每帧只走一帧的时间，采样超前时最多再补
 * [LyricCatchUpStepLimitMs]，一帧永远推进不了半个字。
 */
data class LyricClock(
    val displayMs: Long,
    val lastSampleMs: Long,
    val msWithoutSample: Long = 0L,
) {
    /**
     * 走一帧。[frameDeltaMs] 是这一帧与上一帧的真实间隔，[sampledMs] 是播放器最近一次
     * 发布的位置。返回值里的 [displayMs] 就是这一帧该显示的位置。
     */
    fun step(sampledMs: Long, frameDeltaMs: Long, speed: Float): LyricClock {
        if (frameDeltaMs > LyricFrameGapLimitMs) return LyricClock(sampledMs, sampledMs)
        val rate = playbackRate(speed)
        val dt = frameDeltaMs.coerceAtLeast(0L)
        val fresh = sampledMs != lastSampleMs
        val withoutSample = if (fresh) 0L else msWithoutSample + dt
        // 采样停了：播放器没在推进，位置停在原处等声音回来，别自己跑到前面去。
        if (!fresh && withoutSample >= LyricStallHoldMs) {
            return LyricClock(displayMs, sampledMs, withoutSample)
        }
        val predicted = displayMs + (dt * rate).roundToLong()
        val drift = sampledMs - predicted
        val next = when {
            drift > LyricSeekThresholdMs || drift < -LyricSeekThresholdMs -> sampledMs
            drift <= 0L -> predicted
            else -> predicted + minOf(drift / LyricCatchUpDivisor, LyricCatchUpStepLimitMs)
        }
        return LyricClock(next, sampledMs, withoutSample)
    }

    companion object {
        /** 起手位置取当前已显示的位置，采样留作第一次校正的参照。 */
        fun of(displayMs: Long, sampledMs: Long): LyricClock = LyricClock(displayMs, sampledMs, 0L)
    }
}

private fun playbackRate(speed: Float): Float =
    speed.takeIf { it.isFinite() && it > 0f } ?: 1f

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
