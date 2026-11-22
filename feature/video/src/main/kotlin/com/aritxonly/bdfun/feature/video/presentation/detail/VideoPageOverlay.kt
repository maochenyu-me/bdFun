package com.aritxonly.bdfun.ui


import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import com.aritxonly.teak.common.chrome.rememberSpringFloat
import com.aritxonly.teak.common.interaction.blockTouches
import com.aritxonly.teak.common.chrome.rememberManualProgress

@Composable
fun androidx.compose.foundation.layout.BoxScope.VideoPageOverlay(
    layout: VideoActivityLayout, ui: VideoActivityPageStateHolder,
    pager: androidx.compose.foundation.pager.PagerState, host: VideoPageOverlayHost, slots: VideoPageOverlaySlots,
) {
    if (layout.pictureInPicture) return
    val page = host
    LaunchedEffect(layout.fullscreen) {
        if (layout.fullscreen) {
            page.dismissReply++
            ui.danmakuInputOpen.value = false
            // 全屏只放视频：竖屏评论面板此时必须让位，否则「全屏 + 面板未清」会渲染成整页评论，
            // 表现就是点播放器上的展开按钮却展开了评论区。
            ui.comments.value = null
        }
    }
    // 评论以面板形式覆盖页面时（竖屏底部评论面板），"简介/评论/发弹幕"这一条会被面板压住，
    // 留着只会从评论列表中间透出来，所以只把这一条让位；回复栏和弹幕输入必须保留，
    // 否则面板里的评论「回复」点了没有任何反应。
    val commentSheetOpen = ui.comments.value != null
    if (layout.overlayAlpha > 0.01f) {
        // 进独占全屏的那一瞬间 overlayAlpha 还要淡出 200ms，中间播放器已经铺满，
        // 这一条却还在原位闪一下；全屏时直接不画。
        if (!commentSheetOpen && !layout.fullscreen) slots.tabs(this)
        VideoActivityReplyLayer(layout, pager, host, slots, commentSheetOpen)
        VideoActivityInputLayer(layout, ui, host, slots)
    }
    VideoActivityDanmakuPanel(layout, host, slots)
}

@Composable
private fun androidx.compose.foundation.layout.BoxScope.VideoActivityReplyLayer(
    layout: VideoActivityLayout, pager: androidx.compose.foundation.pager.PagerState,
    host: VideoPageOverlayHost, slots: VideoPageOverlaySlots,
    /** 竖屏评论面板展开：评论不在 pager 第 1 页，但回复栏同样要挂在页面底部。 */
    commentSheetOpen: Boolean = false,
) {
    val VideoPageBus = host
    val PlayerUiState = host
    val pipMode = layout.pictureInPicture
    val pageFullscreen = layout.fullscreen
    val pageOverlayAlpha = layout.overlayAlpha
    val paneLayout = layout.paneLayout
    val splitPanes = layout.splitPanes
    val statusBarDp = layout.statusBarTop
    val playerTopInset = layout.playerTopInset
    val fs = pageFullscreen
    // 评论回复栏属于详情栏；只有独占全屏才隐藏。
    if (!fs && (pager.currentPage == 1 || commentSheetOpen)) {
        // 编辑中：铺一层全屏点击层，点空白处直接退出（同时杜绝点击穿透到播放器）
        if (VideoPageBus.replyEditing) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .pointerInput(Unit) {
                        awaitPointerEventScope {
                            while (true) {
                                val event = awaitPointerEvent()
                                val down = event.changes.firstOrNull { it.pressed }
                                if (down != null) {
                                    VideoPageBus.dismissReply++
                                }
                                event.changes.forEach { it.consume() }
                            }
                        }
                    },
            )
        }
        // 长按评论弹出的下拉是独立 Dialog 窗口，层级本来就在输入栏之上；
        // 不能再因为菜单打开把人输入栏淡出，否则用户会以为输入框被吞了。
        slots.replyBar(
            Modifier
                .align(if (splitPanes) Alignment.BottomEnd else Alignment.BottomCenter)
                .then(if (splitPanes) Modifier.width(paneLayout.detailsWidthDp.dp) else Modifier),
        )
    }

}

