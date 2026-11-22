package com.aritxonly.bdfun.presentation.settings.backup

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import com.aritxonly.bdfun.core.model.settings.Setting
import com.aritxonly.bdfun.feature.settings.application.SettingsBackupController
import com.aritxonly.teak.common.modal.AlertDialog
import com.aritxonly.teak.common.modal.TextButton
import com.aritxonly.teak.common.settings.TeakSettingsActionRow
import com.aritxonly.teak.common.settings.TeakSettingsSwitchRow
import com.aritxonly.teak.common.settings.TeakSettingsValueRow
import top.yukonga.miuix.kmp.basic.TextField

/** 创建面板状态，并把 SAF 的两个 launcher 接进状态机。 */
@Composable
internal fun rememberSettingsBackupStateHolder(
    controller: SettingsBackupController,
    resolver: android.content.ContentResolver?,
    notify: (String) -> Unit,
): SettingsBackupStateHolder {
    val scope = rememberCoroutineScope()
    val holder = remember(controller) { SettingsBackupStateHolder(controller, resolver, scope, notify) }
    val createDocument = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/json"),
    ) { uri -> uri?.let(holder::onExportPicked) }
    val openDocument = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri?.let(holder::onImportPicked)
    }
    SideEffect {
        holder.exportAction = { createDocument.launch(controller.defaultFileName()) }
        holder.importAction = { openDocument.launch(BACKUP_MIME_TYPES) }
    }
    return holder
}

/** 备份页的四个自定义行；key 必须与 settings-index.json 的 pref_name 一致。 */
internal fun backupSettingsExtensions(
    holder: SettingsBackupStateHolder,
): Map<String, @Composable (Setting) -> Unit> = mapOf(
    "backup_include_session" to { entry ->
        TeakSettingsSwitchRow(
            title = entry.title,
            description = entry.subtitle.orEmpty(),
            checked = holder.includeSession,
            onCheckedChange = holder::onIncludeSessionChange,
        )
    },
    "backup_export" to { entry ->
        TeakSettingsActionRow(
            title = entry.title,
            // 副标题在点击行里没有位置，把最要紧的差别（是否含登录态）放到行尾。
            trailing = if (holder.includeSession) "含登录态" else "仅设置",
            onClick = holder::onExportClick,
        )
    },
    "backup_import" to { entry ->
        TeakSettingsActionRow(title = entry.title, onClick = holder::onImportClick)
    },
    "backup_status" to { entry ->
        TeakSettingsValueRow(title = entry.title, value = holder.status ?: "尚未操作")
    },
)

@Composable
internal fun SettingsBackupDialogHost(holder: SettingsBackupStateHolder) {
    when (val dialog = holder.dialog) {
        is BackupDialog.Password -> BackupPasswordDialog(holder, dialog)
        is BackupDialog.Confirm -> BackupConfirmDialog(holder, dialog)
        is BackupDialog.Info -> AlertDialog(
            show = true,
            onDismissRequest = holder::dismissDialog,
            miuixTitle = dialog.title,
            miuixSummary = dialog.message,
            confirmButton = { TextButton(onClick = holder::dismissDialog, miuixText = "知道了") { Text("知道了") } },
        )
        null -> Unit
    }
}

@Composable
private fun BackupPasswordDialog(holder: SettingsBackupStateHolder, dialog: BackupDialog.Password) {
    val password = remember(dialog.purpose) { mutableStateOf("") }
    val isExport = dialog.purpose == BackupDialog.Purpose.Export
    AlertDialog(
        show = true,
        onDismissRequest = holder::dismissDialog,
        miuixTitle = if (isExport) "设置加密口令" else "输入备份口令",
        miuixSummary = dialog.error ?: if (isExport) {
            "备份包含登录态，需要用口令加密。口令不会上传，请自行记住。"
        } else {
            "该备份文件已加密，请输入导出时设置的口令。"
        },
        renderTextContentInMiuix = true,
        text = {
            TextField(
                value = password.value,
                onValueChange = { password.value = it },
                label = "口令",
                singleLine = true,
                visualTransformation = PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
            )
        },
        confirmButton = {
            TextButton(onClick = { holder.onPasswordSubmitted(password.value) }, miuixText = "确定") { Text("确定") }
        },
        dismissButton = {
            TextButton(onClick = holder::dismissDialog, miuixText = "取消") { Text("取消") }
        },
    )
}

@Composable
private fun BackupConfirmDialog(holder: SettingsBackupStateHolder, dialog: BackupDialog.Confirm) {
    val content = dialog.content
    val includesSession = content.sessionCookies != null
    val parts = buildList {
        add("设置 ${content.settings.size} 项" + if (dialog.unsupportedCount > 0) "（${dialog.unsupportedCount} 项无法识别，将跳过）" else "")
        if (content.mineLayout != null) add("我的页布局")
        content.searchHistory?.let { add("搜索历史 ${it.size} 条") }
        if (includesSession) add("B 站登录态（会替换当前账号）")
    }
    AlertDialog(
        show = true,
        onDismissRequest = holder::dismissDialog,
        miuixTitle = "导入这份备份？",
        miuixSummary = "将写入：${parts.joinToString("、")}。\n只覆盖文件里包含的项，其余设置保持现状。",
        confirmButton = {
            TextButton(
                onClick = holder::onImportConfirmed,
                miuixText = "导入",
                destructive = includesSession,
            ) { Text("导入") }
        },
        dismissButton = {
            TextButton(onClick = holder::dismissDialog, miuixText = "取消") { Text("取消") }
        },
    )
}

private val BACKUP_MIME_TYPES = arrayOf("application/json", "text/plain", "application/octet-stream")
