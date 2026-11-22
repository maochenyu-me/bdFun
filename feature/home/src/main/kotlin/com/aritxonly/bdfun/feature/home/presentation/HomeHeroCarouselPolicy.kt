package com.aritxonly.bdfun.feature.home.presentation

import androidx.compose.runtime.Immutable
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.aritxonly.bdfun.core.model.video.VideoItem

/** 顶部轮播最多取几项：再多也滑不过来，取推荐首屏最靠前的几条。 */
internal const val HomeHeroMaxItems = 8

/** 手机竖屏沿用官方 16:9 封面比例。 */
private const val HomeHeroPhoneAspect = 16f / 9f

/** 展开后的平板接近 2:1，21:9 只留给很宽的分栏窗口。 */
private const val HomeHeroTabletAspect = 2f
private const val HomeHeroWideAspect = 21f / 9f

internal val HomeHeroPageSpacing = 10.dp

/** 轮播开关；两个开关都来自设置页「首页、动态与搜索」。 */
@Immutable
data class HomeHeroOptions(
    val enabled: Boolean = true,
    val autoplay: Boolean = false,
)

/** 轮播尺寸：宽窗口限宽居中，避免超宽屏把封面拉成一条。 */
@Immutable
data class HomeHeroLayout(val aspectRatio: Float, val maxWidth: Dp)

internal fun resolveHomeHeroLayout(containerWidth: Dp): HomeHeroLayout = when {
    containerWidth >= 840.dp -> HomeHeroLayout(HomeHeroWideAspect, 980.dp)
    containerWidth >= 600.dp -> HomeHeroLayout(HomeHeroTabletAspect, containerWidth)
    else -> HomeHeroLayout(HomeHeroPhoneAspect, containerWidth)
}

/** 翻页时卡片相对当前页的形变参数，全部按 |offset| 线性收敛。 */
@Immutable
data class HomeHeroTransform(
    val scale: Float,
    val alpha: Float,
    val rotationY: Float,
    /** 平移量，按卡片宽度的比例给出。 */
    val translationFraction: Float,
    val contentScale: Float,
    /** 封面内容反向偏移比例，制造景深。 */
    val contentParallaxFraction: Float,
    val edgeShadeAlpha: Float,
)

internal fun homeHeroTransform(pageOffset: Float): HomeHeroTransform {
    val bounded = pageOffset.coerceIn(-2f, 2f)
    val distance = kotlin.math.abs(bounded).coerceIn(0f, 1f)
    val direction = if (bounded >= 0f) 1f else -1f
    return HomeHeroTransform(
        scale = 1f - 0.10f * distance,
        alpha = (1f - 0.20f * distance).coerceAtLeast(0.35f),
        rotationY = (-bounded * 26f).coerceIn(-40f, 40f),
        translationFraction = 0.04f * distance * direction,
        contentScale = 1f + 0.16f * distance,
        contentParallaxFraction = -bounded * 0.08f,
        edgeShadeAlpha = 0.25f * distance,
    )
}

/** 选片：跳过没有封面/没有 BV 的条目，番剧与图片类推荐不参与轮播。 */
internal fun selectHomeHeroVideos(videos: List<VideoItem>): List<VideoItem> {
    val picked = ArrayList<VideoItem>(HomeHeroMaxItems)
    for (video in videos) {
        if (picked.size >= HomeHeroMaxItems) break
        if (video.bvid.isBlank() || video.cover.isBlank()) continue
        if (video.extras.categoryName.isBangumiOrPicture()) continue
        picked += video
    }
    return picked
}

/** 轮播项已经在顶部展示过，下方网格不再重复。 */
internal fun excludeHomeHeroVideos(videos: List<VideoItem>, hero: List<VideoItem>): List<VideoItem> {
    if (hero.isEmpty()) return videos
    val shown = hero.mapTo(HashSet(hero.size * 2)) { it.bvid }
    return videos.filterNot { it.bvid in shown }
}

private fun String.isBangumiOrPicture(): Boolean =
    equals("bangumi", ignoreCase = true) || equals("picture", ignoreCase = true)
