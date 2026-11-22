package com.aritxonly.bdfun.feature.home.presentation

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.layout
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import kotlin.math.roundToInt
import top.yukonga.miuix.kmp.theme.MiuixTheme

/** 定位胶囊的高度；撤销胶囊贴到它正下方时用它算纵向让位。 */
private val LocatorPillHeight = 40.dp

/** 撤销胶囊与定位胶囊之间的间隔。 */
private val UndoAnchorGap = 8.dp

/**
 * 「新增 N 条内容」提示：刷新成功后压在列表第一行上方，几秒后自动消失。
 * 位置由调用方传入列表顶部的实际留白（状态栏 + 顶栏），不在组件里猜。
 */
@Composable
internal fun HomeRefreshNewItemsTip(
    count: Int,
    visible: Boolean,
    topPadding: Dp,
    modifier: Modifier = Modifier,
) {
    val colors = MiuixTheme.colorScheme
    AnimatedVisibility(
        visible = visible && count > 0,
        enter = fadeIn(tween(180)) + slideInVertically(tween(200), initialOffsetY = { -it / 2 }),
        exit = fadeOut(tween(140)) + slideOutVertically(tween(160), targetOffsetY = { -it / 2 }),
        modifier = modifier,
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = topPadding)
                .zIndex(90f),
            contentAlignment = Alignment.TopCenter,
        ) {
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(50))
                    .background(colors.surfaceContainerHigh.copy(alpha = 0.92f))
                    .padding(horizontal = 18.dp, vertical = 7.dp),
            ) {
                Text(
                    text = "新增 $count 条内容",
                    color = colors.onSurface,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium,
                )
            }
        }
    }
}

/**
 * 「撤销刷新」胶囊：跟着定位胶囊的位置走，贴在它正下方；横滑会被吞掉，避免与列表手势抢。
 * 是否显示（含 5 秒自动失效）由调用方控制。
 */
@Composable
internal fun HomeRefreshUndoPill(
    visible: Boolean,
    anchorX: Float,
    anchorY: Float,
    onUndo: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = MiuixTheme.colorScheme
    AnimatedVisibility(
        visible = visible,
        enter = fadeIn(tween(180)) + slideInVertically(tween(220), initialOffsetY = { it / 4 }),
        exit = fadeOut(tween(140)) + slideOutVertically(tween(160), targetOffsetY = { it / 4 }),
        modifier = modifier
            .zIndex(95f)
            .fillMaxSize()
            .layout { measurable, constraints ->
                val placeable = measurable.measure(constraints.copy(minWidth = 0, minHeight = 0))
                val maxX = (constraints.maxWidth - placeable.width).coerceAtLeast(0)
                val maxY = (constraints.maxHeight - placeable.height).coerceAtLeast(0)
                val belowLocator = LocatorPillHeight.roundToPx() + UndoAnchorGap.roundToPx()
                layout(constraints.maxWidth, constraints.maxHeight) {
                    // 与定位胶囊同一个归一化横坐标，纵坐标往下让一个胶囊高，紧贴它下沿。
                    val left = (maxX * anchorX).roundToInt()
                    val top = (constraints.maxHeight * anchorY + belowLocator).roundToInt().coerceAtMost(maxY)
                    placeable.place(left, top)
                }
            },
    ) {
        Row(
            modifier = Modifier
                .clip(RoundedCornerShape(50))
                .background(colors.surfaceContainerHigh.copy(alpha = 0.92f))
                .clickable(onClick = onUndo)
                .padding(horizontal = 14.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Start,
        ) {
            Text(
                text = "⟲",
                color = colors.onSurface,
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.width(4.dp))
            Text(
                text = "撤销刷新",
                color = colors.onSurface,
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold,
            )
            Spacer(Modifier.width(6.dp))
            Box(
                modifier = Modifier
                    .size(22.dp)
                    .clip(CircleShape)
                    .clickable(onClick = onDismiss),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = "×",
                    color = colors.onSurfaceVariantSummary,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Medium,
                    textAlign = TextAlign.Center,
                )
            }
        }
    }
}
