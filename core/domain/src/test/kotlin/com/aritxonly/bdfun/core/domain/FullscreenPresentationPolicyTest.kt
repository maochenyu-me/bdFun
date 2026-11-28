package com.aritxonly.bdfun.core.domain

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FullscreenPresentationPolicyTest {

    private fun presented(
        intent: Boolean = true,
        windowLandscape: Boolean = false,
        portraitVideo: Boolean = false,
        compactPhone: Boolean = true,
        multiWindow: Boolean = false,
        graceElapsed: Boolean = false,
    ) = videoFullscreenPresented(
        intent = intent,
        windowLandscape = windowLandscape,
        portraitVideo = portraitVideo,
        compactPhone = compactPhone,
        multiWindow = multiWindow,
        rotationGraceElapsed = graceElapsed,
    )

    @Test
    fun landscapeRequestWhileWindowStillPortrait_defersPresentation() {
        assertFalse(presented(windowLandscape = false))
    }

    @Test
    fun onceWindowTurnsLandscape_presentsImmediately() {
        assertTrue(presented(windowLandscape = true))
    }

    @Test
    fun graceElapsed_presentsEvenIfOrientationRequestIgnored() {
        assertTrue(presented(windowLandscape = false, graceElapsed = true))
    }

    @Test
    fun portraitVideo_presentsWithoutWaiting() {
        assertTrue(presented(portraitVideo = true, windowLandscape = false))
    }

    @Test
    fun nonCompactDevice_presentsWithoutWaiting() {
        assertTrue(presented(compactPhone = false, windowLandscape = false))
    }

    @Test
    fun multiWindow_presentsWithoutWaiting() {
        assertTrue(presented(multiWindow = true, windowLandscape = false))
    }

    @Test
    fun exitingFullscreen_neverWaits() {
        assertFalse(presented(intent = false, windowLandscape = true))
        assertFalse(presented(intent = false, windowLandscape = false))
    }
}