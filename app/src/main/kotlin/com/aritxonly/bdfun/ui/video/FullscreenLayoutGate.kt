package com.aritxonly.bdfun.ui

import android.app.Activity
import android.content.pm.ActivityInfo
import android.content.res.Configuration
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import com.aritxonly.bdfun.core.domain.FULLSCREEN_ROTATION_GRACE_MS
import com.aritxonly.bdfun.core.domain.deferFullscreenLayout
import kotlinx.coroutines.delay

/**
 * 全屏「铺满」要不要再等一帧朝向。
 *
 * 竖屏点展开横屏视频时：点击只改 Compose 状态，系统转屏要等下一次配置变更。
 * 这段时间若先铺满，会闪出「竖屏全屏、视频缩在中间」的一帧，然后才转过去。
 * 所以先维持点击前的布局，等窗口真的横过来再展开；[FULLSCREEN_ROTATION_GRACE_MS]
 * 是安全阀，朝向请求没生效时照常铺满。
 *
 * 返回值只用于「视觉铺满」，播放器内部的 fullscreen 语义（朝向请求、系统栏、HDR）不受影响。
 */
@Composable
internal fun rememberLayoutFullscreen(requestedFullscreen: Boolean, pictureInPicture: Boolean): Boolean {
    val configuration = LocalConfiguration.current
    val windowLandscape = configuration.orientation == Configuration.ORIENTATION_LANDSCAPE
    val activity = LocalContext.current as? Activity
    // 初值同步进入「等待」：全屏刚变成 true 的那一帧就要先保持原布局，
    // 否则等下面 effect 跑完再改回来，又会漏出那一帧。
    var awaitingLandscapeRotation by remember(requestedFullscreen, activity) {
        mutableStateOf(requestedFullscreen)
    }
    var rotationGraceElapsed by remember(requestedFullscreen, activity) { mutableStateOf(false) }
    LaunchedEffect(requestedFullscreen, activity) {
        if (!requestedFullscreen) return@LaunchedEffect
        // 朝向是播放器在同一个 applyChanges 里写的；等下一帧再读才有值。
        withFrameNanos { }
        // 朝向请求不是横向（竖屏视频、听视频、锁定方向）就不必等，立刻放行。
        if (activity?.requestedLandscape() != true) awaitingLandscapeRotation = false
        delay(FULLSCREEN_ROTATION_GRACE_MS)
        rotationGraceElapsed = true
    }
    val deferred = deferFullscreenLayout(
        requestedFullscreen = requestedFullscreen,
        pictureInPicture = pictureInPicture,
        windowLandscape = windowLandscape,
        awaitingLandscapeRotation = awaitingLandscapeRotation,
        rotationGraceElapsed = rotationGraceElapsed,
    )
    return requestedFullscreen && !deferred
}

/** 当前是不是已经请求了横向朝向；竖屏视频请求的是竖屏，不该等。 */
private fun Activity.requestedLandscape(): Boolean = when (requestedOrientation) {
    ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE,
    ActivityInfo.SCREEN_ORIENTATION_REVERSE_LANDSCAPE,
    ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE,
    ActivityInfo.SCREEN_ORIENTATION_USER_LANDSCAPE,
    -> true

    else -> false
}