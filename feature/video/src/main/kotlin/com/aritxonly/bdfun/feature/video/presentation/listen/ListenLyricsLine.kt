package com.aritxonly.bdfun.feature.video.presentation.listen

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.sp
import com.aritxonly.bdfun.core.domain.karaokeInactiveAlpha
import com.aritxonly.bdfun.core.domain.karaokeLineProgress
import com.aritxonly.bdfun.core.domain.karaokeLitGlyphCount

/** 一行歌词的绘制：按样式分派到 KTV 逐字或普通裁剪。 */
@Composable
internal fun LyricLineText(
    text: String,
    fromMs: Long,
    toMs: Long,
    current: Boolean,
    style: ListenLyricsStyle,
    distance: Int,
    positionNow: () -> Long,
) {
    val fontSize = if (current) 20.sp else 16.sp
    val weight = if (current) FontWeight.SemiBold else FontWeight.Normal
    when (style) {
        ListenLyricsStyle.KARAOKE ->
            KaraokeLineText(text, fromMs, toMs, current, distance, fontSize, weight, positionNow)
        ListenLyricsStyle.PLAIN ->
            PlainLineText(text, fromMs, toMs, current, fontSize, weight, positionNow)
    }
}

/**
 * KTV 逐字：先整行画暗色底，再把已经唱到的字一个个叠成亮色。
 *
 * 点亮进度按「字」而不是按像素切，扫描边才落在字与字之间；过渡中的那个字按比例画一半，
 * 所以看起来是一路扫过去，而不是一秒跳一格。位置在绘制阶段读取（[positionNow]），
 * 每帧只重绘这一行，不触发重组，滚动列表也不会跟着抖。
 */
@Composable
private fun KaraokeLineText(
    text: String,
    fromMs: Long,
    toMs: Long,
    current: Boolean,
    distance: Int,
    fontSize: TextUnit,
    weight: FontWeight,
    positionNow: () -> Long,
) {
    val shape = remember(text) { LyricLineShape() }
    Text(
        text = text,
        color = Color.White.copy(
            alpha = if (current) karaokeInactiveAlpha(1) else karaokeInactiveAlpha(distance),
        ),
        fontSize = fontSize,
        fontWeight = weight,
        textAlign = TextAlign.Center,
        onTextLayout = { shape.update(it) },
        modifier = Modifier
            .fillMaxWidth()
            .drawWithContent {
                drawContent()
                if (!current) return@drawWithContent
                val progress = karaokeLineProgress(positionNow(), fromMs, toMs)
                if (progress <= 0f) return@drawWithContent
                drawKaraokeFill(shape.layout ?: return@drawWithContent, shape.rows, shape.glyphs, progress)
            },
    )
}

/** 普通样式：当前行按行内进度擦一条亮色，不做逐字。 */
@Composable
private fun PlainLineText(
    text: String,
    fromMs: Long,
    toMs: Long,
    current: Boolean,
    fontSize: TextUnit,
    weight: FontWeight,
    positionNow: () -> Long,
) {
    var layout by remember(text) { mutableStateOf<TextLayoutResult?>(null) }
    Text(
        text = text,
        color = Color.White.copy(alpha = if (current) 0.42f else 0.34f),
        fontSize = fontSize,
        fontWeight = weight,
        textAlign = TextAlign.Center,
        onTextLayout = { layout = it },
        modifier = Modifier
            .fillMaxWidth()
            .drawWithContent {
                drawContent()
                if (!current) return@drawWithContent
                val progress = karaokeLineProgress(positionNow(), fromMs, toMs)
                if (progress <= 0f) return@drawWithContent
                val measured = layout ?: return@drawWithContent
                clipRect(right = size.width * progress) {
                    drawText(measured, color = Color.White)
                }
            },
    )
}

/** 排版结果 + 字素起点 + 按行分组的字素区间；换行或字号变了才重算。 */
private class LyricLineShape {
    var layout: TextLayoutResult? = null
        private set
    var glyphs: IntArray = IntArray(0)
        private set
    var rows: List<IntRange> = emptyList()
        private set
    private var text: String? = null

    fun update(result: TextLayoutResult) {
        if (layout === result) return
        layout = result
        val raw = result.layoutInput.text.text
        if (raw != text) {
            text = raw
            glyphs = glyphStarts(raw)
        }
        rows = groupByLine(result, glyphs)
    }

    private fun glyphStarts(raw: String): IntArray {
        val starts = ArrayList<Int>(raw.length)
        var i = 0
        while (i < raw.length) {
            starts += i
            i += Character.charCount(raw.codePointAt(i))
        }
        return starts.toIntArray()
    }

    /** 同一个字素簇归属同一行；连续同行的字素并成一个区间。 */
    private fun groupByLine(result: TextLayoutResult, glyphs: IntArray): List<IntRange> {
        if (glyphs.isEmpty()) return emptyList()
        val grouped = ArrayList<IntRange>(result.lineCount)
        var start = 0
        var line = result.getLineForOffset(glyphs[0])
        for (i in 1..glyphs.size) {
            val next = if (i < glyphs.size) result.getLineForOffset(glyphs[i]) else -1
            if (next != line) {
                grouped += start until i
                start = i
                line = next
            }
        }
        return grouped
    }
}

/** 已唱到的字按行裁剪叠亮；一行一次 drawText，长句换行也不会两边一起变亮。 */
private fun DrawScope.drawKaraokeFill(
    layout: TextLayoutResult,
    rows: List<IntRange>,
    glyphs: IntArray,
    progress: Float,
) {
    val lit = karaokeLitGlyphCount(progress, glyphs.size)
    if (lit <= 0f || glyphs.isEmpty()) return
    // 过渡字下标与它已经亮出来的比例；两者一起决定扫描边落在哪儿。
    val edge = lit.toInt().coerceAtMost(glyphs.lastIndex)
    val tail = (lit - lit.toInt()).coerceIn(0f, 1f)
    rows.forEachIndexed { line, row ->
        // 整行还没轮到就不画，免得空裁剪里白跑一次排版绘制。
        if (row.first >= lit) return@forEachIndexed
        val startBox = layout.getBoundingBox(glyphs[row.first])
        val right = if (lit >= row.last + 1) {
            layout.getBoundingBox(glyphs[row.last]).right
        } else {
            val edgeBox = layout.getBoundingBox(glyphs[edge])
            edgeBox.left + edgeBox.width * tail
        }
        if (right <= startBox.left) return@forEachIndexed
        clipRect(
            left = startBox.left,
            top = layout.getLineTop(line),
            right = right,
            bottom = layout.getLineBottom(line),
        ) {
            drawText(layout, color = Color.White)
        }
    }
}
