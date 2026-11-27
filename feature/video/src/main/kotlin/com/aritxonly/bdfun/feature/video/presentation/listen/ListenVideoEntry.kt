package com.aritxonly.bdfun.feature.video.presentation.listen

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.zIndex
import com.aritxonly.bdfun.ui.FullscreenActions
import com.aritxonly.bdfun.ui.FullscreenPanel
import com.aritxonly.bdfun.ui.FullscreenPanelHost
import com.aritxonly.bdfun.ui.PlayerRenderPorts
import com.aritxonly.bdfun.ui.VideoMediaInput
import com.aritxonly.bdfun.ui.VideoPlayerPresentation
import com.aritxonly.bdfun.ui.VideoPlayerUiStateHolder
import com.aritxonly.bdfun.core.domain.audioQualityLabelFor
import com.aritxonly.bdfun.core.domain.selectableAudioQualities
import com.aritxonly.bdfun.core.model.video.AudioQuality
import com.aritxonly.bdfun.ui.fullscreenPlaybackProjection

/**
 * 把详情页已有的播放器动作投影成听视频页面需要的一套。
 *
 * 这层只做「翻译」：不新增播放逻辑，也不碰网络。
 * 听视频里能做的事全部落到 [FullscreenActions] 与 [VideoPlayerUiStateHolder]。
 */
@Composable
internal fun PlayerListenVideo(
    media: VideoMediaInput,
    presentation: VideoPlayerPresentation,
    ui: VideoPlayerUiStateHolder,
    fsActions: FullscreenActions,
    playing: Boolean,
    positionMs: Long,
    durationMs: Long,
    bufferedMs: Long,
    ports: PlayerRenderPorts,
    onExitFullscreen: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val activity = LocalContext.current as? android.app.Activity
    val info = presentation.fullscreenInfo
    // 换集/换稿件要重置页内状态（页签、偏移、A-B），否则会带着上一个视频的设定。
    val state = remember(info.currentCid) { ListenVideoUiStateHolder() }
    val listen = ports.listen

    val lyrics = remember(info.subtitleCues) { lyricsFromSubtitles(info.subtitleCues) }
    val secondary = remember(info.secondaryCues) { lyricsFromSubtitles(info.secondaryCues) }
    val imported = ui.listenImportedLyrics.value
    if (imported != null && imported !== state.importedLyrics) state.importedLyrics = imported

    // 进听视频但还没选过字幕：自动挑一条中文轨当歌词，再挑一条不同语言的当「译」。
    AutoPickSubtitles(info, fsActions)

    val sleepAt = ui.fsSleepAt.value
    val (audios, audioLabel) = listenAudioTracks(media, ui.fsAudioQualityId.value)
    val playback = ListenVideoPlayback(
        playing = playing,
        positionMs = positionMs,
        durationMs = durationMs,
        bufferedMs = bufferedMs,
        speed = ui.speed.value,
        loopMode = ui.fsLoopMode.value,
        shuffle = ui.fsShuffle.value,
        backgroundPlay = listen.backgroundPlay(),
        sleepTimerLabel = sleepTimerLabel(sleepAt),
        sleepAtMs = sleepAt,
    )

    val capabilities = ListenMenuCapabilities(
        hasPages = info.pages.size > 1,
        hasLyrics = lyrics.lines.isNotEmpty() || state.importedLyrics != null,
        canFavorite = info.loggedIn,
        canShare = true,
        canPip = true,
        canChangeOrientation = activity != null,
        hasAudioQualities = audios.size > 1,
        loggedIn = info.loggedIn,
    )

    val videoInfo = ListenVideoInfo(
        title = info.title,
        author = info.author,
        cover = info.cover,
        pages = info.pages,
        currentCid = info.currentCid,
        lyrics = lyrics,
        secondaryLyrics = secondary,
        audioLabel = audioLabel,
        audios = audios,
        selectedAudioId = ui.fsAudioQualityId.value,
        favorited = info.favorited,
        capabilities = capabilities,
    )

    // A-B 循环：越过 B 点自动回到 A。只在真正越界时 seek 一次。
    androidx.compose.runtime.LaunchedEffect(positionMs, state.abLoop) {
        state.abLoop.seekBackTarget(positionMs)?.let(fsActions.onSeek)
    }

    val actions = listenActions(ui, fsActions, state, info, activity, onExitFullscreen, listen, positionMs)

    ListenVideoScreen(
        info = videoInfo,
        playback = playback,
        actions = actions,
        ui = state,
        palette = listen.samplePalette(info.cover),
        coverImage = ports.cover,
        icons = listen.icons,
        modifier = modifier,
    )

    ListenPanelHost(
        media, presentation, ui, fsActions, ports, playing, positionMs, durationMs, bufferedMs,
        // 听视频页整页铺满且带 zIndex(30)，配置面板必须比它更高，否则会被压在下面看不见。
        modifier = Modifier.fillMaxSize().zIndex(31f),
    )
}

