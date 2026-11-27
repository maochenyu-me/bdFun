package com.aritxonly.bdfun.core.domain

import com.aritxonly.bdfun.core.model.video.AudioQuality
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Test

class AudioQualityPolicyTest {

    private fun audio(
        id: Int,
        kbps: Long = 128,
        codec: String = "mp4a.40.2",
        url: String = "a-$id",
    ) = AudioQuality(id = id, bandwidth = kbps * 1000, codec = codec, url = url, rawUrl = "raw-$url")

    @Test fun labelsCoverBilibiliAudioTiers() {
        assertEquals("Hi-Res 无损", audioQualityLabel(audio(30251)))
        assertEquals("杜比全景声", audioQualityLabel(audio(30250)))
        assertEquals("192K", audioQualityLabel(audio(30280)))
        assertEquals("132K", audioQualityLabel(audio(30232)))
        assertEquals("64K", audioQualityLabel(audio(30216)))
        assertEquals("300K", audioQualityLabel(audio(9000, kbps = 300)))
    }

    @Test fun selectableDropsBlankUrlsAndDuplicateIds() {
        val low = audio(30280, kbps = 180)
        val high = audio(30280, kbps = 192)
        val picked = selectableAudioQualities(listOf(low, high, audio(30232), audio(30216, url = " ")))
        assertEquals(listOf(30280, 30232), picked.map { it.id })
        assertSame(high, picked.first())
    }

    @Test fun selectableSortsByQualityTierNotById() {
        val picked = selectableAudioQualities(
            listOf(audio(30216), audio(30280), audio(30251), audio(30250), audio(30232)),
        )
        assertEquals(listOf(30251, 30250, 30280, 30232, 30216), picked.map { it.id })
    }

    @Test fun resolveFallsBackToDefaultForAutoOrMissingTier() {
        val audios = listOf(audio(30280), audio(30216))
        assertNull(resolveAudioQuality(audios, AutoAudioQualityId))
        assertNull(resolveAudioQuality(audios, 30232))
        assertNull(resolveAudioQuality(listOf(audio(30232, url = " ")), 30232))
        assertEquals(30280, resolveAudioQuality(audios, 30280)?.id)
    }

    @Test fun labelForSelectionFallsBackToAutoWhenTierDisappears() {
        val audios = listOf(audio(30280), audio(30216))
        assertEquals("自动", audioQualityLabelFor(audios, AutoAudioQualityId))
        assertEquals("192K", audioQualityLabelFor(audios, 30280))
        assertEquals("自动", audioQualityLabelFor(audios, 30251))
    }

    @Test fun detailCombinesCodecAndBitrate() {
        assertEquals("AAC · 192 kbps", audioQualityDetail(audio(30280, kbps = 192)))
        assertEquals("FLAC · 800 kbps", audioQualityDetail(audio(30251, kbps = 800, codec = "fLaC")))
        assertEquals("E-AC-3 · 448 kbps", audioQualityDetail(audio(30250, kbps = 448, codec = "ec-3")))
        assertEquals("", audioQualityDetail(audio(1, kbps = 0, codec = "")))
    }
}
