package com.aritxonly.bdfun.core.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PortraitVideoPolicyTest {

    @Test fun verticalRatesArePortrait() {
        assertTrue(isPortraitVideo(1080, 1920))
        assertTrue(isPortraitVideo(720, 1280))
        assertTrue(isPortraitVideo(1080, 1440))
        assertTrue(isPortraitVideo(1440, 1080 + 1080))
    }

    @Test fun horizontalAndSquareVideosAreNotPortrait() {
        assertFalse(isPortraitVideo(1920, 1080))
        assertFalse(isPortraitVideo(1080, 1080))
        // 4:5 只比方形窄一点，仍按横屏骨架播放
        assertFalse(isPortraitVideo(1000, 1200))
    }

    @Test fun unknownSizeFallsBackToLandscape() {
        assertFalse(isPortraitVideo(0, 0))
        assertFalse(isPortraitVideo(0, 1920))
        assertFalse(isPortraitVideo(1080, 0))
    }

    @Test fun orientationKeepsPortraitVideoUpright() {
        assertEquals(FullscreenOrientation.SensorPortrait, fullscreenOrientation(portraitVideo = true, orientationLocked = false))
        assertEquals(FullscreenOrientation.SensorLandscape, fullscreenOrientation(portraitVideo = false, orientationLocked = false))
        assertEquals(FullscreenOrientation.Locked, fullscreenOrientation(portraitVideo = true, orientationLocked = true))
    }

    @Test fun portraitChromeNeedsPortraitWindow() {
        assertTrue(usesPortraitChrome(portraitVideo = true, windowPortrait = true))
        assertFalse(usesPortraitChrome(portraitVideo = true, windowPortrait = false))
        assertFalse(usesPortraitChrome(portraitVideo = false, windowPortrait = true))
    }

    @Test fun pictureInPictureRatioFollowsVideoShape() {
        assertEquals(PlayerAspectRatio(9, 16), pictureInPictureAspectRatio(portraitVideo = true))
        assertEquals(PlayerAspectRatio(16, 9), pictureInPictureAspectRatio(portraitVideo = false))
    }
}
