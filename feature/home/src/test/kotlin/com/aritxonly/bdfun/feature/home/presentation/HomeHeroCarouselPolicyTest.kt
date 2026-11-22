package com.aritxonly.bdfun.feature.home.presentation

import androidx.compose.ui.unit.dp
import com.aritxonly.bdfun.core.model.video.VideoItem
import com.aritxonly.bdfun.core.model.video.VideoItemExtras
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class HomeHeroCarouselPolicyTest {

    private fun video(
        bvid: String,
        cover: String = "https://i0.hdslb.com/$bvid.jpg",
        category: String = "",
    ) = VideoItem(
        bvid = bvid,
        aid = 1L,
        title = "标题 $bvid",
        cover = cover,
        ownerName = "UP",
        ownerFace = "",
        ownerMid = 0L,
        play = 1L,
        danmaku = 1L,
        duration = 60,
        pubdate = 0L,
        extras = VideoItemExtras(categoryName = category),
    )

    @Test
    fun `居中的封面不缩放也不倾斜`() {
        val transform = homeHeroTransform(0f)
        assertEquals(1f, transform.scale, 0.0001f)
        assertEquals(1f, transform.alpha, 0.0001f)
        assertEquals(0f, transform.rotationY, 0.0001f)
        assertEquals(0f, transform.translationFraction, 0.0001f)
        assertEquals(1f, transform.contentScale, 0.0001f)
        assertEquals(0f, transform.edgeShadeAlpha, 0.0001f)
    }

    @Test
    fun `整页偏移时卡片缩到 0_9 并限制可见度`() {
        val transform = homeHeroTransform(1f)
        assertEquals(0.9f, transform.scale, 0.0001f)
        assertEquals(0.8f, transform.alpha, 0.0001f)
        assertEquals(-26f, transform.rotationY, 0.0001f)
        assertEquals(0.04f, transform.translationFraction, 0.0001f)
        assertEquals(0.25f, transform.edgeShadeAlpha, 0.0001f)
    }

    @Test
    fun `远距离页面的偏移被夹紧且旋转不超出 40 度`() {
        val transform = homeHeroTransform(3f)
        // 位移超过一页后形变不再继续加深，避免离屏卡片画得过大。
        assertEquals(0.9f, transform.scale, 0.0001f)
        assertEquals(-40f, transform.rotationY, 0.0001f)
        assertEquals(40f, homeHeroTransform(-3f).rotationY, 0.0001f)
    }

    @Test
    fun `没有封面或番剧图片类推荐不进入轮播`() {
        val picked = selectHomeHeroVideos(
            listOf(
                video("BV1", cover = ""),
                video("BV2", category = "bangumi"),
                video("BV3", category = "Picture"),
                video("BV4"),
            ),
        )
        assertEquals(listOf("BV4"), picked.map { it.bvid })
    }

    @Test
    fun `轮播最多取八项`() {
        val videos = (1..12).map { video("BV$it") }
        assertEquals(HomeHeroMaxItems, selectHomeHeroVideos(videos).size)
    }

    @Test
    fun `轮播项从网格移除且保持原顺序`() {
        val videos = (1..5).map { video("BV$it") }
        val hero = videos.take(2)
        assertEquals(listOf("BV3", "BV4", "BV5"), excludeHomeHeroVideos(videos, hero).map { it.bvid })
        assertEquals(videos, excludeHomeHeroVideos(videos, emptyList()))
    }

    @Test
    fun `宽度决定轮播比例与限宽`() {
        val phone = resolveHomeHeroLayout(400.dp)
        assertEquals(16f / 9f, phone.aspectRatio, 0.0001f)
        assertEquals(400.dp, phone.maxWidth)

        val tablet = resolveHomeHeroLayout(700.dp)
        assertEquals(2f, tablet.aspectRatio, 0.0001f)

        val wide = resolveHomeHeroLayout(1000.dp)
        assertEquals(21f / 9f, wide.aspectRatio, 0.0001f)
        assertEquals(980.dp, wide.maxWidth)
        assertTrue(wide.maxWidth < 1000.dp)
    }
}