/** 选集 / 音质 / 倍速 / 收藏 / 字幕面板复用现有实现，听视频里也要能开。 */
@Composable
private fun ListenPanelHost(
    media: VideoMediaInput,
    presentation: VideoPlayerPresentation,
    ui: VideoPlayerUiStateHolder,
    fsActions: FullscreenActions,
    ports: PlayerRenderPorts,
    playing: Boolean,
    positionMs: Long,
    durationMs: Long,
    bufferedMs: Long,
    modifier: Modifier = Modifier,
) {
    val panel = ui.fsPanel.value
    if (panel == FullscreenPanel.None || panel == FullscreenPanel.Comments) return
    val visuals = ports.fullscreenVisuals()
    Box(modifier = modifier) {
        FullscreenPanelHost(
            panel = panel,
            info = presentation.fullscreenInfo,
            playback = fullscreenPlaybackProjection(
                media, ui, playing, positionMs, durationMs, bufferedMs, visuals,
                alwaysShowTransport = true,
            ),
            actions = fsActions,
            onClose = { ui.fsPanel.value = FullscreenPanel.None },
            accentColor = visuals.appearance.accentColor,
            danmakuSettings = visuals.slots.danmakuSettings,
            portrait = true,
            slots = visuals.slots,
        )
    }
}

private fun toggleActivityOrientation(activity: android.app.Activity) {
    val landscape = activity.resources.configuration.orientation ==
        android.content.res.Configuration.ORIENTATION_LANDSCAPE
    activity.requestedOrientation = if (landscape) {
        android.content.pm.ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
    } else {
        android.content.pm.ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
    }
}

/** 当前稿件的可选音轨与菜单文案；换稿件后原档位不存在时文案回落成「自动」。 */
private fun listenAudioTracks(media: VideoMediaInput, selectedId: Int): Pair<List<AudioQuality>, String> {
    val audios = selectableAudioQualities(media.info?.audios.orEmpty())
    return audios to audioQualityLabelFor(audios, selectedId)
}

/** 定时关闭文案：没开就不显示右侧小字。 */private fun sleepTimerLabel(sleepAtMs: Long): String {
    val now = System.currentTimeMillis()
    if (sleepAtMs <= now) return ""
    val minutes = ((sleepAtMs - now + 59_999L) / 60_000L).toInt()
    return "$minutes 分钟后"
}

/** 没手动选过字幕时自动挑主/副两条，避免听视频进来是一片空白歌词。 */
@Composable
private fun AutoPickSubtitles(
    info: com.aritxonly.bdfun.ui.FullscreenPlayerInfo,
    fsActions: FullscreenActions,
) {
    androidx.compose.runtime.LaunchedEffect(info.currentCid, info.subtitles) {
        if (info.subtitleCues.isNotEmpty() || info.subtitles.isEmpty()) return@LaunchedEffect
        val primary = pickPrimarySubtitle(info.subtitles)
        if (primary != null) fsActions.onSubtitleSelected(primary)
        pickSecondarySubtitle(info.subtitles, primary)?.let(fsActions.onSecondarySubtitleSelected)
    }
}

/** 主字幕优先中文（含 AI 中文），否则取第一条。 */
internal fun pickPrimarySubtitle(tracks: List<com.aritxonly.bdfun.core.model.video.SubtitleTrack>) =
    tracks.firstOrNull { it.label.contains("中文") || it.label.contains("简体") || it.label.contains("zh", true) }
        ?: tracks.firstOrNull()

