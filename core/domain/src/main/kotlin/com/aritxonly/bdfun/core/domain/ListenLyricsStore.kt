package com.aritxonly.bdfun.core.domain

/** 一个稿件对应的一份本地 lrc 原文和它的校准偏移。 */
data class CachedListenLyrics(val lrc: String, val offsetMs: Long)

/**
 * 本地导入歌词的持久化。
 *
 * 按 cid 存而不是全局存一份：合集连播时下一集不能顶着上一集的歌词开口。
 * 歌词样式是全局偏好，不跟视频走。
 */
interface ListenLyricsStore {

    /** 读某集的 lrc 与偏移；没导入过返回 null。 */
    suspend fun read(cid: Long): CachedListenLyrics?

    /** 写入某集的 lrc 原文；同一集重复导入覆盖旧的。 */
    suspend fun save(cid: Long, lrc: String)

    /** 单独写偏移：校准条每点一下都要落一次，不必带上整份歌词。 */
    suspend fun saveOffset(cid: Long, offsetMs: Long)

    /** 清掉某集的本地歌词与偏移。 */
    suspend fun clear(cid: Long)

    /** 歌词呈现方式的枚举名；没存过返回 null，由调用方用默认值。 */
    suspend fun readStyle(): String?

    suspend fun saveStyle(style: String)

    companion object {
        /** 不落盘的实现：单测和未装配时用，行为等同「从没导入过」。 */
        val None: ListenLyricsStore = object : ListenLyricsStore {
            override suspend fun read(cid: Long): CachedListenLyrics? = null
            override suspend fun save(cid: Long, lrc: String) = Unit
            override suspend fun saveOffset(cid: Long, offsetMs: Long) = Unit
            override suspend fun clear(cid: Long) = Unit
            override suspend fun readStyle(): String? = null
            override suspend fun saveStyle(style: String) = Unit
        }
    }
}
