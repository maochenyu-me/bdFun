package com.aritxonly.bdfun.core.model.video

data class PlayInfo(
    val qualities: List<Quality>,
    val audioUrl: String?,
    val fallbackUrl: String?,
    val durationMs: Long,
    val acceptQuality: List<Int>,
    /** CDN 替换前的原始音轨/兜底地址。 */
    val rawAudioUrl: String? = null,
    val rawFallbackUrl: String? = null,
    /** dash.audio 的全部音轨，听视频里可单独挑一档；空表示只有默认音轨。 */
    val audios: List<AudioQuality> = emptyList(),
) {
    val isDash: Boolean get() = qualities.isNotEmpty() && audioUrl != null
}
