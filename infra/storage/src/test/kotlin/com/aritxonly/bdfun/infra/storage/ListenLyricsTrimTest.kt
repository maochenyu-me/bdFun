package com.aritxonly.bdfun.infra.storage

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class ListenLyricsTrimTest {
    @get:Rule val temporary = TemporaryFolder()

    @Test fun keepsNewestAndDropsOldest() {
        val dir = temporary.newFolder("listen_lyrics")
        // 显式写 mtime：连写几个文件时毫秒级时间戳可能撞在一起，排序就不确定了。
        (1..5).forEach { index ->
            File(dir, "$index.lrc").apply {
                writeText("第 $index 份")
                setLastModified(index * 1_000L)
            }
        }
        trimLyricsFiles(dir, 3)
        assertEquals(listOf("3.lrc", "4.lrc", "5.lrc"), dir.listFiles()!!.map { it.name }.sorted())
        assertFalse(File(dir, "1.lrc").exists())
        assertTrue(File(dir, "5.lrc").exists())
    }

    @Test fun underLimitKeepsEverything() {
        val dir = temporary.newFolder("listen_lyrics")
        (1..2).forEach { File(dir, "$it.lrc").writeText("第 $it 份") }
        trimLyricsFiles(dir, 3)
        assertEquals(2, dir.listFiles()!!.size)
    }

    /** 目录还不存在时不能抛：第一次导入之前就是这种状态。 */
    @Test fun missingDirectoryIsIgnored() {
        trimLyricsFiles(File(temporary.root, "absent"), 3)
    }
}
