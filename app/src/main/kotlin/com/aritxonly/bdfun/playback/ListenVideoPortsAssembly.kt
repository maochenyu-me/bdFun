package com.aritxonly.bdfun.playback

import android.content.Context
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.platform.LocalContext
import com.aritxonly.bdfun.feature.video.presentation.VideoPlaybackPlatform
import com.aritxonly.bdfun.feature.video.presentation.listen.ListenVideoIcons
import com.aritxonly.bdfun.feature.video.presentation.listen.parseLrc
import com.aritxonly.bdfun.infra.storage.AndroidListenLyricsStore
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
    cid: Long,
    ui: VideoPlayerUiStateHolder,
    backgroundPlay: () -> Boolean,
): ListenVideoPorts {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val palette = rememberCoverPalette(cover)
    val store = remember { AndroidListenLyricsStore(context) }
    // 选择器开着的时候用户可能换集，落盘要按当时那一集算，不能按发起时的。
    val currentCid = rememberUpdatedState(cid)
    // 本地 lrc：选完先落盘（下次进来能恢复），再把解析结果投递给页面。
    val lrcPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            val text = withContext(Dispatchers.IO) { readText(context, uri) }
            if (text != null) {
                val parsed = parseLrc(text)
                if (parsed.lines.isNotEmpty()) {
                    store.save(currentCid.value, text)
                    ui.listenImportedLyrics.value = parsed
                }
            }
        }
    }
    return ListenVideoPorts(
        samplePalette = { palette },
        icons = ListenVideoIcons(play = TeakIcon.PlayerPlay, next = TeakIcon.PlayerNext),
        requestLyricsPicker = { runCatching { lrcPicker.launch(arrayOf("*/*")) } },
        setVolume = { playback.setVolume(it) },
        backgroundPlay = backgroundPlay,
        lyricsStore = store,
    )
}

private fun readText(context: Context, uri: android.net.Uri): String? = runCatching {
    context.contentResolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() }
}.getOrNull()