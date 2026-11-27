package com.aritxonly.bdfun.playback

import android.content.Context
import android.graphics.drawable.BitmapDrawable
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import coil.request.ImageRequest
import coil.request.SuccessResult
import com.aritxonly.bdfun.feature.video.presentation.listen.ListenVideoPalette
import com.aritxonly.bdfun.feature.video.presentation.listen.dominantCoverColor
import com.aritxonly.bdfun.feature.video.presentation.listen.listenPaletteFromSeed
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * 听视频背景配色：把封面缩到 16×16 再取样，够用且不用整张大图。
 * 取样失败（无网、图挂了）就退回默认深色，不阻塞界面。
 */
@Composable
internal fun rememberCoverPalette(cover: String): ListenVideoPalette {
    val context = LocalContext.current
    var palette by remember(cover) { mutableStateOf(ListenVideoPalette.Default) }
    LaunchedEffect(cover) {
        if (cover.isBlank()) {
            palette = ListenVideoPalette.Default
            return@LaunchedEffect
        }
        palette = withContext(Dispatchers.IO) { loadPalette(context, cover) }
    }
    return palette
}

private suspend fun loadPalette(context: Context, cover: String): ListenVideoPalette {
    val request = ImageRequest.Builder(context)
        .data(cover)
        .size(16)
        .allowHardware(false)
        .build()
    val result = runCatching { coil.Coil.imageLoader(context).execute(request) }.getOrNull()
    val bitmap = ((result as? SuccessResult)?.drawable as? BitmapDrawable)?.bitmap ?: return ListenVideoPalette.Default
    val pixels = IntArray(bitmap.width * bitmap.height)
    bitmap.getPixels(pixels, 0, bitmap.width, 0, 0, bitmap.width, bitmap.height)
    // 不手动 recycle：这张位图来自 Coil 的缓存池，回收会影响封面本身的显示。
    return listenPaletteFromSeed(dominantCoverColor(pixels))
}