package com.aritxonly.bdfun.infra.storage

import com.aritxonly.bdfun.core.domain.SettingsBackupCodec
import com.aritxonly.bdfun.core.domain.SettingsBackupRead
import com.aritxonly.bdfun.core.model.settings.MineLayout
import com.aritxonly.bdfun.core.model.settings.SettingValue
import com.aritxonly.bdfun.core.model.settings.SettingsBackupContent
import org.json.JSONArray
import org.json.JSONObject

/**
 * 备份文件格式：
 * ```
 * { "schemaVersion": 1, "app": "bdFun", "appVersion": "...", "exportedAt": "...",
 *   "encrypted": false, "payload": { settings / mineLayout / searchHistory / session } }
 * ```
 * 含登录态时整个 payload 替换为 `cipher` 段（see [SettingsBackupCrypto]）。
 *
 * 设置项写成带类型标签的 `{type,value}`，而不是裸值：`Set<String>` 和 `String` 在 JSON 里区分不开，
 * 类型标签让「把集合导成逗号串」这类不可逆转换不会发生。
 */
class JsonSettingsBackupCodec internal constructor(
    private val crypto: SettingsBackupCrypto,
) : SettingsBackupCodec {

    /** 装配入口：加密实现是 infra 内部细节，不对外暴露。 */
    constructor() : this(SettingsBackupCrypto())

    override fun encode(
        content: SettingsBackupContent,
        appVersion: String,
        exportedAt: String,
        password: String?,
    ): String {
        require(content.sessionCookies == null || !password.isNullOrBlank()) { "包含登录态时必须设置密码" }
        val envelope = JSONObject()
            .put("schemaVersion", SCHEMA_VERSION)
            .put("app", APP)
            .put("appVersion", appVersion)
            .put("exportedAt", exportedAt)
        val payload = payloadJson(content)
        if (password.isNullOrBlank()) {
            envelope.put("encrypted", false).put("payload", payload)
        } else {
            val sealed = crypto.seal(payload.toString(), password)
            envelope.put("encrypted", true).put(
                "cipher",
                JSONObject()
                    .put("iterations", sealed.iterations)
                    .put("salt", sealed.salt)
                    .put("iv", sealed.iv)
                    .put("data", sealed.data),
            )
        }
        return envelope.toString(2)
    }

    override fun read(raw: String, password: String?): SettingsBackupRead {
        val envelope = envelopeOf(raw) ?: return SettingsBackupRead.Invalid("不是有效的备份文件")
        val payload = when (val state = readEnvelope(envelope)) {
            is Envelope.Invalid -> return SettingsBackupRead.Invalid(state.reason)
            is Envelope.Plain -> state.payload
            is Envelope.Encrypted -> {
                val secret = password?.takeIf { it.isNotBlank() } ?: return SettingsBackupRead.PasswordRequired
                val plain = crypto.open(state.sealed, secret) ?: return SettingsBackupRead.WrongPassword
                runCatching { JSONObject(plain) }.getOrNull() ?: return SettingsBackupRead.Invalid("口令正确但内容已损坏")
            }
        }
        val parsed = parsePayload(payload) ?: return SettingsBackupRead.Invalid("备份内容无法解析")
        return SettingsBackupRead.Ready(parsed.first, parsed.second)
    }

    private fun payloadJson(content: SettingsBackupContent): JSONObject {
        val settings = JSONObject()
        content.settings.forEach { (key, value) -> settings.put(key, valueJson(value)) }
        val payload = JSONObject().put("settings", settings)
        content.mineLayout?.let {
            payload.put("mineLayout", JSONObject().put("history", it.history)
                .put("favorites", it.favorites).put("watchLater", it.watchLater))
        }
        content.searchHistory?.let { history -> payload.put("searchHistory", JSONArray(history)) }
        content.sessionCookies?.let { payload.put("session", JSONObject().put("cookies", it)) }
        return payload
    }

    private fun valueJson(value: SettingValue): JSONObject = when (value) {
        is SettingValue.Flag -> JSONObject().put("type", "bool").put("value", value.value)
        is SettingValue.Number -> JSONObject().put("type", "int").put("value", value.value)
        is SettingValue.Decimal -> JSONObject().put("type", "float").put("value", value.value.toDouble())
        is SettingValue.Text -> JSONObject().put("type", "text").put("value", value.value)
        is SettingValue.TextSet -> JSONObject().put("type", "set").put("value", JSONArray(value.value.toList().sorted()))
    }

    /** 解析 payload；返回内容与「形状不认识而被跳过的设置键」。整块 JSON 坏掉时返回 null。 */
    private fun parsePayload(payload: JSONObject): Pair<SettingsBackupContent, List<String>>? {
        val settings = LinkedHashMap<String, SettingValue>()
        val unsupported = mutableListOf<String>()
        payload.optJSONObject("settings")?.let { object_ ->
            object_.keys().forEach { key ->
                val parsed = object_.optJSONObject(key)?.let(::parseValue)
                if (parsed == null) unsupported += key else settings[key] = parsed
            }
        }
        val mine = payload.optJSONObject("mineLayout")?.let {
            MineLayout(
                history = it.optBoolean("history", false),
                favorites = it.optBoolean("favorites", false),
                watchLater = it.optBoolean("watchLater", false),
            )
        }
        val history = payload.optJSONArray("searchHistory")?.let { array ->
            (0 until array.length()).mapNotNull { array.optString(it).takeIf(String::isNotBlank) }
        }
        val session = payload.optJSONObject("session")?.optString("cookies")?.takeIf { it.isNotBlank() }
        return SettingsBackupContent(settings, mine, history, session) to unsupported
    }

    private fun parseValue(json: JSONObject): SettingValue? = runCatching {
        when (json.optString("type")) {
            "bool" -> json.getBoolean("value").let(SettingValue::Flag)
            "int" -> json.getInt("value").let(SettingValue::Number)
            "float" -> json.getDouble("value").toFloat().let {
                require(it.isFinite()); SettingValue.Decimal(it)
            }
            "text" -> json.getString("value").let(SettingValue::Text)
            "set" -> json.getJSONArray("value").strings().toSet().let(SettingValue::TextSet)
            else -> null
        }
    }.getOrNull()

    private fun envelopeOf(raw: String): JSONObject? {
        if (raw.length > MAX_CHARS) return null
        return runCatching { JSONObject(raw) }.getOrNull()
    }

    private fun readEnvelope(envelope: JSONObject): Envelope {
        val version = envelope.optInt("schemaVersion", -1)
        if (version != SCHEMA_VERSION) return Envelope.Invalid("备份版本不支持（$version）")
        if (envelope.optString("app") != APP) return Envelope.Invalid("不是 bdFun 的备份文件")
        if (!envelope.optBoolean("encrypted", false)) {
            val payload = envelope.optJSONObject("payload") ?: return Envelope.Invalid("备份文件缺少内容")
            return Envelope.Plain(payload)
        }
        val cipher = envelope.optJSONObject("cipher") ?: return Envelope.Invalid("加密段缺失")
        val sealed = SettingsBackupCrypto.Sealed(
            iterations = cipher.optInt("iterations", 0),
            salt = cipher.optString("salt"),
            iv = cipher.optString("iv"),
            data = cipher.optString("data"),
        )
        if (sealed.salt.isBlank() || sealed.iv.isBlank() || sealed.data.isBlank()) {
            return Envelope.Invalid("加密段不完整")
        }
        return Envelope.Encrypted(sealed)
    }

    private sealed interface Envelope {
        data class Plain(val payload: JSONObject) : Envelope

        data class Encrypted(val sealed: SettingsBackupCrypto.Sealed) : Envelope

        data class Invalid(val reason: String) : Envelope
    }

    private fun JSONArray.strings(): List<String> = (0 until length()).map { optString(it) }

    companion object {
        const val SCHEMA_VERSION = 1
        const val APP = "bdFun"
        private const val MAX_CHARS = 4 * 1024 * 1024
    }
}
