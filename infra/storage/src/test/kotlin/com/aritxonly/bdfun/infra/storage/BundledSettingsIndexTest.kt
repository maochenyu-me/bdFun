package com.aritxonly.bdfun.infra.storage

import com.aritxonly.bdfun.core.domain.*
import org.junit.Assert.*
import org.junit.Test
import java.io.File

class BundledSettingsIndexTest {
    @Test fun shippedIndexPreservesRequestedOrganizationAndRestoredControls() {
        val file = File("../../app/src/main/assets/settings-index.json")
        val decoded = JsonSettingsIndexCodec().decode(file.readText())
        assertTrue(decoded.toString(), decoded is SettingsIndexImport.Success)
        val index = SettingsIndex((decoded as SettingsIndexImport.Success).categories)
        listOf("bt_latency_ms", "onboarding", "live_preview").forEach { assertFalse(index.settings.containsKey(it)) }
        assertEquals("后台播放", index.categories.first { it.route == "settings/player" }.groups.last().title)
        listOf("show_disc_after_video", "exit_video_control", "disc_breath_enabled", "disc_breath_cover_color", "home_card_style", "recommend_web")
            .forEach { assertTrue(it, it in index.settings) }
        assertTrue(index.settings.getValue("quality").options.any { it.value == com.aritxonly.bdfun.core.model.settings.SettingValue.Number(116) })
        assertEquals("root_footer", index.categories.first { it.route == "settings/account" }.layout)
    }

    /** 空降助手独立成页后，原来的播放器页只保留弹幕相关开关。 */
    @Test fun sponsorPageOwnsSkipModeAndCategorySwitches() {
        val file = File("../../app/src/main/assets/settings-index.json")
        val decoded = JsonSettingsIndexCodec().decode(file.readText()) as SettingsIndexImport.Success
        val index = SettingsIndex(decoded.categories)
        val sponsor = index.categories.first { it.route == "settings/sponsor" }
        assertEquals(listOf("跳过行为", "片段类别"), sponsor.groups.map { it.title })
        listOf("sponsor_skip_mode", "sponsor_min_duration", "sponsor_skip_notice",
            "sponsor_cat_sponsor", "sponsor_cat_selfpromo", "sponsor_cat_intro", "sponsor_cat_outro",
            "sponsor_cat_interaction", "sponsor_cat_preview", "sponsor_cat_filler", "sponsor_cat_music")
            .forEach { assertTrue(it, it in index.settings) }
        assertTrue(index.categories.first { it.route == "settings/player" }
            .groups.flatMap { group -> group.settings }.none { it.prefName.startsWith("sponsor_") })
    }
}
