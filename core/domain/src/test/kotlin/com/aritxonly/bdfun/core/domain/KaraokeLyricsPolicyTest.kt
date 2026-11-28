package com.aritxonly.bdfun.core.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class KaraokeLyricsPolicyTest {

    /** 一帧 120Hz 的间隔。 */
    private val frame = 8L

    private fun startClock(ms: Long = 5_000, sampled: Long = 5_000) =
        LyricClock.of(ms, sampled)

    private fun LyricClock.run(frames: Int, sampled: Long, deltaMs: Long = 8L, speed: Float = 1f) =
        (1..frames).fold(this) { acc, _ -> acc.step(sampled, deltaMs, speed) }

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

    /** 采样还没来的那些帧，位置必须一帧一帧往前走，而不是整秒不动。 */
    @Test fun clockAdvancesOneFrameAtATime() {
        var clock = startClock()
        var last = clock.displayMs
        repeat(20) {
            clock = clock.step(5_000, frame, 1f)
            assertEquals(last + frame, clock.displayMs)
            last = clock.displayMs
        }
        assertEquals(5_000 + 20 * frame, clock.displayMs)
    }

    /** 采样超前时单帧最多推进「一帧 + 追赶上限」，一帧永远亮不过半个字。 */
    @Test fun clockNeverJumpsMoreThanOneFramePlusCatchUpStep() {
        val clock = startClock().step(6_000, frame, 1f)
        val step = clock.displayMs - 5_000
        assertTrue("单帧推进了 $step ms", step <= frame + LyricCatchUpStepLimitMs)
        assertTrue("采样超前时应该多补一点", step > frame)
    }

    /** 采样超前 1 秒，靠每帧补一点在几十帧内追上，而不是当帧跳过去。 */
    @Test fun clockCatchesUpGraduallyAfterSampleJumpsAhead() {
        var clock = startClock()
        var previous = clock.displayMs
        var maxStep = 0L
        repeat(40) {
            clock = clock.step(6_000, frame, 1f)
            maxStep = maxOf(maxStep, clock.displayMs - previous)
            previous = clock.displayMs
        }
        assertTrue("单帧最大推进 $maxStep ms", maxStep <= frame + LyricCatchUpStepLimitMs)
        assertTrue("40 帧后还差 ${6_000 - clock.displayMs} ms", clock.displayMs >= 5_900)
    }

    /** 采样整段不动（缓冲）：位置停在原处，不会自己一路跑到前面再往回跳。 */
    @Test fun clockFreezesWhenSampleStalls() {
        var clock = startClock()
        clock = clock.run(300, sampled = 5_000, deltaMs = 16)
        val frozen = clock.displayMs
        assertTrue("缓冲期间不该超前太多：${frozen - 5_000}", frozen - 5_000 <= LyricStallHoldMs + 100)
        clock = clock.run(300, sampled = 5_000, deltaMs = 16)
        assertEquals(frozen, clock.displayMs)
    }

    /** 缓冲结束后从冻结处继续，不会突然跳一大段。 */
    @Test fun clockResumesAfterStall() {
        var clock = startClock().run(300, sampled = 5_000, deltaMs = 16)
        val frozen = clock.displayMs
        val resumed = clock.step(frozen + 16, 16, 1f)
        assertTrue(resumed.displayMs - frozen <= frame + LyricCatchUpStepLimitMs)
    }

    @Test fun clockJumpsOnForwardSeek() {
        val clock = startClock().step(90_000, frame, 1f)
        assertEquals(90_000, clock.displayMs)
    }

    @Test fun clockJumpsBackOnBackwardSeek() {
        val clock = startClock().step(2_000, frame, 1f)
        assertEquals(2_000, clock.displayMs)
    }

    /** 采样比预测落后一点点（发布延迟）：继续按帧走，不倒退。 */
    @Test fun clockKeepsMovingWhenSampleLagsSlightly() {
        val clock = startClock(ms = 5_200, sampled = 5_000).step(5_000, frame, 1f)
        assertTrue(clock.displayMs >= 5_200)
    }

    @Test fun clockHonoursPlaybackSpeed() {
        val clock = startClock().step(5_000, 100L, 2f)
        assertEquals(5_200, clock.displayMs)
    }

    @Test fun clockFallsBackToNormalSpeedOnBadInput() {
        val clock = startClock().step(5_000, 100L, 0f)
        assertEquals(5_100, clock.displayMs)
    }

    /** 掉帧 / 切后台回来：直接对到采样，不做插值。 */
    @Test fun clockSnapsAfterLongFrameGap() {
        val clock = startClock().step(7_500, 3_000L, 1f)
        assertEquals(7_500, clock.displayMs)
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
