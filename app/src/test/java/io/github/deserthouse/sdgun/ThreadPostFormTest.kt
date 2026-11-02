package io.github.deserthouse.sdgun

import io.github.deserthouse.sdgun.feature.thread.ThreadPostForm
import io.github.deserthouse.sdgun.feature.thread.UploadTaskItem
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

    // ── R2/A3-3：附件序列化（合成关闭口径）──
    @org.junit.Test
    fun `UPLOADED 附件序列化进正文尾部`() {
        val U = io.github.deserthouse.sdgun.feature.thread.UploadState.UPLOADED
        val tasks = listOf(
            UploadTaskItem("u1", "kit.zip", U, url = "uploaded://kit.zip"),
            UploadTaskItem("u2", "failed.png", io.github.deserthouse.sdgun.feature.thread.UploadState.FAILED),
            UploadTaskItem("u3", "running.png", io.github.deserthouse.sdgun.feature.thread.UploadState.RUNNING),
        )
        val out = io.github.deserthouse.sdgun.feature.thread.UploadQueueLogic
            .serializeUploadedInto("正文", tasks)
        org.junit.Assert.assertTrue(out.startsWith("正文"))
        org.junit.Assert.assertTrue(out.contains("[附件] kit.zip uploaded://kit.zip"))
        org.junit.Assert.assertFalse("失败/在途不得序列化", out.contains("failed.png"))
        org.junit.Assert.assertFalse(out.contains("running.png"))
    }

    @org.junit.Test
    fun `无 UPLOADED 资源时原样返回`() {
        val tasks = listOf(
            UploadTaskItem("u1", "a.png", io.github.deserthouse.sdgun.feature.thread.UploadState.FAILED))
        val out = io.github.deserthouse.sdgun.feature.thread.UploadQueueLogic
            .serializeUploadedInto("正文", tasks)
        org.junit.Assert.assertEquals("正文", out)
    }

    @org.junit.Test
    fun `VOTE 请求为 GET 且携带 content_id option_id`() {
        val req = io.github.deserthouse.sdgun.core.mutation.MutationRequest(
            kind = io.github.deserthouse.sdgun.core.mutation.MutationKind.VOTE,
            params = mapOf("content_id" to "123", "option_id" to "7"),
            token = "t", method = "GET",
        )
        org.junit.Assert.assertEquals("GET", req.method)
        org.junit.Assert.assertEquals(
            "https://mag1.sdgun.net/mag/circle/v1/show/addVote", req.kind.url)
    }
}
