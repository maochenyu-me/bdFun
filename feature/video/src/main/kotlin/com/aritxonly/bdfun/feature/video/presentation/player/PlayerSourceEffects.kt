package com.aritxonly.bdfun.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import com.aritxonly.bdfun.core.application.PlaybackController
import com.aritxonly.bdfun.core.application.PlaybackDashSource
import com.aritxonly.bdfun.core.application.PlaybackSource
import com.aritxonly.bdfun.core.domain.resolveAudioQuality
import com.aritxonly.bdfun.core.application.PlaybackVideoOutputMode
import com.aritxonly.bdfun.core.application.PlaybackVideoTarget
import com.aritxonly.bdfun.core.application.buildPlaybackDashSource
import com.aritxonly.bdfun.core.domain.isDolbyVisionQuality
import com.aritxonly.bdfun.core.domain.PlaybackSourceUrls
import com.aritxonly.bdfun.core.domain.resolvePlaybackSourceUrls
import com.aritxonly.bdfun.core.model.video.Quality
import com.aritxonly.bdfun.feature.video.presentation.VideoPlaybackPlatform
import androidx.compose.ui.platform.LocalContext

@Composable
fun BindPlayerSource(
    playback: VideoPlaybackPlatform,
    media: VideoMediaInput,
    presentation: VideoPlayerPresentation,
    ui: VideoPlayerUiStateHolder, diagnostics: PlayerDiagnostics, autoPlay: () -> Boolean,
    progress: com.aritxonly.bdfun.core.domain.PlaybackProgressRepository, resumePlayback: () -> Boolean,
) {
    val log = diagnostics.log
    val bvid = media.bvid
    val info = media.info
    val quality = media.quality
    val autoQuality = media.autoQuality
    val resumePositionMs = presentation.resumePositionMs
    val usingFallback by ui.usingFallback
    val usingOriginal by ui.usingOriginal
    val audioOnly by ui.fsAudioOnly
    val audioQualityId by ui.fsAudioQualityId
    val speed by ui.speed
    val mediaKey = "$bvid:${presentation.fullscreenInfo.currentCid}"
    // audioOnly 与音轨 id 都进 key：进出听视频、换音轨都要按新地址重起，否则画面或声音不对。
    LaunchedEffect(mediaKey, info, quality, usingFallback, usingOriginal, audioOnly, audioQualityId) {
        val selectedAudio = if (audioOnly) resolveAudioQuality(info?.audios.orEmpty(), audioQualityId) else null
        val source = resolvePlaybackSourceUrls(
            quality, info, usingOriginal, usingFallback, selectedAudio,
        ) ?: run {
            log(
                "PLAYER",
                "no-source bvid=$bvid q=${quality?.id} hasInfo=${info != null} " +
                    "qualities=${info?.qualities?.size ?: 0} " +
                    "usingOriginal=$usingOriginal usingFallback=$usingFallback",
            )
            if (info != null && info.qualities.isEmpty() && info.fallbackUrl.isNullOrBlank()) {
                diagnostics.toast("播放地址为空，已记录日志")
            }
            return@LaunchedEffect
        }
        val url = source.videoUrl
        val audioUrl = source.audioUrl
        logSource(bvid, quality?.id, autoQuality, usingOriginal, usingFallback, url, audioUrl, log)
        val previousKey = ui.preparedMediaKey.value
        // 同一声明的地址复用：听视频只看音轨、看画面还要比视频地址。
        val unchanged = previousKey == mediaKey &&
            ui.preparedAudioOnly.value == audioOnly &&
            if (audioOnly) {
                ui.preparedAudioUrl.value == audioUrl &&
                    (audioUrl != null || ui.preparedVideoUrl.value == url)
            } else {
                ui.preparedVideoUrl.value == url && ui.preparedAudioUrl.value == audioUrl
            }
        if (unchanged) {
            log("PLAYER", "source-keep bvid=$bvid q=${quality?.id} audioOnly=$audioOnly")
            return@LaunchedEffect
        }
        // 具备完整 segment_base 时拼本地 MPD：之后同一视频换档只改轨道选择，
        // 不重建 MediaSource，音频解码器不会被释放（全屏升画质不断音）。
        val dash = if (usingOriginal || usingFallback) null else {
            info?.let { buildPlaybackDashSource(it, quality?.id ?: 0) }
        }
        if (resumePlayback() && previousKey != null && previousKey != mediaKey) {
            val previous = previousKey.split(":")
            val tick = playback.refresh()
            progress.save(previous[0], previous.getOrNull(1)?.toLongOrNull() ?: 0L, tick.positionMs, tick.durationMs)
        }
        val localResume = if (resumePlayback() && previousKey != mediaKey && resumePositionMs <= 0L) {
            progress.resumeFor(bvid, presentation.fullscreenInfo.currentCid)
        } else 0L
        val sameFullscreenVideo = previousKey == mediaKey && presentation.fullscreen &&
            !audioOnly && !usingOriginal && !usingFallback
        val target = quality?.videoTarget(ui.requestedVideoQualityId.intValue, sameFullscreenVideo)
        ui.preparedMediaKey.value = mediaKey
        ui.requestedVideoQualityId.intValue = quality?.id ?: 0
        ui.preparedVideoUrl.value = url
        ui.preparedAudioUrl.value = audioUrl
        ui.preparedAudioOnly.value = audioOnly
        playback.prepare(sourceOf(source, mediaKey, bvid, resumePositionMs, localResume, autoPlay(), speed,
            info?.fallbackUrl?.takeIf { it.isNotBlank() }, audioOnly, dash, target, playback.userAgent))
    }

}

