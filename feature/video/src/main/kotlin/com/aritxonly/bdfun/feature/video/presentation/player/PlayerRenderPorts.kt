package com.aritxonly.bdfun.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.aritxonly.bdfun.feature.video.presentation.listen.ListenVideoIcons
import com.aritxonly.bdfun.feature.video.presentation.listen.ListenVideoPalette

class PlayerGestureOptions(
    val verticalGesturesEnabled: () -> Boolean,
    val boostDefaultSpeed: () -> Float,
    val centerSwipeEnabled: () -> Boolean,
    val boostSwipeEnabled: () -> Boolean,
    /** 全局震动开关：关闭后长按加速等手势不再触发触感。 */
    val hapticsEnabled: () -> Boolean = { true },
)
class PlayerFullscreenVisuals(
    val appearance: FullscreenAppearance, val slots: FullscreenChromeSlots,
    val danmakuOn: Boolean, val backgroundPlay: Boolean,
)

typealias StyledDanmakuSender = suspend (String, Int, Int, Int, com.aritxonly.bdfun.core.domain.DanmakuSendAppearance) -> Boolean

/** 弹幕的发送与叠加层放在一起：两者总是同时装配，分开传只是让构造函数更长。 */
class PlayerDanmakuPorts(
    val composer: PlayerDanmakuComposePorts,
    val overlays: @Composable androidx.compose.foundation.layout.BoxScope.(Boolean) -> Unit,
)

class PlayerDanmakuComposePorts(
    val sender: () -> StyledDanmakuSender?,
    val allowVipColor: Boolean,
    val allowUpIdentity: Boolean,
)

/** app提供原平台动作/资源/共用弹幕适配；模块拥有原绘制与指针树。 */
class PlayerRenderPorts(
    val page: PlayerPageHost,
    val gestures: PlayerGestureOptions,
    val fullscreenActions: FullscreenActions,
    val picture: VideoPictureSettings,
    val surface: PlayerSurfaceSlots,
    val buffering: @Composable (Modifier) -> Unit,
    val cover: @Composable (String, Modifier) -> Unit,
    val inlineAppearance: () -> InlinePlayerAppearance,
    val fullscreenVisuals: @Composable () -> PlayerFullscreenVisuals,
    val danmaku: PlayerDanmakuPorts,
    val inlineMore: @Composable () -> Unit,
    /** 听视频整页的平台能力：配色取样、图标、本地 lrc、音量、副字幕。 */
    val listen: ListenVideoPorts = ListenVideoPorts(),
)

/**
 * 听视频需要的 app 侧能力。全部给默认实现，其他宿主不接线也能编译。
 */
class ListenVideoPorts(
    /** 封面主色取样：输入封面地址，返回 0xFFRRGGBB；取不到给 null。 */
    val samplePalette: (String) -> ListenVideoPalette = { ListenVideoPalette.Default },
    val icons: ListenVideoIcons = ListenVideoIcons(play = 0, next = 0),
    /** 打开系统文件选择器读 .lrc；用户取消返回 null。 */
    val pickLyrics: () -> String? = { null },
    /** 音量 0..1。 */
    val setVolume: (Float) -> Unit = {},
    /** 后台播放开关的当前值 / 写入。 */
    val backgroundPlay: () -> Boolean = { false },
    /** 打开本地 .lrc 选择器并解析；结果写回 ui.listenImportedLyrics。 */
    val requestLyricsPicker: () -> Unit = {},
)
