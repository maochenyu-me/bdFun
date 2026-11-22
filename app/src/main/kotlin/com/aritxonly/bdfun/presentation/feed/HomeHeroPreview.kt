package com.aritxonly.bdfun.ui

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import com.aritxonly.bdfun.AppGraph
import com.aritxonly.bdfun.core.model.video.VideoItem

/**
 * 首页顶部轮播的静音预览：复用动态页的内联播放链路，只把底色换成透明，
 * 让首帧出来前仍能透出下面的封面。
 */
@Composable
internal fun HomeHeroPreview(video: VideoItem, modifier: Modifier) {
    val holder = remember(video.bvid) { AppGraph.inlineVideoStateHolder(video.bvid) }
    val state by holder.state.collectAsState()
    LaunchedEffect(holder) { holder.load() }
    DisposableEffect(holder) {
        onDispose { holder.close() }
    }
    val info = state.info ?: return
    InlineVideoContent(
        bvid = video.bvid,
        loaded = info,
        muted = true,
        modifier = modifier.fillMaxSize(),
        background = Color.Transparent,
    )
}
