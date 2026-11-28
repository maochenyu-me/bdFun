package com.aritxonly.bdfun.core.domain

/**
 * 全屏转屏的安全阀：朝向请求在某些机型/多窗口下不生效，
 * 超过这个时间就照常铺满，避免「点了展开却一直没反应」。
 */
const val FULLSCREEN_ROTATION_GRACE_MS = 900L

/**
 * 全屏布局是否先等窗口转过去再铺满。
 *
 * 横屏视频从竖屏点开全屏时，系统转屏要几百毫秒；这段时间如果先铺满，
 * 会闪出一帧「竖屏全屏、视频缩在中间」的画面，然后才转到横屏。
 * 所以先维持点击前的布局，等窗口真的横过来再展开。
 *
 * [awaitingLandscapeRotation] 由调用方从 Activity 的 requestedOrientation 读出：
 * 只有「已经请求横屏」才值得等；竖屏视频请求的是竖屏，窗口本来就不用转，立刻铺满。
 */
fun deferFullscreenLayout(
    requestedFullscreen: Boolean,
    pictureInPicture: Boolean,
    windowLandscape: Boolean,
    awaitingLandscapeRotation: Boolean,
    rotationGraceElapsed: Boolean,
): Boolean = requestedFullscreen &&
    !pictureInPicture &&
    !windowLandscape &&
    awaitingLandscapeRotation &&
    !rotationGraceElapsed