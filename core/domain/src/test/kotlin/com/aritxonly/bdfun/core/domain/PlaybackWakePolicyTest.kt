package com.aritxonly.bdfun.core.domain

import org.junit.Assert.assertEquals
import org.junit.Test

class PlaybackWakePolicyTest {
    private val active = 1_000L
    private val idle = 5_000L

    @Test fun interactiveKeepsOriginalCadence() {
        // 亮屏时必须完全保持原有节奏，否则进度与通知会看起来卡顿。
        assertEquals(active, playbackWakeIntervalMs(true, true, active, idle))
        assertEquals(idle, playbackWakeIntervalMs(false, true, active, idle))
    }

    @Test fun screenOffWidensBothBranches() {
        assertEquals(active * 2, playbackWakeIntervalMs(true, false, active, idle))
        assertEquals(idle * 2, playbackWakeIntervalMs(false, false, active, idle))
    }

    @Test fun customIntervalsAreScaledNotReplaced() {
        // 调用方传入的基准值决定量级，策略只负责倍率，避免把两个场景的差异抹平。
        assertEquals(800L, playbackWakeIntervalMs(true, false, 400L, 3_000L))
        assertEquals(6_000L, playbackWakeIntervalMs(false, false, 400L, 3_000L))
        assertEquals(60L, playbackWakeIntervalMs(true, true, 60L, 3_000L))
    }
}