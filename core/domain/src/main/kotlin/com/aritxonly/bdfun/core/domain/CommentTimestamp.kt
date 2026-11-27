package com.aritxonly.bdfun.core.domain

/** 评论正文里能被点击跳转的时间点。 */
data class CommentTimestamp(val start: Int, val end: Int, val positionMs: Long)

/**
 * 与弹幕跳转共用同一套写法：`1:00` / `01:23` / `1:00:00`。
 * 秒必须两位且 00-59；带小时段时分钟不能到 60（`1:67:03` 不是合法时间）。
 * 前后不能再接数字或冒号，避免把 `1234:56`、`1:00:00:00` 这类串切出一段。
 */
internal val textTimestampPattern = Regex("""(?<![\d:])(?:(\d{1,3}):)?(\d{1,3}):([0-5]\d)(?![\d:])""")

/**
 * 扫出正文里的全部时间点。
 *
 * 评论会在一句话里提多个时间点（「前奏 0:10，正片 1:00」），这里全部返回；
 * 时长由调用方传入：0 表示还不知道（首帧未就绪），此时不做上限过滤，
 * 免得把合法时间点误判成越界。
 */
fun commentTimestamps(text: String, durationMs: Long = 0L): List<CommentTimestamp> {
    val result = ArrayList<CommentTimestamp>(2)
    for (match in textTimestampPattern.findAll(text)) {
        val hours = match.groupValues[1].toLongOrNull() ?: 0L
        val minutes = match.groupValues[2].toLong()
        if (hours > 0L && minutes >= 60L) continue
        val positionMs = ((hours * 60 + minutes) * 60 + match.groupValues[3].toLong()) * 1000L
        if (durationMs > 0L && positionMs > durationMs) continue
        result += CommentTimestamp(match.range.first, match.range.last + 1, positionMs)
    }
    return result
}

/** 点击后能不能真的跳：超时长不跳，时长未知时按请求原样交给播放器。 */
fun commentSeekTarget(positionMs: Long, durationMs: Long): CommentSeekResult =
    if (durationMs > 0L && positionMs > durationMs) CommentSeekResult.OutOfRange
    else CommentSeekResult.Seek(positionMs.coerceAtLeast(0L))

sealed interface CommentSeekResult {
    data class Seek(val positionMs: Long) : CommentSeekResult
    data object OutOfRange : CommentSeekResult
}