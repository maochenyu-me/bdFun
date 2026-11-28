package com.aritxonly.bdfun.ui

import com.aritxonly.bdfun.core.domain.SentHistoryRepository
import com.aritxonly.bdfun.core.model.video.VideoDetail
import com.aritxonly.bdfun.core.model.video.VideoItem

interface VideoDetailDanmakuHost {
    var aid: Long
    var cid: Long
    val danmakuProgressMs: Long
    var danmakuModeration: com.aritxonly.bdfun.feature.video.application.DanmakuModerationStateHolder?
    var danmakuReporter: (suspend (Long, com.aritxonly.bdfun.core.domain.DanmakuReportReason, String) -> com.aritxonly.bdfun.core.common.Outcome<Unit>)?
    var danmakuVoteLoader: (suspend (List<Long>) -> Unit)?
    var danmakuVotes: kotlinx.coroutines.flow.StateFlow<Map<Pair<Long, Long>, com.aritxonly.bdfun.core.domain.DanmakuVote>>?
    var danmakuLiker: (suspend (Long) -> com.aritxonly.bdfun.core.common.Outcome<Boolean>)?
    var danmakuOwnerMid: Long
    var styledDanmakuSender: StyledDanmakuSender?
    var danmakuSender: (suspend (String, Int, Int, Int) -> Boolean)?
}

class VideoDetailEffectPorts(
    val danmaku: VideoDetailDanmakuHost,
    val sentHistory: () -> SentHistoryRepository,
    val publishFullscreen: (Boolean) -> Unit,
    /** 把「用户点了展开」的意图同步给 Activity：朝向请求与系统栏按它走。 */
    val publishFullscreenIntent: (Boolean) -> Unit = {},
    val updateBackground: (VideoItem, VideoDetail?, Long, Int) -> Unit,
    /** 当前是不是听视频在独占整屏：决定旋转要不要自动退出全屏。 */
    val listenMode: () -> Boolean = { false },
)