/** 组装播放源；抽出来只为让主流程读得下（尺寸审计限 80 行）。 */
private fun sourceOf(
    urls: PlaybackSourceUrls, mediaKey: String, bvid: String, resumeMs: Long, localResume: Long,
    autoPlay: Boolean, speed: Float, castUrl: String?, audioOnly: Boolean,
    dash: PlaybackDashSource?, target: PlaybackVideoTarget?, userAgent: String,
) = PlaybackSource(
    videoUrl = urls.videoUrl, audioUrl = urls.audioUrl, mediaKey = mediaKey,
    headers = mapOf("Referer" to "https://www.bilibili.com/video/$bvid/"), userAgent = userAgent,
    resumePositionMs = if (resumeMs > 0L) resumeMs else localResume,
    autoPlay = autoPlay, speed = speed,
    // DASH 的音视频是分开的；投屏给电视一个文件地址，优先用能带声音的 durl。
    castUrl = castUrl,
    // 听视频只挂音轨；DASH 走本地 MPD 时换档不重建源。
    audioOnly = audioOnly,
    dash = if (audioOnly) null else dash,
    videoTarget = target,
)

private fun logSource(
    bvid: String, qualityId: Int?, autoQuality: Boolean, usingOriginal: Boolean, usingFallback: Boolean,
    url: String, audioUrl: String?, log: (String, String) -> Unit,
) {
    log(
        "PLAYER",
        "source bvid=$bvid q=$qualityId auto=$autoQuality " +
            "usingOriginal=$usingOriginal usingFallback=$usingFallback " +
            "video=${describeMediaUrl(url)} audio=${describeMediaUrl(audioUrl)}",
    )
}

internal fun Quality.videoTarget(previousId: Int, sameFullscreenVideo: Boolean) = PlaybackVideoTarget(
    qualityId = id,
    mode = when {
        isDolbyVisionQuality(this) -> PlaybackVideoOutputMode.DolbyVision
        id == 125 || codec.contains("hdr", ignoreCase = true) ||
            codec.contains("dovi", ignoreCase = true) -> PlaybackVideoOutputMode.Hdr
        else -> PlaybackVideoOutputMode.Standard
    },
    upgradeInBackground = sameFullscreenVideo && previousId in 1..64 && id > 64,
)
