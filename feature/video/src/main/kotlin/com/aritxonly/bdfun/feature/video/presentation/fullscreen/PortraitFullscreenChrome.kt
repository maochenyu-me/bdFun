package com.aritxonly.bdfun.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.aritxonly.teak.common.icons.AppGlyph
import com.aritxonly.teak.common.icons.LucideIcons
import com.aritxonly.teak.common.player.PauseGlyph
import com.aritxonly.teak.common.player.PixelIcon
import com.aritxonly.teak.common.player.PlayGlyph
import com.kyant.shapes.Capsule
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Add

/**
 * 竖屏全屏的控件骨架。
 *
 * 竖屏视频铺满整屏后，横向 chrome（标题在顶栏、互动在顶栏右侧）会挤成一团，
 * 因此这里按 B 站竖屏的阅读顺序重排：顶部只留导航，互动竖排在右侧拇指区，
 * 作者/标题落在左下，播放进度与控制贴底。
 */
@Composable
fun BoxScope.PortraitFullscreenChrome(
    info: FullscreenPlayerInfo,
    playback: FullscreenPlaybackState,
    actions: FullscreenActions,
    onPanelChanged: (FullscreenPanel) -> Unit,
    appearance: FullscreenAppearance,
    slots: FullscreenChromeSlots,
    scrub: FullscreenScrubActions,
) {
    PortraitChromeMasks()
    PortraitChromeHeader(actions, onPanelChanged)
    PortraitChromeAuthor(info, actions, slots)
    PortraitChromeInteractionRail(info, actions, appearance, onPanelChanged)
    PortraitChromeControls(info, playback, actions, onPanelChanged, appearance, scrub)
}

/** 顶部/底部/右侧三段压暗，保证白字在亮画面上仍可读。 */
@Composable
private fun BoxScope.PortraitChromeMasks() {
    Box(
        Modifier
            .fillMaxWidth()
            .height(210.dp)
            .background(
                Brush.verticalGradient(
                    listOf(Color.Black.copy(alpha = 0.62f), Color.Black.copy(alpha = 0.28f), Color.Transparent),
                ),
            ),
    )
    Box(
        Modifier
            .align(Alignment.BottomCenter)
            .fillMaxWidth()
            .height(300.dp)
            .background(
                Brush.verticalGradient(
                    listOf(Color.Transparent, Color.Black.copy(alpha = 0.42f), Color.Black.copy(alpha = 0.86f)),
                ),
            ),
    )
    Box(
        Modifier
            .align(Alignment.CenterEnd)
            .fillMaxHeight()
            .width(96.dp)
            .background(Brush.horizontalGradient(listOf(Color.Transparent, Color.Black.copy(alpha = 0.34f)))),
    )
}

@Composable
private fun PortraitChromeHeader(actions: FullscreenActions, onPanelChanged: (FullscreenPanel) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .windowInsetsPadding(WindowInsets.displayCutout)
            .padding(start = 12.dp, end = 12.dp, top = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        PortraitGlassButton(size = 34.dp, onClick = actions.onBack) { BackChevron() }
        Spacer(Modifier.width(8.dp))
        PortraitGlassButton(size = 34.dp, onClick = actions.onHome) { InlineHomeGlyph() }
        Spacer(Modifier.weight(1f))
        PortraitGlassButton(size = 34.dp, onClick = { onPanelChanged(FullscreenPanel.More) }) { MoreGlyph() }
    }
}

