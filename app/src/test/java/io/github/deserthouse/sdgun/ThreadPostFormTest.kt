package io.github.deserthouse.sdgun

import io.github.deserthouse.sdgun.feature.thread.ThreadPostForm
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * U3-c 发帖表单契约（jadx ThreadPostActivity.toPost 同构）：
 * 校验规则与 THREAD_POST 表单键序。
 */
class ThreadPostFormTest {

    @Test
    fun `validate 空标题空正文缺板块三分支`() {
        assertNotNull(
            ThreadPostForm("", "c", 1L, 2L).validate()
        )
        assertNotNull(
            ThreadPostForm("t", "  ", 1L, 2L).validate()
        )
        assertNotNull(
            ThreadPostForm("t", "c", 0L, 2L).validate()
        )
        assertNull(ThreadPostForm("t", "c", 1L, 2L).validate())
    }

    @Test
    fun `params 与 jadx toPost 键集同构且标题去尾空白`() {
        val p = ThreadPostForm("  标题  ", "正文", 153L, 153L).toParams()
        // jadx：title/content/fid/circle_id（auth/users/vest 本版不带——同盾未复刻）
        assertEquals(setOf("title", "content", "fid", "circle_id"), p.keys)
        assertEquals("标题", p["title"])
        assertEquals("正文", p["content"])
        assertEquals("153", p["fid"])
        assertEquals("153", p["circle_id"])
    }
}
