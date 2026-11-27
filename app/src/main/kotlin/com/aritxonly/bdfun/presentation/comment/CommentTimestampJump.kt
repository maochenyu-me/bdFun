package com.aritxonly.bdfun.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import com.aritxonly.bdfun.feature.settings.presentation.LocalSettingsBindings

/**
 * 评论正文里「1:00」这类时间点的跳转入口。
 *
 * 只有视频页装配；动态详情这类没有播放器的评论区保持 null，正文按普通文字渲染。
 */
internal val LocalCommentTimestampJump = staticCompositionLocalOf<((Long) -> Unit)?> { null }

/**
 * 给整块评论区提供时间点跳转。
 *
 * 开关关闭时仍然提供 null，让正文退回普通文字；[onJump] 由各宿主决定
 * 跳转之后要不要顺手收起自己那层评论面板。
 */
@Composable
internal fun CommentTimestampJumpHost(onJump: (Long) -> Unit, content: @Composable () -> Unit) {
    val enabled = LocalSettingsBindings.current.commentTimestampJump
    CompositionLocalProvider(
        LocalCommentTimestampJump provides if (enabled) onJump else null,
        content = content,
    )
}