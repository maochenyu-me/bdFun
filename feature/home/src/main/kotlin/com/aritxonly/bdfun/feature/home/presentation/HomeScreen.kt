package com.aritxonly.bdfun.feature.home.presentation

import androidx.compose.foundation.layout.BoxWithConstraints

import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.statusBars
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import com.aritxonly.bdfun.core.domain.HOME_REFRESH_UNDO_TIMEOUT_MS
import com.aritxonly.bdfun.core.model.video.VideoItem
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** 尚未点掉定位胶囊的那一轮刷新；同 key 关闭后不再出现。 */
private const val TIP_VISIBLE_MILLIS = 4_000L

@Composable
fun HomeScreen(
    state: HomeFeedState,
    onOpenVideo: (VideoItem) -> Unit,
    cardStyle: Int = 0,
    host: HomePresentationHost,
    interactive: Boolean = true,
    hero: HomeHeroOptions = HomeHeroOptions(),
    refresh: HomeRefreshOptions = HomeRefreshOptions(),
    onLocatorPositionChanged: (Float, Float) -> Unit = { _, _ -> },
) {
    val scope = rememberCoroutineScope()
    val density = LocalDensity.current
    val ui = remember { HomePresentationStateHolder() }
    val pull = PullRefreshSession(
        thresholdPx = with(density) { 88.dp.toPx() },
        maxPullPx = with(density) { 260.dp.toPx() },
        density = density,
        currentOffset = { ui.pullOffset },
        onPull = { px, dp, fraction ->
            ui.pullOffset = px
            state.pullOffsetDp = dp
            state.pullFraction = fraction
        },
        canScrollBackward = { state.gridState.canScrollBackward },
        isRefreshing = { state.refreshing || state.loading },
        onRefresh = {
            state.pullFraction = 1f
            state.refresh()
        },
        onCanceled = { state.pullFraction = 0f },
    )
    val statusBarTop = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    HomeInstanceEffect(host)
    HomeFeedEffects(state, ui, pull)
    HomeExposureEffect(state, interactive && ui.actionVideo == null)
    val refreshConnection = remember(state) { pull.connection() }

    // 定位胶囊：本轮确实保留了旧内容才出现；点掉或超时后本次刷新不再弹。
    var locatorDismissedKey by remember { mutableIntStateOf(0) }
    val locatorVisible = refresh.locatorEnabled &&
        state.oldContentStartIndex > 0 &&
        state.refreshKey > locatorDismissedKey
    LaunchedEffect(state.refreshKey) {
        if (state.newItemsTipVisible) {
            delay(TIP_VISIBLE_MILLIS)
            state.dismissNewItemsTip()
        }
    }
    LaunchedEffect(state.undoAvailable) {
        if (state.undoAvailable) {
            delay(HOME_REFRESH_UNDO_TIMEOUT_MS)
            state.dismissUndo()
        }
    }

    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        if (ui.pressPoint != androidx.compose.ui.geometry.Offset.Zero) {
            com.aritxonly.teak.common.navigation.TeakMorphPointAnchor(ui.pointMenuState, ui.pressPoint)
        }
        HomeVideoGrid(
            state, cardStyle == 1, maxWidth, statusBarTop, ui, refreshConnection, scope, onOpenVideo, host, hero,
            refreshOptions = refresh,
            onDividerClick = {
                // 「回顶刷新」：先回到顶部，再按一次刷新（会重新计算保留内容与分隔条）。
                scope.launch {
                    state.gridState.animateScrollToItem(0)
                    state.refresh()
                }
            },
        )
        HomeRefreshNewItemsTip(
            count = state.newItemCount,
            visible = state.newItemsTipVisible,
            topPadding = host.topBarHeight + statusBarTop + 6.dp,
        )
        HomeRefreshOverlays(state, ui, refresh, scope, locatorVisible, onLocatorPositionChanged) {
            locatorDismissedKey = state.refreshKey
        }
        HomeFeedStatus(state, scope, host)
    }
    androidx.compose.runtime.LaunchedEffect(ui.openPointMenu, state.menuVideo) {
        if (ui.openPointMenu) {
            androidx.compose.runtime.withFrameNanos { }
            androidx.compose.runtime.withFrameNanos { }
            ui.pointMenuState.openWithAnchorCopy(false)
            ui.openPointMenu = false
        }
    }
}

/**
 * 刷新相关的浮层：定位胶囊 + 撤销胶囊。
 *
 * 这两颗胶囊共用同一套锚点（用户拖过的位置），也共用「本轮刷新」的身份，
 * 所以一起组装；拆开会让 HomeScreen 的主体再涨过行数上限。
 */
@Composable
private fun HomeRefreshOverlays(
    state: HomeFeedState,
    ui: HomePresentationStateHolder,
    refresh: HomeRefreshOptions,
    scope: kotlinx.coroutines.CoroutineScope,
    locatorVisible: Boolean,
    onLocatorPositionChanged: (Float, Float) -> Unit,
    onLocatorDismissed: () -> Unit,
) {
    HomeRefreshLocatorPill(
        refreshKey = state.refreshKey,
        visible = locatorVisible,
        x = refresh.locatorX,
        y = refresh.locatorY,
        durationSeconds = refresh.locatorDurationSeconds,
        onClick = {
            val target = ui.refreshDividerIndex
            if (target >= 0) scope.launch { state.gridState.animateScrollToItem(target) }
        },
        onDismiss = onLocatorDismissed,
        onPositionChanged = onLocatorPositionChanged,
    )
    HomeRefreshUndoPill(
        visible = refresh.undoEnabled && state.undoAvailable,
        anchorX = refresh.locatorX,
        anchorY = refresh.locatorY,
        onUndo = { state.undoRefresh() },
        onDismiss = { state.dismissUndo() },
    )
}

@Composable
private fun HomeInstanceEffect(host: HomePresentationHost, interactive: Boolean = true) {
    androidx.compose.runtime.DisposableEffect(Unit) {
        host.onEnter()
        onDispose {
            host.onExit()
        }
    }
}
