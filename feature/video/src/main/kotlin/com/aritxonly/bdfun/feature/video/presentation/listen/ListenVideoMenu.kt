package com.aritxonly.bdfun.feature.video.presentation.listen

import com.aritxonly.bdfun.core.domain.lyricsOffsetLabel
import com.aritxonly.bdfun.core.domain.playModeValueLabel

/**
 * 听视频「更多」面板的条目。只登记真正可用的项：
 * 项目没有对应基础设施的功能（在线歌词、纯音频缓存、歌单导入、设备接力、封面律动）
 * 不放进枚举，菜单里就不会出现，避免点了没反应。
 */
enum class ListenMenuEntry(val title: String) {
    VIDEO_MODE("返回视频"),
    COLLECTION("选集 / 合集"),
    AUDIO_QUALITY("选择音质"),
    SPEED("播放倍速"),
    PLAY_MODE("播放模式"),
    SLEEP_TIMER("定时关闭"),
    AB_LOOP("A-B 区间循环"),
    TRANSLATION("双语翻译"),
    FAVORITE("收藏"),
    SHARE("分享"),
    PIP("画中画"),
    ORIENTATION("切换横竖屏"),
    SUBTITLE_SETTINGS("字幕 / 歌词设置"),
    LYRICS_SEARCH("搜索歌词"),
    LYRICS_STYLE("歌词样式"),
    LYRICS_CALIBRATE("歌词校准"),
    BACKGROUND_PLAY("后台播放"),
    ;

    companion object {
        /** 播放类排在前面，和现有「更多」面板的信息层级一致。 */
        val Playback = listOf(AUDIO_QUALITY, SPEED, PLAY_MODE, SLEEP_TIMER, AB_LOOP)
        val Navigation = listOf(VIDEO_MODE, COLLECTION, ORIENTATION, PIP)
        val Content = listOf(FAVORITE, SHARE, BACKGROUND_PLAY)
        val Lyrics = listOf(LYRICS_SEARCH, SUBTITLE_SETTINGS, LYRICS_STYLE, LYRICS_CALIBRATE)
    }
}

/** 可用性输入：某项依赖的能力是否具备。 */
data class ListenMenuCapabilities(
    val hasPages: Boolean = false,
    val hasLyrics: Boolean = false,
    val canFavorite: Boolean = false,
    val canShare: Boolean = false,
    val canPip: Boolean = false,
    val canChangeOrientation: Boolean = false,
    /** 有多档 DASH 音轨可选；只有一条音轨时不显示「选择音质」。 */
    val hasAudioQualities: Boolean = false,
    val loggedIn: Boolean = false,
)

/**
 * 只返回当前真的能用的项。依赖项缺失就整条不出现——
 * 置灰或点了弹提示都会让菜单显得像半成品。
 */
fun visibleListenMenuEntries(capabilities: ListenMenuCapabilities): List<ListenMenuEntry> {
    val c = capabilities
    return buildList {
        add(ListenMenuEntry.VIDEO_MODE)
        if (c.hasPages) add(ListenMenuEntry.COLLECTION)
        if (c.canChangeOrientation) add(ListenMenuEntry.ORIENTATION)
        if (c.canPip) add(ListenMenuEntry.PIP)
        if (c.hasAudioQualities) add(ListenMenuEntry.AUDIO_QUALITY)
        add(ListenMenuEntry.SPEED)
        add(ListenMenuEntry.PLAY_MODE)
        add(ListenMenuEntry.SLEEP_TIMER)
        add(ListenMenuEntry.AB_LOOP)
        if (c.hasLyrics) add(ListenMenuEntry.TRANSLATION)
        if (c.canFavorite && c.loggedIn) add(ListenMenuEntry.FAVORITE)
        if (c.canShare) add(ListenMenuEntry.SHARE)
        add(ListenMenuEntry.BACKGROUND_PLAY)
        if (c.hasLyrics) {
            add(ListenMenuEntry.LYRICS_STYLE)
            add(ListenMenuEntry.LYRICS_CALIBRATE)
            add(ListenMenuEntry.LYRICS_SEARCH)
            add(ListenMenuEntry.SUBTITLE_SETTINGS)
        }
    }
}

