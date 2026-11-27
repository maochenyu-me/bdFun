package com.aritxonly.bdfun.feature.video.presentation.listen

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Back
import kotlinx.coroutines.launch

/**
 * 听视频整页。进入后接管整个屏幕——上方不再有详情页的播放器，下方也没有评论区。
 * 退出走左上角返回，回到详情页原位置。
 */
@Composable
fun ListenVideoScreen(
    info: ListenVideoInfo,
    playback: ListenVideoPlayback,
    actions: ListenVideoActions,
    ui: ListenVideoUiStateHolder,
    palette: ListenVideoPalette,
    coverImage: @Composable (String, Modifier) -> Unit,
    icons: ListenVideoIcons,
    modifier: Modifier = Modifier,
) {
    val lyrics = remember(ui.importedLyrics, info.lyrics, info.secondaryLyrics, ui.lyricsOffsetMs) {
        // 副字幕按时间贴到主字幕行上，「译」开关只控制显不显示，不重新解析。
        (ui.importedLyrics ?: info.lyrics)
            .withTranslation(info.secondaryLyrics)
            .withOffset(ui.lyricsOffsetMs)
    }
    val hasLyrics = lyrics.lines.isNotEmpty()
    val pages = listenPages(hasLyrics)
    val pagerState = rememberPagerState(pageCount = { pages.size })
    val scope = rememberCoroutineScope()

    // 字幕没了（切集/关字幕）时把页码收回 0，否则 Pager 会停在不存在的页上。
    LaunchedEffect(hasLyrics) {
        val clamped = clampListenPage(pagerState.currentPage, hasLyrics)
        if (clamped != pagerState.currentPage) pagerState.scrollToPage(clamped)
        ui.pageIndex = clamped
    }
    LaunchedEffect(pagerState.currentPage) { ui.pageIndex = pagerState.currentPage }

    BoxWithConstraints(modifier = modifier.fillMaxSize().background(Color.Black)) {
        ListenVideoBackground(info.cover, palette, coverImage)

        HorizontalPager(
            state = pagerState,
            modifier = Modifier.fillMaxSize(),
            userScrollEnabled = hasLyrics,
        ) { index ->
            when (pages[index].page) {
                ListenPage.Cover -> ListenVideoCoverPage(
                    info = info,
                    playback = playback,
                    actions = actions,
                    ui = ui,
                    palette = palette,
                    lyrics = lyrics,
                    coverImage = coverImage,
                    icons = icons,
                    onOpenMore = { ui.moreSheet = true },
                    onOpenQueue = { ui.queueSheet = true },
                )
                ListenPage.Lyrics -> ListenVideoLyricsPage(
                    info = info,
                    playback = playback,
                    actions = actions,
                    ui = ui,
                    palette = palette,
                    lyrics = lyrics,
                    icons = icons,
                    onOpenMore = { ui.moreSheet = true },
                    onOpenQueue = { ui.queueSheet = true },
                )
            }
        }

        // 胶囊只在两页都存在时才有意义；单页时它是纯装饰，直接不画。
        if (hasLyrics) {
            ListenVideoPageCapsule(
                pages = pages,
                current = pagerState.currentPage,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 26.dp),
                onSelect = { scope.launch { pagerState.animateScrollToPage(it) } },
            )
        }

        ui.message?.let { text ->
            ListenVideoToast(
                text = text,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 84.dp),
                onShown = { ui.message = null },
            )
        }
    }

    ListenVideoSheets(info, playback, actions, ui, palette)
}

/** 更多 / 队列两个浮层；抽出来避免主容器过长。 */
@Composable
private fun ListenVideoSheets(
    info: ListenVideoInfo,
    playback: ListenVideoPlayback,
    actions: ListenVideoActions,
    ui: ListenVideoUiStateHolder,
    palette: ListenVideoPalette,
) {
    if (ui.moreSheet) {
        ListenVideoMoreSheet(
            visible = true,
            onDismiss = { ui.moreSheet = false },
            capabilities = info.capabilities,
            state = info.menuState(ui, playback),
            accent = Color(palette.accent),
            onEntry = { entry -> actions.onMenuEntry(entry); ui.moreSheet = false },
            sleepAtMs = playback.sleepAtMs,
            onSleepAtChanged = actions.onSleepAtChanged,
        )
    }
    if (ui.queueSheet) {
        ListenVideoQueueSheet(
            visible = true,
            onDismiss = { ui.queueSheet = false },
            pages = info.pages,
            currentCid = info.currentCid,
            accent = Color(palette.accent),
            onSelect = { actions.onSelectPage(it); ui.queueSheet = false },
        )
    }
}

