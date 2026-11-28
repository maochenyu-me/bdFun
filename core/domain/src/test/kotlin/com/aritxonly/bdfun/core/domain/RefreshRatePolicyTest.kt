package com.aritxonly.bdfun.core.domain

import org.junit.Assert.*
import org.junit.Test

class RefreshRatePolicyTest {
    @Test fun touchWhilePlayingCapsToSixty() {
        assertEquals(PLAYBACK_TOUCH_REFRESH_CAP_HZ, targetPlaybackRefreshRate(true, 0, true, false))
        assertEquals(PLAYBACK_TOUCH_REFRESH_CAP_HZ, targetPlaybackRefreshRate(true, 1499, true, true))
    }
    @Test fun touchWithoutVideoLeavesRateToSystem() {
        assertEquals(0f, targetPlaybackRefreshRate(true, 0, false, false))
        assertEquals(0f, targetPlaybackRefreshRate(true, 1499, false, true))
    }
    @Test fun disabledSettingAlwaysLeavesRateToSystem() {
        assertEquals(0f, targetPlaybackRefreshRate(false, 0, true, true))
        assertEquals(0f, targetPlaybackRefreshRate(false, 9999, false, false))
    }
    @Test fun idleVideoAndPausedFullscreenKeepSixtyHz() {
        assertEquals(60f, targetPlaybackRefreshRate(true, 1500, true, false))
        assertEquals(60f, targetPlaybackRefreshRate(true, 1500, false, true))
    }
    @Test fun idleStaticPageKeepsOriginalOneHzRequest() {
        assertEquals(1f, targetPlaybackRefreshRate(true, 1500, false, false))
    }
}