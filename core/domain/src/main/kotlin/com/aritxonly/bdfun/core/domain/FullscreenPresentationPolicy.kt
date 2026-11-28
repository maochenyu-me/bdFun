package com.aritxonly.bdfun.core.domain

/**
 * 朝向请求迟迟不生效时的安全阀：多窗口、厂商 ROM 忽略朝向请求的情况确实存在，
 * 超过这个时间就照常按全屏绘制，避免「点了展开却一直没反应」。
 */
const val FULLSCREEN_PRESENTATION_GRACE_MS = 800L

/**
 * 页面该不该「按全屏绘制」。
 *
 * 横屏视频从竖屏点展开时，点击只能改状态，系统转屏要等几百毫秒。这段时间窗口还是竖的，
 * 如果立刻按全屏画，就会闪出一帧「竖屏窗口、视频缩在中间、页面骨架已隐藏」。
 * 所以呈现值跟窗口朝向走，等窗口真的横过来再铺满。
 *
 * 与 [VideoRotationTarget] 同一套「紧凑手机」口径：只有紧凑手机存在「请求朝向 →
 * 等系统转屏」这件事。平板是分栏布局，竖屏视频不涉及转屏，多窗口下朝向请求本来就无效，
 * 这三种情况都按意图立即绘制，不做等待。
 *
 * @param intent 用户点了展开（点击瞬间为真）
 * @param windowLandscape 当前窗口是否已经是横的
 * @param portraitVideo 竖屏视频：全屏仍是竖屏窗口，没有转屏可等
 * @param compactPhone 紧凑手机（smallestWidthDp 在 1..599）
 * @param multiWindow 多窗口/分屏：系统会忽略朝向请求
 * @param rotationGraceElapsed 已超过 [FULLSCREEN_PRESENTATION_GRACE_MS]
 */
fun videoFullscreenPresented(
    intent: Boolean,
    windowLandscape: Boolean,
    portraitVideo: Boolean,
    compactPhone: Boolean,
    multiWindow: Boolean,
    rotationGraceElapsed: Boolean,
): Boolean {
    // 退出全屏不等待：窗口纵向后由 Activity 把朝向还给系统，页面立刻回到半屏，
    // 与改动前完全一致（反向也去等反而会让「返回」看起来没反应）。
    if (!intent) return false
    if (portraitVideo || !compactPhone || multiWindow) return true
    return windowLandscape || rotationGraceElapsed
}