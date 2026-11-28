package com.aritxonly.bdfun.feature.video.presentation.listen

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.aritxonly.bdfun.ui.formatPlayTime
import com.aritxonly.teak.common.icons.AppGlyph
import com.aritxonly.teak.common.icons.LucideIcons
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.More
import top.yukonga.miuix.kmp.icon.extended.Search

/**
 * 歌词页：整屏歌词 + 顶部工具条 + 底部细进度与传输键。
 *
 * 校准条浮在**歌词区内部**的下沿，而不是页面底部：歌词区下沿就是进度条上沿，两者
 * 至少隔开一个内边距，点校准按钮不会再误触到进度条。它也不进内容 Column，
 * 展开/收起都不改变歌词区与传输键的尺寸。
 */
@Composable
internal fun ListenVideoLyricsPage(
    info: ListenVideoInfo,
    playback: ListenVideoPlayback,
    actions: ListenVideoActions,
    ui: ListenVideoUiStateHolder,
    palette: ListenVideoPalette,
    lyrics: Lyrics,
    icons: ListenVideoIcons,
    onOpenMore: () -> Unit,
    onOpenQueue: () -> Unit,
) {
    LyricsPageBody(info, playback, actions, ui, palette, lyrics, icons, onOpenMore, onOpenQueue)
}

@Composable
private fun LyricsPageBody(
    info: ListenVideoInfo,
    playback: ListenVideoPlayback,
    actions: ListenVideoActions,
    ui: ListenVideoUiStateHolder,
    palette: ListenVideoPalette,
    lyrics: Lyrics,
    icons: ListenVideoIcons,
    onOpenMore: () -> Unit,
    onOpenQueue: () -> Unit,
) {
    Column(
        modifier = Modifier.fillMaxSize().padding(top = 4.dp, bottom = 8.dp),
    ) {
        ListenVideoTopBar(
            onBack = actions.onBack,
        ) {
            if (lyrics.lines.isNotEmpty()) {
                ListenVideoGlassButton(onClick = { actions.onTranslationToggle(!ui.translationOn) }) {
                    Text(
                        text = "译",
                        color = Color.White.copy(alpha = if (ui.translationOn) 1f else 0.55f),
                        fontSize = 13.sp,
                        fontWeight = if (ui.translationOn) FontWeight.Medium else FontWeight.Normal,
                    )
                }
                Spacer(Modifier.size(8.dp))
                ListenVideoGlassButton(onClick = { actions.onImportLyrics() }) {
                    Icon(
                        imageVector = MiuixIcons.Search,
                        contentDescription = "导入歌词",
                        tint = Color.White.copy(alpha = 0.9f),
                        modifier = Modifier.size(19.dp),
                    )
                }
                Spacer(Modifier.size(8.dp))
            }
            ListenVideoGlassButton(onClick = onOpenMore) {
                Icon(
                    imageVector = MiuixIcons.More,
                    contentDescription = "更多",
                    tint = Color.White.copy(alpha = 0.9f),
                    modifier = Modifier.size(20.dp),
                )
            }
        }

        if (lyrics.lines.isEmpty()) {
            ListenLyricsEmptyState(
                modifier = Modifier.weight(1f),
                onImport = actions.onImportLyrics,
                onSwitchToCover = { ui.pageIndex = 0 },
            )
        } else {
            LyricsSection(
                playback = playback,
                actions = actions,
                ui = ui,
                palette = palette,
                lyrics = lyrics,
                modifier = Modifier.weight(1f),
            )
        }

        ListenVideoProgress(playback, actions, modifier = Modifier.padding(horizontal = 22.dp))
        Spacer(Modifier.height(4.dp))
        ListenVideoTransport(playback, info, actions, icons.play, icons.next, size = 50.dp)
    }
}

/**
 * 歌词区：歌词列表 + 校准浮层。
 *
 * 校准条挂在这个 Box 的下沿，正好在歌词区里面、进度条上沿之外，
 * 既不会和进度条抢触摸，也不占据内容 Column 的高度。
 */
