package com.aritxonly.bdfun.ui


import com.aritxonly.bdfun.ui.LocalVideoPageState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp
import com.aritxonly.bdfun.ui.CommentScreen
import com.aritxonly.bdfun.ui.VideoScreen
import kotlinx.coroutines.launch

@Composable
internal fun VideoActivityBody(
    isLoggedIn: () -> Boolean, settings: com.aritxonly.bdfun.feature.settings.presentation.SettingsBindings,
    scene: VideoActivityScene, layout: VideoActivityLayout, ui: VideoActivityPageStateHolder,
    pager: androidx.compose.foundation.pager.PagerState, scope: kotlinx.coroutines.CoroutineScope,
    host: VideoActivityHostActions,
    contentBackdrop: top.yukonga.miuix.kmp.blur.LayerBackdrop? = null,
) {
    val VideoPageBus = LocalVideoPageState.current
    val PlayerUiState = VideoPageBus.playerUi
    val pipMode = layout.pictureInPicture
    val pageFullscreen = layout.fullscreen
    val pageOverlayAlpha = layout.overlayAlpha
    val paneLayout = layout.paneLayout
    val splitPanes = layout.splitPanes
    val statusBarDp = layout.statusBarTop
    val playerTopInset = layout.playerTopInset
    val loadedVideo = scene.video
    val commentRequest = scene.commentRequest
    var comments by ui.comments
    // 竖屏评论走底部面板：播放器压到 38% 高度，评论从播放器底边往上铺。
    val windowPortrait = androidx.compose.ui.platform.LocalConfiguration.current.orientation ==
        android.content.res.Configuration.ORIENTATION_PORTRAIT
    val portraitComments = windowPortrait && !splitPanes

    VideoPageBody(layout, ui, commentsVisible = pager.currentPage == 1, playerBottomPx = { VideoPageBus.playerBottomPx },
        contentBackdrop = contentBackdrop,
        // 竖屏评论面板收起（箭头或侧滑）后回到播放器全屏，而不是停在简介/评论页。
        onCommentsDismissed = {
            if (portraitComments) PlayerUiState.fullscreen = true
        },
        detail = {
        VideoScreen(
            isLoggedIn = isLoggedIn,
            request = com.aritxonly.bdfun.ui.VideoScreenRequest(
                video = loadedVideo,
                defaultQuality = settings.qualityPreference,
                openedFromVideoPage = host.openedFromVideoPage(),
                initialCommentRoot = commentRequest.first,
                initialCommentTarget = commentRequest.second,
                initialSeekMs = commentRequest.third,
                epId = scene.epId,
                seasonId = scene.seasonId,
            ),
            presentation = com.aritxonly.bdfun.ui.VideoScreenPresentation(
                paneLayout = paneLayout,
                pictureInPicture = pipMode,
                autoRotate = settings.autoRotate,
                immersiveStatusBar = settings.videoImmersiveStatusBar,
                // 半屏播放限制到 720P，全屏恢复原档位。
                fullscreen = pageFullscreen,
                layoutFullscreen = layout.layoutFullscreen,
                // 从 Activity 根部读到的状态栏高度，避免页面内部读成 0。
                statusBarTop = statusBarDp,
                commentSheetOpen = portraitComments && comments != null,
            ),
            events = com.aritxonly.bdfun.ui.VideoScreenEvents(
                onOpenComments = {
                    if (paneLayout.isSplit) scope.launch { pager.animateScrollToPage(1) }
                    else comments = it
                },
                onBack = { host.onBack() },
                // 面板展开时点画面：先收面板，再回全屏播放（对齐参考实现里点小窗进播放）。
                onDismissComments = {
                    comments = null
                    if (portraitComments) PlayerUiState.fullscreen = true
                },
                // Tab 条在覆盖层，通过这个单向通道拿到详情里的 stat.reply。
                onCommentCount = { ui.commentCount.value = it },
            ),
            pager = pager,
        )
    }, comments = { c, onBack ->
        PortraitCommentSlot(c, onBack, portraitComments, collapseOnJump = portraitComments && !pageFullscreen)
    })
}

/**
 * 竖屏评论面板里挂的评论区。
 *
 * [portraitComments] 同时决定沉浸与跳过状态栏留白：面板自己已经在状态栏下面，
 * 再补一次状态栏高度会让首条评论凭空下移。
 * [collapseOnJump] 只在「面板压着播放器」时打开：点时间戳先收面板回到画面，
 * 铺满整页的评论区（以及已经全屏的形态）不能跟着收。
 */
@Composable
private fun PortraitCommentSlot(
    video: com.aritxonly.bdfun.core.model.video.VideoDetail, onBack: () -> Unit,
    portraitComments: Boolean, collapseOnJump: Boolean,
) {
    val page = LocalVideoPageState.current
    val target = com.aritxonly.bdfun.core.domain.videoCommentTarget(video)
    key(video.bvid, video.aid, target.oid, target.type) {
        CommentTimestampJumpHost(onJump = { positionMs ->
            page.requestSeek(positionMs)
            if (collapseOnJump) onBack()
        }) {
            CommentScreen(
                request = CommentScreenRequest(
                    aid = video.aid, upMid = if (video.isPgc) 0L else video.ownerMid,
                    title = video.title, bvid = video.bvid,
                    commentOid = target.oid, commentType = target.type,
                ),
                onBack = onBack,
                // 竖屏面板顶部不再让状态栏留白，留白由面板自己的把手顶上。
                layout = CommentScreenLayout(
                    flatList = true,
                    insets = CommentInsetPolicy(
                        scrollTop = true,
                        immersiveStatusBar = portraitComments,
                        skipStatusInset = portraitComments,
                    ),
                ),
            )
        }
    }
}
