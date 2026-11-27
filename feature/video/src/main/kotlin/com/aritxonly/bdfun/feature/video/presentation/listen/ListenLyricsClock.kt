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
 * 播放器每秒才发布一次位置，直接拿它当扫亮进度，一句歌词只会亮两三下。
 * 这里按帧把时间补上：位置一律由锚点线性外推，采样只用来发现 seek／卡顿，
 * 不做「追一下、退一点」的修正——那种修正正是扫亮在原地抽搐的原因。
 * 循环只依赖「是否在播」这个离散状态，每秒的采样通过 [rememberUpdatedState] 读最新值，
 * 不会把循环反复重启。
 */
@Composable
internal fun rememberSmoothLyricPosition(
    sampledMs: Long,
    playing: Boolean,
    speed: Float,
): () -> Long {
    var position by remember { mutableLongStateOf(sampledMs) }
    val latestSample by rememberUpdatedState(sampledMs)
    val latestPlaying by rememberUpdatedState(playing)
    val latestSpeed by rememberUpdatedState(speed)
    LaunchedEffect(playing) {
        if (!playing) {
            position = latestSample
            return@LaunchedEffect
        }
        var clock = LyricClock.of(latestSample)
        while (true) {
            val frameNs = withFrameNanos { it }
            clock = clock.advance(latestSample, frameNs, latestPlaying, latestSpeed)
            position = clock.positionAt(frameNs, latestSpeed)
        }
    }
    return { position }
}