/** 副字幕取和主轨不同的语言，优先英文；挑不到返回 null（不强行塞同一条）。 */
internal fun pickSecondarySubtitle(
    tracks: List<com.aritxonly.bdfun.core.model.video.SubtitleTrack>,
    primary: com.aritxonly.bdfun.core.model.video.SubtitleTrack?,
) = tracks.filter { it.id != primary?.id }
    .minByOrNull { if (it.label.contains("英", true) || it.label.contains("en", true)) 0 else 1 }

/** 听视频的动作映射：全部落到详情页已有的动作与面板上，不新增播放逻辑。 */
private fun listenActions(
    ui: VideoPlayerUiStateHolder,
    fsActions: FullscreenActions,
    state: ListenVideoUiStateHolder,
    info: com.aritxonly.bdfun.ui.FullscreenPlayerInfo,
    activity: android.app.Activity?,
    onExitFullscreen: () -> Unit,
    listen: com.aritxonly.bdfun.ui.ListenVideoPorts,
    positionMs: Long,
): ListenVideoActions = ListenVideoActions(

        onBack = { exitListen(ui, fsActions, onExitFullscreen) },
        onPlayPause = fsActions.onPlayPause,
        onSeek = fsActions.onSeek,
        onSelectPage = { cid -> info.pages.firstOrNull { it.cid == cid }?.let(fsActions.onPageSelected) },
        onMenuEntry = { entry ->
            when (entry) {
                ListenMenuEntry.VIDEO_MODE -> exitListen(ui, fsActions, onExitFullscreen)
                ListenMenuEntry.COLLECTION -> ui.fsPanel.value = FullscreenPanel.Episodes
                ListenMenuEntry.AUDIO_QUALITY -> state.audioQualitySheet = true
                ListenMenuEntry.SPEED -> ui.fsPanel.value = FullscreenPanel.Speed
                // 面板里的「播放模式」就地展开四档，由 sheet 内部回调，不再在这里盲切一档。
                ListenMenuEntry.PLAY_MODE -> Unit
                // 定时关闭由面板内联展开，不跳回视频播放器那套「更多」。
                ListenMenuEntry.SLEEP_TIMER -> Unit
                ListenMenuEntry.AB_LOOP -> state.abLoop = state.abLoop.mark(positionMs)
                ListenMenuEntry.TRANSLATION -> state.translationOn = !state.translationOn
                ListenMenuEntry.FAVORITE -> {
                    fsActions.onRequestFavorites()
                    ui.fsPanel.value = FullscreenPanel.Favorite
                }
                ListenMenuEntry.SHARE -> fsActions.onShare()
                ListenMenuEntry.PIP -> fsActions.onPictureInPicture()
                ListenMenuEntry.ORIENTATION -> activity?.let(::toggleActivityOrientation)
                ListenMenuEntry.SUBTITLE_SETTINGS -> ui.fsPanel.value = FullscreenPanel.Subtitles
                ListenMenuEntry.LYRICS_SEARCH -> listen.requestLyricsPicker()
                ListenMenuEntry.BACKGROUND_PLAY ->
                    fsActions.onBackgroundPlayChanged(!listen.backgroundPlay())
            }
        },
        onImportLyrics = listen.requestLyricsPicker,
        onVolumeChange = { listen.setVolume(it) },
        onTranslationToggle = { on -> state.translationOn = on },
        onSleepAtChanged = fsActions.onSleepAtChanged,
        // 底部按钮与面板选档共用这一个入口：一次写入完整档位，避免随机与循环各写一半。
        onPlayModeSelected = { mode, shuffle ->
            ui.fsLoopMode.value = mode
            ui.fsShuffle.value = shuffle
            fsActions.onLoopModeChanged(mode)
        },
        // 只改音轨档位；实际重起播由 PlayerSourceEffects 按 audioQualityId 触发。
        onAudioQualitySelected = { id -> ui.fsAudioQualityId.value = id },
)

private fun exitListen(
    ui: VideoPlayerUiStateHolder,
    fsActions: FullscreenActions,
    onExitFullscreen: () -> Unit,
) {
    // 随机是听视频页面内的临时开关，回到视频播放器就归位，避免「明明没开过却乱跳」。
    ui.fsShuffle.value = false
    fsActions.onAudioOnlyChanged(false)
    if (ui.listenFromInline.value) {
        ui.listenFromInline.value = false
        onExitFullscreen()
    }
}
