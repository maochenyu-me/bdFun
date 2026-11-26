package com.aritxonly.bdfun.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp

@Composable
fun VideoPlayerEntry(
    media: VideoMediaInput, presentation: VideoPlayerPresentation,
    actions: VideoPlayerActions, modifier: Modifier = Modifier, ports: PlayerEntryPorts,
) {
    val ui = remember {
        VideoPlayerUiStateHolder(
            ports.defaultSpeed(), ports.autoPlayNext(), ports.loopMode(), presentation.fullscreen,
        )
    }
    val playback = remember {
        ports.createPlayback()
    }
    val observed = rememberPlayerObservedState(playback)
    BindPlayerInitialEffects(playback, media, ui, ports.engine)
    val tracking = remember { ports.tracking() }
    val sponsor = rememberPlayerSponsor(media.bvid, tracking, observed, ports.sponsorPreferences)
    // 跳转本身由布局里的 autoSkip 执行，这里只按设置补一条提示。
    val autoSkipped = sponsor.autoSkip
    LaunchedEffect(autoSkipped) {
        autoSkipped?.let { segment ->
            if (ports.sponsorPreferences().noticeEnabled) {
                ports.engine.diagnostics.toast("已跳过${sponsorCategoryLabel(segment.category)}片段")
            }
        }
    }
    BindPlayerPresentationSetup(media, presentation, ui, ports.engine, observed.portraitVideo.value)
    val latencyMs = ports.bluetoothLatency()
    BindPlayerContinuation(playback, presentation.fullscreenInfo, actions.transport.onPageSelected, ui,
        ports.engine.page, ports.hasNext, ports.engine.diagnostics.toast, ports.engine.diagnostics.log)
    BindPlayerEngineEffects(playback, observed, media, presentation, actions, ui, tracking, ports.engine)
    val audioOnly by ui.fsAudioOnly
    val danmakuVisible = !audioOnly && ports.danmakuOn() && media.danmaku.isNotEmpty()
    val playing by observed.playing
    BindPlayerHotDanmaku(playback, danmakuVisible, ui)
    BindPlayerDanmakuClock(playback, playing, danmakuVisible, ui)
    BindPlayerSystemBars(presentation.fullscreen, presentation.immersiveStatusBar, ui, presentation.portraitVideo)
    BindPlayerHdrWindow(media.quality, presentation.fullscreen)
    VideoPlayerLayout(playback, observed, media, presentation, actions, ui, danmakuVisible, latencyMs, sponsor, modifier,
        ports.render(playback, media, presentation, actions, ui))
}
