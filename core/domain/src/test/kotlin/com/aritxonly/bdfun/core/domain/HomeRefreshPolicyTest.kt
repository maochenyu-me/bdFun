package com.aritxonly.bdfun.core.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class HomeRefreshPolicyTest {
    @Test fun keepsAllOldItemsBelowThreshold() {
        assertEquals(0, resolveHomeRefreshKeptItemCount(0))
        assertEquals(30, resolveHomeRefreshKeptItemCount(30))
        assertEquals(HOME_REFRESH_KEEP_OLD_THRESHOLD, resolveHomeRefreshKeptItemCount(HOME_REFRESH_KEEP_OLD_THRESHOLD))
    }

    @Test fun trimsOldItemsAboveThreshold() {
        assertEquals(HOME_REFRESH_KEEP_OLD_COUNT, resolveHomeRefreshKeptItemCount(HOME_REFRESH_KEEP_OLD_THRESHOLD + 1))
        assertEquals(HOME_REFRESH_KEEP_OLD_COUNT, resolveHomeRefreshKeptItemCount(500))
    }

    @Test fun newItemsTipOnlyWhenSomethingNewArrived() {
        assertTrue(shouldShowHomeNewItemsTip(8))
        assertFalse(shouldShowHomeNewItemsTip(0))
    }

    @Test fun refreshEventHandledOncePerKey() {
        assertFalse(shouldHandleHomeRefreshEvent(0, 0))
        assertTrue(shouldHandleHomeRefreshEvent(3, 2))
        assertFalse(shouldHandleHomeRefreshEvent(3, 3))
        assertFalse(shouldHandleHomeRefreshEvent(2, 3))
    }

    @Test fun dividerNeedsBothSwitchesAndRevealedKey() {
        assertTrue(shouldShowHomeOldContentDivider(true, true, 4, 4, 10))
        // 保留旧推荐关闭：没有旧内容，也就没有分隔点
        assertFalse(shouldShowHomeOldContentDivider(false, true, 4, 4, 10))
        // 上次刷新提示关闭：只隐藏提示，不影响内容保留
        assertFalse(shouldShowHomeOldContentDivider(true, false, 4, 4, 10))
        // 本轮刷新没有旧内容
        assertFalse(shouldShowHomeOldContentDivider(true, true, 4, 4, 0))
        assertFalse(shouldShowHomeOldContentDivider(true, true, 4, 4, -1))
    }

    @Test fun dividerRequiresCurrentRefreshKey() {
        assertFalse(shouldShowHomeOldContentDivider(true, true, 0, 0, 10))
        assertFalse(shouldShowHomeOldContentDivider(true, true, 5, 4, 10))
    }

    @Test fun locatorDelayHonoursZeroAndNegatives() {
        assertEquals(0L, resolveHomeRefreshLocatorDelayMillis(0))
        assertEquals(0L, resolveHomeRefreshLocatorDelayMillis(-5))
        assertEquals(5_000L, resolveHomeRefreshLocatorDelayMillis(5))
        assertEquals(60_000L, resolveHomeRefreshLocatorDelayMillis(60))
    }

    @Test fun heroKeepsEnoughNewCardsInGrid() {
        // 首次进入没有「新增段」，轮播照常取满。
        assertEquals(8, resolveHomeHeroItemLimit(freshCount = 0, maxItems = 8))
        assertEquals(8, resolveHomeHeroItemLimit(freshCount = -1, maxItems = 8))
        // 新增 10 条：只让轮播吃 4 条，网格里留 6 张新卡。
        assertEquals(4, resolveHomeHeroItemLimit(freshCount = 10, maxItems = 8))
        // 新增再多也受轮播上限约束。
        assertEquals(8, resolveHomeHeroItemLimit(freshCount = 30, maxItems = 8))
    }

    @Test fun heroHidesWhenNewContentIsScarce() {
        assertEquals(0, resolveHomeHeroItemLimit(freshCount = 6, maxItems = 8))
        assertEquals(0, resolveHomeHeroItemLimit(freshCount = 3, maxItems = 8))
        assertEquals(2, resolveHomeHeroItemLimit(freshCount = 8, maxItems = 8))
        assertEquals(2, resolveHomeHeroItemLimit(freshCount = 8, maxItems = 8, minNewCards = 6))
    }
}
