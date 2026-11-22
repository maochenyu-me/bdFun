package com.aritxonly.bdfun.core.domain

import com.aritxonly.bdfun.core.model.settings.SettingsBackupContent

/**
 * 备份文件编解码：格式与加密实现都在 infra:storage，domain 只描述「写出去 / 读回来」的形状。
 *
 * 加密与否由 [encode] 的 password 决定：password 为 null 时是明文 JSON（只有设置），
 * 非 null 时整个 payload 被口令加密（含登录态的备份必须走这条路）。
 */
interface SettingsBackupCodec {
    fun encode(
        content: SettingsBackupContent,
        appVersion: String,
        exportedAt: String,
        password: String?,
    ): String

    /** 读出内容；加密文件必须给出正确口令。 */
    fun read(raw: String, password: String?): SettingsBackupRead
}

sealed interface SettingsBackupRead {
    /** [unsupportedKeys] 是能识别出是设置项、但取值形状不认识而被跳过的键。 */
    data class Ready(
        val content: SettingsBackupContent,
        val unsupportedKeys: List<String> = emptyList(),
    ) : SettingsBackupRead

    data object PasswordRequired : SettingsBackupRead

    data object WrongPassword : SettingsBackupRead

    data class Invalid(val reason: String) : SettingsBackupRead
}

/**
 * B 站登录态的备份端口：实现放在 infra:bili（只有它持有 cookie jar），
 * 界面与业务层不直接接触 cookie 序列化格式。
 */
interface SessionBackupPort {
    /** 当前登录态的 cookies_v1 串；未登录返回 null。 */
    fun exportCookies(): String?

    /** 用备份串覆盖当前登录态；返回是否至少恢复了一条 cookie。 */
    fun importCookies(serialized: String): Boolean
}
