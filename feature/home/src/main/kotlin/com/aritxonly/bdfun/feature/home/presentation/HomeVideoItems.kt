package com.aritxonly.bdfun.feature.home.presentation

import androidx.compose.foundation.lazy.grid.items
import androidx.compose.ui.geometry.Rect
import com.aritxonly.bdfun.core.model.video.VideoItem

/** @param hero 已经在顶部轮播展示过的条目，网格里不再重复。 */
internal fun androidx.compose.foundation.lazy.grid.LazyGridScope.homeVideoItems(
    state: HomeFeedState, hero: List<VideoItem>, largeCards: Boolean, ui: HomePresentationStateHolder,
    onOpenVideo: (VideoItem) -> Unit, host: HomePresentationHost,
) {
    val videos = excludeHomeHeroVideos(state.videos, hero)
    items(items = videos, key = { it.bvid }) { video ->
        // 长按与省略号共用同一份锚点与菜单状态。
        val openMenu: (Rect) -> Unit = { anchor ->
            ui.actionAnchor = anchor
            ui.actionVideo = video
        }
        host.card(video, largeCards, { state.open(video); onOpenVideo(video) }, openMenu, openMenu)
    }

}
