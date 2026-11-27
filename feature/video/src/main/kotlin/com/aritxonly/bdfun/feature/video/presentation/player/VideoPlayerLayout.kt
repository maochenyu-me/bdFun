package com.aritxonly.bdfun.ui

import androidx.activity.compose.BackHandler
import android.content.res.Configuration
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.zIndex
import com.aritxonly.bdfun.feature.video.presentation.listen.PlayerListenVideo
import androidx.compose.ui.platform.LocalContext
import com.aritxonly.bdfun.core.domain.usesPortraitChrome
import com.aritxonly.bdfun.feature.video.presentation.VideoPlaybackPlatform

@Composable
fun VideoPlayerLayout(
    playback: VideoPlaybackPlatform, observed: PlayerObservedStateHolder,
    media: VideoMediaInput, presentation: VideoPlayerPresentation, actions: VideoPlayerActions,
    ui: VideoPlayerUiStateHolder, danmakuVisible: Boolean, btLatencyMs: Int,
    sponsor: PlayerSponsorPresentation, modifier: Modifier, ports: PlayerRenderPorts,
) {
    val fullscreen = presentation.fullscreen
    val fullscreenInfo = presentation.fullscreenInfo
    val windowPortrait = LocalConfiguration.current.orientation == Configuration.ORIENTATION_PORTRAIT
    val portraitChrome = usesPortraitChrome(presentation.portraitVideo, windowPortrait)
    val danmaku = media.danmaku
    val onToggleFullscreen = actions.navigation.onToggleFullscreen
    val playing by observed.playing
    val ended by observed.ended
    val positionMs by observed.positionMs
    val durationMs by observed.durationMs
    val bufferedMs by observed.bufferedMs
    // 听视频整页有自己的手势（横滑切页、拖音量），播放器那套点击/滑动会让横滑失效。
    val audioOnly by ui.fsAudioOnly
    val interaction = if (audioOnly) Modifier else
        playerInteractionModifier(playback, ui, fullscreen, ports.page, ports.gestures, onToggleFullscreen)
    Box(
        modifier = modifier
            .background(Color.Black)
            .onGloballyPositioned { ui.danmakuHit.coordinates = it }
            .then(interaction),
    ) {
        PlayerSurface(
            playback, media, presentation, ui, danmakuVisible, btLatencyMs,
            sponsor.prompt,
            picture = ports.picture,
            slots = ports.surface,
        ) { segment ->
            playback.seekTo(segment.endMs)
            sponsor.dismiss(segment)
        }

        // 自动跳过：进入片段即跳到结尾；不写 dismissed，用户拖回片段时会再次生效。
        val autoSkip = sponsor.autoSkip
        LaunchedEffect(autoSkip) {
            autoSkip?.let { playback.seekTo(it.endMs) }
        }

        // 新视频/分 P 首帧未出来时保持黑屏加载，不再先闪一下封面；
        // 换 CDN 和清晰度仍由播放器保留上一帧。

        PlayerStatusOverlays(ui, fullscreen, ended, durationMs, ports.picture.playIcon, ports.buffering)

        BindPlayerOverlayBackHandling(ui, fullscreen)

        val fsActions = ports.fullscreenActions

        PlayerZoomResetOverlay(ui, fullscreen)

        PlayerFullscreenOverlay(
            media, presentation, ui, fsActions, playing, positionMs, durationMs, bufferedMs, ports,
        )

        // 竖屏 chrome 的右侧已经是一整列互动按钮，锁按钮会和它叠在一起，所以竖屏不给锁；
        // 听视频关掉视频轨后整屏只有一块听视频界面，没有能看的画面，锁也没有意义。
        val audioOnly by ui.fsAudioOnly
        if (!portraitChrome && !audioOnly) PlayerLockOverlay(ui, fullscreen, ports.page)
        PlayerScrubOverlay(ui, fullscreen, positionMs, durationMs, bufferedMs)

        PlayerSubtitleLayer(fullscreenInfo, fullscreen, ui, positionMs)

        PlayerInlineControls(playback, observed, presentation, actions, fsActions, ui, ports)

        // 听视频是整页界面：盖住画面、弹幕和所有视频控件，只留这一层。
        if (audioOnly) {
            ListenVideoLayer(
                media, presentation, ui, fsActions, ports, playing, positionMs, durationMs, bufferedMs,
                onToggleFullscreen,
            )
        }

        PlayerDanmakuInputOverlay(fullscreen, danmaku, ui, ports.danmaku.composer)

        ports.inlineMore()
        if (!presentation.pictureInPicture) ports.danmaku.overlays(this, danmakuVisible)
    }
}

@Composable
private fun BindPlayerOverlayBackHandling(ui: VideoPlayerUiStateHolder, fullscreen: Boolean) {
    val controlsVisible by ui.controlsVisible
    var qualityMenu by ui.qualityMenu
    var speedMenu by ui.speedMenu
    var fsLocked by ui.fsLocked
    var fsPanel by ui.fsPanel
    var fsInputOpen by ui.fsInputOpen
    // 控件隐藏时画质/倍速面板一起收回（之前画质面板会一直挂着）
    LaunchedEffect(controlsVisible) {
        if (!controlsVisible) {
            qualityMenu = false
            speedMenu = false
        }
    }

    BackHandler(enabled = fullscreen && fsLocked) {
        // 锁屏时返回键只解锁，不退出全屏。
        fsLocked = false
    }

    BackHandler(enabled = fullscreen && (fsPanel != FullscreenPanel.None || fsInputOpen)) {
        if (fsInputOpen) {
            fsInputOpen = false
        } else {
            fsPanel = FullscreenPanel.None
        }
    }

}

/** 听视频整页 + 返回键处理：返回键先退听视频，再退全屏，和左上角按钮语义一致。 */
@Composable
private fun ListenVideoLayer(
    media: VideoMediaInput,
    presentation: VideoPlayerPresentation,
    ui: VideoPlayerUiStateHolder,
    fsActions: FullscreenActions,
    ports: PlayerRenderPorts,
    playing: Boolean,
    positionMs: Long,
    durationMs: Long,
    bufferedMs: Long,
    onToggleFullscreen: () -> Unit,
) {
    PlayerListenVideo(
        media, presentation, ui, fsActions, playing, positionMs, durationMs, bufferedMs, ports,
        onExitFullscreen = onToggleFullscreen,
        modifier = Modifier.fillMaxSize().zIndex(30f),
    )
    // 听视频接管整屏：任何来源（换稿件、旧状态残留）把播放器收回半屏时，都顶回全屏。
    val fullscreen = presentation.fullscreen
    LaunchedEffect(fullscreen) {
        if (!fullscreen) onToggleFullscreen()
    }
    BackHandler(enabled = true) {
        // 配置面板（音质/倍速/字幕…）开在听视频页之上，返回键先收面板。
        if (ui.fsPanel.value != FullscreenPanel.None) {
            ui.fsPanel.value = FullscreenPanel.None
            return@BackHandler
        }
        fsActions.onAudioOnlyChanged(false)
        if (ui.listenFromInline.value) {
            ui.listenFromInline.value = false
            onToggleFullscreen()
        }
    }
}
