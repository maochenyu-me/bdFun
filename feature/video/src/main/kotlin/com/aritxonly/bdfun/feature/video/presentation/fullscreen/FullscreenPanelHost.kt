package com.aritxonly.bdfun.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp
import com.aritxonly.teak.common.chrome.rememberManualProgress
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue

@Composable
internal fun FullscreenPanelHost(
    panel: FullscreenPanel,
    info: FullscreenPlayerInfo,
    playback: FullscreenPlaybackState,
    actions: FullscreenActions,
    onClose: () -> Unit,
    visible: Boolean = true,
    accentColor: Color,
    danmakuSettings: @Composable (List<com.aritxonly.bdfun.core.model.video.DanmakuItem>) -> Unit,
    /** 竖屏换用底部实心面板；横屏仍是右侧渐变换出，两者共用同一份面板内容。 */
    portrait: Boolean = false,
    /** 评论面板走自己的外壳，需要拿到评论列表与回复栏插槽。 */
    slots: FullscreenChromeSlots? = null,
) {

    // 评论面板自成一套宽度与排版，直接交给它自己的外壳，不挤在标准右栏里。
    if (panel == FullscreenPanel.Comments && slots != null) {
        FullscreenCommentPanel(
            info = info,
            onClose = onClose,
            visible = visible,
            comments = slots.comments,
            composer = slots.commentComposer,
        )
        return
    }

    val ui = remember { FullscreenPanelHostStateHolder(panel) }
    var shown by ui.shown
    var revealed by ui.revealed
    LaunchedEffect(visible) { revealed = visible }
    val reveal = rememberManualProgress(if (revealed) 1f else 0f, 264f)
    LaunchedEffect(panel) {
        if (panel != FullscreenPanel.None) shown = panel
    }
    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        if (portrait) {
            // 竖屏：整块实心底板从底部顶起来。横屏那套"左透明右变黑"的渐变在窄屏里只剩一条，
            // 白字直接压在画面上看不清。
            PortraitPanelShell(reveal, onClose) {
                FullscreenPanelContent(shown, info, playback, actions, onClose, accentColor, danmakuSettings)
            }
        } else {
            val panelWidth = (maxWidth.value * 0.41f).dp.coerceAtLeast(360.dp)
            // 左透明、右深黑：与参考图三一致，不铺满一块纯黑
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.horizontalGradient(
                            0f to Color.Transparent,
                            0.25f to Color.Transparent,
                            0.45f to Color.Black.copy(alpha = 0.25f),
                            0.58f to Color.Black.copy(alpha = 0.74f),
                            0.68f to Color.Black.copy(alpha = 0.95f),
                            1f to Color.Black,
                        ),
                    )
                    .graphicsLayer { alpha = reveal }
                    .clickable(interactionSource = null, indication = null, onClick = onClose),
            )
            Column(
                modifier = Modifier
                    .align(Alignment.CenterEnd)
                    .width(panelWidth)
                    .fillMaxHeight()
                    .graphicsLayer {
                        alpha = reveal
                        translationX = (1f - reveal) * 90f
                    }
                    .clickable(interactionSource = null, indication = null) {}
                    .padding(start = 32.dp, end = 30.dp, top = 30.dp, bottom = 18.dp),
            ) {
                FullscreenPanelContent(shown, info, playback, actions, onClose, accentColor, danmakuSettings)
            }
        }
    }
}

/**
 * 竖屏面板外壳：遮罩 + 从底部顶起来的实心底板。
 *
 * 与横屏那套两侧渐变外壳分开，是因为两者在窄屏/宽屏下不是同一套观感，
 * 混在一个函数里会让调用点难以判断自己走的是哪条路径。
 */
@Composable
private fun androidx.compose.foundation.layout.BoxScope.PortraitPanelShell(
    reveal: Float,
    onClose: () -> Unit,
    content: @Composable () -> Unit,
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.42f))
            .graphicsLayer { alpha = reveal }
            .clickable(interactionSource = null, indication = null, onClick = onClose),
    )
    Column(
        modifier = Modifier
            .align(Alignment.BottomCenter)
            .fillMaxWidth()
            .fillMaxHeight(0.82f)
            .graphicsLayer {
                alpha = reveal
                translationY = (1f - reveal) * 140f
            }
            .clip(RoundedCornerShape(topStart = 22.dp, topEnd = 22.dp))
            .background(Color(0xFF1A1A1E))
            .clickable(interactionSource = null, indication = null) {}
            .padding(start = 22.dp, end = 22.dp, top = 16.dp, bottom = 20.dp),
    ) {
        content()
    }
}

@Composable
private fun FullscreenPanelContent(
    shown: FullscreenPanel,
    info: FullscreenPlayerInfo,
    playback: FullscreenPlaybackState,
    actions: FullscreenActions,
    onClose: () -> Unit,
    accentColor: Color,
    danmakuSettings: @Composable (List<com.aritxonly.bdfun.core.model.video.DanmakuItem>) -> Unit,
) {
    when (shown) {
        FullscreenPanel.Favorite -> FavoritePanel(info, actions, onClose, accentColor)
        FullscreenPanel.More -> MorePanel(info, playback, actions, onClose, accentColor)
        FullscreenPanel.Danmaku -> DanmakuPanel(info, onClose, danmakuSettings)
        FullscreenPanel.Episodes -> EpisodesPanel(info, actions, onClose, accentColor)
        FullscreenPanel.Speed -> SpeedPanel(playback, actions, onClose, accentColor)
        FullscreenPanel.Quality -> QualityPanel(playback, actions, onClose, accentColor)
        FullscreenPanel.Cache -> CachePanel(playback, actions, onClose, accentColor)
        FullscreenPanel.Subtitles -> SubtitlesPanel(info, actions, onClose, accentColor)
        // 评论面板在上面提前分流到 FullscreenCommentPanel，走不到这里。
        FullscreenPanel.Comments -> Unit
        FullscreenPanel.None -> Unit
    }
}

private class FullscreenPanelHostStateHolder(initial: FullscreenPanel) {
    val shown = mutableStateOf(initial)
    val revealed = mutableStateOf(false)
}
