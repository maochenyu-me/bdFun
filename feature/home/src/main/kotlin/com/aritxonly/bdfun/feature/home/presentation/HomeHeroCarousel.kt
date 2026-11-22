package com.aritxonly.bdfun.feature.home.presentation

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.aritxonly.bdfun.core.common.formatCount
import com.aritxonly.bdfun.core.common.formatDuration
import com.aritxonly.bdfun.core.model.video.VideoItem
import com.aritxonly.teak.common.chrome.TeakContinuousCornerShape

/** 圆角沿用首页卡片的 16dp 连续曲率轮廓，轮播是同一视觉族里更大的一张卡。 */
private val HomeHeroShape = TeakContinuousCornerShape(16.dp)

private val HomeHeroTitleShadow = Shadow(Color(0xB8000000), Offset(0f, 2f), 5f)

/**
 * 推荐页顶部轮播：横向翻页 + 折角堆叠，卡片上覆盖标题、UP 主与统计信息。
 *
 * @param preview 自动播放时叠在封面上的静音预览；由壳注入播放器实现。
 */
@Composable
internal fun HomeHeroCarousel(
    videos: List<VideoItem>,
    autoplay: Boolean,
    onOpen: (VideoItem) -> Unit,
    preview: @Composable (VideoItem, Modifier) -> Unit,
) {
    if (videos.isEmpty()) return
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        val layout = resolveHomeHeroLayout(maxWidth)
        val pager = rememberPagerState { videos.size }
        Column(
            modifier = Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            HorizontalPager(
                state = pager,
                modifier = Modifier
                    .widthIn(max = layout.maxWidth)
                    .fillMaxWidth()
                    .aspectRatio(layout.aspectRatio),
                pageSpacing = HomeHeroPageSpacing,
                beyondViewportPageCount = 1,
                userScrollEnabled = videos.size > 1,
                key = { videos[it].bvid },
            ) { page ->
                HomeHeroCard(
                    video = videos[page],
                    page = page,
                    pager = pager,
                    autoplay = autoplay,
                    onOpen = onOpen,
                    preview = preview,
                )
            }
            if (videos.size > 1) {
                HomeHeroIndicator(pager = pager, count = videos.size)
            }
        }
    }
}

@Composable
private fun HomeHeroCard(
    video: VideoItem,
    page: Int,
    pager: PagerState,
    autoplay: Boolean,
    onOpen: (VideoItem) -> Unit,
    preview: @Composable (VideoItem, Modifier) -> Unit,
) {
    // 折角与淡出都在绘制阶段读取翻页偏移，滑动时不触发整卡重组。
    Box(
        modifier = Modifier
            .fillMaxSize()
            .graphicsLayer {
                val transform = homeHeroTransform(pager.pageOffsetOf(page))
                scaleX = transform.scale
                scaleY = transform.scale
                alpha = transform.alpha
                rotationY = transform.rotationY
                translationX = transform.translationFraction * size.width
                transformOrigin = TransformOrigin(
                    pivotFractionX = if (pager.pageOffsetOf(page) < 0f) 0f else 1f,
                    pivotFractionY = 0.5f,
                )
                // 相机距离按密度换算，保证不同屏幕上的透视强度一致。
                cameraDistance = 12f * density
            }
            .clip(HomeHeroShape)
            .clickable { onOpen(video) },
    ) {
        AsyncImage(
            model = video.cover,
            contentDescription = video.title,
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer {
                    val transform = homeHeroTransform(pager.pageOffsetOf(page))
                    scaleX = transform.contentScale
                    scaleY = transform.contentScale
                    translationX = transform.contentParallaxFraction * size.width
                },
        )
        if (autoplay && pager.settledPage == page) {
            preview(video, Modifier.fillMaxSize())
        }
        // 底部渐变让白字在任意封面上都能读清。
        Box(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .fillMaxHeight(0.62f)
                .background(
                    Brush.verticalGradient(
                        0f to Color.Transparent,
                        0.45f to Color.Transparent,
                        1f to Color.Black.copy(alpha = 0.76f),
                    ),
                ),
        )
        Box(
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer {
                    alpha = homeHeroTransform(pager.pageOffsetOf(page)).edgeShadeAlpha
                }
                .background(Color.Black),
        )
        HomeHeroCaption(video, Modifier.align(Alignment.BottomStart))
    }
}

@Composable
private fun HomeHeroCaption(video: VideoItem, modifier: Modifier) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(start = 14.dp, end = 14.dp, bottom = 14.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        BasicText(
            text = video.title,
            style = TextStyle(
                color = Color.White,
                fontSize = 17.sp,
                lineHeight = 22.sp,
                fontWeight = FontWeight.Bold,
                shadow = HomeHeroTitleShadow,
            ),
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
        if (video.ownerName.isNotBlank()) {
            BasicText(
                text = video.ownerName,
                style = TextStyle(
                    color = Color.White.copy(alpha = 0.78f),
                    fontSize = 12.sp,
                    shadow = HomeHeroTitleShadow,
                ),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        val stats = video.statsLine()
        if (stats.isNotEmpty()) {
            BasicText(
                text = stats,
                style = TextStyle(
                    color = Color.White.copy(alpha = 0.65f),
                    fontSize = 11.sp,
                    shadow = HomeHeroTitleShadow,
                ),
                maxLines = 1,
            )
        }
    }
}

@Composable
private fun HomeHeroIndicator(pager: PagerState, count: Int) {
    Row(
        modifier = Modifier.padding(top = 10.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        for (index in 0 until count) {
            val selected = pager.currentPage == index
            val width by animateDpAsState(if (selected) 14.dp else 5.dp, label = "heroDotWidth")
            Box(
                modifier = Modifier
                    .size(width = width, height = 5.dp)
                    .clip(RoundedCornerShape(3.dp))
                    .background(Color.White.copy(alpha = if (selected) 0.92f else 0.38f)),
            )
        }
    }
}

/** 当前页为 0、翻页偏移为正时表示目标页在当前页左侧，其余同理。 */
private fun PagerState.pageOffsetOf(page: Int): Float =
    (currentPage - page) + currentPageOffsetFraction

private fun VideoItem.statsLine(): String {
    val parts = ArrayList<String>(3)
    if (duration > 0) parts += formatDuration(duration)
    if (play > 0) parts += "${formatCount(play)}播放"
    if (danmaku > 0) parts += "${formatCount(danmaku)}弹幕"
    return parts.joinToString(" · ")
}
