package com.aritxonly.bdfun.feature.home.presentation

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.aritxonly.bdfun.core.domain.resolveHomeHeroItemLimit
import com.aritxonly.bdfun.core.model.video.VideoItem
import com.aritxonly.teak.common.theme.TeakStyle
import com.aritxonly.teak.common.theme.TeakUiStyle
import kotlin.math.roundToInt

@Composable
internal fun HomeVideoGrid(
    state: HomeFeedState, largeCards: Boolean,
    width: androidx.compose.ui.unit.Dp, statusBarTop: androidx.compose.ui.unit.Dp,
    ui: HomePresentationStateHolder, refreshConnection: NestedScrollConnection,
    scope: kotlinx.coroutines.CoroutineScope, onOpenVideo: (VideoItem) -> Unit, host: HomePresentationHost,
    heroOptions: HomeHeroOptions,
    refreshOptions: HomeRefreshOptions = HomeRefreshOptions(),
    onDividerClick: () -> Unit = {},
) {
    // 哔哩哔哩布局的分段 Tab 和首行卡片之间再多留 5dp，和原版间距对齐。
    val sectionGap = if (TeakUiStyle.current == TeakStyle.Official) 5.dp else 0.dp
    // 轮播挑走的条目会从网格里排除，刷新后必须给新增内容留够网格位，否则一刷新就只剩两三张新卡。
    val heroLimit = resolveHomeHeroItemLimit(state.oldContentStartIndex, HomeHeroMaxItems)
    val hero = remember(state.videos, heroOptions.enabled, heroLimit) {
        if (heroOptions.enabled && heroLimit > 0) {
            selectHomeHeroVideos(state.videos).take(heroLimit)
        } else {
            emptyList()
        }
    }
    val displayed = remember(state.videos, hero) { excludeHomeHeroVideos(state.videos, hero) }
    val dividerIndex = resolveHomeRefreshDividerIndex(state, displayed, refreshOptions)
    // 定位胶囊要能滚到这里：网格 item 下标 = 前置的轮播项 + 分隔条之前的条目数。
    val dividerItemIndex = if (dividerIndex < 0) -1 else dividerIndex + if (hero.isEmpty()) 0 else 1
    androidx.compose.runtime.SideEffect { ui.refreshDividerIndex = dividerItemIndex }
    LazyVerticalGrid(
        columns = if (width >= 600.dp) host.gridCells(180.dp, 260.dp) else GridCells.Fixed(if (largeCards) 1 else 2),
        state = state.gridState,
        modifier = Modifier
            .fillMaxSize()
            .offset { IntOffset(0, ui.pullOffset.roundToInt()) }
            .nestedScroll(refreshConnection),
        contentPadding = PaddingValues(
            start = 12.dp,
            top = host.topBarHeight + statusBarTop + 6.dp + sectionGap,
            end = 12.dp,
            // 让最后一排卡片能滚出悬浮底栏
            bottom = 104.dp,
        ),
        horizontalArrangement = Arrangement.spacedBy(10.dp, Alignment.CenterHorizontally),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        if (state.loading && state.videos.isEmpty()) {
            homeFeedSkeletonItems()
        }
        if (hero.isNotEmpty()) {
            item(key = "hero", span = { GridItemSpan(maxLineSpan) }) {
                HomeHeroCarousel(
                    videos = hero,
                    autoplay = heroOptions.autoplay,
                    onOpen = { video ->
                        state.open(video)
                        onOpenVideo(video)
                    },
                    preview = host.heroPreview,
                )
            }
        }
        homeVideoItems(state, displayed, largeCards, ui, onOpenVideo, host, dividerIndex, onDividerClick)
        if (state.videos.isNotEmpty()) {
            item(key = "footer", span = { GridItemSpan(maxLineSpan) }) { HomeFeedFooter(state, scope) }
        }
    }

}
