package com.aritxonly.bdfun.feature.settings.application

import com.aritxonly.bdfun.core.application.MineLayoutStateHolder
import com.aritxonly.bdfun.core.domain.MineLayoutPreferences
import com.aritxonly.bdfun.core.domain.SessionBackupPort
import com.aritxonly.bdfun.core.domain.SettingsBackupCodec
import com.aritxonly.bdfun.core.domain.SettingsBackupRead
import com.aritxonly.bdfun.core.domain.SettingsIndex
import com.aritxonly.bdfun.core.model.settings.MineLayout
import com.aritxonly.bdfun.core.model.settings.Setting
import com.aritxonly.bdfun.core.model.settings.SettingsGroup
import com.aritxonly.bdfun.core.model.settings.SettingOption
import com.aritxonly.bdfun.core.model.settings.SettingValue
import com.aritxonly.bdfun.core.model.settings.SettingsBackupContent
import com.aritxonly.bdfun.core.model.settings.SettingsCategory
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SettingsBackupControllerTest {

    private class FakeCodec : SettingsBackupCodec {
        var content: SettingsBackupContent? = null
        override fun encode(content: SettingsBackupContent, appVersion: String, exportedAt: String, password: String?): String {
            this.content = content
            return "encoded"
        }

        override fun read(raw: String, password: String?): SettingsBackupRead =
            content?.let { SettingsBackupRead.Ready(it) } ?: SettingsBackupRead.Invalid("empty")
    }

    private class FakeSession(private var cookies: String?) : SessionBackupPort {
        var imported: String? = null
        override fun exportCookies(): String? = cookies
        override fun importCookies(serialized: String): Boolean {
            imported = serialized
            cookies = serialized
            return true
        }
    }

    private class MemoryLayout : MineLayoutPreferences {
        var layout = MineLayout()
        override fun read() = layout
        override fun write(layout: MineLayout) { this.layout = layout }
    }

    private val index = SettingsIndex(
        listOf(
            SettingsCategory(
                "播放", "settings/player", null,
                listOf(
                    SettingsGroup(
                        "播放行为",
                        listOf(
                            Setting("background_play", "switch", "后台播放", null, SettingValue.Flag(true)),
                            Setting("blur", "switch", "模糊", null, SettingValue.Flag(false)),
                            Setting(
                                "quality", "select", "清晰度", null, SettingValue.Number(64),
                                listOf(SettingOption("720P", SettingValue.Number(64)), SettingOption("1080P", SettingValue.Number(80))),
                            ),
                        ),
                    ),
                ),
            ),
        ),
    )

    private inner class Fixture(val session: FakeSession?) {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
        val prefs = MemorySettingsPreferences(linkedMapOf<String, Any>("background_play" to false, "blur" to true, "quality" to 80))
        val repository = SettingsStateHolder.create(prefs, TestHistoryCodec, scope)
            .also { it.playback.qualityPreference = 80 }
        val layoutPrefs = MemoryLayout()
        val mineLayout = MineLayoutStateHolder(layoutPrefs)
        val codec = FakeCodec()
        var flushes = 0
        var refreshes = 0
        val controller = SettingsBackupController(
            index = index,
            repository = repository,
            mineLayout = mineLayout,
            codec = codec,
            session = session,
            appVersion = "1.0.0-test",
            now = { 0L },
            flush = { flushes++ },
            refreshSession = { refreshes++ },
        )
        fun close() = scope.cancel()
    }

    @Test
    fun exportCapturesEveryBackedUpSection() {
        val fixture = Fixture(FakeSession("SESSDATA\tv\tbilibili.com\t/\t0"))
        try {
            fixture.repository.general.addSearchHistory("原神")
            fixture.mineLayout.setHistory(true)
            val raw = fixture.controller.export(includeSession = true, password = "123456")
            assertEquals("encoded", raw)
            val content = requireNotNull(fixture.codec.content)
            assertEquals(SettingValue.Flag(false), content.settings.getValue("background_play"))
            assertEquals(SettingValue.Flag(true), content.settings.getValue("blur"))
            assertEquals(SettingValue.Number(80), content.settings.getValue("quality"))
            assertEquals(MineLayout(history = true), content.mineLayout)
            assertEquals(listOf("原神"), content.searchHistory)
            assertEquals("SESSDATA\tv\tbilibili.com\t/\t0", content.sessionCookies)
        } finally {
            fixture.close()
        }
    }

    @Test
    fun exportWithoutSessionLeavesCookiesOut() {
        val fixture = Fixture(FakeSession(null))
        try {
            val content = run {
                fixture.controller.export(includeSession = false, password = null)
                requireNotNull(fixture.codec.content)
            }
            assertNull(content.sessionCookies)
            assertNull(content.searchHistory?.takeIf { it.isNotEmpty() })
        } finally {
            fixture.close()
        }
    }

    @Test
    fun exportingSessionWhileLoggedOutFails() {
        val fixture = Fixture(FakeSession(null))
        try {
            assertFalse(fixture.controller.sessionAvailable)
            val error = runCatching { fixture.controller.export(includeSession = true, password = "123456") }.exceptionOrNull()
            assertTrue(error.toString(), error is IllegalStateException)
        } finally {
            fixture.close()
        }
    }

    @Test
    fun applyOnlyWritesKeysTheFileCarriesAndSkipsInvalidOnes() {
        val fixture = Fixture(null)
        try {
            val report = runBlocking {
                fixture.controller.apply(
                    SettingsBackupContent(
                        settings = linkedMapOf(
                            "background_play" to SettingValue.Flag(true),
                            "quality" to SettingValue.Number(1),
                            "ghost" to SettingValue.Flag(true),
                        ),
                    ),
                )
            }
            assertTrue(fixture.repository.playback.backgroundPlay)
            assertEquals(80, fixture.repository.playback.qualityPreference)
            assertEquals(1, report.importedCount)
            assertEquals(setOf("quality", "ghost"), report.skippedKeys.toSet())
            assertFalse(report.mineLayoutApplied)
            assertFalse(report.searchHistoryApplied)
            assertFalse(report.sessionApplied)
            assertEquals(1, fixture.flushes)
            assertEquals(0, fixture.refreshes)
        } finally {
            fixture.close()
        }
    }

    @Test
    fun applyWritesLayoutHistoryAndSessionInOnePass() {
        val session = FakeSession(null)
        val fixture = Fixture(session)
        try {
            val report = runBlocking {
                fixture.controller.apply(
                    SettingsBackupContent(
                        settings = emptyMap(),
                        mineLayout = MineLayout(history = true, favorites = true, watchLater = false),
                        searchHistory = listOf("a", "b", "c"),
                        sessionCookies = "SESSDATA\tv\tbilibili.com\t/\t0",
                    ),
                )
            }
            assertEquals(MineLayout(history = true, favorites = true), fixture.layoutPrefs.layout)
            assertEquals(listOf("a", "b", "c"), fixture.repository.general.searchHistory)
            assertEquals("SESSDATA\tv\tbilibili.com\t/\t0", session.imported)
            assertTrue(report.mineLayoutApplied)
            assertTrue(report.searchHistoryApplied)
            assertTrue(report.sessionApplied)
            assertEquals(1, fixture.refreshes)
        } finally {
            fixture.close()
        }
    }
}
