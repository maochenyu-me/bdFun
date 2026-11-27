package com.aritxonly.bdfun.core.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class KaraokeLyricsPolicyTest {

    /** 秒 → 帧时间的纳秒。 */
    private fun sec(value: Double) = (value * 1_000_000_000.0).toLong()

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
        val clock = LyricClock.of(5_000).advance(5_000, sec(1.0), playing = true, speed = 1f)
        assertEquals(5_000, clock.positionAt(sec(1.0), 1f))
        // 下一次采样还没来，过了 16ms 也要往前走 16ms。
        assertEquals(5_016, clock.positionAt(sec(1.016), 1f))
        // 再过一个采样周期，正好到采样点。
        assertEquals(6_000, clock.positionAt(sec(2.0), 1f))
    }

    @Test fun clockHonoursPlaybackSpeed() {
        val clock = LyricClock.of(5_000).advance(5_000, sec(1.0), playing = true, speed = 1f)
        assertEquals(5_200, clock.positionAt(sec(1.1), 2f))
    }

    @Test fun clockKeepsAnchorWhenSampleOnlyDriftsALittle() {
        val clock = LyricClock.of(5_000).advance(5_000, sec(1.0), playing = true, speed = 1f)
        // 采样比预测落后 440ms（还没到 snap 阈值）：锚点不动，界面时钟不会往回跳。
        val next = clock.advance(5_560, sec(2.0), playing = true, speed = 1f)
        assertEquals(clock.anchorFrameNs, next.anchorFrameNs)
        assertEquals(6_000, next.positionAt(sec(2.0), 1f))
    }

    @Test fun clockIsMonotonicAcrossSamples() {
        var clock = LyricClock.of(5_000).advance(5_000, sec(1.0), playing = true, speed = 1f)
        var last = clock.positionAt(sec(1.0), 1f)
        for (step in 1..120) {
            val frameNs = sec(1.0 + step / 60.0)
            // 每秒来一次采样，采样本身带 ±60ms 抖动。
            val sampled = 5_000L + step * 16L + if (step % 60 == 0) 60L else 0L
            clock = clock.advance(sampled, frameNs, playing = true, speed = 1f)
            val now = clock.positionAt(frameNs, 1f)
            assertTrue("step $step: $now < $last", now >= last)
            last = now
        }
    }

    @Test fun clockSnapsOnSeek() {
        val clock = LyricClock.of(5_000).advance(5_000, sec(1.0), playing = true, speed = 1f)
        val next = clock.advance(90_000, sec(1.016), playing = true, speed = 1f)
        assertEquals(90_000, next.positionAt(sec(1.016), 1f))
    }

    @Test fun clockFreezesWhilePaused() {
        val clock = LyricClock.of(5_000).advance(5_000, sec(1.0), playing = true, speed = 1f)
        val next = clock.advance(5_400, sec(9.0), playing = false, speed = 1f)
        assertEquals(5_400, next.positionAt(sec(9.0), 1f))
    }

    @Test fun clockFreezesWhenSampleStalls() {
        // 缓冲中采样一直不变：不能每 0.7s 就往回退一次，要把时钟冻住。
        var clock = LyricClock.of(5_000).advance(5_000, sec(1.0), playing = true, speed = 1f)
        clock = clock.advance(5_000, sec(1.2), playing = true, speed = 1f)
        clock = clock.advance(5_000, sec(3.0), playing = true, speed = 1f)
        assertEquals(5_000, clock.positionAt(sec(3.0), 1f))
        clock = clock.advance(5_000, sec(3.016), playing = true, speed = 1f)
        assertEquals(5_000, clock.positionAt(sec(3.016), 1f))
    }

    @Test fun clockResumesAfterStall() {
        var clock = LyricClock.of(5_000).advance(5_000, sec(1.0), playing = true, speed = 1f)
        clock = clock.advance(5_000, sec(3.0), playing = true, speed = 1f)
        assertEquals(5_000, clock.positionAt(sec(3.0), 1f))
        // 采样恢复推进：时钟从冻结处继续走，不会突然跳一大段。
        val resumed = clock.advance(5_050, sec(3.016), playing = true, speed = 1f)
        assertEquals(5_016, resumed.positionAt(sec(3.016), 1f))
        assertEquals(5_032, resumed.positionAt(sec(3.032), 1f))
    }

    @Test fun clockFallsBackToNormalSpeedOnBadInput() {
        val clock = LyricClock.of(5_000).advance(5_000, sec(1.0), playing = true, speed = 1f)
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
