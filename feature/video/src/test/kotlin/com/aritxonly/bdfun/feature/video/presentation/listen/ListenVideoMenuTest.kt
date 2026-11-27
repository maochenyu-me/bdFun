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
        assertFalse(entries.contains(ListenMenuEntry.LYRICS_SEARCH))
        // 基础项永远在：返回视频、倍速、定时、A-B、后台播放
        assertTrue(entries.contains(ListenMenuEntry.VIDEO_MODE))
        assertTrue(entries.contains(ListenMenuEntry.SPEED))
        assertTrue(entries.contains(ListenMenuEntry.SLEEP_TIMER))
        assertTrue(entries.contains(ListenMenuEntry.AB_LOOP))
        assertTrue(entries.contains(ListenMenuEntry.BACKGROUND_PLAY))
    }

    @Test fun showsEntriesWhenCapabilitiesPresent() {
        val entries = visibleListenMenuEntries(
            ListenMenuCapabilities(
                hasPages = true, hasLyrics = true, canFavorite = true, canShare = true,
                canPip = true, canChangeOrientation = true, hasQualities = true, loggedIn = true,
            ),
        )
        assertEquals(ListenMenuEntry.entries.toSet(), entries.toSet())
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

    @Test fun playModeCyclesAndLabels() {
        assertEquals(1, nextListenPlayMode(0))
        assertEquals(2, nextListenPlayMode(1))
        assertEquals(0, nextListenPlayMode(2))
        assertEquals("不循环", listenPlayModeLabel(0))
        assertEquals("单集循环", listenPlayModeLabel(1))
        assertEquals("列表循环", listenPlayModeLabel(2))
    }

    @Test fun playModeIsAlwaysAvailable() {
        assertTrue(visibleListenMenuEntries(ListenMenuCapabilities()).contains(ListenMenuEntry.PLAY_MODE))
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