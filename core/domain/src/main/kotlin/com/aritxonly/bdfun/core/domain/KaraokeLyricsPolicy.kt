package com.aritxonly.bdfun.core.domain

/** 未唱到的字保留的亮度系数，和 BiliPai 的默认值一致。 */
const val KaraokeDimAlphaFactor = 0.36f

/**
 * 一次采样最多往前外推这么长的播放时间。
 *
 * 采样间隔约 1 秒，外推超过这个量说明播放器不再推进（缓冲/卡住），
 * 时钟就此收住，等真实位置追上来，而不是自己一路跑到前头再往回跳。
 */
const val LyricClockMaxLeadMs = 1_100L

/** 采样明显倒退（拖回前面、循环重播）：允许位置跟着回去。 */
const val LyricClockSeekBackMs = 800L

/** 采样明显前跳（拖到后面）：允许位置跟着过去。 */
const val LyricClockSeekAheadMs = 2_500L

/**
 * 逐字扫亮用的界面时钟。
 *
 * 播放器每秒才发布一次位置，直接拿它刷进度是一秒一跳，所以每帧按播放速度线性外推。
 * 关键约束是**输出只增不减**：采样比预测落后时（缓冲、播放器延迟、调度抖动）
 * 位置原地等着真实播放追上来，绝不回退；只有采样本身明显跳变才算 seek，才允许跳。
 * 旧实现靠"锚点永不移动 + 偏差超阈值就重设"来校正，1Hz 采样下几乎每秒都会越界，
 * 于是每秒回退一小段，看起来就是高光在原地抽搐。
 */
data class LyricClock(
    val anchorMs: Long,
    val anchorFrameNs: Long,
    val emittedMs: Long,
    val lastSampleMs: Long,
) {
    /** 锚点位置 + 到这一帧为止走过的播放时间；不超过已发布位置，也不超过外推上限。 */
    fun positionAt(frameNs: Long, speed: Float): Long {
        if (anchorFrameNs <= 0L) return emittedMs
        val rate = playbackRate(speed)
        val leadMs = (frameNs - anchorFrameNs).coerceAtLeast(0L) / 1_000_000f * rate
        val capped = leadMs.coerceAtMost(LyricClockMaxLeadMs * rate)
        return maxOf(emittedMs, anchorMs + capped.toLong())
    }

    /**
     * 用新采样推进时钟：
     * - 暂停 → 停在采样值；
     * - 采样没变（缓冲/卡住）→ 锚点压到采样上，输出原地不动；
     * - 采样跳变（seek、循环重播）→ 直接跟过去；
     * - 正常推进 → 锚点跟采样走，输出取「预测」与「采样」的较大者，永不回退。
     */
    fun advance(
        sampledMs: Long,
        frameNs: Long,
        playing: Boolean,
        speed: Float,
    ): LyricClock {
        val rate = playbackRate(speed)
        if (!playing) return LyricClock(sampledMs, frameNs, sampledMs, sampledMs)
        if (anchorFrameNs <= 0L) return LyricClock(sampledMs, frameNs, sampledMs, sampledMs)
        val delta = sampledMs - lastSampleMs
        if (delta == 0L) {
            // 播放器没推进：把锚点压到采样上，输出停在已发布位置，不再自己往前跑。
            return LyricClock(sampledMs, frameNs, emittedMs, sampledMs)
        }
        val elapsedPlayMs = (frameNs - anchorFrameNs).coerceAtLeast(0L) / 1_000_000f * rate
        val seekBack = delta < -(LyricClockSeekBackMs * rate)
        val seekAhead = delta > elapsedPlayMs + LyricClockSeekAheadMs * rate
        if (seekBack || seekAhead) return LyricClock(sampledMs, frameNs, sampledMs, sampledMs)
        val emitted = maxOf(positionAt(frameNs, speed), sampledMs)
        return LyricClock(sampledMs, frameNs, emitted, sampledMs)
    }

    companion object {
        /** 起始锚点：位置取首帧采样，帧时间为 0 表示下一帧再正式落地。 */
        fun of(sampledMs: Long): LyricClock = LyricClock(sampledMs, 0L, sampledMs, sampledMs)
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
