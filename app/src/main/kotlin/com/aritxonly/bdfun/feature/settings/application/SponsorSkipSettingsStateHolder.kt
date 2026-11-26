package com.aritxonly.bdfun.feature.settings.application

import com.aritxonly.bdfun.core.domain.*
import com.aritxonly.bdfun.core.model.settings.*

/**
 * 空降助手（赞助片段跳过）的四个设置。
 *
 * 单独成类的原因：这是一块完整的功能领域（模式 / 最短时长 / 是否提示 / 类别白名单），
 * 和播放器的主体设置没有耦合；放在 [PlaybackSettingsStateHolder] 里会把它顶过文件行数上限。
 * 接口仍由 [PlaybackSettingsStateHolder] 实现，这里用委托接入，外部读写路径不变。
 */
internal class SponsorSkipSettingsStateHolder(
    prefs: SettingsPreferences,
    values: SettingsValuesStateHolder,
    floats: FloatSettingsWriter,
    codec: SearchHistoryCodec,
) : SettingsGroupStateHolder(prefs, values, floats, codec), SponsorSkipSettings {

    private val sponsorSkipModeState = stateCell("sponsorSkipModeState", prefs.getInt("sponsor_skip_mode", 1))

    private val sponsorMinimumDurationState =
        stateCell("sponsorMinimumDurationState", prefs.getInt("sponsor_min_duration", 0))

    private val sponsorSkipNoticeState = stateCell("sponsorSkipNoticeState", prefs.getBoolean("sponsor_skip_notice", true))

    private val sponsorCategoriesState =
        stateCell("sponsorCategoriesState", prefs.getStringSet("sponsor_categories", null) ?: SPONSOR_CATEGORIES.toSet())

    override var sponsorSkipMode: Int
        get() = sponsorSkipModeState.value.coerceIn(0, 2)
        set(value) {
            sponsorSkipModeState.value = value.coerceIn(0, 2)
            prefs.edit().putInt("sponsor_skip_mode", sponsorSkipModeState.value).apply()
        }

    /** 0 表示不限时长；上限 60 秒只是防脏数据，设置页最大选项是 10 秒。 */
    override var sponsorMinimumDuration: Int
        get() = sponsorMinimumDurationState.value.coerceIn(0, 60)
        set(value) {
            sponsorMinimumDurationState.value = value.coerceIn(0, 60)
            prefs.edit().putInt("sponsor_min_duration", sponsorMinimumDurationState.value).apply()
        }

    override var sponsorSkipNotice: Boolean
        get() = sponsorSkipNoticeState.value
        set(value) {
            sponsorSkipNoticeState.value = value
            prefs.edit().putBoolean("sponsor_skip_notice", value).apply()
        }

    /** 类别白名单只保留已知类别，避免旧版本残留或手写脏值让过滤永远命中。 */
    override var sponsorCategories: Set<String>
        get() = sponsorCategoriesState.value
        set(value) {
            val normalized = value.filterTo(linkedSetOf()) { it in SPONSOR_CATEGORIES }
            sponsorCategoriesState.value = normalized
            prefs.edit().putStringSet("sponsor_categories", normalized).apply()
        }
}