package com.aritxonly.bdfun.core.domain

/**
 * 旋转只为紧凑设备自动进入全屏；展开的大屏保留分栏和手动全屏选择。
 *
 * @param listenMode 听视频是整页界面，横竖屏都得留在全屏：
 *   否则「转回竖屏就退出全屏」会把听视频挤进半屏播放器里。
 */
fun fullscreenAfterVideoRotation(
    landscape: Boolean,
    smallestWidthDp: Int,
    autoRotate: Boolean,
    listenMode: Boolean = false,
): Boolean? = when {
    listenMode -> null
    !landscape -> false
    autoRotate && smallestWidthDp in 1 until 600 -> true
    else -> null
}
