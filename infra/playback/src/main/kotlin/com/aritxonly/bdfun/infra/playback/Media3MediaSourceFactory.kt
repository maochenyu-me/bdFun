@file:androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)

package com.aritxonly.bdfun.infra.playback

import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.exoplayer.dash.DashMediaSource
import androidx.media3.exoplayer.source.MediaSource
import androidx.media3.exoplayer.source.MergingMediaSource
import androidx.media3.exoplayer.source.ProgressiveMediaSource
import com.aritxonly.bdfun.core.application.PlaybackDashSource
import com.aritxonly.bdfun.core.application.PlaybackSource
import java.io.File

/**
 * 播放源装配：从 [Media3PlaybackController] 抽出来，控制器本身只留播放生命周期。
 *
 * 抽出的直接原因是尺寸审计（文件 400 行上限）：加上 DASH 与听视频两条分支后
 * 控制器超过了阈值。这里的函数都是纯装配，不碰播放器状态。
 */

internal fun dataSource(context: android.content.Context, source: PlaybackSource): DefaultDataSource.Factory {
    val http = DefaultHttpDataSource.Factory()
        .setConnectTimeoutMs(15_000).setReadTimeoutMs(30_000)
        .setUserAgent(source.userAgent).setDefaultRequestProperties(source.headers)
    return DefaultDataSource.Factory(context, http)
}

/** 渐进流；听视频时只挂音轨，省掉视频下载与解码。 */
internal fun createProgressiveMediaSource(source: PlaybackSource, data: DefaultDataSource.Factory): MediaSource {
    val audioUrl = source.audioUrl
    if (source.audioOnly && !audioUrl.isNullOrBlank()) {
        return ProgressiveMediaSource.Factory(data).createMediaSource(MediaItem.fromUri(audioUrl))
    }
    val video: MediaSource = ProgressiveMediaSource.Factory(data).createMediaSource(MediaItem.fromUri(source.videoUrl))
    return if (audioUrl.isNullOrBlank()) video else MergingMediaSource(video,
        ProgressiveMediaSource.Factory(data).createMediaSource(MediaItem.fromUri(audioUrl)))
}

/**
 * 把 MPD 写成 cache 文件再交给 DashMediaSource：B 站没有现成 manifest 地址，只能自己拼。
 * 文件按内容 hash 复用，同一视频旋转/切档不会重复写盘。
 */
internal fun createDashMediaSource(
    context: android.content.Context, dash: PlaybackDashSource, data: DefaultDataSource.Factory,
): MediaSource? {
    val file = runCatching {
        val dir = File(context.cacheDir, "dash_manifests").apply { mkdirs() }
        val target = File(dir, "dash_${dash.manifest.playbackSignature()}.mpd")
        if (!target.exists() || target.length() == 0L) target.writeText(dash.manifest)
        target
    }.getOrNull() ?: return null
    return DashMediaSource.Factory(data).createMediaSource(
        MediaItem.Builder()
            .setUri(android.net.Uri.fromFile(file))
            .setMimeType(MimeTypes.APPLICATION_MPD)
            .build(),
    )
}