@Composable
private fun ListenVideoBackground(
    cover: String,
    palette: ListenVideoPalette,
    coverImage: @Composable (String, Modifier) -> Unit,
) {
    Box(modifier = Modifier.fillMaxSize().background(Color(palette.bottom))) {
        // 封面放大铺底再模糊：比高斯模糊大图便宜，而且不会有边缘透明。
        coverImage(
            cover,
            Modifier
                .fillMaxSize()
                .graphicsLayer { scaleX = 1.18f; scaleY = 1.18f; alpha = 0.5f },
        )
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        0f to Color(palette.top).copy(alpha = 0.86f),
                        0.45f to Color(palette.middle).copy(alpha = 0.92f),
                        1f to Color(palette.bottom).copy(alpha = 0.98f),
                    ),
                ),
        )
    }
}

@Composable
private fun ListenVideoPageCapsule(
    pages: List<ListenPageSpec>,
    current: Int,
    modifier: Modifier = Modifier,
    onSelect: (Int) -> Unit,
) {
    Row(
        modifier = modifier
            .clip(CircleShape)
            .background(Color.Black.copy(alpha = 0.34f))
            .padding(4.dp),
        horizontalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        pages.forEachIndexed { index, spec ->
            val selected = index == current
            val alpha by animateFloatAsState(if (selected) 1f else 0f, label = "capsule")
            Box(
                modifier = Modifier
                    .clip(CircleShape)
                    .background(Color.White.copy(alpha = 0.16f * alpha))
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                    ) { onSelect(index) }
                    .padding(horizontal = 22.dp, vertical = 8.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = spec.label,
                    color = Color.White.copy(alpha = if (selected) 0.98f else 0.6f),
                    fontSize = 13.sp,
                    fontWeight = if (selected) FontWeight.Medium else FontWeight.Normal,
                )
            }
        }
    }
}

@Composable
private fun ListenVideoToast(text: String, modifier: Modifier, onShown: () -> Unit) {
    LaunchedEffect(text) {
        kotlinx.coroutines.delay(2_200L)
        onShown()
    }
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(18.dp))
            .background(Color.Black.copy(alpha = 0.6f))
            .padding(horizontal = 16.dp, vertical = 9.dp),
    ) {
        Text(text, color = Color.White.copy(alpha = 0.92f), fontSize = 12.sp)
    }
}

/** 顶栏：返回 / 标题区 / 右侧动作。两页共用，保证标题与按钮位置一致。 */
@Composable
internal fun ListenVideoTopBar(
    title: String,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    trailing: @Composable () -> Unit = {},
) {
    Row(
        modifier = modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(40.dp)
                .clip(CircleShape)
                .background(Color.White.copy(alpha = 0.1f))
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                ) { onBack() },
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = MiuixIcons.Back,
                contentDescription = "返回",
                tint = Color.White.copy(alpha = 0.9f),
                modifier = Modifier.size(20.dp),
            )
        }
        Spacer(Modifier.width(12.dp))
        Text(
            text = title,
            color = Color.White.copy(alpha = 0.75f),
            fontSize = 13.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        trailing()
    }
}

@Composable
internal fun ListenVideoGlassButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    Box(
        modifier = modifier
            .size(40.dp)
            .clip(CircleShape)
            .background(Color.White.copy(alpha = 0.1f))
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick,
            ),
        contentAlignment = Alignment.Center,
    ) { content() }
}

@Composable
internal fun ListenVideoPadding(height: androidx.compose.ui.unit.Dp, modifier: Modifier = Modifier) {
    Column(modifier = modifier) { Spacer(Modifier.height(height)) }
}