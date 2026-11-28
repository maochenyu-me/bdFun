package com.aritxonly.bdfun.core.domain

/** 触控后维持满帧的静止门槛：调用方按同一常量安排延时回调，避免各自复制魔数。 */
const val PLAYBACK_REFRESH_IDLE_GATE_MS = 1500L

/**
 * 播放中触控时的上限帧率。
 *
 * 视频内容本身不超过 60fps，把触摸 boost 放到 120Hz 只是让同一帧多合成一次，
 * 显示与 GPU 功耗近似翻倍而画面没有变化，因此播放中把上限收到 60。
 */
const val PLAYBACK_TOUCH_REFRESH_CAP_HZ = 60f

/**
 * 保留 1500ms 静止门槛和 0/1/60Hz 请求值，窗口能力由 Android 适配层处理。
 *
 * - 未开启或还在触控门槛内：播放中给 [PLAYBACK_TOUCH_REFRESH_CAP_HZ]，
 *   其余情况给 0（交回系统，滑动才有跟手感）；
 * - 稳定态：播放或全屏 60Hz，静止页面 1Hz。
 */
fun targetPlaybackRefreshRate(enabled: Boolean, idleMs: Long, playing: Boolean, fullscreen: Boolean): Float = when {
    !enabled -> 0f
    idleMs < PLAYBACK_REFRESH_IDLE_GATE_MS -> if (playing) PLAYBACK_TOUCH_REFRESH_CAP_HZ else 0f
    playing || fullscreen -> 60f
    else -> 1f
}
