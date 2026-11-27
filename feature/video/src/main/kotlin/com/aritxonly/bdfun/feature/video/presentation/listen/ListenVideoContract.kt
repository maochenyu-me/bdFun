package com.aritxonly.bdfun.feature.video.presentation.listen

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
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
    val qualityLabel: String = "",
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

    fun menuState(ui: ListenVideoUiStateHolder, playback: ListenVideoPlayback): ListenMenuState =
        ListenMenuState(
            speed = playback.speed,
            loopMode = playback.loopMode,
            sleepTimerLabel = playback.sleepTimerLabel,
            abLoopLabel = ui.abLoop.label(),
            translationOn = ui.translationOn,
            qualityLabel = qualityLabel,
            favorited = favorited,
            backgroundPlay = playback.backgroundPlay,
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
    val onSeekRelative: (Long) -> Unit,
    val onSelectPage: (Long) -> Unit,
    val onMenuEntry: (ListenMenuEntry) -> Unit = {},
    val onImportLyrics: () -> Unit = {},
    val onVolumeChange: (Float) -> Unit = {},
    val onTranslationToggle: (Boolean) -> Unit = {},
    val onSleepAtChanged: (Long) -> Unit = {},
    /** 随机播放开关；与播放模式互斥。 */
    val onShuffleToggle: () -> Unit = {},
    /** 顺序 → 单曲 → 列表 循环切换。 */
    val onPlayModeToggle: () -> Unit = {},
)

/** 播放控件用到的像素图标资源由 app 侧提供（与全屏播放器同一套）。 */
data class ListenVideoIcons(
    val play: Int,
    val next: Int,
)

/** app 侧把封面渲染注入进来，模块不直接依赖 Coil。 */
typealias ListenCoverSlot = @Composable (String, Modifier) -> Unit