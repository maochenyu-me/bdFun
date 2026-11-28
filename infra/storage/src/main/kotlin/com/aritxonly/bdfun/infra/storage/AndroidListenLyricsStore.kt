package com.aritxonly.bdfun.infra.storage

import android.content.Context
import com.aritxonly.bdfun.core.domain.CachedListenLyrics
import com.aritxonly.bdfun.core.domain.ListenLyricsStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/**
 * 本地 lrc 存应用私有目录，偏移与样式存 SharedPreferences。
 *
 * 不存 SAF 的 uri：用户挪走或删掉文件、或系统回收授权后就读不出来了，
 * 表现出来就是「明明持久化了却打不开」。lrc 通常几 KB，留原文副本代价可以忽略。
 */
class AndroidListenLyricsStore(context: Context) : ListenLyricsStore {
    private val prefs = context.applicationContext
        .getSharedPreferences("listen_lyrics", Context.MODE_PRIVATE)
    private val dir = File(context.applicationContext.filesDir, "listen_lyrics")

    override suspend fun read(cid: Long): CachedListenLyrics? = withContext(Dispatchers.IO) {
        val lrc = runCatching { file(cid).takeIf { it.isFile }?.readText() }.getOrNull()
        if (lrc.isNullOrBlank()) null else CachedListenLyrics(lrc, prefs.getLong(offsetKey(cid), 0L))
    }

    override suspend fun save(cid: Long, lrc: String) = withContext(Dispatchers.IO) {
        runCatching {
            dir.mkdirs()
            file(cid).writeText(lrc)
            trim()
        }
        Unit
    }

    // 校准条上每点一下都要落一次，走 apply() 异步写，不阻塞点击。
    override suspend fun saveOffset(cid: Long, offsetMs: Long) {
        prefs.edit().putLong(offsetKey(cid), offsetMs).apply()
    }

    override suspend fun clear(cid: Long) = withContext(Dispatchers.IO) {
        runCatching { file(cid).delete() }
        prefs.edit().remove(offsetKey(cid)).apply()
        Unit
    }

    override suspend fun readStyle(): String? = prefs.getString(KeyStyle, null)

    override suspend fun saveStyle(style: String) {
        prefs.edit().putString(KeyStyle, style).apply()
    }

    private fun file(cid: Long) = File(dir, "$cid.lrc")

    private fun offsetKey(cid: Long) = "offset_$cid"

    /** 每集一份原文，用久了会攒起来：超过上限按最后修改时间删最旧的。 */
    private fun trim() {
        trimLyricsFiles(dir, MaxCachedLyrics)
    }

    private companion object {
        const val KeyStyle = "style"
        const val MaxCachedLyrics = 40
    }
}

/** 只保留最新的 [keep] 份歌词，其余删掉。抽出来是为了能脱离 Android 跑单测。 */
internal fun trimLyricsFiles(dir: File, keep: Int) {
    val files = dir.listFiles()?.sortedBy { it.lastModified() } ?: return
    if (files.size <= keep) return
    files.take(files.size - keep).forEach { runCatching { it.delete() } }
}
