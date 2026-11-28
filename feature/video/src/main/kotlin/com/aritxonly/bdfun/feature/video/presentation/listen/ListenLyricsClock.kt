package com.aritxonly.bdfun.feature.video.presentation.listen

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import com.aritxonly.bdfun.core.domain.LyricClock

/**
 * 逐字扫亮用的界面时钟。
 *
 * 播放器每秒才发布一次位置，直接拿它刷进度是一秒一跳。这里按帧把时间补上：
 * 每帧走上这一帧的真实时长，采样只用来校正偏差（小偏差逐帧收敛，大偏差才当 seek）。
 * 循环只依赖「是否在播 / 是否在缓冲」两个离散状态，每秒的采样通过
 * [rememberUpdatedState] 读最新值，不会把循环反复重启。
 *
 * 缓冲时循环直接退出：播放器没在推进，歌词就该停在原处等声音回来。
 */
@Composable
internal fun rememberSmoothLyricPosition(
    sampledMs: Long,
    playing: Boolean,
    buffering: Boolean,
    speed: Float,
): () -> Long {
    var position by remember { mutableLongStateOf(sampledMs) }
    val latestSample by rememberUpdatedState(sampledMs)
    val latestSpeed by rememberUpdatedState(speed)

    // 暂停时直接对到采样：拖完进度条要立刻看到歌词跟过去。
    LaunchedEffect(sampledMs, playing) {
        if (!playing) position = sampledMs
    }
    // 播放中每帧补齐时间；缓冲时让位，保持当前位置不动。
    LaunchedEffect(playing, buffering) {
        if (!playing || buffering) return@LaunchedEffect
        var clock = LyricClock.of(position, latestSample)
        var lastFrameNs = 0L
        while (true) {
            val frameNs = withFrameNanos { it }
            val deltaMs = if (lastFrameNs == 0L) 0L else (frameNs - lastFrameNs) / 1_000_000L
            lastFrameNs = frameNs
            clock = clock.step(latestSample, deltaMs, latestSpeed)
            position = clock.displayMs
        }
    }
    return { position }
}
