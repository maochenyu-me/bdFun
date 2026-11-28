package com.aritxonly.bdfun.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.pager.PagerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue

@Composable
fun BindVideoDetailEffects(
    request: VideoScreenRequest, content: VideoScreenContent,
    holders: VideoScreenStateHolders, actions: VideoDetailViewActions, pager: PagerState,
    load: VideoDetailLoadPorts, ports: VideoDetailEffectPorts, autoRotate: Boolean,
    /** Activity 侧看到的独占全屏状态；竖屏评论面板收起后会要求它变 true。 */
    requestedFullscreen: Boolean = false, pictureInPicture: Boolean = false,
    /** 当前视频是竖屏：全屏仍留在竖屏窗口，不必等转屏。 */
    portraitVideo: Boolean = false,
) {
    val detail = content.read.detail
    val currentCid = content.selection.cid
    val controllers = holders.controllers
    val ui = holders.ui
    // 返回键按「意图」判断：转屏那几百毫秒里呈现值还没翻，读它会让返回直接退出视频页。
    var fullscreen by ui.fullscreenIntent
    BindVideoAuthor(detail, content.loggedIn, holders.metadata, controllers.interaction, ui, holders.description)
    LaunchedEffect(content.interaction) {
        val current = content.interaction
        val d = current.detail ?: return@LaunchedEffect
        controllers.readCache.updateVideo(d.bvid) { cached ->
            cached.copy(
                interaction = cached.interaction.copy(liked = current.liked, coined = current.coined,
                    favorited = current.favorited),
                counts = cached.counts.copy(like = current.likeCount, coin = current.coinCount,
                    favorite = current.favoriteCount),
            )
        }
    }
    BackHandler {
        when {
            fullscreen -> fullscreen = false
            else -> actions.leaveVideo()
        }
    }
    LoadInitialVideo(request.video, request.defaultQuality, request.epId, request.seasonId, controllers, ui, load)
    BindVideoPageMetadata(detail, currentCid, content.loggedIn, holders.metadata, controllers.interaction, ui)
    RegisterVideoDanmakuSender(detail, currentCid, holders.metadata, controllers.detail, ports)
    BindVideoFullscreenState(
        ui, ports.publishFullscreen, autoRotate, requestedFullscreen, pictureInPicture,
        listenMode = ports.listenMode(),
        // 竖屏视频全屏仍是竖屏窗口，没有转屏可等，按意图立即铺满。
        portraitVideo = portraitVideo,
        publishIntent = ports.publishFullscreenIntent,
    )
    BindBackgroundVideoMetadata(request.video, detail, currentCid, content.quality, ports)
    ReconcileVideoPager(pager)
}
