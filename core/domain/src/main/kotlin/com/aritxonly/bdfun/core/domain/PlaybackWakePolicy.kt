package com.aritxonly.bdfun.core.domain

/**
 * 后台播放轮询与常驻通知刷新共用的唤醒节奏。
 *
 * 息屏后画面不可见、也没有触控，按原节奏唤醒只会白耗电，因此统一降频到亮屏的两倍间隔；
 * 两种场景的“播放中 / 暂停”基准不同，用参数传入而不是写死，方便调用方各自保持原有手感。
 */
fun playbackWakeIntervalMs(
    playing: Boolean,
    screenInteractive: Boolean,
    activeIntervalMs: Long,
    idleIntervalMs: Long,
): Long = when {
    // 播放中息屏：仍需刷新进度与通知，但没必要维持亮屏节奏。
    !screenInteractive && playing -> activeIntervalMs * 2
    // 暂停又息屏：没有任何可见变化，间隔再放宽一档。
    !screenInteractive -> idleIntervalMs * 2
    playing -> activeIntervalMs
    else -> idleIntervalMs
}