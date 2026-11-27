package com.aritxonly.bdfun.feature.video.presentation.listen

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ListenLyricsTest {

    @Test fun parsesSimpleLrcWithTimestamps() {
        val lyrics = parseLrc(
            """
            [ti:标题]
            [00:01.00]第一句
            [00:03.50]第二句
            """.trimIndent(),
        )
        assertEquals(listOf("第一句", "第二句"), lyrics.lines.map { it.text })
        assertEquals(1000L, lyrics.lines[0].fromMs)
        assertEquals(3500L, lyrics.lines[1].fromMs)
        // 最后一句没有下一行：给一个有限的收尾时长，避免永远高亮
        assertTrue(lyrics.lines[1].toMs > 3500L)
    }

    @Test fun supportsMultipleTimestampsOnOneLine() {
        val lyrics = parseLrc("[00:10.00][00:20.00]副歌")
        assertEquals(2, lyrics.lines.size)
        assertEquals(10_000L, lyrics.lines[0].fromMs)
        assertEquals(20_000L, lyrics.lines[1].fromMs)
        assertEquals("副歌", lyrics.lines[0].text)
    }

    @Test fun appliesOffsetAndSortsByTime() {
        val lyrics = parseLrc(
            """
            [00:05.00]后一句
            [00:01.00]前一句
            """.trimIndent(),
        )
        assertEquals(listOf("前一句", "后一句"), lyrics.lines.map { it.text })
        val shifted = lyrics.withOffset(500L)
        assertEquals(1500L, shifted.lines[0].fromMs)
        assertEquals(5500L, shifted.lines[1].fromMs)
    }

    @Test fun ignoresMetadataAndBlankLines() {
        val lyrics = parseLrc(
            """
            [ar:某人]

            [by:工具]
            [00:02.00]只有这句
            """.trimIndent(),
        )
        assertEquals(1, lyrics.lines.size)
        assertEquals("只有这句", lyrics.lines.single().text)
    }

    @Test fun returnsMinusOneBeforeFirstLine() {
        val lyrics = parseLrc("[00:10.00]开始")
        assertEquals(-1, lyrics.indexAt(9_999L))
        assertEquals(0, lyrics.indexAt(10_000L))
        assertEquals(0, lyrics.indexAt(60_000L))
    }

    @Test fun indexWalksAcrossLines() {
        val lyrics = parseLrc(
            """
            [00:00.00]一
            [00:05.00]二
            [00:10.00]三
            """.trimIndent(),
        )
        assertEquals(0, lyrics.indexAt(2_000L))
        assertEquals(1, lyrics.indexAt(5_000L))
        assertEquals(2, lyrics.indexAt(11_000L))
    }

    @Test fun lineProgressIsClamped() {
        val lyrics = parseLrc(
            """
            [00:00.00]一
            [00:04.00]二
            """.trimIndent(),
        )
        assertEquals(0f, lyrics.progressAt(0, -100L), 0.001f)
        assertEquals(0.5f, lyrics.progressAt(0, 2_000L), 0.001f)
        assertEquals(1f, lyrics.progressAt(0, 9_000L), 0.001f)
    }

    @Test fun convertsSubtitleCuesToLyrics() {
        val cues = listOf(
            SubtitleCueInput(1_000L, 3_000L, "第一句"),
            SubtitleCueInput(3_000L, 6_000L, "第二句"),
        )
        val lyrics = lyricsFromCues(cues)
        assertEquals(2, lyrics.lines.size)
        assertEquals(3_000L, lyrics.lines[0].toMs)
    }

    @Test fun attachesTranslationByTimeOverlap() {
        val main = lyricsFromCues(listOf(
            SubtitleCueInput(0L, 2_000L, "你好"),
            SubtitleCueInput(2_000L, 4_000L, "再见"),
        ))
        val sub = lyricsFromCues(listOf(
            SubtitleCueInput(0L, 2_000L, "Hello"),
            SubtitleCueInput(2_000L, 4_000L, "Bye"),
        ))
        val merged = main.withTranslation(sub)
        assertEquals("Hello", merged.lines[0].translation)
        assertEquals("Bye", merged.lines[1].translation)
    }

    @Test fun translationWithoutOverlapStaysNull() {
        val main = lyricsFromCues(listOf(SubtitleCueInput(0L, 2_000L, "你好")))
        val sub = lyricsFromCues(listOf(SubtitleCueInput(10_000L, 12_000L, "Hello")))
        assertNull(main.withTranslation(sub).lines[0].translation)
    }

    @Test fun emptyInputProducesEmptyLyrics() {
        assertTrue(parseLrc("").lines.isEmpty())
        assertTrue(lyricsFromCues(emptyList()).lines.isEmpty())
        assertEquals(-1, parseLrc("").indexAt(1_000L))
    }
}
