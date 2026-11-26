package com.aritxonly.bdfun.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.staggeredgrid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

@Composable
internal fun androidx.compose.foundation.layout.BoxScope.FeedIndicators(state: FeedState, statusTop: Dp) {
    // 首页同款下拉小电视：只在真的下拉时出现，不压头像卡片。
    // 下拉位移是拖拽回调每帧写入的，淡入/位移走绘制期 lambda 不触发重组；
    // 显隐只在越过阈值那一刻重组一次，不可见时不合成指示器（避免语义节点常驻）。
    val indicatorVisible by androidx.compose.runtime.remember(state) {
        androidx.compose.runtime.derivedStateOf {
            state.refreshing || ((state.pullOffsetDp - 58f) / 26f) > 0.005f
        }
    }
    if (indicatorVisible) {
        HomePullIndicator(
            reveal = {
                if (state.pullOffsetDp <= 1f) {
                    0f
                } else if (state.refreshing) {
                    1f
                } else {
                    ((state.pullOffsetDp - 58f) / 26f).coerceIn(0f, 1f)
                }
            },
            animated = state.refreshing,
            modifier = Modifier
                .align(Alignment.TopCenter)
                .padding(top = statusTop + FeedTopBarHeight + 4.dp),
        )
    }

    if (state.refreshing && state.items.isEmpty()) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            TeakLoading(size = 44.dp)
        }
    }
}
