package com.aritxonly.bdfun.core.model.settings

/**
 * 一份备份快照：只包含用户在界面上能改的设置与登录态，不含播放进度、离线缓存、发送记录等。
 *
 * 字段全部可空是为了区分「备份里没有这一块」和「备份里是空值」：导入时 null 表示保持现状，
 * 非 null 才覆盖，这样旧版本导出的文件不会把新版本新增的区块清掉。
 */
data class SettingsBackupContent(
    val settings: Map<String, SettingValue> = emptyMap(),
    val mineLayout: MineLayout? = null,
    val searchHistory: List<String>? = null,
    /** 与 BiliCookieJar 相同的 cookies_v1 序列化串；null 表示这份备份不含登录态。 */
    val sessionCookies: String? = null,
)

/** 导入结果；skipped 是文件里有、本版本不认识或类型不符的配置键。 */
data class SettingsBackupApplyReport(
    val importedCount: Int,
    val skippedKeys: List<String>,
    val mineLayoutApplied: Boolean,
    val searchHistoryApplied: Boolean,
    val sessionApplied: Boolean,
)
