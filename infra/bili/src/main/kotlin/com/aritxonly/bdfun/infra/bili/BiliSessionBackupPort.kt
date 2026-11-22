package com.aritxonly.bdfun.infra.bili

import com.aritxonly.bdfun.core.domain.SessionBackupPort

/**
 * 登录态备份端口：直接读写 [BiliCookieJar] 的 cookies_v1 内容。
 *
 * 备份文件里放的就是可复用的登录凭据（SESSDATA 等），加密与警告都在上层负责；
 * 这里只保证「没登录时不导出」和「导入是整体替换」。
 */
class BiliSessionBackupPort(private val jar: BiliCookieJar) : SessionBackupPort {
    override fun exportCookies(): String? = jar.snapshot().takeIf { jar.hasSession }

    override fun importCookies(serialized: String): Boolean = jar.restore(serialized)
}
