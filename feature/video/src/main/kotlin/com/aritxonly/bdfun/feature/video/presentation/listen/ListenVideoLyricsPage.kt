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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
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
 * 当前行按行内时间做填充高亮；点某行跳到该行开头，长按进入偏移微调。
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
    var offsetEditing by remember { mutableStateOf(false) }
    Column(modifier = Modifier.fillMaxSize().padding(top = 4.dp, bottom = 8.dp)) {
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
            ListenLyricsList(
                lyrics = lyrics,
                positionMs = playback.positionMs,
                index = lyrics.indexAt(playback.positionMs),
                translationOn = ui.translationOn,
                modifier = Modifier.weight(1f),
                onSeek = actions.onSeek,
                onLongPress = { offsetEditing = true },
            )
        }

        if (offsetEditing) {
            ListenLyricsOffsetBar(
                offsetMs = ui.lyricsOffsetMs,
                onChange = { ui.lyricsOffsetMs = it },
                onClose = { offsetEditing = false },
            )
        }

        ListenVideoProgress(playback, actions, modifier = Modifier.padding(horizontal = 22.dp))
        Spacer(Modifier.height(4.dp))
        ListenVideoTransport(playback, info, actions, icons.play, icons.next, size = 50.dp)
    }
}

@Composable
private fun ListenLyricsList(
    lyrics: Lyrics,
    positionMs: Long,
    index: Int,
    translationOn: Boolean,
    modifier: Modifier = Modifier,
    onSeek: (Long) -> Unit,
    onLongPress: () -> Unit,
) {
    val listState = rememberLazyListState()
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
        contentPadding = PaddingValues(horizontal = 30.dp, vertical = 40.dp),
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
                    progress = if (current) lyrics.progressAt(i, positionMs) else 0f,
                    current = current,
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

/**
 * 逐字填充的近似实现：字幕只有整句时间，所以按行内进度从左往右裁一条亮色文字出来。
 * 视觉上和参考实现一致，但不要理解成真的逐字对齐。
 */
@Composable
private fun LyricLineText(text: String, progress: Float, current: Boolean) {
    val base = if (current) Color.White.copy(alpha = 0.42f) else Color.White.copy(alpha = 0.34f)
    val fontSize = if (current) 20.sp else 16.sp
    val weight = if (current) FontWeight.SemiBold else FontWeight.Normal
    Box(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = text,
            color = base,
            fontSize = fontSize,
            fontWeight = weight,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth(),
        )
        if (current && progress > 0f) {
            Text(
                text = text,
                color = Color.White,
                fontSize = fontSize,
                fontWeight = weight,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .fillMaxWidth()
                    .drawWithContent {
                        clipRect(right = size.width * progress.coerceIn(0f, 1f)) {
                            this@drawWithContent.drawContent()
                        }
                    },
            )
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

@Composable
private fun ListenLyricsOffsetBar(offsetMs: Long, onChange: (Long) -> Unit, onClose: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 22.dp, vertical = 4.dp)
            .clip(CircleShape)
            .background(Color.Black.copy(alpha = 0.35f))
            .padding(horizontal = 14.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text("偏移", color = Color.White.copy(alpha = 0.5f), fontSize = 11.sp)
        ListenOffsetStep("-0.5", -500L, offsetMs, onChange)
        ListenOffsetStep("-0.1", -100L, offsetMs, onChange)
        Text(
            text = if (offsetMs >= 0) "+%.1fs".format(offsetMs / 1000f) else "%.1fs".format(offsetMs / 1000f),
            color = Color.White.copy(alpha = 0.85f),
            fontSize = 12.sp,
            modifier = Modifier.padding(horizontal = 4.dp),
        )
        ListenOffsetStep("+0.1", 100L, offsetMs, onChange)
        ListenOffsetStep("+0.5", 500L, offsetMs, onChange)
        Spacer(Modifier.weight(1f))
        Text(
            "完成",
            color = Color.White.copy(alpha = 0.75f),
            fontSize = 12.sp,
            modifier = Modifier
                .clip(CircleShape)
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = onClose,
                )
                .padding(horizontal = 10.dp, vertical = 4.dp),
        )
    }
}

@Composable
private fun ListenOffsetStep(label: String, deltaMs: Long, currentMs: Long, onChange: (Long) -> Unit) {
    Text(
        text = label,
        color = Color.White.copy(alpha = 0.7f),
        fontSize = 11.sp,
        modifier = Modifier
            .clip(CircleShape)
            .background(Color.White.copy(alpha = 0.1f))
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
            ) { onChange((currentMs + deltaMs).coerceIn(-30_000L, 30_000L)) }
            .padding(horizontal = 9.dp, vertical = 4.dp),
    )
}

/** 歌词页也给一个格式化的当前时间，便于和视频侧对照。 */
@Composable
internal fun ListenLyricsClock(positionMs: Long) {
    Text(formatPlayTime(positionMs), color = Color.White.copy(alpha = 0.5f), fontSize = 11.sp)
}