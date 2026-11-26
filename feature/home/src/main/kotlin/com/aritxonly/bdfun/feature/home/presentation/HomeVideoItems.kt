package com.aritxonly.bdfun.feature.home.presentation

import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyGridScope
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.geometry.Rect
import com.aritxonly.bdfun.core.model.video.VideoItem
import com.aritxonly.teak.common.navigation.TeakMorphMenuState

/**
 * @param videos 网格要画的条目（轮播项已由调用方剔除）。
 * @param dividerIndex 旧内容第一项的下标，-1 表示本轮不加分隔条。
 */
internal fun LazyGridScope.homeVideoItems(
    state: HomeFeedState, videos: List<VideoItem>, largeCards: Boolean, ui: HomePresentationStateHolder,
    onOpenVideo: (VideoItem) -> Unit, host: HomePresentationHost,
    dividerIndex: Int = -1, onDividerClick: () -> Unit = {},
) {
    val card: @Composable (VideoItem) -> Unit = { video ->
        // 每张卡片独立锚点：Miuix 的 anchor 只记录最后一次 bounds。
        val cardMenu = remember(video.bvid) { TeakMorphMenuState() }
        // 长按：从手指位置长出来，不画省略号副本；省略号：从图标长出来。
        val openLongPress: (Rect) -> Unit = { point ->
            state.menuVideo = video
            state.menuState = ui.pointMenuState
            ui.pressPoint = androidx.compose.ui.geometry.Offset(point.left, point.top)
            ui.openPointMenu = true
        }
        host.card(
            video, largeCards,
            { state.open(video); onOpenVideo(video) },
            openLongPress,
            openLongPress,
            cardMenu,
            {
                state.menuVideo = video
                state.menuState = cardMenu
            },
        )
    }
    if (dividerIndex in 1 until videos.size) {
        items(items = videos.subList(0, dividerIndex), key = { it.bvid }) { card(it) }
        item(key = "refresh-divider", span = { GridItemSpan(maxLineSpan) }) {
            HomeOldContentDivider(onClick = onDividerClick)
        }
        items(items = videos.subList(dividerIndex, videos.size), key = { it.bvid }) { card(it) }
        return
    }
    items(items = videos, key = { it.bvid }) { card(it) }
}
