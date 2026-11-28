package com.aritxonly.bdfun.ui

import androidx.compose.runtime.mutableStateOf

/** 页面瞬时控制；真实播放、请求和互动状态仍由各应用层容器发布。 */
class VideoDetailUiStateHolder {
    /**
     * 用户的全屏意图：点击展开的那一刻就是 true，驱动朝向请求、清晰度回档。
     *
     * 与 [fullscreen] 分开：横屏视频从竖屏点展开时系统转屏要几百毫秒，
     * 这期间窗口还是竖的，立刻按全屏布局会闪一帧「竖屏窗口、视频缩在中间」。
     */
    val fullscreenIntent = mutableStateOf(false)

    /** 页面真的按全屏绘制：由意图 + 窗口朝向共同决定，见 `VideoFullscreenPresentation`。 */
    val fullscreen = mutableStateOf(false)

    val playing = mutableStateOf(true)
    val metaReady = mutableStateOf(false)
    val coinSheet = mutableStateOf(false)
    val favSheet = mutableStateOf(false)
}