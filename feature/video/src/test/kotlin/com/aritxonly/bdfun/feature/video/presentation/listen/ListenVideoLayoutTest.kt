package com.aritxonly.bdfun.feature.video.presentation.listen

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ListenVideoLayoutTest {

    @Test fun portraitWindowStacksCoverAboveText() {
        val layout = resolveListenLayout(widthDp = 412f, heightDp = 915f)
        assertEquals(ListenLayout.Stacked, layout.mode)
        assertTrue(layout.coverSizeDp > 0f)
        // 封面不能吃掉整屏：下方要留给标题、进度、控件和胶囊。
        assertTrue(layout.coverSizeDp < 915f * 0.5f)
    }

    @Test fun landscapeWindowPutsCoverLeftTextRight() {
        val layout = resolveListenLayout(widthDp = 915f, heightDp = 412f)
        assertEquals(ListenLayout.SideBySide, layout.mode)
        assertTrue(layout.coverSizeDp <= 412f)
    }

    @Test fun squareWindowKeepsStackedLayout() {
        // 折叠屏近方形窗口：左右分栏会让封面和文字都很挤，仍然竖排。
        assertEquals(ListenLayout.Stacked, resolveListenLayout(600f, 600f).mode)
    }

    @Test fun coverNeverExceedsAvailableWidth() {
        for (width in listOf(320f, 360f, 412f, 600f, 915f, 1280f)) {
            for (height in listOf(320f, 480f, 915f)) {
                val layout = resolveListenLayout(width, height)
                assertTrue(layout.coverSizeDp <= minOf(width, height) + 0.01f)
            }
        }
    }

    @Test fun lyricsPageShowsPagerOnlyWhenBothPagesExist() {
        assertTrue(listenPageCount(hasLyrics = true) == 2)
        assertTrue(listenPageCount(hasLyrics = false) == 1)
    }

    @Test fun pageIndexClampsWhenLyricsDisappear() {
        // 正在歌词页时字幕被关掉：页面数变成 1，下标要收回 0，否则 Pager 会越界空白。
        assertEquals(0, clampListenPage(1, hasLyrics = false))
        assertEquals(1, clampListenPage(1, hasLyrics = true))
        assertEquals(1, clampListenPage(9, hasLyrics = true))
    }

    @Test fun capsuleOnlyOffersPagesThatExist() {
        val pages = listenPages(hasLyrics = true)
        assertEquals(listOf("封面", "歌词"), pages.map { it.label })
        assertEquals(listOf("封面"), listenPages(hasLyrics = false).map { it.label })
    }

    @Test fun defaultPageIsCover() {
        assertEquals(ListenPage.Cover, listenPages(hasLyrics = true).first().page)
        assertFalse(listenPages(hasLyrics = true).first().hasLyricsPage)
    }

    @Test fun coverIsSquareInCardStyle() {
        // 卡片式封面按正方形摆放，不跟随视频原始宽高比。
        val layout = resolveListenLayout(412f, 915f)
        assertEquals(layout.coverSizeDp, layout.coverHeightDp, 0.01f)
    }
}