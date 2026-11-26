package com.aritxonly.bdfun.feature.home.application

import com.aritxonly.bdfun.core.common.*
import com.aritxonly.bdfun.core.application.StateHolder
import com.aritxonly.bdfun.core.application.UiState
import com.aritxonly.bdfun.core.domain.*
import com.aritxonly.bdfun.core.model.video.VideoItem
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.sync.withPermit

/**
 * 本轮刷新的结果快照。
 *
 * 这五个字段描述的都是「最近一次刷新发生了什么」：新内容的身份、新增数量、
 * 提示是否还没被忽略、旧内容从哪一条开始、能不能撤销。它们要么同时有效要么同时无效，
 * 分散在 UiState 里容易漏传，所以打包成一个对象。
 */
internal data class HomeRefreshRoll(
    /** 每次刷新成功自增；定位胶囊与分隔条都以它作为本轮刷新的身份。 */
    val key: Int = 0,
    /** 本轮刷新新增了多少条内容，0 表示没有新增。 */
    val newItemCount: Int = 0,
    /** 「新增 N 条内容」提示是否还没被忽略。 */
    val newItemsTipVisible: Boolean = false,
    /** 旧内容在列表中的起始下标；-1 表示本轮刷新没有旧内容。 */
    val oldContentStartIndex: Int = -1,
    /** 撤销刷新胶囊是否可用（刷新成功且拿到了刷新前的快照）。 */
    val undoAvailable: Boolean = false,
)

internal data class HomeFeedUiState(
    val videos: List<VideoItem> = emptyList(),
    val loading: Boolean = true,
    val refreshing: Boolean = false,
    val loadingMore: Boolean = false,
    val error: AppError? = null,
    val loadMoreError: AppError? = null,
    val endReached: Boolean = false,
    val refreshRequest: Int = 0,
    val scrollRevision: Long = 0,
    val filteredPage: Boolean = false,
    val empty: Boolean = false,
    /** 本轮刷新的身份与提示；见 [HomeRefreshRoll]。 */
    val roll: HomeRefreshRoll = HomeRefreshRoll(),
) : UiState

/** 刷新前的列表快照；撤销只恢复刚看到的那一段，不回头重放分页。 */
private data class HomeRefreshUndo(
    val videos: List<VideoItem>,
    val endReached: Boolean,
    val refreshKey: Int,
    val newItemCount: Int,
    val oldContentStartIndex: Int,
)