/** 菜单里的当前值：没值就不显示右侧小字，避免出现「未知」「--」。 */
fun listenMenuValue(entry: ListenMenuEntry, state: ListenMenuState): String? = when (entry) {
    ListenMenuEntry.SPEED -> if (state.speed == 1f) "1.0x" else trimSpeedValue(state.speed)
    ListenMenuEntry.PLAY_MODE -> playModeValueLabel(state.loopMode, state.shuffle)
    ListenMenuEntry.SLEEP_TIMER -> state.sleepTimerLabel.takeIf { it.isNotBlank() }
    ListenMenuEntry.AB_LOOP -> state.abLoopLabel.takeIf { it.isNotBlank() }
    ListenMenuEntry.AUDIO_QUALITY -> state.audioLabel.takeIf { it.isNotBlank() }
    ListenMenuEntry.FAVORITE -> if (state.favorited) "已收藏" else null
    ListenMenuEntry.TRANSLATION -> if (state.translationOn) "已开启" else "未开启"
    ListenMenuEntry.BACKGROUND_PLAY -> if (state.backgroundPlay) "已开启" else "未开启"
    ListenMenuEntry.ORIENTATION -> state.orientationLabel.takeIf { it.isNotBlank() }
    ListenMenuEntry.LYRICS_STYLE -> state.lyricsStyle.label
    ListenMenuEntry.LYRICS_CALIBRATE ->
        if (state.lyricsOffsetMs == 0L) null else lyricsOffsetLabel(state.lyricsOffsetMs)
    else -> null
}

data class ListenMenuState(
    val speed: Float = 1f,
    val translationOn: Boolean = false,
    val loopMode: Int = 0,
    /** 随机开着时 loopMode 被收回顺序，两者要一起看才说得清当前模式。 */
    val shuffle: Boolean = false,
    val sleepTimerLabel: String = "",
    val abLoopLabel: String = "",
    /** 当前音轨档位；听视频里选的是 dash.audio，不是画质。 */
    val audioLabel: String = "",
    val favorited: Boolean = false,
    val backgroundPlay: Boolean = false,
    val orientationLabel: String = "横屏",
    val lyricsStyle: ListenLyricsStyle = ListenLyricsStyle.KARAOKE,
    /** 歌词校准偏移；非 0 时菜单右侧显示，让用户知道现在不是原时间轴。 */
    val lyricsOffsetMs: Long = 0L,
)

/** 与视频播放器的 loopMode 取值一致：0 不循环、1 单集、2 列表。 */
fun trimSpeedValue(speed: Float): String {
    val text = "%.2f".format(speed)
    return text.trimEnd('0').trimEnd('.') + "x"
}

/**
 * A-B 循环的纯状态机。用毫秒区间表达，只允许 A < B；
 * 重复设置 A 视为重新开始，避免出现 A > B 的死区。
 */
data class AbLoop(val startMs: Long = -1L, val endMs: Long = -1L) {
    val hasStart: Boolean get() = startMs >= 0L
    val active: Boolean get() = hasStart && endMs > startMs

    fun mark(positionMs: Long): AbLoop = when {
        !hasStart -> copy(startMs = positionMs, endMs = -1L)
        endMs < 0L -> if (positionMs > startMs) copy(endMs = positionMs) else this
        else -> AbLoop()
    }

    /** 播放到 B 点该跳回 A；不在区间内返回 null。 */
    fun seekBackTarget(positionMs: Long): Long? =
        if (active && positionMs >= endMs) startMs else null

    fun label(): String = when {
        startMs < 0L -> ""
        endMs < 0L -> "${formatClock(startMs)} → ?"
        else -> "${formatClock(startMs)} → ${formatClock(endMs)}"
    }
}

private fun formatClock(ms: Long): String {
    val total = (ms / 1000).coerceAtLeast(0L)
    val m = total / 60
    val s = total % 60
    return "%d:%02d".format(m, s)
}