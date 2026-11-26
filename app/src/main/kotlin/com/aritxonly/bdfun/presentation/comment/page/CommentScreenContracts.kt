package com.aritxonly.bdfun.ui

import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

internal data class CommentScreenRequest(
    val aid: Long, val upMid: Long, val title: String, val bvid: String = "",
    /** 动态用 type=17 + id_str，视频用 type=1 + aid。 */
    val commentOid: String = aid.toString(), val commentType: Int = 1,
    val focusRootRpid: Long = 0L, val focusTargetRpid: Long = 0L,
)
/**
 * 评论列表的 inset 与页头策略。
 *
 * 这三个开关描述的是「评论区坐在什么样的容器里」，永远成套出现，
 * 单独传容易漏一个导致顶部留白算错，所以打包。
 */
internal data class CommentInsetPolicy(
    /** 评论区作为独立可见页面时注册「回到顶部」；视频详情双栏/评论 tab 由调用方按可见性传。 */
    val scrollTop: Boolean = false,
    /** 动态详情这类沉浸页：列表滚到状态栏后面，而不是被状态栏高度截断。 */
    val immersiveStatusBar: Boolean = false,
    /** 容器已经在状态栏下面（竖屏评论面板）：immersive 时也不再给首个条目补状态栏高度。 */
    val skipStatusInset: Boolean = false,
)

/**
 * 评论区的容器形态。
 *
 * [insets] 之外的布尔开关决定「这是不是一个被嵌进别处的评论列表」，
 * 屏幕上表现为返回键归属、加载态、标题留白和列表分隔线，必须一起变。
 */
internal data class CommentScreenLayout(
    val bare: Boolean = false, val topInset: Dp = 0.dp, val bottomPadding: Dp = 96.dp,
    val trackBackProgress: Boolean = false, val showLoading: Boolean = true,
    val threadPlayerBottom: Dp? = null, val threadPaneWidth: Dp? = null,
    val playerFullscreen: Boolean = false, val flatList: Boolean = false,
    /** 视频详情竖屏主评论区：收紧缩进播放器下方的标题留白（横屏分栏保持原样）。 */
    val compactHeaderSpacing: Boolean = false,
    /** inset 与页头策略；默认值等同于「独立整页评论区」。 */
    val insets: CommentInsetPolicy = CommentInsetPolicy(),
)
internal typealias CommentScreenHeader = LazyListScope.() -> Unit


