package com.aritxonly.bdfun.ui

/** 页面瞬时投影与暂停注册端口；实现继续使用原页面StateFlow与暂停注册表。 */
interface PlayerPageHost {
    val aid: Long
    val cid: Long
    var danmakuProgressMs: Long
    fun requestAutoNext()
    var controlsVisible: Boolean
    var danmakuPanelOpen: Boolean
    val danmakuInteract: Int
    var orientationLocked: Boolean

    /** 竖屏视频标记：Activity 侧据此放弃传感器转屏，画中画也用竖屏比例。 */
    var portraitVideo: Boolean
    fun registerPause(owner: Any?, hook: (Boolean) -> Unit)
    fun unregisterPause(hook: (Boolean) -> Unit)
}

class PlayerDiagnostics(val log: (String, String) -> Unit, val toast: (String) -> Unit)
