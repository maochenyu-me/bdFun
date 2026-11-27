package com.aritxonly.bdfun.feature.video.presentation.listen

import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * 听视频背景的三段渐变。颜色全部从封面取样得来，但**不直接用取样值铺底**：
 * 封面经常是亮色（比如白字截图），直接铺会让白色文字看不清，
 * 所以统一压暗成「顶部还能看出色相、底部基本纯黑」的梯度。
 */
data class ListenVideoPalette(
    val top: Int,
    val middle: Int,
    val bottom: Int,
    val accent: Int,
) {
    companion object {
        val Default = ListenVideoPalette(
            top = 0xFF1B1D24.toInt(),
            middle = 0xFF101218.toInt(),
            bottom = 0xFF07080B.toInt(),
            accent = 0xFF2F7DFF.toInt(),
        )
    }
}

/** 顶部亮度上限：再亮白字就开始糊了。 */
private const val TOP_MAX_LUMINANCE = 0.42f

/**
 * 取样色太暗或太亮（接近纯黑/纯白）时没有信息量，退回默认配色。
 * 返回 null 表示「没有可用种子」，由 [listenPaletteFromSeed] 兜底。
 */
fun listenPaletteFromSeed(seed: Int?): ListenVideoPalette {
    if (seed == null) return ListenVideoPalette.Default
    val lum = seed.luminance()
    if (lum < 0.02f || lum > 0.97f) return ListenVideoPalette.Default
    val accent = seed.toAccent()
    return ListenVideoPalette(
        top = accent.scale(0.34f),
        middle = accent.scale(0.17f),
        bottom = accent.scale(0.06f),
        accent = accent,
    )
}

/**
 * 从封面像素里挑主色：先丢掉灰阶（饱和度过低）的像素，再按色相分桶取最大桶，
 * 桶内取平均。这样大面积黑白文字不会盖过真正的主题色。
 */
fun dominantCoverColor(pixels: IntArray): Int? {
    if (pixels.isEmpty()) return null
    val buckets = HashMap<Int, LongArray>()
    for (px in pixels) {
        val a = (px ushr 24) and 0xFF
        if (a < 0x80) continue
        val r = (px shr 16) and 0xFF
        val g = (px shr 8) and 0xFF
        val b = px and 0xFF
        if (saturation(r, g, b) < 0.18f) continue
        if (luminanceOf(r, g, b) < 0.08f) continue
        val hue = hueOf(r, g, b)
        val key = (hue / 15).toInt()
        val acc = buckets.getOrPut(key) { LongArray(4) }
        acc[0] += r; acc[1] += g; acc[2] += b; acc[3] += 1
    }
    val best = buckets.entries.maxByOrNull { it.value[3] } ?: return null
    val acc = best.value
    val count = acc[3]
    if (count == 0L) return null
    val r = ((acc[0].toFloat() / count).roundToInt()).coerceIn(0, 255)
    val g = ((acc[1].toFloat() / count).roundToInt()).coerceIn(0, 255)
    val b = ((acc[2].toFloat() / count).roundToInt()).coerceIn(0, 255)
    return (0xFF shl 24) or (r shl 16) or (g shl 8) or b
}

/** 取样色可能过亮（浅粉、米黄），提饱和度再压亮度，得到一个当强调色不刺眼的版本。 */
private fun Int.toAccent(): Int {
    val r = (this shr 16) and 0xFF
    val g = (this shr 8) and 0xFF
    val b = this and 0xFF
    val sat = saturation(r, g, b)
    val boost = if (sat < 0.35f) (0.35f / sat.coerceAtLeast(0.01f)).coerceAtMost(2.2f) else 1f
    fun mix(c: Int) = ((c - 128) * boost + 128).roundToInt().coerceIn(0, 255)
    val nr = mix(r); val ng = mix(g); val nb = mix(b)
    // 顶部亮度限制：按比例整体压，保持色相不变。
    val lum = luminanceOf(nr, ng, nb)
    val factor = if (lum > TOP_MAX_LUMINANCE) TOP_MAX_LUMINANCE / lum else 1f
    fun dim(c: Int) = (c * factor).roundToInt().coerceIn(0, 255)
    return (0xFF shl 24) or (dim(nr) shl 16) or (dim(ng) shl 8) or dim(nb)
}

private fun Int.scale(f: Float): Int {
    val r = (((this shr 16) and 0xFF) * f).roundToInt().coerceIn(0, 255)
    val g = (((this shr 8) and 0xFF) * f).roundToInt().coerceIn(0, 255)
    val b = ((this and 0xFF) * f).roundToInt().coerceIn(0, 255)
    return (0xFF shl 24) or (r shl 16) or (g shl 8) or b
}

internal fun Int.luminance(): Float {
    val r = (this shr 16) and 0xFF
    val g = (this shr 8) and 0xFF
    val b = this and 0xFF
    return luminanceOf(r, g, b)
}

private fun luminanceOf(r: Int, g: Int, b: Int): Float =
    (0.2126f * r + 0.7152f * g + 0.0722f * b) / 255f

private fun saturation(r: Int, g: Int, b: Int): Float {
    val maxC = max(max(r, g), b) / 255f
    val minC = min(min(r, g), b) / 255f
    return if (maxC <= 0f) 0f else (maxC - minC) / maxC
}

private fun hueOf(r: Int, g: Int, b: Int): Float {
    val rf = r / 255f; val gf = g / 255f; val bf = b / 255f
    val maxC = max(max(rf, gf), bf)
    val minC = min(min(rf, gf), bf)
    val delta = maxC - minC
    if (delta <= 0.0001f) return 0f
    val h = when (maxC) {
        rf -> ((gf - bf) / delta) % 6f
        gf -> ((bf - rf) / delta) + 2f
        else -> ((rf - gf) / delta) + 4f
    }
    val deg = h * 60f
    return if (deg < 0f) deg + 360f else deg
}