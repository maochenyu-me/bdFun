package com.aritxonly.bdfun.core.domain

import com.aritxonly.bdfun.core.domain.activeSponsorSegmentAt
import com.aritxonly.bdfun.core.model.video.SponsorSegment
import org.junit.Assert.*
import org.junit.Test

class SponsorSegmentPolicyTest {
    private fun segment(
        id: String,
        start: Long,
        end: Long,
        category: String = "sponsor",
        action: String = "skip",
    ) = SponsorSegment(start, end, category, action, id)

    @Test fun startIsIncludedAndEndIsExcluded() {
        val segment = segment("one", 1000, 2000)
        assertNull(activeSponsorSegmentAt(listOf(segment), 999, emptySet()))
        assertEquals(segment, activeSponsorSegmentAt(listOf(segment), 1000, emptySet()))
        assertEquals(segment, activeSponsorSegmentAt(listOf(segment), 1999, emptySet()))
        assertNull(activeSponsorSegmentAt(listOf(segment), 2000, emptySet()))
    }

    @Test fun overlappingSegmentsKeepOriginalOrderAndDismissalRevealsNext() {
        val first = segment("first", 1000, 3000)
        val second = segment("second", 0, 4000)
        assertEquals(first, activeSponsorSegmentAt(listOf(first, second), 1500, emptySet()))
        assertEquals(second, activeSponsorSegmentAt(listOf(first, second), 1500, setOf("first")))
        assertNull(activeSponsorSegmentAt(listOf(first, second), 1500, setOf("first", "second")))
    }

    @Test fun zeroLengthReversedAndEmptySegmentsDoNotActivate() {
        assertNull(activeSponsorSegmentAt(listOf(segment("zero", 1000, 1000)), 1000, emptySet()))
        assertNull(activeSponsorSegmentAt(listOf(segment("backward", 2000, 1000)), 1500, emptySet()))
        assertNull(activeSponsorSegmentAt(emptyList(), 0, emptySet()))
    }

    @Test fun skipModeMapsToOffPromptOrAuto() {
        assertEquals(SponsorSkipAction.Off, sponsorSkipAction(0))
        assertEquals(SponsorSkipAction.Prompt, sponsorSkipAction(1))
        assertEquals(SponsorSkipAction.Auto, sponsorSkipAction(2))
        // 未知值沿用旧默认的「提示」，不静默关闭或自动跳转。
        assertEquals(SponsorSkipAction.Prompt, sponsorSkipAction(99))
    }

    @Test fun disabledCategoryHidesSegment() {
        val ad = segment("ad", 1000, 5000, category = "sponsor")
        assertNull(activeSponsorSegmentAt(listOf(ad), 2000, emptySet(), SponsorPreferences(categories = setOf("intro"))))
        // 默认偏好放行全部类别，行为与改造前一致。
        assertEquals(ad, activeSponsorSegmentAt(listOf(ad), 2000, emptySet(), SponsorPreferences()))
    }

    @Test fun minimumDurationDropsShortSegmentsAndKeepsBoundary() {
        val short = segment("short", 0, 1999)
        assertNull(activeSponsorSegmentAt(listOf(short), 500, emptySet(), SponsorPreferences(minimumDurationSeconds = 2)))
        val exact = segment("exact", 0, 2000)
        assertEquals(exact, activeSponsorSegmentAt(listOf(exact), 500, emptySet(), SponsorPreferences(minimumDurationSeconds = 2)))
        // 0 秒表示不限时长。
        assertEquals(exact, activeSponsorSegmentAt(listOf(exact), 500, emptySet(), SponsorPreferences()))
    }

    @Test fun nonSkipActionTypesAreLeftToPlayer() {
        val mute = segment("mute", 0, 4000, action = "mute")
        assertNull(activeSponsorSegmentAt(listOf(mute), 100, emptySet(), SponsorPreferences()))
        // 服务端漏写 actionType 时按 skip 处理，否则整个片段会被丢掉。
        val blank = segment("blank", 0, 4000, action = "")
        assertEquals(blank, activeSponsorSegmentAt(listOf(blank), 100, emptySet(), SponsorPreferences()))
    }

    @Test fun defaultPreferencesKeepEveryCategoryWithPrompt() {
        val preferences = SponsorPreferences()
        assertEquals(SPONSOR_CATEGORIES.toSet(), preferences.categories)
        assertEquals(SponsorSkipAction.Prompt, preferences.action)
        assertEquals(0, preferences.minimumDurationSeconds)
        assertTrue(preferences.noticeEnabled)
        assertEquals(SponsorSkipAction.Auto, SponsorPreferences(skipMode = 2).action)
    }
}
