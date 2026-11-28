package com.aritxonly.bdfun.feature.video.presentation.listen

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ListenVideoMenuTest {

    @Test fun hidesEntriesWithoutCapabilities() {
        val entries = visibleListenMenuEntries(ListenMenuCapabilities())
        assertFalse(entries.contains(ListenMenuEntry.COLLECTION))
        assertFalse(entries.contains(ListenMenuEntry.FAVORITE))
        assertFalse(entries.contains(ListenMenuEntry.PIP))
        assertFalse(entries.contains(ListenMenuEntry.LYRICS_CALIBRATE))
        assertFalse(entries.contains(ListenMenuEntry.LYRICS_CLEAR))
        // 基础项永远在：返回视频、倍速、定时、A-B、后台播放、导入歌词
        assertTrue(entries.contains(ListenMenuEntry.VIDEO_MODE))
        assertTrue(entries.contains(ListenMenuEntry.SPEED))
        assertTrue(entries.contains(ListenMenuEntry.SLEEP_TIMER))
        assertTrue(entries.contains(ListenMenuEntry.AB_LOOP))
        assertTrue(entries.contains(ListenMenuEntry.BACKGROUND_PLAY))
    }

    /** 导入歌词恰恰是「没有歌词」时才最需要，不能跟着 hasLyrics 一起藏起来。 */
    @Test fun lyricsSearchIsAlwaysAvailable() {
        assertTrue(visibleListenMenuEntries(ListenMenuCapabilities()).contains(ListenMenuEntry.LYRICS_SEARCH))
    }

    @Test fun showsEntriesWhenCapabilitiesPresent() {
        val entries = visibleListenMenuEntries(
            ListenMenuCapabilities(
                hasPages = true, hasLyrics = true, canFavorite = true, canShare = true,
                canPip = true, canChangeOrientation = true, hasAudioQualities = true, loggedIn = true,
                hasImportedLyrics = true,
            ),
        )
        assertEquals(ListenMenuEntry.entries.toSet(), entries.toSet())
    }

    /** 「清除本地歌词」只在确实挂着导入的 lrc 时出现，否则没有可清的东西。 */
    @Test fun lyricsClearNeedsImportedLyrics() {
        assertFalse(
            visibleListenMenuEntries(ListenMenuCapabilities(hasLyrics = true))
                .contains(ListenMenuEntry.LYRICS_CLEAR),
        )
        assertTrue(
            visibleListenMenuEntries(ListenMenuCapabilities(hasLyrics = true, hasImportedLyrics = true))
                .contains(ListenMenuEntry.LYRICS_CLEAR),
        )
    }

    @Test fun favoriteRequiresLogin() {
        val entries = visibleListenMenuEntries(ListenMenuCapabilities(canFavorite = true, loggedIn = false))
        assertFalse(entries.contains(ListenMenuEntry.FAVORITE))
    }

    @Test fun menuOrderGroupsPlaybackFirst() {
        val entries = visibleListenMenuEntries(
            ListenMenuCapabilities(hasPages = true, canFavorite = true, canShare = true, loggedIn = true),
        )
        assertEquals(ListenMenuEntry.VIDEO_MODE, entries.first())
        // 传输/播放相关项排在内容相关项之前
        assertTrue(entries.indexOf(ListenMenuEntry.SPEED) < entries.indexOf(ListenMenuEntry.FAVORITE))
        assertTrue(entries.indexOf(ListenMenuEntry.SLEEP_TIMER) < entries.indexOf(ListenMenuEntry.SHARE))
    }

    @Test fun valuesOnlyShownWhenMeaningful() {
        val state = ListenMenuState()
        assertNull(listenMenuValue(ListenMenuEntry.SLEEP_TIMER, state))
        assertNull(listenMenuValue(ListenMenuEntry.AB_LOOP, state))
        assertNull(listenMenuValue(ListenMenuEntry.FAVORITE, state))
        assertEquals("1.0x", listenMenuValue(ListenMenuEntry.SPEED, state))
        assertEquals("未开启", listenMenuValue(ListenMenuEntry.BACKGROUND_PLAY, state))
    }

    @Test fun playModeLabelComesFromDomain() {
        assertEquals("顺序播放", listenMenuValue(ListenMenuEntry.PLAY_MODE, ListenMenuState(loopMode = 0)))
        assertEquals("单曲循环", listenMenuValue(ListenMenuEntry.PLAY_MODE, ListenMenuState(loopMode = 1)))
        assertEquals("列表循环", listenMenuValue(ListenMenuEntry.PLAY_MODE, ListenMenuState(loopMode = 2)))
    }

    @Test fun shuffleShowsAsItsOwnMode() {
        // 随机开着时 loopMode 被收回 0，菜单不能还写「顺序播放」。
        val state = ListenMenuState(loopMode = 0, shuffle = true)
        assertEquals("随机播放", listenMenuValue(ListenMenuEntry.PLAY_MODE, state))
    }

    @Test fun translationRowOnlyShownWithLyrics() {
        assertFalse(visibleListenMenuEntries(ListenMenuCapabilities()).contains(ListenMenuEntry.TRANSLATION))
        assertTrue(visibleListenMenuEntries(ListenMenuCapabilities(hasLyrics = true)).contains(ListenMenuEntry.TRANSLATION))
        assertEquals("未开启", listenMenuValue(ListenMenuEntry.TRANSLATION, ListenMenuState()))
        assertEquals("已开启", listenMenuValue(ListenMenuEntry.TRANSLATION, ListenMenuState(translationOn = true)))
    }

    @Test fun playModeIsAlwaysAvailable() {
        assertTrue(visibleListenMenuEntries(ListenMenuCapabilities()).contains(ListenMenuEntry.PLAY_MODE))
    }

    @Test fun lyricsStyleNeedsLyrics() {
        assertFalse(visibleListenMenuEntries(ListenMenuCapabilities()).contains(ListenMenuEntry.LYRICS_STYLE))
        assertTrue(
            visibleListenMenuEntries(ListenMenuCapabilities(hasLyrics = true))
                .contains(ListenMenuEntry.LYRICS_STYLE),
        )
    }

    @Test fun lyricsStyleValueFollowsState() {
        assertEquals(
            "KTV 逐字",
            listenMenuValue(ListenMenuEntry.LYRICS_STYLE, ListenMenuState()),
        )
        assertEquals(
            "普通",
            listenMenuValue(
                ListenMenuEntry.LYRICS_STYLE,
                ListenMenuState(lyricsStyle = ListenLyricsStyle.PLAIN),
            ),
        )
    }

    @Test fun lyricsCalibrateNeedsLyrics() {
        assertFalse(visibleListenMenuEntries(ListenMenuCapabilities()).contains(ListenMenuEntry.LYRICS_CALIBRATE))
        assertTrue(
            visibleListenMenuEntries(ListenMenuCapabilities(hasLyrics = true))
                .contains(ListenMenuEntry.LYRICS_CALIBRATE),
        )
    }

    @Test fun lyricsCalibrateValueShowsOnlyWhenShifted() {
        assertNull(listenMenuValue(ListenMenuEntry.LYRICS_CALIBRATE, ListenMenuState()))
        assertEquals(
            "+1.2s",
            listenMenuValue(ListenMenuEntry.LYRICS_CALIBRATE, ListenMenuState(lyricsOffsetMs = 1_200L)),
        )
        assertEquals(
            "-0.3s",
            listenMenuValue(ListenMenuEntry.LYRICS_CALIBRATE, ListenMenuState(lyricsOffsetMs = -300L)),
        )
    }

    @Test fun speedValueTrimsTrailingZeros() {
        assertEquals("1.25x", trimSpeedValue(1.25f))
        assertEquals("1.5x", trimSpeedValue(1.5f))
        assertEquals("2x", trimSpeedValue(2f))
    }

    @Test fun abLoopFirstMarkSetsStart() {
        val loop = AbLoop().mark(10_000L)
        assertEquals(10_000L, loop.startMs)
        assertEquals(-1L, loop.endMs)
        assertFalse(loop.active)
        assertTrue(loop.hasStart)
    }

    @Test fun abLoopSecondMarkSetsEnd() {
        val loop = AbLoop().mark(10_000L).mark(20_000L)
        assertTrue(loop.active)
        assertEquals(10_000L, loop.startMs)
        assertEquals(20_000L, loop.endMs)
    }

    @Test fun abLoopIgnoresBackwardsSecondMark() {
        val loop = AbLoop().mark(20_000L).mark(10_000L)
        assertFalse(loop.active)
        assertEquals(20_000L, loop.startMs)
    }

    @Test fun abLoopThirdMarkClears() {
        val loop = AbLoop().mark(10_000L).mark(20_000L).mark(30_000L)
        assertEquals(-1L, loop.startMs)
        assertEquals(-1L, loop.endMs)
    }

    @Test fun abLoopSeeksBackAtEnd() {
        val loop = AbLoop(10_000L, 20_000L)
        assertNull(loop.seekBackTarget(15_000L))
        assertEquals(10_000L, loop.seekBackTarget(20_000L))
        assertEquals(10_000L, loop.seekBackTarget(25_000L))
    }

    @Test fun abLoopLabelShowsPendingEnd() {
        assertEquals("0:10 → ?", AbLoop(10_000L, -1L).label())
        assertEquals("0:10 → 0:20", AbLoop(10_000L, 20_000L).label())
        assertEquals("", AbLoop().label())
    }
}