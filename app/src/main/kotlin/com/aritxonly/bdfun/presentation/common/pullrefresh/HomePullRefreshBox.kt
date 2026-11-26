package com.aritxonly.bdfun.presentation.common.pullrefresh

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.aritxonly.bdfun.feature.home.presentation.PullRefreshSession
import com.aritxonly.bdfun.feature.home.presentation.PullRefreshSettleEffect
import com.aritxonly.bdfun.feature.home.presentation.RefreshToTopEffect
import com.aritxonly.bdfun.ui.HomePullIndicator
import kotlin.math.roundToInt

/**
 * 首页同款下拉刷新外壳：[PullRefreshSession] + App 层小电视 + 回顶/收尾时序。
 *
 * 消息等独立 Activity 页没有主壳的 `MainHomeChrome`，所以由这个外壳自己画小电视；
 * 手势、阻尼、阈值、收尾时序仍走首页那一份实现，不再各页用 M3 的 PullToRefreshBox。
 *
 * [content] 拿到的是已经挂好下拉位移和 nestedScroll 的 Modifier，页面直接交给自己的列表。
 */
@Composable
internal fun HomePullRefreshBox(
    refreshing: Boolean,
    topPadding: Dp,
    canScrollBackward: () -> Boolean,
    isAtTop: () -> Boolean,
    scrollToTop: suspend () -> Unit,
    onRefresh: suspend () -> Unit,
    modifier: Modifier = Modifier,
    refreshRequest: Int = 0,
    content: @Composable (Modifier) -> Unit,
) {
    val density = LocalDensity.current
    val offsetPx = remember { mutableFloatStateOf(0f) }
    val latestRefreshing by rememberUpdatedState(refreshing)
    val latestCanScrollBackward by rememberUpdatedState(canScrollBackward)
    val latestOnRefresh by rememberUpdatedState(onRefresh)
    val session = remember(density) {
        PullRefreshSession(
            thresholdPx = with(density) { 88.dp.toPx() },
            maxPullPx = with(density) { 260.dp.toPx() },
            density = density,
            currentOffset = { offsetPx.floatValue },
            onPull = { px, _, _ -> offsetPx.floatValue = px },
            canScrollBackward = { latestCanScrollBackward() },
            isRefreshing = { latestRefreshing },
            onRefresh = { latestOnRefresh() },
        )
    }
    // “再点一次当前入口”的回顶刷新；消息页目前没有这个入口，默认 request=0 不触发。
    RefreshToTopEffect(
        request = refreshRequest,
        pullThresholdPx = session.thresholdPx,
        animatePull = { target, duration -> session.animatePullTo(target, duration) },
        isBusy = { latestRefreshing },
        onPulling = { _ -> },
        isAtTop = isAtTop,
        scrollToTop = scrollToTop,
        refresh = { latestOnRefresh() },
    )
    PullRefreshSettleEffect(
        refreshing = refreshing,
        currentOffset = { offsetPx.floatValue },
        onSettledStart = {},
        animatePull = { target, duration -> session.animatePullTo(target, duration) },
    )
    Box(modifier) {
        content(
            Modifier
                .offset { IntOffset(0, offsetPx.floatValue.roundToInt()) }
                .nestedScroll(session.connection()),
        )
        // 下拉位移是拖拽回调每帧写入的：位移本身在 offset 里绘制期读，
        // 小电视的淡入/位移也走绘制期 lambda，拖动时不再每帧重组指示器。
        // 显隐单独用 derivedStateOf 只在越过阈值那一刻重组一次；
        // 不可见时不合成指示器，避免语义节点常驻。
        val indicatorVisible by remember(offsetPx) {
            androidx.compose.runtime.derivedStateOf {
                ((offsetPx.floatValue - 58f) / 26f) > 0.005f
            }
        }
        if (latestRefreshing || indicatorVisible) {
            HomePullIndicator(
                reveal = {
                    if (latestRefreshing) 1f
                    else with(density) { ((offsetPx.floatValue.toDp().value - 58f) / 26f) }
                        .coerceIn(0f, 1f)
                },
                animated = latestRefreshing,
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .statusBarsPadding()
                    .padding(top = topPadding),
            )
        }
    }
}
