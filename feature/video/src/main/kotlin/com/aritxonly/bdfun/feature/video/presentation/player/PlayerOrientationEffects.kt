package com.aritxonly.bdfun.ui

import android.app.Activity
import android.content.pm.ActivityInfo
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import com.aritxonly.bdfun.core.domain.FullscreenOrientation
import com.aritxonly.bdfun.core.domain.fullscreenOrientation
import com.aritxonly.bdfun.core.domain.listenVideoOrientation
import kotlinx.coroutines.delay

@Composable
fun BindPlayerOrientation(
    fullscreen: Boolean,
    portraitVideo: Boolean,
    ui: VideoPlayerUiStateHolder,
    page: PlayerPageHost,
    audioOnly: Boolean = false,
) {
    val activity = LocalContext.current as? Activity
    var fsLockHint by ui.fsLockHint
    val fsLocked by ui.fsLocked
    LaunchedEffect(fsLockHint) {
        if (fsLockHint) {
            kotlinx.coroutines.delay(3_000L)
            fsLockHint = false
        }
    }
    LaunchedEffect(fsLocked, fullscreen, portraitVideo, audioOnly) {
        page.orientationLocked = fsLocked && fullscreen
        if (fullscreen) {
            // 听视频要跟随设备方向；其余仍按视频本身的宽高比决定
            val target = if (audioOnly) listenVideoOrientation(fsLocked)
            else fullscreenOrientation(portraitVideo, fsLocked)
            activity?.requestedOrientation = target.toActivityOrientation()
        }
    }
    DisposableEffect(Unit) {
        onDispose { page.orientationLocked = false }
    }
}

private fun FullscreenOrientation.toActivityOrientation(): Int = when (this) {
    FullscreenOrientation.Locked -> ActivityInfo.SCREEN_ORIENTATION_LOCKED
    FullscreenOrientation.SensorPortrait -> ActivityInfo.SCREEN_ORIENTATION_SENSOR_PORTRAIT
    FullscreenOrientation.SensorLandscape -> ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
    FullscreenOrientation.FullSensor -> ActivityInfo.SCREEN_ORIENTATION_FULL_SENSOR
}
