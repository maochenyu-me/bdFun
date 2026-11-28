package com.aritxonly.bdfun.ui

import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
internal fun VideoActivityContent(
    isLoggedIn: () -> Boolean, settings: com.aritxonly.bdfun.feature.settings.presentation.SettingsBindings,
    route: com.aritxonly.bdfun.core.navigation.Route.Video?, requestedBvid: String?, requestedAid: Long,
    pipMode: Boolean, host: VideoActivityHostActions,
) {
    val page = LocalVideoPageState.current
    val video = rememberVideoActivityEntry(route?.snapshot, requestedBvid, requestedAid, host, page.dependencies.controllers) ?: return
    val ui = remember { VideoActivityPageStateHolder(page.autoNextRequest) }
    BindVideoActivityQueue(video, ui, host, page.dependencies.queue, { page.autoNextRequest }, page.dependencies.log)
    val pipWindow = pipMode
    // 用户点了展开就是全屏：朝向、清晰度、系统栏都立刻按全屏走，不能等。
    val fullscreen = page.playerUi.fullscreen || pipWindow
    // 但「画面铺满」要等窗口真的转过去。横屏视频从竖屏点展开时系统转屏要几百毫秒，
    // 抢先在竖屏窗口里铺满会闪一帧「竖屏全屏、视频缩在中间」。
    val layoutFullscreen = rememberLayoutFullscreen(requestedFullscreen = fullscreen, pictureInPicture = pipWindow)
    val alpha = rememberManualProgress(if (layoutFullscreen) 0f else 1f, 200f)
    val commentRequest = remember { Triple(route?.commentRoot ?: 0L, route?.commentTarget ?: 0L, route?.seekMs ?: 0L) }
    val pager = androidx.compose.foundation.pager.rememberPagerState(
        initialPage = if (commentRequest.first > 0L) 1 else 0, pageCount = { 2 },
    )
    val scope = rememberCoroutineScope()
    val configuration = androidx.compose.ui.platform.LocalConfiguration.current
    val screenWidthDp = configuration.screenWidthDp
    val smallestWidthDp = configuration.smallestScreenWidthDp
    val statusBarDp = rememberStatusBarTop()
    val playerHeightDp = (screenWidthDp * 9f / 16f).dp
    val immersiveStatus = settings.videoImmersiveStatusBar
    val playerTopInset = if (immersiveStatus) 0.dp else statusBarDp
    BindVideoActivityWindow(immersiveStatus, ui, host)
    // Tab 玻璃只盖在播放器下方，采不到视频像素；单独给它一份“播放器以下内容”的底片，
    // 避免整个视频层被卷进离屏合成。渐变遮罩仍在录制层里，模糊结果和原来一致。
    val contentBackground = com.aritxonly.teak.common.theme.LocalTeakContentColors.current.background
    val contentBackdrop = top.yukonga.miuix.kmp.blur.rememberLayerBackdrop {
        drawRect(contentBackground)
        drawContent()
    }
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val paneLayout = resolveVideoPaneLayout(maxWidth.value, maxHeight.value, smallestWidthDp)
        val layout = VideoActivityLayout(paneLayout, statusBarDp, fullscreen, pipMode, alpha, playerTopInset, layoutFullscreen)
        androidx.compose.runtime.SideEffect { host.syncSystemBars(layout.splitPanes) }
        TeakActivityScaffold(
            settings = settings,
            onBack = { host.onBack() },
            options = ActivityPageOptions(
                backRevealed = false,
                showBackButton = false,
                showTopBar = false,
                // 铺满时评论页不可见，回到顶部控件也必须一起消失。
                showScrollTop = !pipMode && !layoutFullscreen,
            ),
            disc = ActivityDiscOptions(
                showBackgroundDisc = false,
            ),
            slots = ActivityPageSlots(
                overlay = { VideoActivityOverlay(layout, ui, pager, scope, contentBackdrop) },
            ),
        ) {
            VideoActivityBody(isLoggedIn, settings,
                VideoActivityScene(video, commentRequest, route?.epId ?: 0L, route?.seasonId ?: 0L),
                layout, ui, pager, scope, host,
                contentBackdrop = contentBackdrop)
        }
    }
}
