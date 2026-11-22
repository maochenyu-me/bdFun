package com.aritxonly.bdfun.feature.home.presentation

import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyGridScope
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.runtime.Composable
import androidx.compose.ui.geometry.Rect
import com.aritxonly.bdfun.core.model.video.VideoItem

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
        // 长按与省略号共用同一份锚点与菜单状态。
        val openMenu: (Rect) -> Unit = { anchor ->
            ui.actionAnchor = anchor
            ui.actionVideo = video
        }
        host.card(video, largeCards, { state.open(video); onOpenVideo(video) }, openMenu, openMenu)
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
