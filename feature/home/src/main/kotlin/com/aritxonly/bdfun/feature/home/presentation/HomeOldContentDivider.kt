package com.aritxonly.bdfun.feature.home.presentation

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.aritxonly.bdfun.core.domain.shouldShowHomeOldContentDivider
import com.aritxonly.bdfun.core.model.video.VideoItem
import top.yukonga.miuix.kmp.theme.MiuixTheme

/**
 * 本次刷新后「新内容」与「旧内容」之间的分隔条。
 * 点击回到顶部并再刷新一次，和参考实现一致。
 */
@Composable
internal fun HomeOldContentDivider(onClick: () -> Unit, modifier: Modifier = Modifier) {
    val colors = MiuixTheme.colorScheme
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(colors.secondaryContainer.copy(alpha = 0.72f))
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 10.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = "上次刷新到这里，点击回顶刷新",
            color = colors.onSecondaryContainer,
            fontSize = 13.sp,
            fontWeight = FontWeight.SemiBold,
            textAlign = TextAlign.Center,
        )
        Text(
            text = "以下是之前的内容",
            color = colors.onSecondaryContainer.copy(alpha = 0.8f),
            fontSize = 11.sp,
            textAlign = TextAlign.Center,
        )
    }
}

/**
 * 分隔条在网格里的落点：以「旧内容第一条」为准，在去掉轮播重复项后的列表里定位。
 * 返回 -1 表示本轮不显示分隔条（含旧内容正好排在最前面、没有可分隔的新内容）。
 *
 * @param displayed 网格实际要画的条目（已去掉轮播项）
 */
internal fun resolveHomeRefreshDividerIndex(
    state: HomeFeedState,
    displayed: List<VideoItem>,
    options: HomeRefreshOptions,
): Int {
    val oldStart = state.oldContentStartIndex
    val show = shouldShowHomeOldContentDivider(
        keepOldContent = options.keepOldContent,
        locatorEnabled = options.locatorEnabled,
        refreshKey = state.refreshKey,
        revealedRefreshKey = state.refreshKey,
        oldContentStartIndex = oldStart,
    )
    if (!show) return -1
    val firstOldBvid = state.videos.getOrNull(oldStart)?.bvid ?: return -1
    val index = displayed.indexOfFirst { it.bvid == firstOldBvid }
    return if (index <= 0) -1 else index
}
