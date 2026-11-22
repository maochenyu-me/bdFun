package com.aritxonly.bdfun.ui

import android.view.TextureView
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import com.aritxonly.bdfun.AppGraph
import com.aritxonly.bdfun.core.model.video.PlayInfo

@Composable
internal fun InlineVideoContent(
    bvid: String, loaded: PlayInfo, muted: Boolean, modifier: Modifier,
    /** 首页轮播叠在封面上，用透明底色避免遮挡封面。 */
    background: Color = Color.Black,
) {
    val context = LocalContext.current
    val runtime = remember(bvid) { AppGraph.inlinePlayback(context, bvid, muted) }
    val textureView = remember(bvid) {
        TextureView(context).apply {
            // 首帧出来之前透出封面，避免闪黑。
            isOpaque = false
        }
    }
    val videoAspect by runtime.controller.aspectRatio.collectAsState()
    InlineVideoEffects(loaded, muted, runtime, textureView)
    InlineVideoSurface(textureView, videoAspect, modifier, background)
}