@Composable
private fun androidx.compose.foundation.layout.BoxScope.VideoActivityInputLayer(
    layout: VideoActivityLayout, ui: VideoActivityPageStateHolder,
    host: VideoPageOverlayHost, slots: VideoPageOverlaySlots,
) {
    val VideoPageBus = host
    val PlayerUiState = host
    val pipMode = layout.pictureInPicture
    val pageFullscreen = layout.fullscreen
    val pageOverlayAlpha = layout.overlayAlpha
    val paneLayout = layout.paneLayout
    val splitPanes = layout.splitPanes
    val statusBarDp = layout.statusBarTop
    val playerTopInset = layout.playerTopInset
    val fs = pageFullscreen
    var danmakuInputOpen by ui.danmakuInputOpen
    // 发弹幕输入
    if (danmakuInputOpen && !fs) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(Unit) {
                    awaitPointerEventScope {
                        while (true) {
                            val event = awaitPointerEvent()
                            val down = event.changes.firstOrNull { it.pressed }
                            if (down != null) VideoPageBus.danmakuInputClose++
                            event.changes.forEach { it.consume() }
                        }
                    }
                },
        )
        slots.inputBar(
            { danmakuInputOpen = false },
            Modifier
                .align(if (splitPanes) Alignment.BottomStart else Alignment.BottomCenter)
                .then(if (splitPanes) Modifier.width(paneLayout.playerWidthDp.dp) else Modifier),
        )
    }
}

@Composable
private fun androidx.compose.foundation.layout.BoxScope.VideoActivityDanmakuPanel(layout: VideoActivityLayout, host: VideoPageOverlayHost, slots: VideoPageOverlaySlots) {
    val VideoPageBus = host
    val PlayerUiState = host
    val pipMode = layout.pictureInPicture
    val pageFullscreen = layout.fullscreen
    val pageOverlayAlpha = layout.overlayAlpha
    val paneLayout = layout.paneLayout
    val splitPanes = layout.splitPanes
    val statusBarDp = layout.statusBarTop
    val playerTopInset = layout.playerTopInset
    // 弹幕设置面板：统一玻璃 + 弹簧弹出；动画过程中面板自身由模糊变清晰
    val panelOpen = PlayerUiState.danmakuPanelOpen
    val spring = rememberSpringFloat(
        target = if (panelOpen) 1f else 0f,
        stiffness = 300f,
        damping = 22f,
    )
    val shown = spring.coerceIn(0f, 1f)
    if (panelOpen || shown > 0.01f) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(Unit) {
                    awaitPointerEventScope {
                        while (true) {
                            val event = awaitPointerEvent()
                            if (event.changes.any { it.pressed }) {
                                PlayerUiState.danmakuPanelOpen = false
                            }
                            event.changes.forEach { it.consume() }
                        }
                    }
                },
        )
        val selfBlur = if (shown < 0.99f) {
            Modifier.blur((10f * (1f - shown)).dp)
        } else {
            Modifier
        }
        slots.panel(
            { PlayerUiState.danmakuInteract++ },
            VideoPageBus.panelBackdrop,
            Modifier
                .align(Alignment.TopEnd)
                .padding(top = statusBarDp + 54.dp, end = if (splitPanes) paneLayout.detailsWidthDp.dp + 12.dp else 12.dp)
                .graphicsLayer {
                    alpha = shown
                    scaleX = 0.82f + 0.18f * spring
                    scaleY = 0.82f + 0.18f * spring
                    translationY = (1f - shown) * -14f
                    transformOrigin =
                        androidx.compose.ui.graphics.TransformOrigin(1f, 0f)
                }
                .then(selfBlur),
        )
    }

}
