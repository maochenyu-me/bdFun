package com.aritxonly.bdfun.ui

import android.app.Activity
import android.content.res.Configuration
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import com.aritxonly.bdfun.core.domain.FULLSCREEN_PRESENTATION_GRACE_MS
import com.aritxonly.bdfun.core.domain.TabletSmallestWidthDp
import com.aritxonly.bdfun.core.domain.videoFullscreenPresented
import kotlinx.coroutines.delay
import kotlin.math.roundToInt

@Composable
fun BindVideoFullscreenState(
    ui: VideoDetailUiStateHolder, publish: (Boolean) -> Unit, autoRotate: Boolean,
    requestedFullscreen: Boolean = false, pictureInPicture: Boolean = false,
    /** 听视频独占整屏：横竖屏都不该被「转回竖屏就退出全屏」收回半屏。 */
    listenMode: Boolean = false,
    /** 当前视频是竖屏：全屏仍是竖屏窗口，没有转屏可等。 */
    portraitVideo: Boolean = false,
    /** 把意图同步给 Activity：Activity 侧据此请求朝向、切系统栏。 */
    publishIntent: (Boolean) -> Unit = {},
) {
    var fullscreen by ui.fullscreen
    var coinSheet by ui.coinSheet
    var favSheet by ui.favSheet
    // 独占全屏状态共享给 Activity；横屏分栏保留 tab / 回复栏。
    LaunchedEffect(fullscreen) { publish(fullscreen) }
    // 意图也要同步出去：Activity 侧写它的时机（面板收起回播放器）先于呈现值落地。
    val intent = ui.fullscreenIntent.value
    LaunchedEffect(intent) { publishIntent(intent) }
    // Activity 侧主动要求进独占全屏（竖屏评论面板收起后回到播放器）时落回页面自己的状态。
    // 这条只是跟随外部请求：两侧平时由上面的 publish 保持一致，画中画不参与，
    // 否则 PiP 会被当成展开的独占全屏。
    LaunchedEffect(requestedFullscreen, pictureInPicture) {
        // 只在值真的不同时回写：两侧平时由 publishIntent 保持一致，
        // 无条件写会形成「页面写 Activity → Activity 回写页面」的自我触发。
        if (!pictureInPicture && ui.fullscreenIntent.value != requestedFullscreen) {
            ui.fullscreenIntent.value = requestedFullscreen
        }
    }
    // 意图 → 呈现：窗口真的到目标朝向后才铺满，见 VideoFullscreenPresentation。
    val presented = VideoFullscreenPresentation(intent = intent, portraitVideo = portraitVideo)
    LaunchedEffect(presented) { fullscreen = presented }
    // 独占全屏时收回投币 / 收藏弹层。
    LaunchedEffect(fullscreen) {
        if (fullscreen) {
            coinSheet = false
            favSheet = false
        }
    }
    // 横屏页面布局和播放器独占全屏分别管理。
    val configuration = androidx.compose.ui.platform.LocalConfiguration.current
    val landscape = configuration.orientation == android.content.res.Configuration.ORIENTATION_LANDSCAPE
    // 横屏宽度会把直板机也判成分栏；用不随旋转交换的最小宽度区分紧凑设备。
    val smallestWidthDp = configuration.smallestScreenWidthDp
    LaunchedEffect(landscape, smallestWidthDp, listenMode) {
        com.aritxonly.bdfun.core.domain.fullscreenAfterVideoRotation(
            landscape, smallestWidthDp, autoRotate, listenMode,
        )?.let { ui.fullscreenIntent.value = it }
    }
}

/**
 * 「页面该按全屏画了吗」：全屏意图已成立、且窗口真的到了目标朝向。
 *
 * 横屏视频从竖屏点展开时，点击只改状态，系统转屏要几百毫秒；这段时间窗口还是竖的，
 * 立刻按全屏画就会闪一帧「竖屏窗口、视频缩在中间、骨架已隐藏」。这里让呈现值等窗口到位，
 * 使骨架、播放器视口、Activity 覆盖层在同一帧一起翻。
 *
 * 判定交给 `videoFullscreenPresented`；本函数只负责读配置、读紧凑手机/多窗口，
 * 以及 [FULLSCREEN_PRESENTATION_GRACE_MS] 这个朝向请求没生效时的安全阀。
 */
@Composable
fun VideoFullscreenPresentation(intent: Boolean, portraitVideo: Boolean): Boolean {
    val configuration = LocalConfiguration.current
    val windowLandscape = configuration.orientation == Configuration.ORIENTATION_LANDSCAPE
    val smallestWidthDp = configuration.smallestScreenWidthDp
    val compactPhone = smallestWidthDp in 1 until TabletSmallestWidthDp
    val multiWindow = (LocalContext.current as? Activity)?.isInMultiWindowMode == true
    // remember(intent) 随意图重建，退出全屏时自然回到 false，不需要额外重置。
    var graceElapsed by remember(intent) { mutableStateOf(false) }
    LaunchedEffect(intent) {
        if (!intent) return@LaunchedEffect
        delay(FULLSCREEN_PRESENTATION_GRACE_MS)
        graceElapsed = true
    }
    return videoFullscreenPresented(
        intent = intent,
        windowLandscape = windowLandscape,
        portraitVideo = portraitVideo,
        compactPhone = compactPhone,
        multiWindow = multiWindow,
        rotationGraceElapsed = graceElapsed,
    )
}

@Composable
fun ReconcileVideoPager(pager: androidx.compose.foundation.pager.PagerState) {
    // 兜底：极少数情况下 pager 停在上一次拖动的中间位置，
    // 松手后强制吸附到最近的一页，避免停在 0.3 / 0.7 这种位置。
    LaunchedEffect(pager) {
        snapshotFlow {
            pager.isScrollInProgress to pager.currentPageOffsetFraction
        }.collect { (scrolling, offset) ->
            if (!scrolling && kotlin.math.abs(offset) > 0.02f) {
                val target = (pager.currentPage + offset)
                    .roundToInt()
                    .coerceIn(0, pager.pageCount - 1)
                runCatching { pager.animateScrollToPage(target) }
            }
        }
    }

}