package com.aritxonly.bdfun.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import com.aritxonly.bdfun.core.application.PlaybackController
import com.aritxonly.bdfun.core.domain.CommentSeekResult
import com.aritxonly.bdfun.core.domain.commentSeekTarget

/**
 * 评论里点了「1:00」之后唯一的落地处。
 *
 * 位置合法性只能在这里判：时长只有播放器知道，评论区拿不到。
 * 超出视频时长不跳转，只提示一次；0:00 正常回到开头。
 */
@Composable
fun BindPlayerCommentSeek(playback: PlaybackController, page: PlayerPageHost, toast: (String) -> Unit) {
    val request = page.seekRequest
    LaunchedEffect(request) {
        // 换集或页面重建后 consumed 已经等于它，不能把上一次的跳转再演一遍。
        if (request == 0 || request == page.consumedSeekRequest) return@LaunchedEffect
        page.consumedSeekRequest = request
        val target = page.seekTargetMs
        when (val result = commentSeekTarget(target, playback.refresh().durationMs)) {
            is CommentSeekResult.Seek -> playback.seekTo(result.positionMs)
            CommentSeekResult.OutOfRange -> toast("超出视频时长")
        }
    }
}