/** 这是首页状态模板：不可变 UiState + StateFlow，界面事件只调用这里的动作。 */
internal class HomeFeedStateHolder(private val feed: FeedRepository, private val videos: VideoRepository,
    private val optimizer: com.aritxonly.bdfun.core.application.RecommendationOptimizer? = null,
    private val cache: HomeFeedCache? = null, private val requestTimeoutMs: Long = 12_000,
    /** 刷新时是否保留旧推荐；由设置在每次刷新时读取，避免改开关后需要重建控制器。 */
    private val keepOldContent: () -> Boolean = { true },
    /** 是否允许出现「撤销刷新」；关闭后连快照都不留。 */
    private val undoEnabled: () -> Boolean = { true },
) : StateHolder<HomeFeedUiState> {
    private val mutable = MutableStateFlow(HomeFeedUiState())
    override val state: StateFlow<HomeFeedUiState> = mutable.asStateFlow()
    private val firstLoad = Mutex()
    private val exclusions = RecommendationExclusions()
    private var page = 1
    private var started = false
    private var generation = 0L
    private var closed = false
    private var resetOnRefresh = false
    private var undoSnapshot: HomeRefreshUndo? = null
    fun modeChanged() {
        if (closed) return
        generation++
        resetOnRefresh = true
        mutable.update { it.copy(loading = false, refreshing = false, loadingMore = false, endReached = false) }
        if (mutable.value.videos.isEmpty()) requestRefresh()
    }
    fun expose(video: VideoItem, ticket: com.aritxonly.bdfun.core.application.RecommendationTicket) {
        if (!closed) optimizer?.expose(video, ticket)
    }
    fun open(video: VideoItem) { if (!closed) optimizer?.open(video) }
    fun exposureTicket() = optimizer?.ticket()
    private fun current(ticket: Long, mode: com.aritxonly.bdfun.core.application.RecommendationTicket?) =
        ticket == generation && (mode == null || optimizer?.valid(mode) == true)

    fun close() {
        if (closed) return
        closed = true
        generation++
        mutable.update { it.copy(loading = false, refreshing = false, loadingMore = false) }
    }
    private data class Batch(val items: List<VideoItem>, val rawEmpty: Boolean)

    fun requestRefresh() { if (closed) return; mutable.update { it.copy(refreshRequest = it.refreshRequest + 1) } }
    fun dismissRecommendation(video: VideoItem, creator: Boolean = false, category: String? = null) {
        if (closed) return
        exclusions.exclude(video, creator, category)
        mutable.update { it.copy(videos = it.videos.filter(exclusions::accepts)) }
    }

    suspend fun ensureLoaded() = firstLoad.withLock {
        if (closed || started) return@withLock
        val mode = optimizer?.ticket()
        val ticket = ++generation
        mutable.update { it.copy(loading = true, error = null, loadMoreError = null, empty = false) }
        try {
            if (mutable.value.videos.isEmpty()) {
                val saved = (cache?.read() as? Outcome.Success)?.value.orEmpty().filter(exclusions::accepts)
                if (current(ticket, mode) && saved.isNotEmpty()) mutable.update { it.copy(videos = saved, loading = false) }
            }
            when (val result = recommendations(1)) {
                is Outcome.Failure -> if (current(ticket, mode)) mutable.update { it.copy(error = result.error) }
                is Outcome.Success -> if (current(ticket, mode)) {
                    val distinct = result.value.items.distinctBy { it.bvid }
                    if (distinct.isNotEmpty()) {
                        val old = mutable.value.videos
                        val same = old.size == distinct.size && old.indices.all { old[it].bvid == distinct[it].bvid }
                        page = 1
                        started = true
                        mutable.update { it.copy(videos = if (same) old else distinct, endReached = false) }
                        cache?.write(distinct)
                    } else mutable.update { it.copy(empty = it.videos.isEmpty()) }
                }
            }
        } finally {
            if (current(ticket, mode)) mutable.update { it.copy(loading = false, refreshing = false) }
        }
    }

    suspend fun refresh() {
        if (closed || mutable.value.refreshing) return
        val before = mutable.value
        // 快照必须在发请求前取：请求期间列表可能被滑出、删除或继续分页。
        undoSnapshot = if (undoEnabled() && before.videos.isNotEmpty()) {
            HomeRefreshUndo(
                videos = before.videos.take(HOME_REFRESH_UNDO_MAX_ITEMS),
                endReached = before.endReached,
                refreshKey = before.roll.key,
                newItemCount = before.roll.newItemCount,
                oldContentStartIndex = before.roll.oldContentStartIndex,
            )
        } else null
        val mode = optimizer?.ticket()
        val ticket = ++generation
        mutable.update { it.copy(refreshing = true, loadingMore = false, error = null, loadMoreError = null, filteredPage = false, empty = false) }
        try {
            when (val result = recommendations(1)) {
                is Outcome.Failure -> if (current(ticket, mode)) mutable.update { it.copy(error = result.error) }
                is Outcome.Success -> if (current(ticket, mode)) {
                    val items = result.value.items
                    if (items.isNotEmpty()) {
                        started = true
                        mutable.update { old ->
                            if (resetOnRefresh) page = 1
                            val keepOld = !resetOnRefresh && keepOldContent()
                            val retained = if (keepOld) {
                                old.videos.take(resolveHomeRefreshKeptItemCount(old.videos.size))
                            } else {
                                emptyList()
                            }
                            val known = retained.mapTo(HashSet()) { it.bvid }
                            val fresh = items.filter { known.add(it.bvid) }
                            resetOnRefresh = false
                            old.copy(
                                videos = fresh + retained,
                                endReached = if (fresh.isNotEmpty()) false else old.endReached,
                                scrollRevision = old.scrollRevision + 1,
                                roll = HomeRefreshRoll(
                                    key = old.roll.key + 1,
                                    newItemCount = fresh.size,
                                    newItemsTipVisible = shouldShowHomeNewItemsTip(fresh.size),
                                    oldContentStartIndex = if (retained.isEmpty()) -1 else fresh.size,
                                    undoAvailable = undoSnapshot != null,
                                ),
                            )
                        }
                        cache?.write(items)
                    } else mutable.update { it.copy(empty = it.videos.isEmpty()) }
                }
            }
        } finally {
            if (current(ticket, mode)) mutable.update { it.copy(refreshing = false) }
        }
    }

    /** 恢复刷新前的列表；快照只保留一次，撤销后再点无效。 */
    fun undoRefresh() {
        if (closed) return
        val snapshot = undoSnapshot ?: return
        undoSnapshot = null
        mutable.update { old ->
            old.copy(
                videos = snapshot.videos,
                endReached = snapshot.endReached,
                roll = HomeRefreshRoll(
                    key = snapshot.refreshKey,
                    newItemCount = snapshot.newItemCount,
                    newItemsTipVisible = false,
                    oldContentStartIndex = snapshot.oldContentStartIndex,
                    undoAvailable = false,
                ),
                scrollRevision = old.scrollRevision + 1,
            )
        }
    }

    fun dismissUndo() {
        if (closed || !mutable.value.roll.undoAvailable) return
        undoSnapshot = null
        mutable.update { it.copy(roll = it.roll.copy(undoAvailable = false)) }
    }

    fun dismissNewItemsTip() {
        if (closed || !mutable.value.roll.newItemsTipVisible) return
        mutable.update { it.copy(roll = it.roll.copy(newItemsTipVisible = false)) }
    }

    suspend fun loadMore() {
        val current = mutable.value
        if (closed || current.loadingMore || current.endReached || current.loading || current.refreshing) return
        val mode = optimizer?.ticket()
        val ticket = generation
        var next = page + 1
        mutable.update { it.copy(loadingMore = true, loadMoreError = null, filteredPage = false) }
        try {
            var result = recommendations(next)
            var extra = 0
            val known = mutable.value.videos.mapTo(HashSet()) { it.bvid }
            while (optimizer?.state?.value?.enabled == true && current(ticket, mode) &&
                result is Outcome.Success && result.value.items.none { it.bvid !in known } && extra < 2) {
                extra++
                next++
                result = recommendations(next)
            }
            when (result) {
                is Outcome.Failure -> if (current(ticket, mode)) mutable.update { it.copy(loadMoreError = result.error) }
                is Outcome.Success -> if (current(ticket, mode)) {
                    page = next
                    mutable.update { old ->
                        val known = old.videos.mapTo(HashSet()) { it.bvid }
                        val fresh = result.value.items.filter { known.add(it.bvid) }
                        val ended = optimizer?.state?.value?.enabled != true &&
                            (result.value.rawEmpty || (fresh.isEmpty() && exclusions.isEmpty))
                        old.copy(videos = old.videos + fresh, endReached = ended, filteredPage = fresh.isEmpty() && !ended)
                    }
                }
            }
        } finally {
            if (current(ticket, mode)) mutable.update { it.copy(loadingMore = false) }
        }
    }

    private suspend fun recommendations(page: Int): Outcome<Batch> =
        kotlinx.coroutines.withTimeoutOrNull(requestTimeoutMs) { loadRecommendations(page) }
            ?: Outcome.Failure(AppError.Network("网络较慢，请稍后重试"))

    private suspend fun loadRecommendations(page: Int): Outcome<Batch> {
        val items = when (val result = feed.recommend(page)) {
            is Outcome.Failure -> return result
            is Outcome.Success -> result.value
        }
        val filtered = coroutineScope {
            val limit = Semaphore(3)
            items.map { video -> async {
                if (exclusions.hasCategories && video.categoryName.isBlank()) {
                    limit.withPermit {
                        when (val detail = videos.videoDetail(video.bvid)) {
                            is Outcome.Success -> video.copy(
                                extras = video.extras.copy(
                                    categoryName = detail.value.tname,
                                ),
                            )
                            is Outcome.Failure -> video
                        }
                    }
                } else video
            } }.map { it.await() }.filter(exclusions::accepts)
        }
        val mode = optimizer?.ticket()
        val ranked = if (mode == null) filtered else kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Default) {
            optimizer.rank(filtered, mutable.value.videos, mode)
        }
        return Outcome.Success(Batch(ranked, items.isEmpty()))
    }
}
