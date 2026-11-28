package com.aritxonly.bdfun.core.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class KaraokeLyricsPolicyTest {

    /** 秒 → 帧时间的纳秒。 */
    private fun sec(value: Double) = (value * 1_000_000_000.0).toLong()

    private fun startClock(ms: Long = 5_000) =
        LyricClock.of(ms).advance(ms, sec(1.0), playing = true, speed = 1f)

    @Test fun lineProgressFollowsPositionInsideTheLine() {
        assertEquals(0f, karaokeLineProgress(1_000, 1_000, 3_000), 0.0001f)
        assertEquals(0.5f, karaokeLineProgress(2_000, 1_000, 3_000), 0.0001f)
        assertEquals(1f, karaokeLineProgress(3_000, 1_000, 3_000), 0.0001f)
    }

    @Test fun lineProgressIsClampedOutsideTheLine() {
        assertEquals(0f, karaokeLineProgress(0, 1_000, 3_000), 0.0001f)
        assertEquals(1f, karaokeLineProgress(90_000, 1_000, 3_000), 0.0001f)
    }

    @Test fun zeroLengthLineDoesNotDivideByZero() {
        assertEquals(1f, karaokeLineProgress(1_500, 1_000, 1_000), 0.0001f)
    }

    @Test fun litGlyphCountWalksGlyphByGlyph() {
        assertEquals(0f, karaokeLitGlyphCount(0f, 10), 0.0001f)
        assertEquals(2.5f, karaokeLitGlyphCount(0.25f, 10), 0.0001f)
        assertEquals(10f, karaokeLitGlyphCount(1f, 10), 0.0001f)
    }

    @Test fun litGlyphCountIsSafeOnEmptyText() {
        assertEquals(0f, karaokeLitGlyphCount(0.5f, 0), 0.0001f)
    }

    @Test fun clockExtrapolatesBetweenSamples() {
        val clock = startClock()
        assertEquals(5_000, clock.positionAt(sec(1.0), 1f))
        // 下一次采样还没来，过了 16ms 也要往前走 16ms。
        assertEquals(5_016, clock.positionAt(sec(1.016), 1f))
        // 一个采样周期后正好到采样点，外推上限不会把它截住。
        assertEquals(6_000, clock.positionAt(sec(2.0), 1f))
    }

    @Test fun clockHonoursPlaybackSpeed() {
        val clock = startClock()
        assertEquals(5_200, clock.positionAt(sec(1.1), 2f))
    }

    @Test fun clockKeepsRunningWhenSampleLagsBehindPrediction() {
        val clock = startClock()
        // 采样只有 5 560，比预测落后 440ms：位置不许往回跳，原地等真实播放追上来。
        val next = clock.advance(5_560, sec(2.0), playing = true, speed = 1f)
        assertEquals(6_000, next.positionAt(sec(2.0), 1f))
        assertEquals(6_000, next.positionAt(sec(2.016), 1f))
        // 下一秒采样追上来，继续按采样的增量往前走。
        val later = next.advance(6_560, sec(3.0), playing = true, speed = 1f)
        assertEquals(6_560, later.positionAt(sec(3.0), 1f))
    }

    @Test fun clockIsMonotonicAcrossJitteredSamples() {
        var clock = startClock()
        var last = clock.positionAt(sec(1.0), 1f)
        var sampled = 5_000L
        for (frame in 1..600) {
            val frameNs = sec(1.0 + frame / 60.0)
            if (frame % 60 == 0) {
                // 每秒一次采样，采样本身带 ±60ms 抖动。
                sampled = 5_000L + frame * 16L + if ((frame / 60) % 2 == 0) 60L else -60L
                clock = clock.advance(sampled, frameNs, playing = true, speed = 1f)
            }
            val now = clock.positionAt(frameNs, 1f)
            assertTrue("frame $frame: $now < $last", now >= last)
            last = now
        }
    }

    @Test fun clockStopsRunningAheadWhileSampleStalls() {
        var clock = startClock()
        // 缓冲 8 秒：采样一直是 5 000，每帧推进时时钟也不能自己往前跑。
        for (second in 1..8) {
            clock = clock.advance(5_000, sec(1.0 + second), playing = true, speed = 1f)
            assertEquals(5_000, clock.positionAt(sec(1.0 + second), 1f))
        }
    }

    @Test fun clockResumesAfterStall() {
        var clock = startClock()
        clock = clock.advance(5_000, sec(4.0), playing = true, speed = 1f)
        assertEquals(5_000, clock.positionAt(sec(4.0), 1f))
        // 采样恢复推进：从冻结处继续走，不会突然跳一大段。
        val resumed = clock.advance(5_050, sec(4.016), playing = true, speed = 1f)
        assertEquals(5_050, resumed.positionAt(sec(4.016), 1f))
        assertEquals(5_066, resumed.positionAt(sec(4.032), 1f))
    }

    @Test fun clockJumpsOnForwardSeek() {
        val clock = startClock()
        val next = clock.advance(90_000, sec(2.0), playing = true, speed = 1f)
        assertEquals(90_000, next.positionAt(sec(2.0), 1f))
    }

    @Test fun clockJumpsBackOnBackwardSeek() {
        val clock = startClock()
        val next = clock.advance(2_000, sec(2.0), playing = true, speed = 1f)
        assertEquals(2_000, next.positionAt(sec(2.0), 1f))
    }

    @Test fun clockFreezesWhilePaused() {
        val clock = startClock()
        val next = clock.advance(5_400, sec(9.0), playing = false, speed = 1f)
        assertEquals(5_400, next.positionAt(sec(9.0), 1f))
    }

    @Test fun clockFallsBackToNormalSpeedOnBadInput() {
        val clock = startClock()
        assertEquals(5_010, clock.positionAt(sec(1.01), 0f))
    }

    @Test fun inactiveLinesFadeWithDistance() {
        val near = karaokeInactiveAlpha(1)
        val far = karaokeInactiveAlpha(3)
        assertEquals(KaraokeDimAlphaFactor, near, 0.0001f)
        assertTrue(far < near)
        // 再远也不会低于基线的一半。
        assertEquals(KaraokeDimAlphaFactor * 0.45f, karaokeInactiveAlpha(40), 0.0001f)
    }

    @Test fun distanceZeroBehavesLikeNeighbour() {
        assertEquals(karaokeInactiveAlpha(1), karaokeInactiveAlpha(0), 0.0001f)
        assertEquals(karaokeInactiveAlpha(1), karaokeInactiveAlpha(-3), 0.0001f)
    }
}
