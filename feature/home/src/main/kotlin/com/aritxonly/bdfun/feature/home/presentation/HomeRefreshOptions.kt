package com.aritxonly.bdfun.feature.home.presentation

import androidx.compose.runtime.Immutable

/**
 * 首页刷新相关的开关与胶囊位置。
 * 四个开关分别对应设置「首页、动态与搜索 → 首页推荐」里的四行，
 * 位置是拖动松手后记忆的归一化坐标。
 */
@Immutable
data class HomeRefreshOptions(
    val keepOldContent: Boolean = true,
    val locatorEnabled: Boolean = true,
    val locatorDurationSeconds: Int = 5,
    val locatorX: Float = 0.94f,
    val locatorY: Float = 0.38f,
    val undoEnabled: Boolean = true,
)
