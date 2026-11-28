package com.aritxonly.bdfun.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.pager.PagerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.CoroutineScope
import top.yukonga.miuix.kmp.theme.MiuixTheme

@Composable
fun VideoDetailLayout(
    presentation: VideoScreenPresentation, inlineLayout: VideoInlineLayout,
    pageUi: VideoDetailUiStateHolder, loading: Boolean, slots: VideoDetailLayoutSlots,
) {
    // 这里只决定"画多大"，所以用 layoutFullscreen：横屏视频从竖屏点展开、
    // 窗口还没转过去的那一帧仍按半屏画，等窗口横过来再铺满，免得闪一下。
    val layoutFullscreen = presentation.layoutFullscreen
    val playing by pageUi.playing
    val colors = MiuixTheme.colorScheme
    val pictureInPicture = presentation.pictureInPicture
    val paneLayout = presentation.paneLayout
    val immersiveStatusBar = presentation.immersiveStatusBar
    val statusBarTop = presentation.statusBarTop
    val statusTop = statusBarTop
    val splitPanes = paneLayout.isSplit && !layoutFullscreen && !pictureInPicture
    val playerBlockHeight = inlineLayout.playerBlockHeight
    val expandedPlayerHeight = inlineLayout.expandedPlayerHeight
    val collapsedPlayerHeight = inlineLayout.collapsedPlayerHeight
    val animatedPlayerHeight = inlineLayout.animatedPlayerHeight
    val collapseState = inlineLayout.collapseState
    Box(modifier = Modifier.fillMaxSize()) {
        slots.pager(this)

        // 竖屏评论面板展开：播放器收缩的那一两帧里，下一层「简介」的作者信息会从缝里闪出来。
        // 这层底色压在详情内容之上、播放器之下，直接堵住；播放器随后仍画在它上面。
        if (presentation.commentSheetOpen) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(com.aritxonly.teak.common.theme.LocalTeakContentColors.current.background),
            )
        }

        if (loading) {
            slots.loading(
                Modifier
                    .align(Alignment.TopCenter)
                    .padding(top = 120.dp)
                    .size(28.dp),
                colors.primary,
            )
        }

        val viewport = resolveVideoPlayerViewport(VideoViewportInput(
            expandedPlayerHeight, collapsedPlayerHeight, animatedPlayerHeight, statusTop, immersiveStatusBar,
            layoutFullscreen, pictureInPicture, splitPanes,
        ))
        slots.player(this, viewport)

        // 竖屏评论面板展开时画面只剩上半屏，点这块画面应当是「直接进全屏播放」，
        // 而不是先弹一层控件。这层透明点击区画在播放器之上，所以播放器自己的点按手势
        // 拿不到这次点击；面板收起后它就不存在，详情页里的点按恢复原行为。
        if (presentation.commentSheetOpen) {
            Box(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .fillMaxWidth()
                    .height(with(LocalDensity.current) { animatedPlayerHeight.coerceAtLeast(0f).toDp() })
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = slots.onPlayerAreaTap,
                    ),
            )
        }

        VideoCollapseOverlay(
            fullscreen = layoutFullscreen,
            collapseProgress = viewport.collapseProgress,
            playerBlockHeight = playerBlockHeight,
            statusTop = statusTop,
            accentColor = slots.accentColor,
            playIcon = slots.playIcon,
            onRestorePlay = {
                collapseState.restore()
                if (!playing) slots.togglePlayback()
            },
            onBack = slots.onBack,
            onHome = slots.onHome,
        )
    }

}
