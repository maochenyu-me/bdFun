package com.aritxonly.bdfun.core.domain

import com.aritxonly.bdfun.core.model.video.AudioQuality
import com.aritxonly.bdfun.core.model.video.PlayInfo
import com.aritxonly.bdfun.core.model.video.Quality

data class PlaybackSourceUrls(val videoUrl: String, val audioUrl: String?)
enum class PlaybackRecoveryAction { RetryOriginal, RetryFallback, Fail }

/**
 * 保留旧播放器的null/空串区别与原始地址优先级，不额外清洗返回字段。
 *
 * [selectedAudio] 是听视频里手动挑的音轨；不传就沿用 [PlayInfo.audioUrl]（B 站默认那条）。
 * durl 兜底文件自带音轨，此时不再额外挂 DASH 音轨，否则会双音轨。
 */
fun resolvePlaybackSourceUrls(
    quality: Quality?, info: PlayInfo?, usingOriginal: Boolean, usingFallback: Boolean,
    selectedAudio: AudioQuality? = null,
): PlaybackSourceUrls? {
    val video = when {
        usingFallback -> info?.fallbackUrl
        usingOriginal -> quality?.rawVideoUrl?.takeIf { it.isNotBlank() }
            ?: info?.rawFallbackUrl?.takeIf { it.isNotBlank() }
            ?: quality?.videoUrl
            ?: info?.fallbackUrl
        else -> quality?.videoUrl ?: info?.fallbackUrl
    } ?: return null
    val audio = when {
        usingFallback -> null
        selectedAudio != null -> if (usingOriginal) {
            selectedAudio.rawUrl.takeIf { it.isNotBlank() } ?: selectedAudio.url
        } else {
            selectedAudio.url
        }
        usingOriginal -> info?.rawAudioUrl?.takeIf { it.isNotBlank() } ?: info?.audioUrl
        else -> info?.audioUrl
    }
    return PlaybackSourceUrls(video, audio)
}

/** 403先试原视频/音轨地址，再试durl；仅有rawFallback不会触发第一步。 */
fun selectPlaybackRecovery(
    quality: Quality?, info: PlayInfo?, usingOriginal: Boolean, usingFallback: Boolean,
): PlaybackRecoveryAction = when {
    !usingOriginal && (!quality?.rawVideoUrl.isNullOrBlank() || !info?.rawAudioUrl.isNullOrBlank()) ->
        PlaybackRecoveryAction.RetryOriginal
    !usingFallback && !info?.fallbackUrl.isNullOrBlank() -> PlaybackRecoveryAction.RetryFallback
    else -> PlaybackRecoveryAction.Fail
}
