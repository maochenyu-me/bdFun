package com.aritxonly.bdfun.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.unit.dp
import com.aritxonly.teak.common.icons.AppGlyph
import com.aritxonly.teak.common.icons.LucideIcons
import com.kyant.shapes.Capsule
import top.yukonga.miuix.kmp.theme.MiuixTheme

/**
 * 竖屏评论面板（对齐 BiliPai 的竖屏详情）。
 *
 * 由调用方把面板放到播放器底边以下，这里只负责圆角、把手和收起按钮；
 * 收起后回到播放状态，播放器恢复大画面。
 */
@Composable
internal fun PortraitCommentSheet(
    onCollapse: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    val colors = MiuixTheme.colorScheme
    // 收起按钮贴着「回到顶部」正上方排：两者都是右下角的圆形浮钮，
    // 一个在顶一个在底会被读成两个滚动按钮，距离也太远。
    val navigationBottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
    val screenHeight = LocalConfiguration.current.screenHeightDp.dp
    val collapseBottom = navigationBottom + screenHeight * 0.10f + 16.dp + 48.dp
    Box(modifier = modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .clip(RoundedCornerShape(topStart = 18.dp, topEnd = 18.dp))
                .background(colors.background),
        ) {
            // 顶部把手：只是视觉提示，这一层不接管拖动，滚动仍归评论列表。
            Box(
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp, bottom = 2.dp),
                contentAlignment = Alignment.Center,
            ) {
                Box(
                    modifier = Modifier
                        .width(34.dp)
                        .height(4.dp)
                        .clip(Capsule())
                        .background(colors.onBackground.copy(alpha = 0.16f)),
                )
            }
            Box(modifier = Modifier.weight(1f)) { content() }
        }
        Box(
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(end = 12.dp, bottom = collapseBottom)
                .size(40.dp)
                .clip(CircleShape)
                .background(colors.surfaceContainerHigh.copy(alpha = 0.9f))
                .clickable(interactionSource = null, indication = null, onClick = onCollapse),
            contentAlignment = Alignment.Center,
        ) {
            AppGlyph(LucideIcons.ChevronDown, colors.onBackground, size = 18.dp)
        }
    }
}
