package com.aritxonly.bdfun.presentation.settings.backup

import android.content.ContentResolver
import android.net.Uri
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.aritxonly.bdfun.core.domain.SettingsBackupRead
import com.aritxonly.bdfun.core.model.settings.SettingsBackupApplyReport
import com.aritxonly.bdfun.core.model.settings.SettingsBackupContent
import com.aritxonly.bdfun.feature.settings.application.SettingsBackupController
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.IOException

/** 备份面板的弹窗状态。 */
sealed interface BackupDialog {
    enum class Purpose { Export, Import }

    data class Password(
        val purpose: Purpose,
        val error: String? = null,
    ) : BackupDialog

    data class Confirm(
        val content: SettingsBackupContent,
        val unsupportedCount: Int,
    ) : BackupDialog

    data class Info(val title: String, val message: String) : BackupDialog
}

/**
 * 备份/恢复面板的界面状态。
 *
 * 文件读写在这里做（SAF uri 只在拿到后才有效，不能存进控制器），业务写入交给
 * [SettingsBackupController]；所有弹窗共用一个 [dialog] 字段，避免多个 AlertDialog 叠着抢焦点。
 */
@Stable
class SettingsBackupStateHolder(
    private val controller: SettingsBackupController,
    private val contentResolver: ContentResolver?,
    private val scope: CoroutineScope,
    private val notify: (String) -> Unit,
) {
    var includeSession by mutableStateOf(false)
        private set
    var busy by mutableStateOf(false)
        private set
    var status by mutableStateOf<String?>(null)
        private set
    var dialog by mutableStateOf<BackupDialog?>(null)
        private set

    /** SAF launcher 只能在组合里创建，由 remember 侧回填。 */
    var exportAction: () -> Unit = {}
    var importAction: () -> Unit = {}

    private var pendingUri: Uri? = null
    private var pendingRaw: String? = null

    fun onIncludeSessionChange(value: Boolean) {
        if (value && !controller.sessionAvailable) {
            notify("当前未登录，没有可备份的登录态")
            return
        }
        includeSession = value
    }

    fun onExportClick() = exportAction()

    fun onImportClick() = importAction()

    fun onExportPicked(uri: Uri) {
        pendingUri = uri
        if (includeSession) {
            dialog = BackupDialog.Password(BackupDialog.Purpose.Export)
        } else {
            exportNow(password = null)
        }
    }

    fun onImportPicked(uri: Uri) {
        val resolver = contentResolver ?: return notify("无法读取文件")
        busy = true
        scope.launch {
            val raw = runCatching { withContext(Dispatchers.IO) { readText(resolver, uri) } }.getOrNull()
            busy = false
            if (raw == null) {
                dialog = BackupDialog.Info("无法导入", "读取所选文件失败")
                return@launch
            }
            pendingRaw = raw
            inspectAndConfirm(raw, password = null)
        }
    }

    /** 口令弹窗确认；导出与导入共用同一个输入框。 */
    fun onPasswordSubmitted(password: String) {
        val current = dialog as? BackupDialog.Password ?: return
        if (password.length < MIN_PASSWORD_LENGTH) {
            dialog = current.copy(error = "口令至少 $MIN_PASSWORD_LENGTH 位")
            return
        }
        when (current.purpose) {
            BackupDialog.Purpose.Export -> exportNow(password)
            BackupDialog.Purpose.Import -> inspectAndConfirm(pendingRaw ?: return, password)
        }
    }

    fun onImportConfirmed() {
        val confirm = dialog as? BackupDialog.Confirm ?: return
        busy = true
        scope.launch {
            val report = runCatching { controller.apply(confirm.content) }.getOrNull()
            busy = false
            dialog = null
            if (report == null) {
                notify("导入失败：写入设置时出错")
                return@launch
            }
            status = reportText(report)
            notify(status.orEmpty())
        }
    }

    fun dismissDialog() {
        dialog = null
    }

    private fun exportNow(password: String?) {
        val uri = pendingUri ?: return
        val resolver = contentResolver ?: return notify("无法写入文件")
        busy = true
        scope.launch {
            val failure = runCatching { controller.export(includeSession, password) }
            val text = failure.getOrNull()
            if (text == null) {
                busy = false
                dialog = BackupDialog.Info("导出失败", failure.exceptionOrNull()?.message ?: "生成备份内容失败")
                return@launch
            }
            val written = runCatching { withContext(Dispatchers.IO) { writeText(resolver, uri, text) } }.isSuccess
            busy = false
            dialog = null
            if (written) {
                status = if (includeSession) "已导出 ${controller.settingCount} 项设置（含登录态）" else "已导出 ${controller.settingCount} 项设置"
                notify(status.orEmpty())
            } else {
                notify("导出失败：无法写入所选文件")
            }
        }
    }

    private fun inspectAndConfirm(raw: String, password: String?) {
        busy = true
        scope.launch {
            val result = runCatching { controller.read(raw, password) }.getOrNull()
            busy = false
            when (result) {
                is SettingsBackupRead.Ready -> dialog = BackupDialog.Confirm(result.content, result.unsupportedKeys.size)
                is SettingsBackupRead.WrongPassword ->
                    dialog = BackupDialog.Password(BackupDialog.Purpose.Import, "口令不正确")
                is SettingsBackupRead.PasswordRequired ->
                    dialog = BackupDialog.Password(BackupDialog.Purpose.Import)
                is SettingsBackupRead.Invalid -> dialog = BackupDialog.Info("无法导入", result.reason)
                null -> dialog = BackupDialog.Info("无法导入", "备份文件无法解析")
            }
        }
    }

    private fun reportText(report: SettingsBackupApplyReport): String {
        val base = "已导入 ${report.importedCount} 项设置"
        val skipped = if (report.skippedKeys.isEmpty()) "" else "，跳过 ${report.skippedKeys.size} 项"
        val extras = buildList {
            if (report.mineLayoutApplied) add("我的页布局")
            if (report.searchHistoryApplied) add("搜索历史")
            if (report.sessionApplied) add("登录态")
        }
        return if (extras.isEmpty()) base + skipped else base + skipped + "（含${extras.joinToString("、")}）"
    }

    private fun readText(resolver: ContentResolver, uri: Uri): String {
        val bytes = resolver.openInputStream(uri)?.use { it.readBytes() } ?: throw IOException("无法打开文件")
        if (bytes.size > MAX_BACKUP_BYTES) throw IOException("备份文件过大")
        return String(bytes, Charsets.UTF_8)
    }

    private fun writeText(resolver: ContentResolver, uri: Uri, text: String) {
        resolver.openOutputStream(uri, "wt")?.use { it.write(text.toByteArray(Charsets.UTF_8)) }
            ?: throw IOException("无法打开文件")
    }

    private companion object {
        const val MIN_PASSWORD_LENGTH = 6
        const val MAX_BACKUP_BYTES = 4 * 1024 * 1024
    }
}
