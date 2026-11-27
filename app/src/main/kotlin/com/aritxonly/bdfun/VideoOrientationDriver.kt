package com.aritxonly.bdfun

import android.app.Activity
import android.content.pm.ActivityInfo
import android.provider.Settings
import com.aritxonly.bdfun.core.domain.VideoRotationTarget
import com.aritxonly.bdfun.core.domain.rotationTargetForPose

/**
 * 视频页的传感器转屏驱动：监听设备姿态，姿态稳定后请求横/竖屏朝向。
 *
 * 期望朝向由 [rotationTargetForPose] 统一判定（全屏、小屏、自动旋转、方向锁），
 * 这里只负责三件事：姿态去抖、系统方向锁的缓存、以及请求 Activity 转屏。
 */
internal class VideoOrientationDriver(
    private val activity: Activity,
    /** 竖屏视频的朝向由播放器自己定（SENSOR_PORTRAIT），传感器转屏要让路。 */
    private val portraitVideo: () -> Boolean,
    private val fullscreen: () -> Boolean,
    private val autoRotateEnabled: () -> Boolean,
    private val orientationLocked: () -> Boolean,
) {
    private var listener: android.view.OrientationEventListener? = null
    private var pendingLandscape: Boolean? = null
    private var pendingSince = 0L

    /** 上一次真正生效的设备姿态（0 竖屏 / 1 横屏）：只有姿态真的翻转才驱动转屏。 */
    private var lastQuadrant = -1

    /**
     * 系统方向锁的缓存值。
     *
     * 传感器回调一秒十几次，每次都 `Settings.System.getInt` 是一次跨进程查询，
     * 这个开销**不能忽略**；所以改成 ContentObserver：值变化时才重新读，
     * 回调里只做一次布尔读取。
     */
    private var rotationLocked = false
    private var lockObserver: android.database.ContentObserver? = null

    /** 是不是我们刚为了「转屏进全屏」写下的横屏朝向：Compose 置上 fullscreen 之前不要被 idle 锁回去。 */
    val autoLandscapePending: Boolean
        get() = !fullscreen() &&
            activity.requestedOrientation == ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE

    fun autoRotateAllowed(): Boolean = autoRotateEnabled() && !rotationLocked

    /** 回前台重新读一次系统方向锁（用户可能刚在快捷开关里改过）；之后交给 ContentObserver。 */
    fun refreshRotationLock() {
        rotationLocked = readSystemRotationLocked()
    }

    fun start() {
        if (listener != null) return
        startLockObserver()
        lastQuadrant = if (
            activity.resources.configuration.orientation ==
            android.content.res.Configuration.ORIENTATION_LANDSCAPE
        ) 1 else 0
        listener = object : android.view.OrientationEventListener(
            activity,
            android.hardware.SensorManager.SENSOR_DELAY_UI,
        ) {
            override fun onOrientationChanged(orientation: Int) {
                if (orientation == ORIENTATION_UNKNOWN) return
                if (portraitVideo()) return
                onPose(orientation)
            }
        }.also { it.enable() }
        if (BuildConfig.DEBUG) {
            android.util.Log.d("ROTDBG", "orientation listener enabled")
        }
    }

    fun stop() {
        listener?.disable()
        listener = null
        pendingLandscape = null
        stopLockObserver()
    }

    private fun onPose(orientation: Int) {
        // 判定：横屏要转到离水平 12° 以内（比上一版 18° 更严格）
        val landscape = orientation in 78..102 || orientation in 258..282
        val portrait = orientation in 0..24 || orientation in 156..204 || orientation in 336..359
        val quadrant = when {
            landscape -> 1
            portrait -> 0
            else -> return
        }
        // 姿态没变就不动：这样「竖屏时点全屏按钮」不会被传感器立刻扳回竖屏，
        // 也避免每次回调都去读设置 / 方向锁。
        if (quadrant == lastQuadrant) {
            pendingLandscape = null
            return
        }
        // 每次真正翻姿态都重新读设置：用户可能中途打开 / 关闭了自动旋转或方向锁定。
        val wanted = rotationTargetForPose(
            landscape = quadrant == 1,
            fullscreen = fullscreen(),
            smallestWidthDp = activity.resources.configuration.smallestScreenWidthDp,
            autoRotate = autoRotateAllowed(),
            orientationLocked = orientationLocked(),
        )
        if (wanted == null) {
            pendingLandscape = null
            return
        }
        val target = quadrant == 1
        if (pendingLandscape != target) {
            pendingLandscape = target
            pendingSince = System.currentTimeMillis()
            return
        }
        // 姿态稳定保持 220ms 就转屏：再长会让「手机转过去 App 半天不跟」
        if (System.currentTimeMillis() - pendingSince < 220) return
        lastQuadrant = quadrant
        val requested = when (wanted) {
            VideoRotationTarget.Landscape -> ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
            VideoRotationTarget.Portrait -> ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
        }
        if (activity.requestedOrientation != requested) {
            if (BuildConfig.DEBUG) {
                android.util.Log.d(
                    "ROTDBG",
                    "apply requestedOrientation=$requested (angle=$orientation)",
                )
            }
            activity.requestedOrientation = requested
        }
    }

    private fun readSystemRotationLocked(): Boolean = runCatching {
        Settings.System.getInt(activity.contentResolver, Settings.System.ACCELEROMETER_ROTATION, 1) == 0
    }.getOrDefault(false)

    private fun startLockObserver() {
        if (lockObserver != null) return
        val observer = object : android.database.ContentObserver(
            android.os.Handler(android.os.Looper.getMainLooper()),
        ) {
            override fun onChange(selfChange: Boolean) {
                rotationLocked = readSystemRotationLocked()
            }
        }
        lockObserver = observer
        runCatching {
            activity.contentResolver.registerContentObserver(
                Settings.System.getUriFor(Settings.System.ACCELEROMETER_ROTATION),
                false,
                observer,
            )
        }
    }

    private fun stopLockObserver() {
        lockObserver?.let { runCatching { activity.contentResolver.unregisterContentObserver(it) } }
        lockObserver = null
    }
}
