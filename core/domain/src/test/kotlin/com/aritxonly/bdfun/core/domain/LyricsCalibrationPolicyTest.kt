package com.aritxonly.bdfun.core.domain

import org.junit.Assert.assertEquals
import org.junit.Test

class LyricsCalibrationPolicyTest {

    @Test fun stepsAccumulateFromCurrentOffset() {
        assertEquals(500L, calibrateLyricsOffset(0L, 500L))
        assertEquals(400L, calibrateLyricsOffset(500L, -100L))
        assertEquals(-1_000L, calibrateLyricsOffset(-500L, -500L))
    }

    @Test fun offsetIsClampedToLimit() {
        assertEquals(LyricsOffsetLimitMs, calibrateLyricsOffset(LyricsOffsetLimitMs, 500L))
        assertEquals(-LyricsOffsetLimitMs, calibrateLyricsOffset(-LyricsOffsetLimitMs, -500L))
        assertEquals(LyricsOffsetLimitMs, calibrateLyricsOffset(0L, 999_999L))
    }

    @Test fun labelShowsSignAndOneDecimal() {
        assertEquals("未偏移", lyricsOffsetLabel(0L))
        assertEquals("+1.2s", lyricsOffsetLabel(1_200L))
        assertEquals("-0.3s", lyricsOffsetLabel(-300L))
        assertEquals("+2.0s", lyricsOffsetLabel(2_000L))
    }

    @Test fun alignmentMovesLineToCurrentPosition() {
        // 这句原本 30.0s 开始，现在播到 31.5s 才听到 → 整份歌词往后挪 1.5s。
        assertEquals(1_500L, alignmentOffset(lineFromMs = 30_000L, positionMs = 31_500L))
        // 起步早了 → 往前挪。
        assertEquals(-800L, alignmentOffset(lineFromMs = 30_000L, positionMs = 29_200L))
    }

    @Test fun alignmentIsClampedLikeManualSteps() {
        assertEquals(
            LyricsOffsetLimitMs,
            alignmentOffset(lineFromMs = 0L, positionMs = 5_000_000L),
        )
    }
}
