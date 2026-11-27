package com.aritxonly.bdfun

import com.aritxonly.bdfun.navigation.readRouteIntent
import com.aritxonly.bdfun.navigation.finishForMissingRoute
import com.aritxonly.bdfun.navigateToVideo
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.lifecycle.lifecycleScope
import com.aritxonly.bdfun.ui.LocalVideoPageState
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.ui.graphics.toArgb
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import kotlinx.coroutines.launch

/** 视频详情：独立 Activity；评论页留在本 Activity 内，避免播放器被销毁。 */
class VideoActivity : OneTakeExitActivity() {
    private val pageState by lazy { AppGraph.videoPageState(lifecycleScope) }
    private val VideoPageBus get() = pageState
    private val PlayerUiState get() = pageState.playerUi
    private val VideoOrientationLock get() = pageState.orientation


    // ---- 自己监听传感器决定横竖屏 ----
    private var splitSystemBars = false
    private val windowState = com.aritxonly.bdfun.ui.VideoActivityWindowStateHolder()
    private var pipMode by windowState.pipMode

    private fun syncVideoSystemBars(split: Boolean = splitSystemBars) {
        splitSystemBars = split
        val dark = when (AppGraph.settings.darkMode) {
            1 -> false
            2 -> true
            else -> resources.configuration.uiMode and android.content.res.Configuration.UI_MODE_NIGHT_MASK ==
                android.content.res.Configuration.UI_MODE_NIGHT_YES
        }
        androidx.core.view.WindowCompat.getInsetsController(window, window.decorView)
            .isAppearanceLightStatusBars = split && !dark
    }

    /**
     * 传感器转屏：期望朝向由 core:domain 的 rotationTargetForPose 判定，
     * 姿态去抖与系统方向锁缓存在 [VideoOrientationDriver] 里。
     */
    private val orientation by lazy {
        VideoOrientationDriver(
            this,
            portraitVideo = { PlayerUiState.portraitVideo },
            fullscreen = { PlayerUiState.fullscreen },
            autoRotateEnabled = { AppGraph.settings.autoRotate },
            orientationLocked = { VideoOrientationLock.locked },
        )
    }


    override fun onResume() {
        super.onResume()
        // 从后台/相关推荐返回：只有此前是自动暂停的才续播，用户手动暂停不会被恢复。
        VideoPageBus.resumeIfPending()
        com.aritxonly.bdfun.ui.BackgroundPlayback.stopKeepAlive()
        // Split panes use the page theme under system bars; stacked/fullscreen video uses light icons.
        runCatching {
            syncVideoSystemBars()
        }
        // 回前台重新读一次系统方向锁（用户可能刚在快捷开关里改过）；之后交给 ContentObserver。
        orientation.refreshRotationLock()
        if (!PlayerUiState.fullscreen && !orientation.autoLandscapePending) applyIdleOrientation()
        if (orientation.autoRotateAllowed()) orientation.start() else orientation.stop()
    }

    override fun onPause() {
        // 在窗口仍可见的 onPause 启动，避免等 onStop 后触发后台启动限制。
        val background = com.aritxonly.bdfun.ui.BackgroundPlayback
        val playback = background.state.value
        if (com.aritxonly.bdfun.core.domain.shouldKeepPlaybackAlive(
                AppGraph.settings.backgroundPlay, playback.active, playback.playback.playWhenReady)) {
            background.startKeepAlive(this)
        }
        super.onPause()
        orientation.stop()
    }

    /** 退出视频：若设置选择「画中画」，先进入系统画中画；否则正常关闭并交给唱片。 */
    private fun exitVideoPage() {
        val s = AppGraph.settings
        if (s.stopOnVideoPageExit) {
            com.aritxonly.bdfun.ui.BackgroundPlayback.cancelHandOff()
            VideoPageBus.pauseFor(this)
            finish()
            return
        }
        if (s.showDiscAfterVideo && s.exitVideoControl == 0) {
            val ok = runCatching {
                // 竖屏视频给 9:16，系统才会把窗口做成竖着的窄条
                val ratio = if (PlayerUiState.portraitVideo) {
                    android.util.Rational(9, 16)
                } else {
                    android.util.Rational(16, 9)
                }
                val params = android.app.PictureInPictureParams.Builder()
                    .setAspectRatio(ratio)
                    .build()
                enterPictureInPictureMode(params)
            }.getOrDefault(false)
            if (ok) return
        }
        finish()
    }

    override fun onPictureInPictureModeChanged(isInPictureInPictureMode: Boolean, newConfig: android.content.res.Configuration) {
        super.onPictureInPictureModeChanged(isInPictureInPictureMode, newConfig)
        pipMode = isInPictureInPictureMode
        val pages = AppGraph.pictureInPicturePages
        if (isInPictureInPictureMode) {
            pages.enter(this) { closePictureInPicturePage(removeTask = false) }
            return
        }
        pages.exit(this)
        // 离开画中画时页面已 stop：用户点了小窗的关闭（展开回全屏时页面仍是 STARTED）。
        // 关闭小窗应当停止播放并移除小窗所在的独立任务，而不是让它在后台继续响。
        if (lifecycle.currentState == androidx.lifecycle.Lifecycle.State.CREATED) {
            closePictureInPicturePage(removeTask = true)
        }
    }

