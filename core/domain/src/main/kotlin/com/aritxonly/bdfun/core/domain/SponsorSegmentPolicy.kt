package com.aritxonly.bdfun.core.domain

import com.aritxonly.bdfun.core.model.video.SponsorSegment

/**
 * 设置页「片段类别」的固定顺序：索引 JSON 里的 8 个开关与这里一一对应，
 * 顺序变化会改变页面排列，也决定默认白名单。
 */
val SPONSOR_CATEGORIES = listOf(
    "sponsor",
    "selfpromo",
    "intro",
    "outro",
    "interaction",
    "preview",
    "filler",
    "music_offtopic",
)

/** 空降助手的生效参数：跳过方式、参与跳过的类别、最短片段时长、是否弹跳过提示。 */
data class SponsorPreferences(
    val skipMode: Int = 1,
    val categories: Set<String> = SPONSOR_CATEGORIES.toSet(),
    val minimumDurationSeconds: Int = 0,
    val noticeEnabled: Boolean = true,
) {
    val action: SponsorSkipAction get() = sponsorSkipAction(skipMode)
}

/** 沿用接口顺序、左闭右开区间；关闭过的片段在本视频内不再提示。 */
fun activeSponsorSegmentAt(
    segments: List<SponsorSegment>,
    positionMs: Long,
    dismissed: Set<String>,
    preferences: SponsorPreferences? = null,
): SponsorSegment? =
    segments.firstOrNull { segment ->
        segment.uuid !in dismissed && positionMs >= segment.startMs && positionMs < segment.endMs &&
            (preferences == null || sponsorSegmentSkippable(segment, preferences))
    }

/**
 * 类别与最短时长在这里就过滤掉，避免出现「提示点了却没反应」；
 * mute / poi / full 这类非 skip 片段交回播放器处理，空降助手只负责跳。
 */
fun sponsorSegmentSkippable(segment: SponsorSegment, preferences: SponsorPreferences): Boolean =
    segment.category in preferences.categories &&
        (segment.actionType.isEmpty() || segment.actionType == "skip") &&
        segment.endMs - segment.startMs >= preferences.minimumDurationSeconds * 1000L

/** 设置字段 → 播放器策略；类别在设置页是 8 个开关，落盘是一组集合。 */
fun PlaybackSettings.sponsorPreferences(): SponsorPreferences = SponsorPreferences(
    skipMode = sponsorSkipMode,
    categories = sponsorCategories,
    minimumDurationSeconds = sponsorMinimumDuration,
    noticeEnabled = sponsorSkipNotice,
)

/** 空降助手模式：0 关闭 / 1 提示 / 2 自动跳过；旧布尔配置迁移后只会落在 0 或 1。 */
enum class SponsorSkipAction { Off, Prompt, Auto }

fun sponsorSkipAction(mode: Int): SponsorSkipAction = when (mode) {
    0 -> SponsorSkipAction.Off
    2 -> SponsorSkipAction.Auto
    else -> SponsorSkipAction.Prompt
}
