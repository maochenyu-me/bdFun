package com.aritxonly.bdfun.core.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ListenPlayModePolicyTest {

    @Test fun shuffleSkipsCurrentItem() {
        // 三项、随机值给 0：跳过 0 号，落到 1 号。
        assertEquals(1, shuffleNextIndex(0, 3, 0f))
        // 随机值给接近 1：落到最后一项。
        assertEquals(2, shuffleNextIndex(0, 3, 0.99f))
        // 当前在中间、随机 0：落到 0 号。
        assertEquals(0, shuffleNextIndex(1, 3, 0f))
    }

    @Test fun shuffleNeverReturnsCurrent() {
        for (size in 2..8) {
            for (current in 0 until size) {
                for (step in 0 until 20) {
                    val next = shuffleNextIndex(current, size, step / 20f)
                    assertTrue("size=$size current=$current got=$next", next in 0 until size)
                    assertTrue("size=$size current=$current got=$next", next != current)
                }
            }
        }
    }

    @Test fun shuffleWithoutTargetReturnsMinusOne() {
        assertEquals(-1, shuffleNextIndex(0, 1, 0.5f))
        assertEquals(-1, shuffleNextIndex(3, 0, 0.5f))
    }

    @Test fun shuffleHandlesOutOfRangeCurrent() {
        // 当前下标失效（刚切过列表）时退回普通均匀采样，不崩也不返回 -1。
        assertTrue(shuffleNextIndex(-1, 4, 0.6f) in 0 until 4)
    }

    @Test fun playModeCycles() {
        assertEquals(1, nextPlayMode(0))
        assertEquals(2, nextPlayMode(1))
        assertEquals(0, nextPlayMode(2))
    }

    @Test fun playModeLabels() {
        assertEquals("顺序播放", playModeLabel(0))
        assertEquals("单曲循环", playModeLabel(1))
        assertEquals("列表循环", playModeLabel(2))
    }

    @Test fun enablingShuffleClearsLoopMode() {
        // 当前未开随机：点一下开启，并把循环模式收回顺序。
        val (mode, shuffle) = toggleShuffle(loopMode = 2, shuffleOn = false)
        assertEquals(0, mode)
        assertTrue(shuffle)
    }

    @Test fun disablingShuffleKeepsLoopMode() {
        // 当前已开随机：点一下关闭，循环模式保持不动。
        val (mode, shuffle) = toggleShuffle(loopMode = 2, shuffleOn = true)
        assertEquals(2, mode)
        assertFalse(shuffle)
    }

    @Test fun modeToggleTurnsShuffleOff() {
        val (mode, shuffle) = playModeAfterModeToggle(loopMode = 0, shuffleOn = true)
        assertEquals(1, mode)
        assertFalse(shuffle)
    }
}