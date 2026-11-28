package com.aritxonly.bdfun.ui

import com.aritxonly.bdfun.core.domain.PLAYBACK_REFRESH_IDLE_GATE_MS
import com.aritxonly.bdfun.core.domain.targetPlaybackRefreshRate
import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.os.Build
import android.os.SystemClock
import android.view.Window
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.lang.ref.WeakReference

/**
 * 智能刷新率：
 * - 无视频播放且静止：锁到 1 帧（省电），手指一碰立刻恢复系统满帧；
 * - 有视频播放且静止：锁 60 帧；
 * - 全屏播放且静止：同样 60 帧。
 * 通过 View.setRequestedFrameRate 生效（API 35+），同时清掉 preferredRefreshRate 让系统自己选模式。
 *
 * 只在状态变化和触控结束这两个时刻各下发一次，不再常驻轮询：屏幕静止时没有任何定时唤醒，
 * 也就没有主线程被反复拉起来重算帧率的开销。
 */
private class WindowRefreshStateHolder(private val scope: CoroutineScope) {

    private var lastTouchAt = SystemClock.uptimeMillis()
    private var applied = Float.NaN
    private var windowRef: WeakReference<Window>? = null
    private var enabled = false
    private var playing = false
    private var fullscreen = false
    private var boostApplied: Boolean? = null

    /** 触控静止门槛到点后的补发任务；同一时刻最多一个。 */
    private var reapply: Job? = null

    fun attach(window: Window?) {
        windowRef = window?.let { WeakReference(it) }
        applied = Float.NaN
        boostApplied = null
        // 先按当前播放态决定要不要开"触摸即满帧"，不再无条件打开：
        // 播放中打开会盖掉我们请求的 60Hz。
        syncTouchBoost()
    }

    fun detach() {
        reapply?.cancel()
        reapply = null
        runCatching {
            if (Build.VERSION.SDK_INT >= 35) {
                windowRef?.get()?.decorView?.setRequestedFrameRate(0f)
            }
        }
        windowRef = null
        applied = Float.NaN
        boostApplied = null
    }

    /** 手指按下：立即恢复系统满帧，并重新起算静止门槛。 */
    fun touch() {
        lastTouchAt = SystemClock.uptimeMillis()
        apply()
        armGate()
    }

    fun update(enabled: Boolean, playing: Boolean, fullscreen: Boolean) {
        this.enabled = enabled
        this.playing = playing
        this.fullscreen = fullscreen
        syncTouchBoost()
        apply()
        armGate()
    }

    /**
     * 播放中关掉"触摸即满帧"：系统的触摸 boost 会盖过我们请求的 60Hz，
     * 而视频本身只有 60fps，拖动进度条时多合成的那一倍画面既看不出差别又要多耗电。
     * 非播放态仍然保留 boost，滑动跟手感不变。
     */
    private fun syncTouchBoost() {
        if (Build.VERSION.SDK_INT < 35) return
        // 关掉智能刷新率时不干预系统默认行为；只有"开启 + 正在播放"才需要压掉 boost。
        val boost = !enabled || !playing
        if (boost == boostApplied) return
        val window = windowRef?.get() ?: return
        boostApplied = boost
        runCatching { window.setFrameRateBoostOnTouchEnabled(boost) }
    }

    private fun apply() {
        if (Build.VERSION.SDK_INT < 35) return
        val window = windowRef?.get() ?: return
        val target = targetPlaybackRefreshRate(
            enabled, SystemClock.uptimeMillis() - lastTouchAt, playing, fullscreen,
        )
        if (target == applied) return
        applied = target
        runCatching {
            // API 35+：给窗口一个期望帧率（0 = 取消限制，恢复系统默认）
            window.decorView.setRequestedFrameRate(target)
        }
    }

    /**
     * 还在触控静止门槛内时，门槛到点补发一次；拖拽没松手就继续等下一个门槛。
     * 门槛已过则什么都不排，稳定态下没有任何常驻任务。多留一帧余量，保证补发时 uptime 已越过门槛。
     */
    private fun armGate() {
        // 开关关闭时始终交回系统，没有"门槛到点后补发"这回事，别排无用任务。
        if (Build.VERSION.SDK_INT < 35 || !enabled) return
        if (reapply != null) return
        val remaining = PLAYBACK_REFRESH_IDLE_GATE_MS - (SystemClock.uptimeMillis() - lastTouchAt)
        if (remaining <= 0L) return
        reapply = scope.launch {
            delay(remaining + 16L)
            reapply = null
            apply()
            armGate()
        }
    }
}

private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}

/** 挂在页面根节点：收集触摸 + 状态变化时调整刷新率。 */
@Composable
fun Modifier.smartRefresh(enabled: Boolean): Modifier {
    val context = LocalContext.current
    val activity = remember(context) { context.findActivity() }
    val scope = rememberCoroutineScope()
    val controller = remember(activity, scope) { WindowRefreshStateHolder(scope) }
    val playing = BackgroundPlayback.playing
    val fullscreen = BackgroundPlayback.fullscreen
    DisposableEffect(activity) {
        controller.attach(activity?.window)
        onDispose { controller.detach() }
    }
    LaunchedEffect(enabled, playing, fullscreen) {
        // 播放/全屏/开关变化时下发一次；其余时间靠触控结束时的补发，不再每 400ms 轮询。
        controller.update(enabled, playing, fullscreen)
    }
    return this.pointerInput(Unit) {
        awaitPointerEventScope {
            while (true) {
                val event = awaitPointerEvent()
                if (event.changes.any { it.pressed }) controller.touch()
            }
        }
    }
}