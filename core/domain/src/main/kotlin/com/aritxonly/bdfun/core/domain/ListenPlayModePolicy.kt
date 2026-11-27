package com.aritxonly.bdfun.core.domain

/**
 * 随机播放的下一项选择。
 *
 * 只做「排除当前项」的均匀采样：真正洗牌需要维护一个已播集合，
 * 而播放列表会随时长、分 P 变化，维护成本高于收益。
 * 排除当前项已经能避免「随机到正在播的这首」这种最刺眼的问题。
 *
 * @param randomValue [0,1) 的随机数，由调用方提供，便于纯函数测试。
 * @return 选中的下标；列表不足两项时返回 -1（没有可跳的目标）。
 */
fun shuffleNextIndex(currentIndex: Int, size: Int, randomValue: Float): Int {
    if (size <= 1) return -1
    if (currentIndex !in 0 until size) return (randomValue.coerceIn(0f, 0.9999f) * size).toInt()
    val slots = size - 1
    val offset = (randomValue.coerceIn(0f, 0.9999f) * slots).toInt().coerceIn(0, slots - 1)
    // 把「除当前项之外」的序列映射回真实下标：落在当前项之后整体后移一位。
    return if (offset >= currentIndex) offset + 1 else offset
}

/**
 * 听视频的播放模式按钮：顺序 → 单曲 → 列表 → 顺序。
 * 与视频播放器里的 loopMode 取值一致（0 不循环 / 1 单集 / 2 列表）。
 */
fun nextPlayMode(loopMode: Int): Int = when (loopMode) {
    0 -> 1
    1 -> 2
    else -> 0
}

fun playModeLabel(loopMode: Int): String = when (loopMode) {
    1 -> "单曲循环"
    2 -> "列表循环"
    else -> "顺序播放"
}

/**
 * 随机与播放模式互斥，和 BiliPai 一致：
 * 打开随机时把循环模式收回「顺序」；再点一次关闭随机时循环模式仍是顺序。
 * 点播放模式按钮时一定先关随机。
 *
 * @param shuffleOn 当前是否正在随机，不是目标值。
 */
fun toggleShuffle(loopMode: Int, shuffleOn: Boolean): Pair<Int, Boolean> =
    if (shuffleOn) loopMode to false else 0 to true

fun playModeAfterModeToggle(loopMode: Int, shuffleOn: Boolean): Pair<Int, Boolean> =
    nextPlayMode(loopMode) to false

/**
 * 播放模式的一个可选项。随机与循环互斥，所以四档并列放在一张表里：
 * 前三档只改 loopMode，随机独占一档并把 loopMode 收回顺序。
 */
data class PlayModeOption(
    val loopMode: Int,
    val shuffle: Boolean,
    val label: String,
    val detail: String,
)

/** 面板里直接遍历的顺序；[currentPlayMode] 靠这张表判定选中项。 */
val playModeOptions: List<PlayModeOption> = listOf(
    PlayModeOption(loopMode = 0, shuffle = false, label = "顺序播放", detail = "播完即停"),
    PlayModeOption(loopMode = 1, shuffle = false, label = "单曲循环", detail = "单集反复播放"),
    PlayModeOption(loopMode = 2, shuffle = false, label = "列表循环", detail = "播完从头再来"),
    PlayModeOption(loopMode = 0, shuffle = true, label = "随机播放", detail = "列表内随机跳转"),
)

/** 当前生效的档位；loopMode 出现意料外的值时回落成顺序播放，不抛异常。 */
fun currentPlayMode(loopMode: Int, shuffleOn: Boolean): PlayModeOption =
    if (shuffleOn) {
        playModeOptions.last()
    } else {
        playModeOptions.firstOrNull { it.loopMode == loopMode && !it.shuffle } ?: playModeOptions.first()
    }

/** 菜单右侧小字：随机开着时不能还写「顺序播放」。 */
fun playModeValueLabel(loopMode: Int, shuffleOn: Boolean): String =
    currentPlayMode(loopMode, shuffleOn).label