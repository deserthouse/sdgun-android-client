package io.github.deserthouse.sdgun

import io.github.deserthouse.sdgun.data.repository.ThreadRepository
import io.github.deserthouse.sdgun.feature.thread.FakeUploadTransport
import io.github.deserthouse.sdgun.feature.thread.HonestUnavailableUpload
import io.github.deserthouse.sdgun.feature.thread.HttpUploadTransport
import io.github.deserthouse.sdgun.feature.thread.ThreadPostForm
import io.github.deserthouse.sdgun.feature.thread.UploadMultipart
import io.github.deserthouse.sdgun.feature.thread.UploadOutcome
import io.github.deserthouse.sdgun.feature.thread.UploadQueueLogic
import io.github.deserthouse.sdgun.feature.thread.UploadState
import io.github.deserthouse.sdgun.feature.thread.UploadTaskItem
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Q3 投稿链常驻回归（合成关闭制）：
 * column-info 解析（circle_id 服务端换取+分类 types）/ multipart 体构造（官方字段形态）/
 * 上传响应判定 / runTask 收尾语义 / threadAdd 成功定位 tid 解析 / 表单 type_id 贯通。
 * jadx 实据：ThreadPostActivity.toNet（GET fid→data.circle_id+types）、LocalFileUpload
 * （multipart type=share/filename=imgs/imgs=文件）、API.java uploadPicNew。
 */
class Q3PostFlowTest {

    // ── column-info 解析 ──

    @Test
    fun `C1 column-info 合法信封解析出 circle_id 与 types`() {
        val info = ThreadRepository.parsePostColumnInfo(
            """{"success":true,"data":{"circle_id":99,"types":[{"id":3,"name":"评测"},{"id":7,"name":"求购"}]}}""",
        )
        assertEquals(99L, info?.circleId)
        assertEquals(listOf(3 to "评测", 7 to "求购"), info?.types)
    }

    @Test
    fun `C2 circle_id 缺失或非正即失败（不回退 fid 冒充）`() {
        assertNull(ThreadRepository.parsePostColumnInfo("""{"success":true,"data":{}}"""))
        assertNull(ThreadRepository.parsePostColumnInfo("""{"success":true,"data":{"circle_id":0,"types":[]}}"""))
    }

    @Test
    fun `C3 拒绝信封与畸形输入为 null 不假称空`() {
        assertNull(ThreadRepository.parsePostColumnInfo("""{"success":false,"msg":"denied"}"""))
        assertNull(ThreadRepository.parsePostColumnInfo("<html>gateway</html>"))
        assertNull(ThreadRepository.parsePostColumnInfo(""))
        // 合法空分类=空列表（非 null）
        assertEquals(emptyList<Pair<Int, String>>(),
            ThreadRepository.parsePostColumnInfo("""{"success":true,"data":{"circle_id":5}}""")?.types)
    }

    // ── multipart 体构造（官方字段形态逐项断言） ──

