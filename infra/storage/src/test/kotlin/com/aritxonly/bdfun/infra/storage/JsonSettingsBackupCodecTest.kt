package com.aritxonly.bdfun.infra.storage

import com.aritxonly.bdfun.core.domain.SettingsBackupRead
import com.aritxonly.bdfun.core.model.settings.MineLayout
import com.aritxonly.bdfun.core.model.settings.SettingValue
import com.aritxonly.bdfun.core.model.settings.SettingsBackupContent
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class JsonSettingsBackupCodecTest {

    private val codec = JsonSettingsBackupCodec()

    private val sample = SettingsBackupContent(
        settings = linkedMapOf(
            "dark_mode" to SettingValue.Number(2),
            "blur" to SettingValue.Flag(true),
            "font_scale" to SettingValue.Decimal(1.15f),
            "codec_preference" to SettingValue.Text("hevc"),
            "home_channels" to SettingValue.TextSet(setOf("推荐", "热门", "动画")),
        ),
        mineLayout = MineLayout(history = true, favorites = false, watchLater = true),
        searchHistory = listOf("原神", "崩坏"),
        sessionCookies = null,
    )

    @Test
    fun plainBackupRoundTripsEveryValueType() {
        val raw = codec.encode(sample, appVersion = "1.2.3", exportedAt = "2026-11-22T05:00:00Z", password = null)
        val envelope = JSONObject(raw)
        assertEquals(1, envelope.getInt("schemaVersion"))
        assertEquals("bdFun", envelope.getString("app"))
        assertEquals(false, envelope.getBoolean("encrypted"))

        val read = codec.read(raw, password = null)
        assertTrue(read.toString(), read is SettingsBackupRead.Ready)
        val content = (read as SettingsBackupRead.Ready).content
        assertEquals(sample.settings, content.settings)
        assertEquals(sample.mineLayout, content.mineLayout)
        assertEquals(sample.searchHistory, content.searchHistory)
        assertNull(content.sessionCookies)
        assertTrue(read.unsupportedKeys.isEmpty())
    }

    @Test
    fun encryptedBackupNeedsTheRightPassword() {
        val withSession = sample.copy(sessionCookies = "SESSDATA\tv\tbilibili.com\t/\t0")
        val raw = codec.encode(withSession, "1.2.3", "2026-11-22T05:00:00Z", password = "123456")
        assertTrue(JSONObject(raw).getBoolean("encrypted"))
        assertTrue(codec.read(raw, password = null) is SettingsBackupRead.PasswordRequired)
        assertTrue(codec.read(raw, password = "wrong") is SettingsBackupRead.WrongPassword)

        val read = codec.read(raw, password = "123456")
        assertTrue(read.toString(), read is SettingsBackupRead.Ready)
        val content = (read as SettingsBackupRead.Ready).content
        assertEquals(withSession.settings, content.settings)
        assertEquals("SESSDATA\tv\tbilibili.com\t/\t0", content.sessionCookies)
    }

    @Test
    fun sessionBackupWithoutPasswordIsRefusedAtExport() {
        val withSession = sample.copy(sessionCookies = "SESSDATA\tv\tbilibili.com\t/\t0")
        val error = runCatching { codec.encode(withSession, "1.2.3", "now", password = null) }.exceptionOrNull()
        assertTrue(error.toString(), error is IllegalArgumentException)
    }

    @Test
    fun foreignOrFutureFilesAreRejected() {
        val plain = codec.encode(sample, "1.2.3", "now", password = null)
        val future = JSONObject(plain).put("schemaVersion", 99).toString()
        assertTrue(codec.read(future, null) is SettingsBackupRead.Invalid)
        val foreign = JSONObject(plain).put("app", "BiliPai").toString()
        assertTrue(codec.read(foreign, null) is SettingsBackupRead.Invalid)
        assertTrue(codec.read("not json", null) is SettingsBackupRead.Invalid)
        assertTrue(codec.read("{\"schemaVersion\":1,\"app\":\"bdFun\"}", null) is SettingsBackupRead.Invalid)
    }

    @Test
    fun unknownSettingShapesAreSkippedInsteadOfFailingTheFile() {
        val plain = JSONObject(codec.encode(sample, "1.2.3", "now", password = null))
        val settings = plain.getJSONObject("payload").getJSONObject("settings")
        settings.put("mystery", JSONObject().put("type", "color").put("value", "#fff"))
        settings.put("broken", "not-an-object")

        val read = codec.read(plain.toString(), null)
        assertTrue(read.toString(), read is SettingsBackupRead.Ready)
        val ready = read as SettingsBackupRead.Ready
        assertEquals(setOf("mystery", "broken"), ready.unsupportedKeys.toSet())
        assertEquals(sample.settings, ready.content.settings)
    }

    @Test
    fun missingSectionsStayNullSoImportKeepsCurrentValues() {
        val partial = codec.encode(SettingsBackupContent(settings = mapOf("blur" to SettingValue.Flag(false))), "1.2.3", "now", null)
        val content = (codec.read(partial, null) as SettingsBackupRead.Ready).content
        assertNull(content.mineLayout)
        assertNull(content.searchHistory)
        assertNull(content.sessionCookies)
        assertEquals(mapOf("blur" to SettingValue.Flag(false)), content.settings)
    }
}
