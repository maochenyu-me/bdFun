package com.aritxonly.bdfun.core.domain

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FullscreenLayoutPolicyTest {
    @Test fun waitsWhileLandscapeRotationPending() {
        assertTrue(
            deferFullscreenLayout(
                requestedFullscreen = true,
                pictureInPicture = false,
                windowLandscape = false,
                awaitingLandscapeRotation = true,
                rotationGraceElapsed = false,
            ),
        )
    }

    @Test fun fillsOnceWindowTurnedLandscape() {
        assertFalse(
            deferFullscreenLayout(
                requestedFullscreen = true,
                pictureInPicture = false,
                windowLandscape = true,
                awaitingLandscapeRotation = true,
                rotationGraceElapsed = false,
            ),
        )
    }

    @Test fun fillsAfterGraceWhenRotationNeverHappens() {
        assertFalse(
            deferFullscreenLayout(
                requestedFullscreen = true,
                pictureInPicture = false,
                windowLandscape = false,
                awaitingLandscapeRotation = true,
                rotationGraceElapsed = true,
            ),
        )
    }

    @Test fun fillsImmediatelyWhenNoLandscapeRequested() {
        assertFalse(
            deferFullscreenLayout(
                requestedFullscreen = true,
                pictureInPicture = false,
                windowLandscape = false,
                awaitingLandscapeRotation = false,
                rotationGraceElapsed = false,
            ),
        )
    }

    @Test fun pictureInPictureNeverDefers() {
        assertFalse(
            deferFullscreenLayout(
                requestedFullscreen = true,
                pictureInPicture = true,
                windowLandscape = false,
                awaitingLandscapeRotation = true,
                rotationGraceElapsed = false,
            ),
        )
    }

    @Test fun notFullscreenNeverDefers() {
        assertFalse(
            deferFullscreenLayout(
                requestedFullscreen = false,
                pictureInPicture = false,
                windowLandscape = false,
                awaitingLandscapeRotation = true,
                rotationGraceElapsed = false,
            ),
        )
    }
}