@Composable
private fun BoxScope.PortraitChromeAuthor(
    info: FullscreenPlayerInfo,
    actions: FullscreenActions,
    slots: FullscreenChromeSlots,
) {
    Column(
        modifier = Modifier
            .align(Alignment.BottomStart)
            .fillMaxWidth(0.72f)
            .padding(start = 16.dp, bottom = 96.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            slots.avatar(info.authorFace, Modifier.size(26.dp).clip(CircleShape))
            Spacer(Modifier.width(8.dp))
            Text(
                text = info.author,
                color = Color.White,
                fontSize = 12.5.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f, fill = false),
            )
            Spacer(Modifier.width(10.dp))
            Row(
                modifier = Modifier
                    .height(24.dp)
                    .clip(Capsule())
                    .background(Color.Black.copy(alpha = 0.35f))
                    .clickable(onClick = actions.onOpenSpace)
                    .padding(horizontal = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (!info.followed) {
                    AppGlyph(MiuixIcons.Add, Color.White, Modifier.size(11.dp))
                    Spacer(Modifier.width(2.dp))
                }
                Text(
                    text = if (info.followed) "已关注" else "关注",
                    color = Color.White,
                    fontSize = 11.5.sp,
                )
            }
        }
        Spacer(Modifier.height(10.dp))
        Text(
            text = info.title,
            color = Color.White,
            fontSize = 14.sp,
            fontWeight = FontWeight.Medium,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
        Spacer(Modifier.height(6.dp))
        Text(
            text = "视频详情",
            color = Color.White.copy(alpha = 0.72f),
            fontSize = 12.sp,
            modifier = Modifier.clickable(onClick = actions.onBack),
        )
    }
}

/** 右侧竖排互动：竖屏下这是唯一不挡画面的位置，也是单手可达区。 */
@Composable
private fun BoxScope.PortraitChromeInteractionRail(
    info: FullscreenPlayerInfo,
    actions: FullscreenActions,
    appearance: FullscreenAppearance,
    onPanelChanged: (FullscreenPanel) -> Unit,
) {
    if (!appearance.showActions) return
    val icons = appearance.icons
    Column(
        modifier = Modifier
            .align(Alignment.BottomEnd)
            .padding(end = 8.dp, bottom = 96.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        PortraitStatAction(icons.like(info.liked), info.likeCount, appearance.accentColor, info.liked, actions.onLike)
        PortraitStatAction(icons.comment, info.replyCount, appearance.accentColor, false, actions.onComment)
        PortraitStatAction(LucideIcons.CoinsFilled, info.coinCount, appearance.accentColor, info.coined, actions.onCoin)
        PortraitStatAction(icons.favorite, info.favoriteCount, appearance.accentColor, info.favorited) {
            onPanelChanged(FullscreenPanel.Favorite)
            actions.onRequestFavorites()
        }
        PortraitStatAction(icons.share, info.shareCount, appearance.accentColor, false, actions.onShare)
    }
}

@Composable
private fun PortraitStatAction(
    icon: Int,
    count: Long,
    accentColor: Color,
    active: Boolean,
    onClick: () -> Unit,
) {
    Column(
        modifier = Modifier
            .width(46.dp)
            .clip(Capsule())
            .clickable(onClick = onClick)
            .padding(vertical = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        PixelIcon(icon, Modifier.size(26.dp), tint = if (active) accentColor else Color.White)
        Spacer(Modifier.height(3.dp))
        Text(
            text = if (count > 0) compactCount(count) else "",
            color = Color.White,
            fontSize = 10.sp,
            maxLines = 1,
        )
    }
}

@Composable
private fun BoxScope.PortraitChromeControls(
    info: FullscreenPlayerInfo,
    playback: FullscreenPlaybackState,
    actions: FullscreenActions,
    onPanelChanged: (FullscreenPanel) -> Unit,
    appearance: FullscreenAppearance,
    scrub: FullscreenScrubActions,
) {
    val icons = appearance.icons
    Column(
        modifier = Modifier
            .align(Alignment.BottomCenter)
            .fillMaxWidth()
            .padding(start = 12.dp, end = 12.dp, bottom = 8.dp),
    ) {
        FullscreenSeekBar(
            positionMs = playback.positionMs,
            durationMs = playback.durationMs,
            bufferedMs = playback.bufferedMs,
            onInteract = {},
            onSeek = actions.onSeek,
            modifier = Modifier.fillMaxWidth(),
            externalPressed = playback.scrubbing,
            externalLook = playback.seekLook,
            onScrubStart = scrub.onScrubStart,
            onScrubEnd = scrub.onScrubEnd,
            onLookChanged = scrub.onScrubLook,
            onScrubPreview = scrub.onScrubPreview,
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            PlayerIconButton(onClick = actions.onPlayPause, size = 34.dp) {
                if (playback.playing) PauseGlyph() else PlayGlyph(icons.play)
            }
            Spacer(Modifier.width(4.dp))
            Text(
                text = "${formatPlayTime(playback.positionMs)}/${formatPlayTime(playback.durationMs)}",
                color = Color.White.copy(alpha = 0.92f),
                fontSize = 11.5.sp,
            )
            Spacer(Modifier.weight(1f))
            PortraitChromeActions(info, playback, actions, onPanelChanged, appearance)
        }
    }
}

/**
 * 竖屏底栏右侧那一排：弹幕开关 / 弹幕设置 / 字幕 / 倍速 / 清晰度 / 发弹幕 / 收起。
 *
 * 单独抽出来是因为每个入口都是「图标或文字 + 固定内边距」的重复结构，
 * 留在 [PortraitChromeControls] 里会把那个函数顶过行数上限，也不便于逐个调整。
 */
@Composable
private fun PortraitChromeActions(
    info: FullscreenPlayerInfo,
    playback: FullscreenPlaybackState,
    actions: FullscreenActions,
    onPanelChanged: (FullscreenPanel) -> Unit,
    appearance: FullscreenAppearance,
) {
    val icons = appearance.icons
    Box(
        modifier = Modifier
            .size(32.dp)
            .clip(CircleShape)
            .clickable(onClick = actions.onToggleDanmaku),
        contentAlignment = Alignment.Center,
    ) {
        DanmakuToggleGlyph(icons.danmakuOn, icons.danmakuOff, playback.danmakuEnabled)
    }
    Box(
        modifier = Modifier
            .size(32.dp)
            .clip(CircleShape)
            .clickable { onPanelChanged(FullscreenPanel.Danmaku) },
        contentAlignment = Alignment.Center,
    ) {
        SlidersGlyph(icons.danmakuSettings)
    }
    if (info.selectedSubtitle != null) {
        Text(
            text = "CC",
            color = appearance.accentColor,
            fontSize = 12.sp,
            modifier = Modifier
                .clip(Capsule())
                .clickable { onPanelChanged(FullscreenPanel.Subtitles) }
                .padding(horizontal = 6.dp, vertical = 4.dp),
        )
    }
    Text(
        text = "${trimSpeed(playback.speed)}×",
        color = Color.White,
        fontSize = 12.sp,
        modifier = Modifier
            .clip(Capsule())
            .clickable { onPanelChanged(FullscreenPanel.Speed) }
            .padding(horizontal = 6.dp, vertical = 4.dp),
    )
    Text(
        text = info.qualityLabel,
        color = Color.White,
        fontSize = 12.sp,
        modifier = Modifier
            .clip(Capsule())
            .clickable { onPanelChanged(FullscreenPanel.Quality) }
            .padding(horizontal = 6.dp, vertical = 4.dp),
    )
    // 竖屏没有横向那条弹幕输入胶囊，这里留一个入口，否则竖屏发不了弹幕
    Box(
        modifier = Modifier
            .size(32.dp)
            .clip(CircleShape)
            .clickable(onClick = actions.onSendDanmaku),
        contentAlignment = Alignment.Center,
    ) {
        AppGlyph(LucideIcons.SendHorizontalFilled, Color.White, Modifier.size(19.dp))
    }
    Box(
        modifier = Modifier
            .size(32.dp)
            .clip(CircleShape)
            .clickable(onClick = actions.onBack),
        contentAlignment = Alignment.Center,
    ) {
        AppGlyph(LucideIcons.ChevronDown, Color.White, Modifier.size(22.dp))
    }
}

@Composable
private fun PortraitGlassButton(size: androidx.compose.ui.unit.Dp, onClick: () -> Unit, content: @Composable () -> Unit) {
    Box(
        modifier = Modifier
            .size(size)
            .clip(CircleShape)
            .background(Color.Black.copy(alpha = 0.32f))
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        content()
    }
}
