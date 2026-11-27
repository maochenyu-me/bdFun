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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.More

/**
 * 封面页。竖排是「封面 → 歌词预览 → 标题 → 进度 → 传输 → 音量 → 次级按钮」，
 * 横排把封面放左、其余放右，其余顺序不变。
 */
@Composable
internal fun ListenVideoCoverPage(
    info: ListenVideoInfo,
    playback: ListenVideoPlayback,
    actions: ListenVideoActions,
    ui: ListenVideoUiStateHolder,
    palette: ListenVideoPalette,
    lyrics: Lyrics,
    coverImage: @Composable (String, Modifier) -> Unit,
    icons: ListenVideoIcons,
    onOpenMore: () -> Unit,
    onOpenQueue: () -> Unit,
) {
    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val layout = resolveListenLayout(maxWidth.value, maxHeight.value)
        ListenVideoTopBar(
            title = info.title,
            onBack = actions.onBack,
            modifier = Modifier.align(Alignment.TopCenter),
        ) {
            ListenVideoGlassButton(onClick = onOpenMore) {
                Icon(
                    imageVector = MiuixIcons.More,
                    contentDescription = "更多",
                    tint = Color.White.copy(alpha = 0.9f),
                    modifier = Modifier.size(20.dp),
                )
            }
        }

        if (layout.mode == ListenLayout.SideBySide) {
            LandscapeCoverBody(info, playback, actions, ui, layout, lyrics, coverImage, icons, onOpenQueue)
        } else {
            PortraitCoverBody(info, playback, actions, ui, layout, lyrics, coverImage, icons, onOpenQueue)
        }
    }
}

@Composable
private fun LandscapeCoverBody(
    info: ListenVideoInfo,
    playback: ListenVideoPlayback,
    actions: ListenVideoActions,
    ui: ListenVideoUiStateHolder,
    layout: ListenLayoutSpec,
    lyrics: Lyrics,
    coverImage: @Composable (String, Modifier) -> Unit,
    icons: ListenVideoIcons,
    onOpenQueue: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 44.dp)
            .padding(top = 62.dp, bottom = 30.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(34.dp),
    ) {
        Box(
            modifier = Modifier
                .size(layout.coverSizeDp.dp)
                .clip(RoundedCornerShape(14.dp))
                .background(Color.White.copy(alpha = 0.08f)),
        ) {
            coverImage(info.cover, Modifier.fillMaxSize())
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = info.title,
                color = Color.White,
                fontSize = 22.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.height(6.dp))
            Text(
                text = info.author.ifBlank { "UP 主" },
                color = Color.White.copy(alpha = 0.55f),
                fontSize = 13.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.height(22.dp))
            ListenVideoProgress(playback, actions)
            Spacer(Modifier.height(10.dp))
            ListenVideoTransport(playback, info, actions, icons.play, icons.next, size = 58.dp)
            Spacer(Modifier.height(18.dp))
            ListenVideoDock(ui, playback, actions, onOpenQueue)
        }
    }
}

@Composable
private fun PortraitCoverBody(
    info: ListenVideoInfo,
    playback: ListenVideoPlayback,
    actions: ListenVideoActions,
    ui: ListenVideoUiStateHolder,
    layout: ListenLayoutSpec,
    lyrics: Lyrics,
    coverImage: @Composable (String, Modifier) -> Unit,
    icons: ListenVideoIcons,
    onOpenQueue: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 26.dp)
            .padding(top = 62.dp, bottom = 62.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Spacer(Modifier.weight(0.6f))
        Box(
            modifier = Modifier
                .size(layout.coverSizeDp.dp)
                .clip(RoundedCornerShape(16.dp))
                .background(Color.White.copy(alpha = 0.08f)),
        ) {
            coverImage(info.cover, Modifier.fillMaxSize())
        }
        Spacer(Modifier.weight(0.5f))
        ListenLyricsPreview(
            lyrics = lyrics,
            positionMs = playback.positionMs,
            translationOn = ui.translationOn,
            onSeek = actions.onLyricsSeek,
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(18.dp))
        Text(
            text = info.title,
            color = Color.White,
            fontSize = 17.sp,
            fontWeight = FontWeight.Medium,
            textAlign = TextAlign.Center,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(5.dp))
        Text(
            text = info.author.ifBlank { "UP 主" },
            color = Color.White.copy(alpha = 0.55f),
            fontSize = 12.sp,
            textAlign = TextAlign.Center,
            maxLines = 1,
        )
        Spacer(Modifier.height(18.dp))
        ListenVideoProgress(playback, actions)
        Spacer(Modifier.height(6.dp))
        ListenVideoTransport(playback, info, actions, icons.play, icons.next, size = 52.dp)
        Spacer(Modifier.height(12.dp))
        ListenVideoVolume(ui, actions)
        Spacer(Modifier.height(14.dp))
        ListenVideoDock(ui, playback, actions, onOpenQueue)
        Spacer(Modifier.weight(0.4f))
    }
}

/**
 * 封面页的实时歌词预览：当前行高亮，上下各两行做淡出。
 * 没有歌词时整块不占位，让封面和标题自然上移。
 */
@Composable
private fun ListenLyricsPreview(
    lyrics: Lyrics,
    positionMs: Long,
    translationOn: Boolean,
    onSeek: (Long) -> Unit,
    modifier: Modifier = Modifier,
) {
    if (lyrics.lines.isEmpty()) return
    val index = lyrics.indexAt(positionMs)
    val center = index.coerceAtLeast(0)
    val window = (center - 2..center + 2).filter { it in lyrics.lines.indices }
    Column(
        modifier = modifier.padding(horizontal = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        window.forEach { i ->
            val line = lyrics.lines[i]
            val current = i == index
            val alpha by animateFloatAsState(
                when {
                    current -> 1f
                    kotlin.math.abs(i - index) == 1 -> 0.45f
                    else -> 0.22f
                },
                label = "preview-alpha",
            )
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                ) { onSeek(line.fromMs) },
            ) {
                Text(
                    text = line.text,
                    color = Color.White.copy(alpha = alpha),
                    fontSize = if (current) 15.sp else 12.sp,
                    fontWeight = if (current) FontWeight.SemiBold else FontWeight.Normal,
                    textAlign = TextAlign.Center,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (translationOn && current && line.translation != null) {
                    Text(
                        text = line.translation,
                        color = Color.White.copy(alpha = 0.5f),
                        fontSize = 11.sp,
                        textAlign = TextAlign.Center,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
    }
}