package com.aritxonly.bdfun.feature.video.presentation.listen

/** 听视频只认两种排布：竖排（封面上、信息下）和左右分栏（封面左、信息右）。 */
enum class ListenLayout { Stacked, SideBySide }

data class ListenLayoutSpec(
    val mode: ListenLayout,
    val coverSizeDp: Float,
    val coverHeightDp: Float,
)

/**
 * 纯函数：窗口宽高比决定排布。
 * 用「宽 > 高 * 1.15」而不是简单的宽>高，避免折叠屏展开时的近方形窗口被判成横排，
 * 那样封面和文字都会挤在半屏宽里。
 */
fun resolveListenLayout(widthDp: Float, heightDp: Float): ListenLayoutSpec {
    val w = widthDp.coerceAtLeast(1f)
    val h = heightDp.coerceAtLeast(1f)
    val landscape = w > h * 1.15f
    return if (landscape) {
        // 横排：封面边长跟高度走，再留出上下边距。
        val side = (h * 0.62f).coerceAtMost(w * 0.42f).coerceAtLeast(96f)
        ListenLayoutSpec(ListenLayout.SideBySide, side, side)
    } else {
        // 竖排：封面取「可用宽度的 72%」和「可用高度的 38%」的较小值。
        val side = minOf(w * 0.72f, h * 0.38f).coerceAtLeast(120f)
        ListenLayoutSpec(ListenLayout.Stacked, side, side)
    }
}

enum class ListenPage { Cover, Lyrics }

data class ListenPageSpec(val page: ListenPage, val label: String, val hasLyricsPage: Boolean = false)

fun listenPageCount(hasLyrics: Boolean): Int = if (hasLyrics) 2 else 1

fun listenPages(hasLyrics: Boolean): List<ListenPageSpec> = buildList {
    add(ListenPageSpec(ListenPage.Cover, "封面"))
    if (hasLyrics) add(ListenPageSpec(ListenPage.Lyrics, "歌词", hasLyricsPage = true))
}

fun clampListenPage(index: Int, hasLyrics: Boolean): Int {
    val last = listenPageCount(hasLyrics) - 1
    return index.coerceIn(0, last)
}