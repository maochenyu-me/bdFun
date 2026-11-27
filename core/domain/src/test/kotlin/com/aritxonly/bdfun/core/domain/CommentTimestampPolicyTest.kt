package com.aritxonly.bdfun.core.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CommentTimestampPolicyTest {
    @Test fun collectsEveryTimestampWithUtf16Offsets() {
        val text = "前奏 0:10，正片 1:00"
        val hits = commentTimestamps(text)
        assertEquals(listOf(10_000L, 60_000L), hits.map { it.positionMs })
        assertEquals("0:10", text.substring(hits[0].start, hits[0].end))
        assertEquals("1:00", text.substring(hits[1].start, hits[1].end))
    }

    @Test fun supportsHoursAndKeepsTheWholeTokenAsOneSpan() {
        val text = "结尾 1:00:00"
        val hit = commentTimestamps(text).single()
        assertEquals(3_600_000L, hit.positionMs)
        assertEquals("1:00:00", text.substring(hit.start, hit.end))
    }

    @Test fun rejectsMinuteOverflowThatOnlyLooksLikeATime() {
        assertTrue(commentTimestamps("1:67:03").isEmpty())
        assertTrue(commentTimestamps("00:99").isEmpty())
    }

    @Test fun allowsMinutesPastFiftyNineWhenThereIsNoHourSegment() {
        assertEquals(listOf(3_720_000L), commentTimestamps("62:00").map { it.positionMs })
    }

    @Test fun durationFiltersOutTargetsPastTheEndAndUnknownDurationKeepsThem() {
        assertTrue(commentTimestamps("2:15", durationMs = 120_000L).isEmpty())
        assertEquals(listOf(135_000L), commentTimestamps("2:15", durationMs = 0L).map { it.positionMs })
    }

    @Test fun zeroStaysClickableSoItCanJumpBackToTheStart() {
        val hit = commentTimestamps("从 0:00 开始").single()
        assertEquals(0L, hit.positionMs)
        assertEquals(CommentSeekResult.Seek(0L), commentSeekTarget(0L, 120_000L))
    }

    @Test fun doesNotMatchInsideLongerNumbersOrClockStrings() {
        assertTrue(commentTimestamps("20261127 1234:56").isEmpty())
        assertTrue(commentTimestamps("1:00:00:00").isEmpty())
    }

    @Test fun seekTargetsRejectOnlyTargetsPastAKnownDuration() {
        assertEquals(CommentSeekResult.Seek(60_000L), commentSeekTarget(60_000L, 120_000L))
        assertEquals(CommentSeekResult.OutOfRange, commentSeekTarget(135_000L, 120_000L))
        assertEquals(CommentSeekResult.Seek(60_000L), commentSeekTarget(60_000L, 0L))
    }
}