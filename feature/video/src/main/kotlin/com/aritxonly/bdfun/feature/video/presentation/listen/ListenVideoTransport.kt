package com.aritxonly.bdfun.feature.video.presentation.listen

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.aritxonly.bdfun.ui.NextGlyph
import com.aritxonly.bdfun.ui.formatPlayTime
import com.aritxonly.bdfun.ui.FullscreenSeekBar
import com.aritxonly.teak.common.player.PauseGlyph
import com.aritxonly.teak.common.player.PlayGlyph
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Playlist
import top.yukonga.miuix.kmp.icon.extended.Tune
import top.yukonga.miuix.kmp.icon.extended.VolumeUp

@Composable
internal fun ListenVideoProgress(
    playback: ListenVideoPlayback,
    actions: ListenVideoActions,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.fillMaxWidth()) {
        FullscreenSeekBar(
            positionMs = playback.positionMs,
            durationMs = playback.durationMs,
            bufferedMs = playback.bufferedMs,
            onInteract = {},
            onSeek = actions.onSeek,
            modifier = Modifier.fillMaxWidth(),
        )
        Row(modifier = Modifier.fillMaxWidth()) {
            Text(
                text = formatPlayTime(playback.positionMs),
                color = Color.White.copy(alpha = 0.5f),
                fontSize = 11.sp,
            )
            Spacer(Modifier.weight(1f))
            Text(
                // 右侧显示剩余时间：听视频时比总时长更有用。
                text = "-" + formatPlayTime((playback.durationMs - playback.positionMs).coerceAtLeast(0L)),
                color = Color.White.copy(alpha = 0.5f),
                fontSize = 11.sp,
            )
        }
    }
}

@Composable
internal fun ListenVideoTransport(
    playback: ListenVideoPlayback,
    info: ListenVideoInfo,
    actions: ListenVideoActions,
    playIconRes: Int,
    nextIconRes: Int,
    size: androidx.compose.ui.unit.Dp,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        ListenStepButton(info.hasPrevious, "上一集", mirror = true, nextIconRes = nextIconRes) {
            info.previousCid()?.let(actions.onSelectPage)
        }
        Spacer(Modifier.width(26.dp))
        Box(
            modifier = Modifier
                .size(size)
                .clip(CircleShape)
                .background(Color.White.copy(alpha = 0.1f))
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = actions.onPlayPause,
                ),
            contentAlignment = Alignment.Center,
        ) {
            if (playback.playing) PauseGlyph(Color.White) else PlayGlyph(playIconRes, Color.White, 26.dp)
        }
        Spacer(Modifier.width(26.dp))
        ListenStepButton(info.hasNext, "下一集", mirror = false, nextIconRes = nextIconRes) {
            info.nextCid()?.let(actions.onSelectPage)
        }
    }
}

@Composable
private fun ListenStepButton(
    enabled: Boolean,
    description: String,
    mirror: Boolean,
    nextIconRes: Int,
    onClick: () -> Unit,
) {
    Box(
        modifier = Modifier
            .size(40.dp)
            .clip(CircleShape)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                enabled = enabled,
                onClick = onClick,
            ),
        contentAlignment = Alignment.Center,
    ) {
        NextGlyph(res = nextIconRes, enabled = enabled, mirror = mirror, description = description)
    }
}

@Composable
internal fun ListenVideoVolume(ui: ListenVideoUiStateHolder, actions: ListenVideoActions) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = MiuixIcons.VolumeUp,
            contentDescription = "音量",
            tint = Color.White.copy(alpha = 0.55f),
            modifier = Modifier.size(17.dp),
        )
        Spacer(Modifier.width(12.dp))
        ListenVolumeSlider(
            value = ui.volume.coerceAtLeast(0f),
            modifier = Modifier.weight(1f),
            onChange = {
                ui.volume = it
                actions.onVolumeChange(it)
            },
        )
    }
}

/** 底部一排次级按钮：A-B 循环 / 翻译 / 播放队列 / 播放设置。 */
@Composable
internal fun ListenVideoDock(
    ui: ListenVideoUiStateHolder,
    playback: ListenVideoPlayback,
    actions: ListenVideoActions,
    onOpenQueue: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceEvenly,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        ListenDockText("A-B", ui.abLoop.hasStart) { actions.onAbLoopMark() }
        ListenDockText("翻译", ui.translationOn) { actions.onTranslationToggle(!ui.translationOn) }
        ListenDockIcon(MiuixIcons.Playlist, "列表") { onOpenQueue() }
        ListenDockIcon(MiuixIcons.Tune, "设置") { ui.moreSheet = true }
    }
}

@Composable
private fun ListenDockText(label: String, selected: Boolean, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .clip(CircleShape)
            .background(Color.White.copy(alpha = if (selected) 0.16f else 0f))
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick,
            )
            .padding(horizontal = 16.dp, vertical = 9.dp),
    ) {
        Text(
            text = label,
            color = Color.White.copy(alpha = if (selected) 1f else 0.6f),
            fontSize = 11.sp,
            fontWeight = if (selected) FontWeight.Medium else FontWeight.Normal,
        )
    }
}

@Composable
private fun ListenDockIcon(icon: androidx.compose.ui.graphics.vector.ImageVector, label: String, onClick: () -> Unit) {
    Column(
        modifier = Modifier
            .clip(CircleShape)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick,
            )
            .padding(horizontal = 12.dp, vertical = 6.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = label,
            tint = Color.White.copy(alpha = 0.65f),
            modifier = Modifier.size(19.dp),
        )
        Spacer(Modifier.height(3.dp))
        Text(text = label, color = Color.White.copy(alpha = 0.45f), fontSize = 9.sp)
    }
}

/** 音量条：拖动即生效，不做按下才出现的浮层。 */
@Composable
private fun ListenVolumeSlider(
    value: Float,
    modifier: Modifier = Modifier,
    onChange: (Float) -> Unit,
) {
    var trackWidthPx by remember { mutableStateOf(1f) }
    Box(
        modifier = modifier
            .height(22.dp)
            .pointerInput(Unit) {
                detectHorizontalDragGestures(
                    onDragStart = { offset ->
                        trackWidthPx = size.width.toFloat().coerceAtLeast(1f)
                        onChange((offset.x / trackWidthPx).coerceIn(0f, 1f))
                    },
                    onHorizontalDrag = { change, _ ->
                        change.consume()
                        onChange((change.position.x / trackWidthPx).coerceIn(0f, 1f))
                    },
                )
            },
        contentAlignment = Alignment.CenterStart,
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val h = 3.dp.toPx()
            val centerY = size.height / 2f
            val ratio = value.coerceIn(0f, 1f)
            drawRoundRect(
                color = Color.White.copy(alpha = 0.2f),
                size = Size(size.width, h),
                topLeft = Offset(0f, centerY - h / 2f),
                cornerRadius = CornerRadius(h / 2f),
            )
            drawRoundRect(
                color = Color.White.copy(alpha = 0.85f),
                size = Size(size.width * ratio, h),
                topLeft = Offset(0f, centerY - h / 2f),
                cornerRadius = CornerRadius(h / 2f),
            )
            drawCircle(
                color = Color.White,
                radius = 5.dp.toPx(),
                center = Offset(size.width * ratio, centerY),
            )
        }
    }
}