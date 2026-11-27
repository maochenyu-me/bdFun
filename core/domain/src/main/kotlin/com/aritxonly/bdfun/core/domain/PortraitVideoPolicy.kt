package com.aritxonly.bdfun.core.domain

/**
 * 竖屏视频的宽高比上限（宽/高）：9:16=0.56、3:4=0.75 命中；
 * 接近方形的画面（如 1:1）仍按横屏处理，避免把常规视频误判成竖屏。
 */
private const val PortraitAspectLimit = 0.8f

/**
 * 竖屏视频判定：宽高都必须有效，且明显窄于高。
 * 拿不到清晰度尺寸（播放信息未就绪、音频流）时按横屏处理，保持既有全屏行为。
 */
fun isPortraitVideo(width: Int, height: Int): Boolean =
    width > 0 && height > 0 && width < height &&
        width.toFloat() / height.toFloat() <= PortraitAspectLimit

/** 全屏时要请求的物理朝向；竖屏视频留在竖屏，其余沿用可旋转横屏。 */
enum class FullscreenOrientation { Locked, SensorPortrait, SensorLandscape, FullSensor }

/**
 * 听视频要跟随设备方向（竖持上图下文、横持左右分栏），所以走 FULL_SENSOR；
 * 锁定时仍然只认锁定，避免听视频里出现「锁了还转」的矛盾。
 */
fun listenVideoOrientation(orientationLocked: Boolean): FullscreenOrientation =
    if (orientationLocked) FullscreenOrientation.Locked else FullscreenOrientation.FullSensor

fun fullscreenOrientation(portraitVideo: Boolean, orientationLocked: Boolean): FullscreenOrientation =
    when {
        orientationLocked -> FullscreenOrientation.Locked
        portraitVideo -> FullscreenOrientation.SensorPortrait
        else -> FullscreenOrientation.SensorLandscape
    }

/** 全屏控件骨架：竖屏视频只有在竖屏窗口里才换竖屏骨架。 */
fun usesPortraitChrome(portraitVideo: Boolean, windowPortrait: Boolean): Boolean =
    portraitVideo && windowPortrait

/** 画中画窗口比例（不含系统栏差异）。 */
data class PlayerAspectRatio(val width: Int, val height: Int)

/** 画中画比例：竖屏视频 9:16，其它仍沿用 16:9。 */
fun pictureInPictureAspectRatio(portraitVideo: Boolean): PlayerAspectRatio =
    if (portraitVideo) PlayerAspectRatio(9, 16) else PlayerAspectRatio(16, 9)
