package com.aritxonly.bdfun.core.model.video

/**
 * DASH 音轨。与 [Quality]（dash.video）是两条平行列表：
 * id 固定表达码率档位，换画质不会换音轨。
 */
data class AudioQuality(
    val id: Int,
    val bandwidth: Long,
    val codec: String,
    val url: String,
    /** CDN 替换前的原始地址，CDN 403 时用来重试。 */
    val rawUrl: String = "",
)
