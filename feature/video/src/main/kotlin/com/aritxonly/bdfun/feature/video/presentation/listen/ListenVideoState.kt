package com.aritxonly.bdfun.feature.video.presentation.listen

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

/** 歌词行的呈现方式；由「更多 → 歌词样式」切换。 */
enum class ListenLyricsStyle(val label: String, val detail: String) {
    /** KTV 逐字扫亮：当前行按行内进度从左往右点亮，扫描边带柔和过渡。 */
    KARAOKE("KTV 逐字", "跟着进度逐字点亮"),

    /** 普通：整行等亮，只有当前行高亮，不做扫描。 */
    PLAIN("普通", "整行同时高亮"),
}

/**
 * 听视频页面的本地界面状态。播放进度、播放态这类由播放器持有的值不进这里，
 * 免得同一份状态存在两处、谁赢谁输说不清。
 */
class ListenVideoUiStateHolder {

    /** 当前页，0 封面 / 1 歌词。字幕被关掉时要收敛回 0。 */
    var pageIndex by mutableIntStateOf(0)

    /** 歌词整体偏移（毫秒），用户手动校正用。 */
    var lyricsOffsetMs by mutableLongStateOf(0L)

    /** 音量 0..1；播放器默认就是 1，界面初始位置必须跟它一致。 */
    var volume by mutableFloatStateOf(1f)

    /** 更多 / 队列 / 音质面板。 */
    var moreSheet by mutableStateOf(false)
    var queueSheet by mutableStateOf(false)
    var audioQualitySheet by mutableStateOf(false)

    /** A-B 循环状态。 */
    var abLoop by mutableStateOf(AbLoop())

    /** 歌词行的呈现方式，默认用 KTV 逐字。 */
    var lyricsStyle by mutableStateOf(ListenLyricsStyle.KARAOKE)

    /** 顶栏「翻译」开关：副字幕是否叠加显示。 */
    var translationOn by mutableStateOf(false)

    /** 本地导入的歌词；null 表示没有，仍走 B 站字幕。 */
    var importedLyrics by mutableStateOf<Lyrics?>(null)

    /** 一次性提示（导入成功、歌词缺失等）。 */
    var message by mutableStateOf<String?>(null)

    /** 沉浸：内容区控件淡出，只剩歌词与细进度线。 */
    var chromeHidden by mutableStateOf(false)
}