package com.aritxonly.bdfun.core.domain

/** 歌词偏移上限：导入的 lrc 与视频通常只差几秒，给到 ±60s 足够，也防止误按跑飞。 */
const val LyricsOffsetLimitMs = 60_000L

/** 校准条上的步进档位（毫秒）：先粗后细，和 BiliPai 的两档手感一致。 */
val lyricsCalibrationSteps: List<Long> = listOf(-500L, -100L, 100L, 500L)

/** 在一次步进后的偏移量，夹在 ±[LyricsOffsetLimitMs] 之内。 */
fun calibrateLyricsOffset(currentMs: Long, deltaMs: Long): Long =
    (currentMs + deltaMs).coerceIn(-LyricsOffsetLimitMs, LyricsOffsetLimitMs)

/**
 * 偏移量的显示文本：0 说「未偏移」，其余带符号、保留一位小数。
 * 负值是歌词提前（原本的句子往后挪），正值是歌词延后。
 */
fun lyricsOffsetLabel(offsetMs: Long): String = when {
    offsetMs == 0L -> "未偏移"
    offsetMs > 0L -> "+%.1fs".format(offsetMs / 1000f)
    else -> "%.1fs".format(offsetMs / 1000f)
}

/**
 * 把一句歌词对齐到当前播放位置所需的偏移量。
 *
 * [withOffset] 的实现是 `fromMs + offset`，所以让某句正好从 [positionMs] 开始唱，
 * 偏移就取两者之差。用户在听到某句开口时点一下，整份歌词就整体平移过去。
 */
fun alignmentOffset(lineFromMs: Long, positionMs: Long): Long =
    (positionMs - lineFromMs).coerceIn(-LyricsOffsetLimitMs, LyricsOffsetLimitMs)
