package com.aritxonly.bdfun.feature.video.presentation.listen

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.aritxonly.bdfun.core.domain.AutoAudioQualityId
import com.aritxonly.bdfun.core.domain.playModeAfterModeToggle
import com.aritxonly.bdfun.core.domain.toggleShuffle
import com.aritxonly.bdfun.core.model.video.AudioQuality
import com.aritxonly.bdfun.core.model.video.VideoPage

/** 听视频页面需要的内容。全部由 app 侧从现有详情页状态投影出来，模块内不查网络。 */
data class ListenVideoInfo(
    val title: String,
    val author: String,
    val cover: String,
    val pages: List<VideoPage>,
    val currentCid: Long,
    val lyrics: Lyrics,
    val secondaryLyrics: Lyrics = Lyrics.Empty,
    /** 菜单右侧显示的当前音轨，如「192K」「自动」。 */
    val audioLabel: String = "",
    /** 可选音轨（已按档位排好序，自动档单独在面板里给）。 */
    val audios: List<AudioQuality> = emptyList(),
    val selectedAudioId: Int = AutoAudioQualityId,
    val favorited: Boolean = false,
    val capabilities: ListenMenuCapabilities = ListenMenuCapabilities(),
) {
    val hasPrevious: Boolean
        get() = pages.indexOfFirst { it.cid == currentCid }.let { it > 0 }

    val hasNext: Boolean
        get() = pages.indexOfFirst { it.cid == currentCid }
            .let { it >= 0 && it < pages.lastIndex }

    fun previousCid(): Long? = pages.getOrNull(pages.indexOfFirst { it.cid == currentCid } - 1)?.cid
    fun nextCid(): Long? = pages.getOrNull(pages.indexOfFirst { it.cid == currentCid } + 1)?.cid

    fun menuState(
        ui: ListenVideoUiStateHolder,
        playback: ListenVideoPlayback,
        /** 菜单里只标「点了会转成什么」，不能写死横屏。 */
        orientationLabel: String = "横屏",
    ): ListenMenuState =
        ListenMenuState(
            speed = playback.speed,
            loopMode = playback.loopMode,
            shuffle = playback.shuffle,
            sleepTimerLabel = playback.sleepTimerLabel,
            abLoopLabel = ui.abLoop.label(),
            translationOn = ui.translationOn,
            audioLabel = audioLabel,
            favorited = favorited,
            backgroundPlay = playback.backgroundPlay,
            orientationLabel = orientationLabel,
            lyricsStyle = ui.lyricsStyle,
            lyricsOffsetMs = ui.lyricsOffsetMs,
        )
}

/** 播放态投影：听视频只读这些值，不反向持有播放器。 */
data class ListenVideoPlayback(
    val playing: Boolean,
    val positionMs: Long,
    val durationMs: Long,
    val bufferedMs: Long,
    val speed: Float,
    val loopMode: Int = 0,
    val shuffle: Boolean = false,
    val backgroundPlay: Boolean,
    val sleepTimerLabel: String = "",
    /** 定时关闭的目标时间戳；0 表示未开启。 */
    val sleepAtMs: Long = 0L,
)

/** 听视频页面的动作。全部落到详情页已有的播放器动作上。 */
class ListenVideoActions(
    val onBack: () -> Unit,
    val onPlayPause: () -> Unit,
    val onSeek: (Long) -> Unit,
    val onSelectPage: (Long) -> Unit,
    val onMenuEntry: (ListenMenuEntry) -> Unit = {},
    val onImportLyrics: () -> Unit = {},
    val onVolumeChange: (Float) -> Unit = {},
    val onTranslationToggle: (Boolean) -> Unit = {},
    val onSleepAtChanged: (Long) -> Unit = {},
    /**
     * 设置完整的一档播放模式（循环档位 + 随机开关）。
     * 底部两个按钮与「更多」面板里的选择都落到这里，避免出现两套各改一半的入口。
     */
    val onPlayModeSelected: (loopMode: Int, shuffle: Boolean) -> Unit = { _, _ -> },
    /** 换音轨：0 = 自动，其余为 dash.audio 的 id。 */
    val onAudioQualitySelected: (Int) -> Unit = {},
)

/** 底部按钮：随机是独立开关，与循环档位互斥。 */
fun ListenVideoPlayback.toggledShuffle(): Pair<Int, Boolean> = toggleShuffle(loopMode, shuffle)

/** 底部按钮：顺序 → 单曲 → 列表 循环，切档时关掉随机。 */
fun ListenVideoPlayback.nextPlayMode(): Pair<Int, Boolean> = playModeAfterModeToggle(loopMode, shuffle)

/** 播放控件用到的像素图标资源由 app 侧提供（与全屏播放器同一套）。 */
data class ListenVideoIcons(
    val play: Int,
    val next: Int,
)

/** app 侧把封面渲染注入进来，模块不直接依赖 Coil。 */
typealias ListenCoverSlot = @Composable (String, Modifier) -> Unit