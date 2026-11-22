package com.aritxonly.bdfun.core.domain

/** 刷新成功后「撤销刷新」胶囊的可撤销时长。 */
const val HOME_REFRESH_UNDO_TIMEOUT_MS = 5_000L

/** 旧内容超过这个条数时只保留最新的若干条，避免反复刷新让列表无限膨胀。 */
const val HOME_REFRESH_KEEP_OLD_THRESHOLD = 200

/** 超过阈值时保留的旧内容条数。 */
const val HOME_REFRESH_KEEP_OLD_COUNT = 50

/** 撤销快照最多能恢复的旧条目数：撤销只负责找回刚看到的那一段，不是完整分页回滚。 */
const val HOME_REFRESH_UNDO_MAX_ITEMS = 20

/** 刷新后实际保留多少条旧内容；旧内容过多时只留最新的一段。 */
fun resolveHomeRefreshKeptItemCount(
    oldCount: Int,
    threshold: Int = HOME_REFRESH_KEEP_OLD_THRESHOLD,
    keepCount: Int = HOME_REFRESH_KEEP_OLD_COUNT,
): Int = if (oldCount > threshold) keepCount else oldCount

/** 本轮刷新是否产生了「新增内容」提示：只有真正插入了新条目的那一轮才提示。 */
fun shouldShowHomeNewItemsTip(newItemCount: Int): Boolean = newItemCount > 0

/** 每次刷新只有一个 refreshKey，重复的刷新事件不能重复触发滚动/提示。 */
fun shouldHandleHomeRefreshEvent(refreshKey: Int, handledKey: Int): Boolean =
    refreshKey > 0 && refreshKey > handledKey

/**
 * 「上次刷新到这里」分隔条：保留旧推荐与上次刷新提示是两个独立开关，
 * 任一关闭都不再显示分隔条；本轮刷新必须真的产生了旧内容（下标 > 0）才有分隔点。
 */
fun shouldShowHomeOldContentDivider(
    keepOldContent: Boolean,
    locatorEnabled: Boolean,
    refreshKey: Int,
    revealedRefreshKey: Int,
    oldContentStartIndex: Int,
): Boolean = keepOldContent && locatorEnabled && refreshKey > 0 &&
    revealedRefreshKey == refreshKey && oldContentStartIndex > 0

/** 定位胶囊的停留时长；0 表示不自动收起。负数按 0 处理，避免出现负数延时。 */
fun resolveHomeRefreshLocatorDelayMillis(durationSeconds: Int): Long =
    if (durationSeconds <= 0) 0L else durationSeconds * 1_000L

/** 刷新后网格里至少要留下这么多张新增卡片，轮播不能把它们全部吃掉。 */
const val HOME_HERO_MIN_NEW_CARDS = 6

/**
 * 轮播本轮最多能占几张。轮播是从列表最前面挑片、挑完又从网格里排除的，
 * 所以刷新后必须给新增内容留够网格位，剩下的才归轮播；没有「新增段」（freshCount <= 0，
 * 例如首次进入）时轮播照常取满。返回 0 表示这一轮不显示轮播。
 */
fun resolveHomeHeroItemLimit(
    freshCount: Int,
    maxItems: Int,
    minNewCards: Int = HOME_HERO_MIN_NEW_CARDS,
): Int = if (freshCount <= 0) maxItems else (freshCount - minNewCards).coerceIn(0, maxItems)
