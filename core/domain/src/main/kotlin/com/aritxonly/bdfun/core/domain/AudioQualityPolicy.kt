package com.aritxonly.bdfun.core.domain

import com.aritxonly.bdfun.core.model.video.AudioQuality

/** 0 表示自动：沿用 B 站默认音轨（标准档里码率最高那条）。 */
const val AutoAudioQualityId: Int = 0

/** 数值越小越优先：无损 > 杜比 > 192K > 132K > 64K。 */
fun audioQualityRank(id: Int): Int = when (id) {
    30251 -> 0
    30250 -> 1
    30280 -> 2
    30232 -> 3
    30216 -> 4
    else -> 5
}

fun audioQualityLabel(quality: AudioQuality): String = when (quality.id) {
    30251 -> "Hi-Res 无损"
    30250 -> "杜比全景声"
    30280 -> "192K"
    30232 -> "132K"
    30216 -> "64K"
    else -> if (quality.bandwidth > 0) "${quality.bandwidth / 1000}K" else "音质 ${quality.id}"
}

/** 码率与编码，面板右侧的说明文字；拿不到码率就只用编码。 */
fun audioQualityDetail(quality: AudioQuality): String {
    val codec = when {
        quality.codec.contains("flac", true) -> "FLAC"
        quality.codec.contains("ec-3", true) || quality.codec.contains("eac3", true) -> "E-AC-3"
        quality.codec.contains("ac-3", true) -> "AC-3"
        quality.codec.contains("mp4a", true) || quality.codec.contains("aac", true) -> "AAC"
        quality.codec.isBlank() -> ""
        else -> quality.codec
    }
    val kbps = if (quality.bandwidth > 0) "${quality.bandwidth / 1000} kbps" else ""
    return listOf(codec, kbps).filter { it.isNotBlank() }.joinToString(" · ")
}

/**
 * 列给用户选的音轨：同一 id 只留码率最高的一条（B 站会为同一档给多条 CDN / 编码），
 * 空地址直接丢掉，否则选中会起播失败。
 */
fun selectableAudioQualities(items: List<AudioQuality>): List<AudioQuality> =
    items.filter { it.url.isNotBlank() }
        .groupBy { it.id }
        .mapNotNull { (_, same) -> same.maxByOrNull { it.bandwidth } }
        .sortedWith(compareBy({ audioQualityRank(it.id) }, { -it.bandwidth }))

/** 选中音轨；自动或该档已消失（换稿件后档位不同）时返回 null，回落到默认音轨。 */
fun resolveAudioQuality(items: List<AudioQuality>, id: Int): AudioQuality? =
    if (id == AutoAudioQualityId) null else items.firstOrNull { it.id == id && it.url.isNotBlank() }

/** 菜单右侧文案：档位已不存在（换稿件）时也显示「自动」，与实际播放保持一致。 */
fun audioQualityLabelFor(items: List<AudioQuality>, id: Int): String =
    resolveAudioQuality(items, id)?.let(::audioQualityLabel) ?: "自动"