@Composable
private fun LyricsSection(
    playback: ListenVideoPlayback,
    actions: ListenVideoActions,
    ui: ListenVideoUiStateHolder,
    palette: ListenVideoPalette,
    lyrics: Lyrics,
    modifier: Modifier = Modifier,
) {
    // 播放器的位置每秒才来一次，逐字扫亮要按帧补齐，否则一句只亮两三下。
    val positionNow = rememberSmoothLyricPosition(
        sampledMs = playback.positionMs,
        playing = playback.playing,
        buffering = playback.buffering,
        speed = playback.speed,
    )
    Box(modifier = modifier.fillMaxWidth()) {
        ListenLyricsList(
            lyrics = lyrics,
            positionNow = positionNow,
            translationOn = ui.translationOn,
            style = ui.lyricsStyle,
            modifier = Modifier.fillMaxSize(),
            // 校准条占了底部一条，给歌词列表留出同样的空间，免得挡住当前行。
            bottomPadding = if (ui.lyricsCalibrating) 72.dp else 40.dp,
            onSeek = actions.onSeek,
            onLongPress = { ui.lyricsCalibrating = true },
        )
        if (ui.lyricsCalibrating) {
            LyricsCalibrateBar(
                offsetMs = ui.lyricsOffsetMs,
                accent = Color(palette.accent),
                onOffset = { ui.lyricsOffsetMs = it },
                onDone = { ui.lyricsCalibrating = false },
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 12.dp),
            )
        }
    }
}

@Composable
private fun ListenLyricsList(
    lyrics: Lyrics,
    positionNow: () -> Long,
    translationOn: Boolean,
    style: ListenLyricsStyle,
    modifier: Modifier = Modifier,
    bottomPadding: Dp = 40.dp,
    onSeek: (Long) -> Unit,
    onLongPress: () -> Unit,
) {
    val listState = rememberLazyListState()
    // 只有当前行变了才需要重组列表；位置本身每帧在变，走 derivedStateOf 挡掉。
    val index by remember(lyrics) { derivedStateOf { lyrics.indexAt(positionNow()) } }
    // 当前行变化时才滚动，避免每秒都触发一次动画。
    LaunchedEffect(index) {
        if (index < 0) return@LaunchedEffect
        val viewport = listState.layoutInfo.viewportSize.height
        val center = if (viewport > 0) viewport / 3 else 0
        listState.animateScrollToItem(index, scrollOffset = -center)
    }
    LazyColumn(
        state = listState,
        modifier = modifier.fillMaxWidth(),
        contentPadding = PaddingValues(start = 30.dp, end = 30.dp, top = 40.dp, bottom = bottomPadding),
        verticalArrangement = Arrangement.spacedBy(18.dp),
    ) {
        itemsIndexed(lyrics.lines) { i, line ->
            val current = i == index
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .pointerInput(line.fromMs, current) {
                        detectTapGestures(
                            onTap = { onSeek(line.fromMs) },
                            onLongPress = { if (current) onLongPress() },
                        )
                    },
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                LyricLineText(
                    text = line.text,
                    fromMs = line.fromMs,
                    toMs = line.toMs,
                    current = current,
                    style = style,
                    distance = if (current) 0 else kotlin.math.abs(i - index),
                    positionNow = positionNow,
                )
                if (translationOn && line.translation != null) {
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = line.translation,
                        color = Color.White.copy(alpha = if (current) 0.6f else 0.3f),
                        fontSize = if (current) 13.sp else 12.sp,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
        }
    }
}

@Composable
private fun ListenLyricsEmptyState(
    modifier: Modifier,
    onImport: () -> Unit,
    onSwitchToCover: () -> Unit,
) {
    Column(
        modifier = modifier.fillMaxWidth().padding(horizontal = 32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        AppGlyph(LucideIcons.Captions, Color.White.copy(alpha = 0.4f), size = 34.dp)
        Spacer(Modifier.height(14.dp))
        Text("该视频没有字幕", color = Color.White.copy(alpha = 0.7f), fontSize = 14.sp)
        Spacer(Modifier.height(6.dp))
        Text(
            "可以导入本地 .lrc 当作歌词",
            color = Color.White.copy(alpha = 0.4f),
            fontSize = 12.sp,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(18.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            ListenPillButton("导入歌词", onImport)
            ListenPillButton("回封面", onSwitchToCover)
        }
    }
}

@Composable
private fun ListenPillButton(label: String, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .clip(CircleShape)
            .background(Color.White.copy(alpha = 0.14f))
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick,
            )
            .padding(horizontal = 18.dp, vertical = 9.dp),
    ) {
        Text(label, color = Color.White.copy(alpha = 0.9f), fontSize = 12.sp)
    }
}

/** 歌词页也给一个格式化的当前时间，便于和视频侧对照。 */
@Composable
internal fun ListenLyricsClock(positionMs: Long) {
    Text(formatPlayTime(positionMs), color = Color.White.copy(alpha = 0.5f), fontSize = 11.sp)
}