    @Test
    fun `M1 multipart 含官方三字段与文件段`() {
        val body = UploadMultipart.build(
            boundary = "BNDR",
            fields = mapOf("type" to "share", "filename" to "imgs", "time_stamp" to "1730000000"),
            fileField = "imgs", fileName = "pic.jpg",
            fileBytes = byteArrayOf(1, 2, 3), fileMime = "image/jpeg",
        )
        val text = body.toString(Charsets.UTF_8)
        assertTrue(text.contains("""Content-Disposition: form-data; name="type""""))
        assertTrue(text.contains("share"))
        assertTrue(text.contains("""name="filename""""))
        assertTrue(text.contains("imgs"))
        assertTrue(text.contains("""name="imgs"; filename="pic.jpg""""))
        assertTrue(text.contains("Content-Type: image/jpeg"))
        assertTrue(body.toList().windowed(3).contains(listOf<Byte>(1, 2, 3)))
        assertTrue(text.trimEnd().endsWith("--BNDR--"))
    }

    @Test
    fun `M2 mime 按扩展名判定`() {
        assertEquals("image/png", UploadMultipart.mimeOf("a.PNG"))
        assertEquals("image/jpeg", UploadMultipart.mimeOf("b.jpeg"))
        assertEquals("application/octet-stream", UploadMultipart.mimeOf("c.bin"))
    }

    // ── 上传响应判定（A28-02 同口径） ──

    @Test
    fun `U1 成功信封取 data url 非 2xx 拒绝`() {
        val ok = HttpUploadTransport.parseUploadResponse(
            200, """{"success":true,"data":{"url":"https://pic/x.jpg","aid":"42"}}""",
        )
        assertTrue(ok is UploadOutcome.Ok && ok.url == "https://pic/x.jpg" && ok.aid == "42")
        assertTrue(HttpUploadTransport.parseUploadResponse(403, """{"success":true}""") is UploadOutcome.Fail)
        assertTrue(HttpUploadTransport.parseUploadResponse(200, """{"success":"true"}""") is UploadOutcome.Fail)
        assertTrue(HttpUploadTransport.parseUploadResponse(200, "<html/>") is UploadOutcome.Fail)
        // 成功但无 url=Ok(null)——runTask 收尾按失败处理（无地址无法序列化）
        val noUrl = HttpUploadTransport.parseUploadResponse(200, """{"success":true,"data":{}}""")
        assertTrue(noUrl is UploadOutcome.Ok && noUrl.url == null)
    }

    // ── runTask 收尾语义 ──

    private fun task(state: UploadState, url: String? = null, uri: String? = null) =
        UploadTaskItem("u1", "pic.jpg", state, url = url, uri = uri)

    @Test
    fun `R1 Ok 带 url 转 UPLOADED 并落服务端地址`() = runBlocking {
        var out: UploadTaskItem? = null
        UploadQueueLogic.runTask(task(UploadState.WAITING), FakeUploadTransport(ok = true, url = "https://pic/x.jpg")) { out = it }
        assertEquals(UploadState.UPLOADED, out?.state)
        assertEquals("https://pic/x.jpg", out?.url)
    }

    @Test
    fun `R2 Ok 无 url 转 FAILED 显错（不假称成功）`() = runBlocking {
        var out: UploadTaskItem? = null
        UploadQueueLogic.runTask(
            task(UploadState.WAITING),
            object : io.github.deserthouse.sdgun.feature.thread.UploadTransport {
                override suspend fun upload(t: UploadTaskItem, onProgress: (Int) -> Unit) =
                    UploadOutcome.Ok(null)
            },
        ) { out = it }
        assertEquals(UploadState.FAILED, out?.state)
        assertEquals("上传响应缺少图片地址", out?.message)
    }

    @Test
    fun `R3 失败带原因 RUNNING 与 UPLOADED 幂等`() = runBlocking {
        var out: UploadTaskItem? = null
        UploadQueueLogic.runTask(task(UploadState.WAITING), FakeUploadTransport(ok = false)) { out = it }
        assertEquals(UploadState.FAILED, out?.state)
        assertEquals("（合成）上传失败", out?.message)
        var out2: UploadTaskItem? = null
        UploadQueueLogic.runTask(task(UploadState.UPLOADED, "u"), FakeUploadTransport(ok = true)) { out2 = it }
        assertNull(out2)  // 幂等：已上传不再推进
    }

    // ── threadAdd 成功定位 ──

    @Test
    fun `P1 新帖 tid 从 data 或顶层解析`() {
        assertEquals(8899L, ThreadRepository.parseThreadAddTid("""{"success":true,"data":{"tid":"8899"}}"""))
        assertEquals(8899L, ThreadRepository.parseThreadAddTid("""{"success":true,"tid":8899}"""))
        assertNull(ThreadRepository.parseThreadAddTid("""{"success":true,"msg":"ok"}"""))
        assertNull(ThreadRepository.parseThreadAddTid(null))
        assertNull(ThreadRepository.parseThreadAddTid("garbage"))
    }

    // ── 表单 type_id 贯通 ──

    @Test
    fun `F1 type_id 选中才入参`() {
        val with = ThreadPostForm("t", "c", 153, 99, typeId = 3).toParams()
        assertEquals("3", with["type_id"])
        val without = ThreadPostForm("t", "c", 153, 99).toParams()
        assertTrue(!without.containsKey("type_id"))
    }

    @Test
    fun `F2 游客传输诚实失败`() = runBlocking {
        val r = HonestUnavailableUpload.upload(task(UploadState.WAITING)) { }
        assertTrue(r is UploadOutcome.Fail)
    }
}