    /**
     * 关闭画中画页面：先取消离开时登记的唱片交接并暂停，再结束页面。
     * 不取消交接的话，页面销毁时播放器会被交给唱片，声音继续（关闭小窗后仍在播 / 与新视频叠在一起）。
     * 被新视频页替换时只 finish：新页面可能与它同任务，不能连任务一起移除。
     */
    private fun closePictureInPicturePage(removeTask: Boolean) {
        if (isFinishing || isDestroyed) return
        com.aritxonly.bdfun.ui.BackgroundPlayback.cancelHandOff()
        VideoPageBus.pauseFor(this)
        if (removeTask) finishAndRemoveTask() else finish()
    }

    override fun onStop() {
        // 不可见不等于退出视频页；Home、锁屏与隐藏画中画由后台播放设置控制。
        if (com.aritxonly.bdfun.core.domain.shouldPauseForVideoPageExit(
                AppGraph.settings.stopOnVideoPageExit, isFinishing, isChangingConfigurations)) {
            com.aritxonly.bdfun.ui.BackgroundPlayback.cancelHandOff()
            VideoPageBus.pauseFor(this)
        }
        super.onStop()
    }

    override fun onDestroy() {
        AppGraph.pictureInPicturePages.exit(this)
        // 只有真正退出这个视频页才清相关推荐；从相关推荐进另一个视频时
        // 这个 Activity 只是 stop、不会 finish，所以返回时不会重载。
        pageState.readCache.close(if (isFinishing) VideoPageBus.currentBvid else null)
        super.onDestroy()
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) {
            // 从评论/空间等页面返回或转场结束时，补一次图标明暗同步。
            runCatching {
                syncVideoSystemBars()
            }
        }
    }

    /**
     * 退出全屏时把被钉住的窗口朝向还给系统旋转。
     *
     * 全屏期间朝向由播放器写（SENSOR_LANDSCAPE / SENSOR_PORTRAIT / LOCKED），而 [onResume]
     * 与 [onConfigurationChanged] 都只在「已经不在全屏」时才补 idle 朝向 —— 同一次姿态下
     * 退全屏不触发配置变更，没人补，于是横转竖之后再转不回横屏，只能退出视频重进。
     *
     * 用 [VideoPagePresentationState.pageFlow] 而不是 `PlayerUiState.fullscreen` 投影：
     * 投影要等 Compose 重组，而配置变更那一刻已经过去了。
     */
    private fun bindOrientationRestore() {
        lifecycleScope.launch {
            var wasFullscreen = pageState.pageFlow.value.fullscreen
            pageState.pageFlow.collect { state ->
                if (wasFullscreen && !state.fullscreen) {
                    applyIdleOrientation()
                }
                wasFullscreen = state.fullscreen
            }
        }
    }

    override fun onConfigurationChanged(newConfig: android.content.res.Configuration) {
        super.onConfigurationChanged(newConfig)
        // 刚为「转屏进全屏」请求的横屏：Compose 侧会在这一帧把 fullscreen 置上，
        // 不能在这里又用 idle 竖屏把它锁回去（否则转过去立刻弹回，看起来就是"转不动"）。
        if (!PlayerUiState.fullscreen && !orientation.autoLandscapePending) applyIdleOrientation()
        if (BuildConfig.DEBUG) {
            android.util.Log.d(
                "ROTDBG",
                "onConfigChanged orientation=${newConfig.orientation} rotation=${display?.rotation} " +
                    "size=${resources.displayMetrics.widthPixels}x${resources.displayMetrics.heightPixels} " +
                    "reqOri=$requestedOrientation",
            )
        }
    }

    /**
     * 视频页的状态栏样式：
     * - 沉浸：状态栏透明，视频才真正铺到状态栏下面
     * - 非沉浸：状态栏保持黑色（与旧版一致）
     */
    private fun applyStatusBarStyle(immersive: Boolean) {
        enableEdgeToEdge(
            // Content paints each pane to the window edges; system bars must not add a solid strip.
            statusBarStyle = androidx.activity.SystemBarStyle.dark(android.graphics.Color.TRANSPARENT),
            navigationBarStyle = androidx.activity.SystemBarStyle.auto(
                android.graphics.Color.TRANSPARENT, android.graphics.Color.TRANSPARENT,
            ) { resources ->
                when (AppGraph.settings.darkMode) {
                    1 -> false
                    2 -> true
                    else -> resources.configuration.uiMode and android.content.res.Configuration.UI_MODE_NIGHT_MASK ==
                        android.content.res.Configuration.UI_MODE_NIGHT_YES
                }
            },
        )
        window.isNavigationBarContrastEnforced = false
    }

    @OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        AppGraph.ensure(applicationContext)
        prefillWindowBackground()
        // 任何方式新开视频页，都先停掉后台播放器，避免两路声音。
        com.aritxonly.bdfun.ui.BackgroundPlayback.stopDetached()
        // 同理：旧视频页若还在画中画小窗里，新视频页打开时把它关掉。
        AppGraph.pictureInPicturePages.closeOthers(this)
        applyStatusBarStyle(AppGraph.settings.videoImmersiveStatusBar)
        askNotificationPermissionOnce()
        val isLoggedIn = AppGraph.loginStatus::isLoggedIn
        val settings = AppGraph.settings
        val route = readRouteIntent(intent) as? com.aritxonly.bdfun.core.navigation.Route.Video
        val requestedBvid = route?.bvid?.takeIf { it.isNotBlank() } ?: intent?.getStringExtra("bvid")
        val requestedAid = route?.aid ?: intent?.getLongExtra("aid", 0L) ?: 0L
        if (route?.snapshot == null && requestedBvid.isNullOrBlank() && requestedAid <= 0L) {
            finishForMissingRoute("视频标识")
            return
        }
        // 退出全屏时把被钉住的窗口朝向还给系统旋转：全屏期间朝向是播放器写的
        // （SENSOR_LANDSCAPE / SENSOR_PORTRAIT / LOCKED），而 onResume 与
        // onConfigurationChanged 都只在「已经不在全屏」时才补 idle 朝向，同一次姿态下
        // 退全屏不会触发配置变更，也就没人补。播放器侧不再插手这件事，统一在这里收尾。
        // 用 pageFlow 而不是 PlayerUiState 投影：投影要等 Compose 重组，而这一刻已经晚了。
        bindOrientationRestore()
        setContent {
            com.aritxonly.bdfun.ui.PhotoViewerRoot(animationsEnabled = settings.animationsEnabled) {
            val morphMenu = androidx.compose.runtime.remember {
                com.aritxonly.teak.common.navigation.TeakMorphMenuController()
            }
            // 相关推荐复用搜索结果的视频行，也走同一套省略号/长按形变菜单。
            val videoMorph = com.aritxonly.bdfun.ui.rememberVideoCardMorphMenuHandler(
                com.aritxonly.bdfun.ui.rememberVideoShareMorphItems(),
            )
            androidx.compose.runtime.CompositionLocalProvider(LocalVideoPageState provides pageState,
                com.aritxonly.bdfun.feature.settings.presentation.LocalSettingsBindings provides settings,
                com.aritxonly.teak.common.navigation.LocalTeakMorphMenu provides morphMenu,
                com.aritxonly.bdfun.ui.LocalVideoCardMorphMenu provides videoMorph,
                com.aritxonly.bdfun.ui.LocalRichTextDependencies provides AppGraph.richTextDependencies) {
            androidx.compose.foundation.layout.Box {
            com.aritxonly.bdfun.ui.VideoActivityContent(isLoggedIn, settings, route, requestedBvid, requestedAid, pipMode,
                com.aritxonly.bdfun.ui.VideoActivityHostActions(
                    onBack = ::exitVideoPage,
                    finish = ::finish,
                    applyStatusBarStyle = ::applyStatusBarStyle,
                    resyncSystemBars = { syncVideoSystemBars() },
                    syncSystemBars = { syncVideoSystemBars(it) },
                    openVideo = { navigateToVideo(this@VideoActivity, it) },
                    openedFromVideoPage = { intent?.getBooleanExtra("fromVideoPage", false) == true },
                    hasWindow = { window != null },
                ))
            }
            }
            }
        }
    }

    /**
     * 进详情页先按同一套色板把窗口底色填好。
     *
     * 窗口底色由 TeakTheme 在首帧里写成设置里的颜色；在那之前露出的是主题文件里写死的
     * 浅灰 windowBackground —— 深色下就是点进详情页的一下白闪。首帧前后颜色保持一致。
     */
    private fun prefillWindowBackground() {
        runCatching {
            val dark = when (AppGraph.settings.darkMode) {
                1 -> false
                2 -> true
                else -> (resources.configuration.uiMode and
                    android.content.res.Configuration.UI_MODE_NIGHT_MASK) ==
                    android.content.res.Configuration.UI_MODE_NIGHT_YES
            }
            window?.setBackgroundDrawable(
                android.graphics.drawable.ColorDrawable(
                    appMaterialColors(dark).background.toArgb(),
                ),
            )
        }
    }

    /** 通知权限只问一次：没授权就申请，并把「问过了」落到设置里，避免每次进视频都弹。 */
    private fun askNotificationPermissionOnce() {
        if (android.os.Build.VERSION.SDK_INT < 33 || AppGraph.settings.notifAsked) return
        val granted = checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS) ==
            android.content.pm.PackageManager.PERMISSION_GRANTED
        if (granted) return
        AppGraph.settings.notifAsked = true
        runCatching {
            requestPermissions(arrayOf(android.Manifest.permission.POST_NOTIFICATIONS), 1001)
        }
    }
}
