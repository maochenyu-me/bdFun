package com.aritxonly.bdfun.ui

import com.aritxonly.teak.common.theme.LocalTeakContentColors
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp
import com.aritxonly.bdfun.core.model.video.VideoDetail
import top.yukonga.miuix.kmp.blur.LayerBackdrop
import top.yukonga.miuix.kmp.blur.layerBackdrop

@Composable
fun VideoPageBody(
    layout: VideoActivityLayout, ui: VideoActivityPageStateHolder, commentsVisible: Boolean, playerBottomPx: () -> Float,
    detail: @Composable () -> Unit, comments: @Composable (VideoDetail, () -> Unit) -> Unit,
    /** 竖屏评论面板收起后要回到播放器，由调用方决定（例如重新进全屏）。 */
    onCommentsDismissed: () -> Unit = {},
    /** Tab 玻璃专用：只录播放器以下的渐变与内容，不含视频层。 */
    contentBackdrop: LayerBackdrop? = null,
) {
    Box(modifier = Modifier.fillMaxSize().background(LocalTeakContentColors.current.background)) {
        detail()
        // 渐变和评论区一起录进这份底片：Tab 玻璃采样到的仍是同一张画面，
        // 只是播放器纹理不再参与录制/合成。
        val contentLayer = if (contentBackdrop != null) {
            Modifier.fillMaxSize().layerBackdrop(contentBackdrop)
        } else {
            Modifier.fillMaxSize()
        }
        var current by ui.comments
        val c = if (layout.pictureInPicture) null else current
        // 竖屏非全屏：评论改成底部面板 —— 视频留在上半屏继续播，
        // 评论从播放器底边往上铺；横屏和分栏仍铺满整页。
        val portraitSheet = !layout.splitPanes &&
            layout.paneLayout.heightDp > layout.paneLayout.widthDp
        val sheetOpen = portraitSheet && c != null && !layout.fullscreen
        Box(modifier = contentLayer) {
            if (layout.splitPanes) VideoCommentTopFade(layout)
            // 播放器底边那层渐变原本是给 tab 条垫底的；面板展开时 tab 条已经让位，
            // 只剩这层 54dp 渐变横在视频下面，看起来像一条黑条，所以一起收掉。
            if (!layout.fullscreen && !sheetOpen) VideoPageTopFade(layout, playerBottomPx)
            if (commentsVisible) VideoCommentBottomFade(layout)
            if (c != null && !layout.fullscreen) {
                if (portraitSheet) {
                    // 面板从播放器当前底边开始铺，上方留给仍在播放的画面。
                    val topInset = with(androidx.compose.ui.platform.LocalDensity.current) {
                        playerBottomPx().coerceAtLeast(0f).toDp()
                    }
                    PortraitCommentSheet(
                        // 收起时不立刻把面板拆掉：进全屏要等页面状态跟上来，
                        // 中间那几帧如果露出详情页，就会闪一下页面底色。
                        // 面板留到 layout.fullscreen 变 true 的那一帧一起消失。
                        onCollapse = onCommentsDismissed,
                        modifier = Modifier.fillMaxSize().padding(top = topInset),
                    ) {
                        comments(c) { onCommentsDismissed() }
                    }
                } else {
                    Box(modifier = Modifier.fillMaxSize().background(LocalTeakContentColors.current.background)) {
                        comments(c) { current = null }
                    }
                }
            }
        }
    }
}

@Composable
private fun androidx.compose.foundation.layout.BoxScope.VideoCommentTopFade(layout: VideoActivityLayout) {
    val pipMode = layout.pictureInPicture
    val pageOverlayAlpha = layout.overlayAlpha
    val paneLayout = layout.paneLayout
    val fadeColor = LocalTeakContentColors.current.background
    // 横屏分栏：评论区顶部也要有一层和底部对称的渐变，内容滚到 tab 下面时被盖住。
    Box(
        modifier = Modifier
            .align(Alignment.TopEnd)
            .width(paneLayout.detailsWidthDp.dp)
            .height(layout.statusBarTop + 52.dp)
            .graphicsLayer { alpha = if (pipMode) 0f else pageOverlayAlpha }
            .background(
                Brush.verticalGradient(
                    0f to fadeColor,
                    0.55f to fadeColor.copy(alpha = 0.88f),
                    1f to androidx.compose.ui.graphics.Color.Transparent,
                ),
            ),
    )
}

@Composable
private fun androidx.compose.foundation.layout.BoxScope.VideoCommentBottomFade(layout: VideoActivityLayout) {
    val pipMode = layout.pictureInPicture
    val pageOverlayAlpha = layout.overlayAlpha
    val paneLayout = layout.paneLayout
    val splitPanes = layout.splitPanes
    // 页面底部反向渐变：顶部透明，越往下越接近页面底色。
    Box(
        modifier = Modifier
            .align(if (splitPanes) Alignment.BottomEnd else Alignment.BottomCenter)
            .then(if (splitPanes) Modifier.width(paneLayout.detailsWidthDp.dp) else Modifier.fillMaxWidth())
            .height(54.dp)
            .graphicsLayer { alpha = if (pipMode) 0f else pageOverlayAlpha }
            .background(
                Brush.verticalGradient(
                    listOf(
                        LocalTeakContentColors.current.background.copy(alpha = 0f),
                        LocalTeakContentColors.current.background,
                    ),
                ),
            ),
    )
}

@Composable
private fun androidx.compose.foundation.layout.BoxScope.VideoPageTopFade(layout: VideoActivityLayout, playerBottomPx: () -> Float) {
    val pipMode = layout.pictureInPicture
    val pageOverlayAlpha = layout.overlayAlpha
    val paneLayout = layout.paneLayout
    val splitPanes = layout.splitPanes
    val playerTopInset = layout.playerTopInset
    // 视频底边的白色渐变放进页面录制层，Tab 玻璃采样时会把它和下方内容
    // 当成同一张底片一起模糊，避免 Tab 内外一亮一白。
    val fadeColor = LocalTeakContentColors.current.background
    if (!splitPanes) Box(
        modifier = Modifier
            .align(if (splitPanes) Alignment.TopEnd else Alignment.TopStart)
            .then(if (splitPanes) Modifier.width(paneLayout.detailsWidthDp.dp) else Modifier.fillMaxWidth())
            .height(54.dp)
            .graphicsLayer {
                alpha = if (pipMode) 0f else pageOverlayAlpha
                // 渐变紧贴播放器真实底边。
                translationY = if (splitPanes) playerTopInset.toPx() else playerBottomPx()
            }
            .background(
                Brush.verticalGradient(
                    0f to fadeColor,
                    0.5f to fadeColor.copy(alpha = 0.94f),
                    0.8f to fadeColor.copy(alpha = 0.48f),
                    1f to fadeColor.copy(alpha = 0f),
                ),
            ),
    )
}
