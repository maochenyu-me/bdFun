package com.aritxonly.bdfun.infra.bili

import android.content.Context
import android.content.SharedPreferences
import okhttp3.Cookie
import okhttp3.CookieJar
import okhttp3.HttpUrl
import java.util.concurrent.ConcurrentHashMap

class BiliCookieJar(context: Context) : CookieJar {
    private val prefs: SharedPreferences = context.getSharedPreferences("bili_session", Context.MODE_PRIVATE)
    private val cookies = ConcurrentHashMap<String, Cookie>()

    init {
        cookies.putAll(parse(prefs.getString(KEY_COOKIES, null)))
    }

    override fun loadForRequest(url: HttpUrl): List<Cookie> {
        if (!url.host.endsWith("bilibili.com")) return emptyList()
        val now = System.currentTimeMillis()
        return cookies.values.filter { cookie ->
            (cookie.expiresAt == Long.MAX_VALUE || cookie.expiresAt > now) && cookie.matches(url)
        }
    }

    override fun saveFromResponse(url: HttpUrl, cookies: List<Cookie>) {
        if (cookies.isEmpty()) return
        var changed = false
        cookies.forEach {
            if (it.name.isNotBlank()) {
                // B 站几乎每个响应都下发 Cookie；值没变就别再整份序列化写盘。
                val previous = this.cookies[it.name]
                if (previous == null || previous.value != it.value || previous.expiresAt != it.expiresAt) {
                    this.cookies[it.name] = it
                    changed = true
                }
            }
        }
        if (changed) persist()
    }

    fun put(name: String, value: String, domain: String = "bilibili.com") {
        if (name.isBlank()) return
        if (value.isNotBlank() && cookies[name]?.value == value) return
        cookies[name] = Cookie.Builder()
            .name(name)
            .value(value)
            .domain(domain)
            .path("/")
            .build()
        persist()
    }

    fun value(name: String): String = cookies[name]?.value.orEmpty()

    /** 拼成 Cookie 请求头；播放器直接拉流时也要带，否则部分 CDN 会 403。 */
    fun header(): String = cookies.values.joinToString("; ") { "${it.name}=${it.value}" }

    val csrf: String get() = value("bili_jct")

    val hasSession: Boolean get() = value("SESSDATA").isNotBlank()

    val mid: Long get() = value("DedeUserID").toLongOrNull() ?: 0L

    fun clear() {
        cookies.clear()
        prefs.edit().remove(KEY_COOKIES).apply()
    }

    /** 导出全部 cookie，格式与持久化一致（`name\tvalue\tdomain\tpath\texpiresAt`，会话 cookie 为 0）。 */
    fun snapshot(): String = serialize()

    /**
     * 用备份内容整体替换当前 cookie（导入的是另一台设备的登录态，替换比合并安全）。
     * 一条都解析不出来时返回 false，且不动现有状态。
     */
    fun restore(serialized: String): Boolean {
        val restored = parse(serialized)
        if (restored.isEmpty()) return false
        cookies.clear()
        cookies.putAll(restored)
        persist()
        return true
    }

    private fun parse(data: String?): Map<String, Cookie> {
        val result = LinkedHashMap<String, Cookie>()
        data?.lineSequence()?.forEach { line ->
            val parts = line.split('\t')
            if (parts.size >= 5) {
                runCatching {
                    val builder = Cookie.Builder()
                        .name(parts[0])
                        .value(parts[1])
                        .domain(parts[2].removePrefix(".").ifBlank { "bilibili.com" })
                        .path(parts[3].ifBlank { "/" })
                    if (parts[4].toLongOrNull()?.let { it > 0 } == true) {
                        builder.expiresAt(parts[4].toLong())
                    }
                    val cookie = builder.build()
                    if (cookie.name.isNotBlank()) result[cookie.name] = cookie
                }
            }
        }
        return result
    }

    private fun serialize(): String = cookies.values.joinToString("\n") {
        listOf(
            it.name,
            it.value,
            it.domain,
            it.path,
            if (it.expiresAt == Long.MAX_VALUE) "0" else it.expiresAt.toString(),
        ).joinToString("\t")
    }

    private fun persist() {
        prefs.edit().putString(KEY_COOKIES, serialize()).apply()
    }

    private companion object {
        const val KEY_COOKIES = "cookies_v1"
    }
}
