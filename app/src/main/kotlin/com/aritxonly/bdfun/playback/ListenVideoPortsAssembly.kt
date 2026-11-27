package com.aritxonly.bdfun.playback

import android.content.Context
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.platform.LocalContext
import com.aritxonly.bdfun.feature.video.presentation.VideoPlaybackPlatform
import com.aritxonly.bdfun.feature.video.presentation.listen.ListenVideoIcons
import com.aritxonly.bdfun.feature.video.presentation.listen.parseLrc
import com.aritxonly.bdfun.ui.ListenVideoPorts
import com.aritxonly.bdfun.ui.TeakIcon
import com.aritxonly.bdfun.ui.VideoPlayerUiStateHolder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * 听视频的 app 侧接线：封面取色、图标、本地 lrc 选择、音量与后台播放开关。
 * 单独拆出来是为了让渲染装配本身保持在尺寸审计线以内。
 */
@Composable
internal fun rememberListenVideoPorts(
    playback: VideoPlaybackPlatform,
    cover: String,
    ui: VideoPlayerUiStateHolder,
    backgroundPlay: () -> Boolean,
): ListenVideoPorts {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val palette = rememberCoverPalette(cover)
    // 本地 lrc：选完立刻解析进播放器状态，不落盘（换视频即失效）。
    val lrcPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            val text = withContext(Dispatchers.IO) { readText(context, uri) }
            val parsed = text?.let(::parseLrc)
            if (parsed != null && parsed.lines.isNotEmpty()) ui.listenImportedLyrics.value = parsed
        }
    }
    return ListenVideoPorts(
        samplePalette = { palette },
        icons = ListenVideoIcons(play = TeakIcon.PlayerPlay, next = TeakIcon.PlayerNext),
        requestLyricsPicker = { runCatching { lrcPicker.launch(arrayOf("*/*")) } },
        setVolume = { playback.setVolume(it) },
        backgroundPlay = backgroundPlay,
    )
}

private fun readText(context: Context, uri: android.net.Uri): String? = runCatching {
    context.contentResolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() }
}.getOrNull()