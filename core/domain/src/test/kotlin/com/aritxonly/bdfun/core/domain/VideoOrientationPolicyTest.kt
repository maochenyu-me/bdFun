package com.aritxonly.bdfun.core.domain

import org.junit.Assert.*
import org.junit.Test

class VideoOrientationPolicyTest {
    @Test fun compactPhoneLandscapeEntersFullscreen() {
        assertEquals(true, fullscreenAfterVideoRotation(true, 393, true))
        assertEquals(true, fullscreenAfterVideoRotation(true, 599, true))
    }
    @Test fun expandedFoldableKeepsLandscapePageAndManualFullscreen() {
        assertNull(fullscreenAfterVideoRotation(true, 600, true))
        assertNull(fullscreenAfterVideoRotation(true, 840, true))
    }
    @Test fun disabledRotationAndUnknownSizeDoNotForceFullscreen() {
        assertNull(fullscreenAfterVideoRotation(true, 393, false))
        assertNull(fullscreenAfterVideoRotation(true, 0, true))
    }
    @Test fun portraitLeavesFullscreenOnEitherDevice() {
        assertEquals(false, fullscreenAfterVideoRotation(false, 393, true))
        assertEquals(false, fullscreenAfterVideoRotation(false, 840, false))
    }

    @Test fun listenModeNeverCollapsesOutOfFullscreen() {
        // 听视频是整页界面，转回竖屏也不能被收回半屏播放器。
        assertNull(fullscreenAfterVideoRotation(false, 393, true, listenMode = true))
        assertNull(fullscreenAfterVideoRotation(false, 840, false, listenMode = true))
        assertNull(fullscreenAfterVideoRotation(true, 393, true, listenMode = true))
    }
}
