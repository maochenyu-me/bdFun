package com.aritxonly.bdfun.ui

import android.app.Activity
import android.view.WindowManager
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.platform.LocalContext
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import com.aritxonly.bdfun.core.model.video.Quality

@Composable
fun BindPlayerSystemBars(
    fullscreen: Boolean,
    immersiveStatusBar: Boolean,
    portraitVideo: Boolean,
) {
    val activity = LocalContext.current as? Activity
    DisposableEffect(fullscreen, immersiveStatusBar, portraitVideo) {
        val window = activity?.window
        val controller = window?.let { WindowCompat.getInsetsController(it, it.decorView) }
        val previousCutoutMode = window?.attributes?.layoutInDisplayCutoutMode
        if (fullscreen) {
            // 竖屏全屏要顶到挖孔区，否则状态栏那一带会留一条纯黑边。
            // 朝向本身由 BindPlayerOrientation 统一决定，这里不再抢着写。
            if (portraitVideo && window != null) {
                runCatching {
                    window.attributes = window.attributes.apply {
                        layoutInDisplayCutoutMode =
                            WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
                    }
                }
            }
            controller?.apply {
                systemBarsBehavior =
                    WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
                hide(WindowInsetsCompat.Type.systemBars())
            }
        } else {
            if (immersiveStatusBar) {
                // 沉浸模式：半屏也不显示状态栏时间等信息，把状态栏一起藏掉；
                // 导航栏保留，手势条不受影响。
                controller?.apply {
                    systemBarsBehavior =
                        WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
                    show(WindowInsetsCompat.Type.navigationBars())
                    hide(WindowInsetsCompat.Type.statusBars())
                }
            } else {
                controller?.show(WindowInsetsCompat.Type.systemBars())
            }
        }
        onDispose {
            controller?.show(WindowInsetsCompat.Type.systemBars())
            if (portraitVideo && window != null) {
                previousCutoutMode?.let { mode ->
                    runCatching {
                        window.attributes = window.attributes.apply {
                            layoutInDisplayCutoutMode = mode
                        }
                    }
                }
            }
            // 退出全屏把朝向还给系统旋转这件事只在 VideoActivity 一处做（退出全屏的
            // 所有路径都经过那里），这里不再插手：两处都写会互相覆盖，也容易出现
            // 「谁的 key 先失效」的竞态。此处只负责系统栏与挖孔区。
        }
    }

}

@Composable
fun BindPlayerHdrWindow(quality: Quality?, fullscreen: Boolean) {
    val activity = LocalContext.current as? Activity
    // HDR / 杜比视界：把屏幕亮度拉到最高，请求 HDR 颜色模式与 10bit 输出。
    // 部分机型不会因为 Surface 是 HDR 就自动提亮，这里显式做一次。
    val isHdrQuality = quality?.let { q ->
        q.id == 125 || q.id == 126 ||
            q.codec.contains("hdr", ignoreCase = true) ||
            q.codec.contains("dovi", ignoreCase = true) ||
            q.codec.contains("dvh", ignoreCase = true) ||
            q.codec.contains("dolby", ignoreCase = true)
    } == true
    DisposableEffect(isHdrQuality, fullscreen, activity) {
        val window = activity?.window
        val hdrActive = isHdrQuality
        val previousBrightness = window?.attributes?.screenBrightness ?: -1f
        val previousColorMode = window?.attributes?.colorMode ?: 0
        if (hdrActive && window != null) {
            runCatching {
                window.attributes = window.attributes.apply {
                    screenBrightness =
                        android.view.WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_FULL
                    colorMode = android.content.pm.ActivityInfo.COLOR_MODE_HDR
                }
                window.setFormat(android.graphics.PixelFormat.RGBA_1010102)
            }
        }
        onDispose {
            if (hdrActive && window != null) {
                // 用户如果自己调过亮度，就别强行拽回旧值。
                if (window.attributes.screenBrightness ==
                    android.view.WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_FULL
                ) {
                    window.attributes = window.attributes.apply {
                        screenBrightness = previousBrightness
                        colorMode = previousColorMode
                    }
                }
            }
        }
    }

}
