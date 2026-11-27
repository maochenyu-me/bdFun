package com.aritxonly.bdfun.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.key
import com.aritxonly.bdfun.navigateToVideo
import com.aritxonly.bdfun.navigateToSpace

@Composable
internal fun androidx.compose.foundation.layout.BoxScope.VideoScreenPager(
    request: VideoScreenRequest, presentation: VideoScreenPresentation,
    pager: androidx.compose.foundation.pager.PagerState, inlineLayout: VideoInlineLayout,
    pageUi: VideoDetailUiStateHolder, description: VideoDescriptionStateHolder,
    content: VideoScreenContent, actions: VideoDetailViewActions,
) {
    val page = LocalVideoPageState.current
    val context = androidx.compose.ui.platform.LocalContext.current
    val renderActions = VideoDetailRenderActions(
        VideoMetadataActions(actions::toggleFollow, actions::toggleLike, actions::toggleDislike,
            actions::giveCoin, actions::toggleFavorite, actions::shareVideo, actions::tripleAction,
            onOpenSpace = {
                page.pauseAll()
                navigateToSpace(context, content.read.detail?.ownerMid ?: content.video.ownerMid)
            }),
        onPage = actions::switchPage,
        onRelated = { page.pauseAll(); navigateToVideo(context, it, fromVideoPage = true) },
    )
    VideoDetailContentPager(request, presentation, pager, inlineLayout, pageUi, description,
        content, renderActions, VideoDetailPagerSlots(
            metadata = {
                val metadata = rememberVideoMetadataAssembly()
                VideoMetadataVisuals(metadata.appearance, metadata.slots)
            },
            tail = rememberVideoDetailTailSlots(), comments = { pane ->
                VideoScreenComments(pane, scrollTop = pager.currentPage == 1)
            },
        ))
}

@Composable
private fun VideoScreenComments(pane: VideoCommentPane, scrollTop: Boolean) {
    val d = pane.detail
    val page = LocalVideoPageState.current
    // PGC 评论挂在 epId 上；换集后 bvid/aid/oid 都会变，显式换 key，评论列表、滚动位置、楼中楼全部从新集重建。
    val target = com.aritxonly.bdfun.core.domain.videoCommentTarget(d)
    key(d.bvid, d.aid, target.oid, target.type) {
        // 评论 tab / 横屏分栏：播放器一直在画面上，跳转后不用收任何东西。
        CommentTimestampJumpHost(onJump = { positionMs -> page.requestSeek(positionMs) }) {
            CommentScreen(
                request = CommentScreenRequest(aid = d.aid, upMid = if (d.isPgc) 0L else d.ownerMid,
                    title = d.title, bvid = d.bvid,
                    commentOid = target.oid, commentType = target.type,
                    focusRootRpid = pane.root, focusTargetRpid = pane.target),
                onBack = {},
                layout = CommentScreenLayout(bare = true, flatList = true, topInset = pane.topInset,
                    trackBackProgress = pane.trackBackProgress, threadPlayerBottom = pane.threadPlayerBottom,
                    threadPaneWidth = pane.threadPaneWidth, playerFullscreen = pane.fullscreen,
                    compactHeaderSpacing = pane.compactHeaderSpacing,
                    // 竖屏：topInset 已是「tab 行底边 + 12dp」的绝对偏移（含状态栏），列表不能再补，
                    // 否则沉浸页里 statusBarsPadding() 给 0dp、上一层又减了状态栏，两处互相打架。
                    // 横屏分栏：topInset 只有 52dp 行高，状态栏留白仍由列表自己补，保持原样。
                    insets = CommentInsetPolicy(
                        scrollTop = scrollTop,
                        immersiveStatusBar = !pane.splitPanes,
                        skipStatusInset = !pane.splitPanes,
                    )),
            )
        }
    }
}
