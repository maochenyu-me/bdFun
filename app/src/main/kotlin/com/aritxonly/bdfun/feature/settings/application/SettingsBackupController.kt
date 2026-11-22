package com.aritxonly.bdfun.feature.settings.application

import com.aritxonly.bdfun.core.application.MineLayoutStateHolder
import com.aritxonly.bdfun.core.domain.SessionBackupPort
import com.aritxonly.bdfun.core.domain.SettingsBackupCodec
import com.aritxonly.bdfun.core.domain.SettingsBackupRead
import com.aritxonly.bdfun.core.domain.SettingsIndex
import com.aritxonly.bdfun.core.domain.SettingsRepository
import com.aritxonly.bdfun.core.model.settings.SettingValue
import com.aritxonly.bdfun.core.model.settings.SettingsBackupApplyReport
import com.aritxonly.bdfun.core.model.settings.SettingsBackupContent
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/**
 * 设置备份的业务入口：把「设置页能改的东西」抓成一个快照，或把一个快照写回去。
 *
 * 走 [SettingsRepository] 的 setter 而不是直接写 SharedPreferences —— 设置状态是 StateCell + StateFlow，
 * 绕过它写盘界面上不会变，导入后还会被内存里的旧值覆盖回去。
 *
 * 增量语义：备份里没有的区块（null）保持现状；单个设置键无法应用时进 skippedKeys，不影响其他项。
 */
class SettingsBackupController(
    private val index: SettingsIndex,
    private val repository: SettingsRepository,
    private val mineLayout: MineLayoutStateHolder,
    private val codec: SettingsBackupCodec,
    private val session: SessionBackupPort?,
    private val appVersion: String,
    private val now: () -> Long,
    private val flush: () -> Unit,
    private val refreshSession: suspend () -> Unit,
) {
    /** 与设置页一致的字段注册表（52 项）；导出与导入都以它为边界。 */
    private val bindings = indexedSettingBindings(repository)

    val settingCount: Int get() = bindings.size

    val sessionAvailable: Boolean get() = session?.exportCookies() != null

    fun export(includeSession: Boolean, password: String?): String {
        val cookies = if (includeSession) {
            session?.exportCookies() ?: error("当前未登录，无法导出登录态")
        } else {
            null
        }
        return codec.encode(capture(cookies), appVersion, nowIso(), password)
    }

    fun read(raw: String, password: String?): SettingsBackupRead = codec.read(raw, password)

    /**
     * 应用一份已解密的备份。登录态放在最后：设置先落盘，再切换账号，
     * 这样切换成功后页面刷新读到的已经是新设置。
     */
    suspend fun apply(content: SettingsBackupContent): SettingsBackupApplyReport {
        val skipped = content.settings.filterNot { (key, value) -> canApply(key, value) }.keys.toList()
        content.settings.forEach { (key, value) ->
            if (key !in skipped) bindings.getValue(key).write(value)
        }
        val layoutApplied = content.mineLayout?.let {
            mineLayout.setHistory(it.history)
            mineLayout.setFavorites(it.favorites)
            mineLayout.setWatchLater(it.watchLater)
            true
        } ?: false
        val historyApplied = content.searchHistory?.let { history ->
            repository.general.clearSearchHistory()
            // addSearchHistory 是前插，倒序写回才能保持备份里的先后顺序。
            history.asReversed().forEach(repository.general::addSearchHistory)
            true
        } ?: false
        val sessionApplied = content.sessionCookies?.let { cookies ->
            session?.importCookies(cookies) == true
        } ?: false
        flush()
        if (sessionApplied) refreshSession()
        return SettingsBackupApplyReport(
            importedCount = content.settings.size - skipped.size,
            skippedKeys = skipped,
            mineLayoutApplied = layoutApplied,
            searchHistoryApplied = historyApplied,
            sessionApplied = sessionApplied,
        )
    }

    /** 默认文件名；只允许 ASCII，避免分享/另存为在不同文件管理器上出问题。 */
    fun defaultFileName(): String {
        val stamp = FILE_STAMP.format(Instant.ofEpochMilli(now()))
        return "bdFun-backup-$stamp.json"
    }

    private fun canApply(key: String, value: SettingValue): Boolean {
        val setting = index.settings[key] ?: return false
        return setting.storage == "settings" && key in bindings && index.accepts(setting, value)
    }

    private fun capture(cookies: String?) = SettingsBackupContent(
        settings = bindings.mapValues { (_, binding) -> binding.read() },
        mineLayout = mineLayout.state.value,
        searchHistory = repository.general.searchHistory,
        sessionCookies = cookies,
    )

    private fun nowIso(): String = Instant.ofEpochMilli(now()).toString()

    private companion object {
        val FILE_STAMP: DateTimeFormatter =
            DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss").withZone(ZoneId.systemDefault())
    }
}
