package com.aritxonly.bdfun.feature.home.presentation

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.layout
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.aritxonly.bdfun.core.domain.resolveHomeRefreshLocatorDelayMillis
import kotlinx.coroutines.delay
import kotlin.math.roundToInt
import top.yukonga.miuix.kmp.theme.MiuixTheme

/** 关闭按钮的命中区与图标尺寸，和参考实现一致：22dp 命中、14dp 文字。 */
private val CloseHitSize = 22.dp
private val CloseGlyphSize = 14.sp
private val TextCloseGap = 6.dp

/** 拖动中的归一化位置与可放置区域；按帧更新，所以用可变状态。 */
private class LocatorDragPosition {
    var x by mutableFloatStateOf(0.94f)
    var y by mutableFloatStateOf(0.38f)
    var availableWidth = 0
    var availableHeight = 0
}

/**
 * 「定位上次刷新」悬浮胶囊：可拖动、松手记住位置并重新计时。
 *
 * 只有本轮刷新确实保留了旧内容时调用方才会显示它；计时归零或点关闭都由 [onDismiss] 收起，
 * 点击正文先触发 [onClick] 再收起，避免回滚到分隔条后胶囊还盖在列表上。
 */
@Composable
fun HomeRefreshLocatorPill(
    refreshKey: Int,
    visible: Boolean,
    x: Float,
    y: Float,
    durationSeconds: Int,
    onClick: () -> Unit,
    onDismiss: () -> Unit,
    onPositionChanged: (Float, Float) -> Unit,
    modifier: Modifier = Modifier,
) {
    val position = remember { LocatorDragPosition() }
    var dragging by remember { mutableStateOf(false) }
    // 关闭后本次刷新不再出现；换了一轮刷新才重新给机会。
    var closed by rememberSaveable(refreshKey) { mutableStateOf(false) }

    LaunchedEffect(x, y) {
        if (dragging) return@LaunchedEffect
        position.x = x
        position.y = y
    }

    // 拖动中暂停计时，松手后 key 变化重新开始，等于「松手重新计时」。
    LaunchedEffect(refreshKey, durationSeconds, dragging, visible, closed) {
        if (!visible || dragging || closed) return@LaunchedEffect
        val delayMillis = resolveHomeRefreshLocatorDelayMillis(durationSeconds)
        if (delayMillis <= 0L) return@LaunchedEffect
        delay(delayMillis)
        onDismiss()
    }

    AnimatedVisibility(
        visible = visible && !closed,
        enter = fadeIn(tween(160)) + scaleIn(initialScale = 0.92f),
        exit = fadeOut(tween(140)),
        modifier = modifier
            .fillMaxSize()
            .layout { measurable, constraints ->
                val placeable = measurable.measure(constraints.copy(minWidth = 0, minHeight = 0))
                position.availableWidth = (constraints.maxWidth - placeable.width).coerceAtLeast(0)
                position.availableHeight = (constraints.maxHeight - placeable.height).coerceAtLeast(0)
                layout(constraints.maxWidth, constraints.maxHeight) {
                    // 只在测量阶段按归一化坐标摆位置，拖动不需要重组整页。
                    placeable.place(
                        (position.availableWidth * position.x).roundToInt(),
                        (position.availableHeight * position.y).roundToInt(),
                    )
                }
            },
    ) {
        val colors = MiuixTheme.colorScheme
        Row(
            modifier = Modifier
                .heightIn(min = 40.dp)
                .clip(RoundedCornerShape(50))
                .background(colors.surfaceContainerHigh.copy(alpha = 0.92f))
                .pointerInput(position) {
                    detectDragGestures(
                        onDragStart = { dragging = true },
                        onDragEnd = {
                            dragging = false
                            onPositionChanged(position.x, position.y)
                        },
                        onDragCancel = { dragging = false },
                    ) { change, dragAmount ->
                        change.consume()
                        if (position.availableWidth > 0) {
                            position.x = (position.x + dragAmount.x / position.availableWidth).coerceIn(0f, 1f)
                        }
                        if (position.availableHeight > 0) {
                            position.y = (position.y + dragAmount.y / position.availableHeight).coerceIn(0f, 1f)
                        }
                    }
                }
                .clickable {
                    onClick()
                    closed = true
                    onDismiss()
                }
                .padding(horizontal = 14.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Start,
        ) {
            Text(
                text = "定位上次刷新",
                color = colors.onSurface,
                fontSize = 13.sp,
                fontWeight = FontWeight.Medium,
            )
            Spacer(Modifier.width(TextCloseGap))
            Box(
                modifier = Modifier
                    .size(CloseHitSize)
                    .clip(CircleShape)
                    .clickable {
                        closed = true
                        onDismiss()
                    },
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = "×",
                    color = colors.onSurfaceVariantSummary,
                    fontSize = CloseGlyphSize,
                    fontWeight = FontWeight.Medium,
                    textAlign = TextAlign.Center,
                )
            }
        }
    }
}
