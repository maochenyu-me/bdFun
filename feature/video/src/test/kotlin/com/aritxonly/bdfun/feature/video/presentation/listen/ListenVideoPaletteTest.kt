package com.aritxonly.bdfun.feature.video.presentation.listen

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ListenVideoPaletteTest {

    @Test fun gradientGoesDarkFromTopToBottom() {
        val palette = listenPaletteFromSeed(0xFF7B3FE4.toInt())
        val topLum = palette.top.luminance()
        val midLum = palette.middle.luminance()
        val bottomLum = palette.bottom.luminance()
        assertTrue("顶部应比中部亮", topLum > midLum)
        assertTrue("中部应比底部亮", midLum > bottomLum)
    }

    @Test fun gradientStaysDarkEnoughForWhiteText() {
        // 亮黄色封面也不能把背景冲成浅色，否则白字看不清。
        val palette = listenPaletteFromSeed(0xFFFFE94D.toInt())
        assertTrue(palette.top.luminance() < 0.45f)
        assertTrue(palette.bottom.luminance() < 0.1f)
    }

    @Test fun accentKeepsSeedHue() {
        val palette = listenPaletteFromSeed(0xFF2F7DFF.toInt())
        val r = (palette.accent shr 16) and 0xFF
        val g = (palette.accent shr 8) and 0xFF
        val b = palette.accent and 0xFF
        // 蓝色种子仍然是蓝调：蓝分量最大，红分量最小；亮度被压到可读范围。
        assertTrue(b > g)
        assertTrue(g > r)
        assertTrue(palette.accent.luminance() <= 0.43f)
    }

    @Test fun nearBlackSeedFallsBackToDefault() {
        assertEquals(ListenVideoPalette.Default, listenPaletteFromSeed(0xFF050505.toInt()))
    }

    @Test fun nearWhiteSeedFallsBackToDefault() {
        assertEquals(ListenVideoPalette.Default, listenPaletteFromSeed(0xFFFFFFFF.toInt()))
    }

    @Test fun dominantColorIgnoresGrayscalePixels() {
        val pixels = IntArray(100) { 0xFF808080.toInt() } + IntArray(40) { 0xFFB03030.toInt() }
        val seed = dominantCoverColor(pixels)
        assertNotNull(seed)
        assertEquals(0xB0, (seed!! shr 16) and 0xFF)
    }

    @Test fun dominantColorReturnsNullForAllGray() {
        assertEquals(null, dominantCoverColor(IntArray(64) { 0xFF9A9A9A.toInt() }))
    }

    @Test fun emptySamplesFallBackToDefault() {
        assertEquals(ListenVideoPalette.Default, listenPaletteFromSeed(null))
    }
}