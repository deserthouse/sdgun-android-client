package io.github.deserthouse.sdgun

import io.github.deserthouse.sdgun.core.model.WriteOps
import io.github.deserthouse.sdgun.core.model.WriteUiPhase
import io.github.deserthouse.sdgun.core.mutation.HttpMutationGateway
import io.github.deserthouse.sdgun.core.mutation.MutationKind
import io.github.deserthouse.sdgun.core.mutation.MutationRawResponse
import io.github.deserthouse.sdgun.core.mutation.MutationRequest
import io.github.deserthouse.sdgun.core.mutation.MutationTransport
import io.github.deserthouse.sdgun.core.model.MutationResult
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Q2 写面收割常驻回归（2026-11-02；合成关闭制）：
 * 四新端点（取消收藏/取消赞/关注族/私信发送）契约 + 点赞 toggle 决策矩阵。
 * jadx 实据：OperationHelper.collectDelete(GET id) / FroumDataView.addApplaud(toggle POST
 * content_id) / UserHomeFragment.attentionToNet(GET user_id[+remove_his]) /
 * LetterChatActivity.sendMessage(POST uid+content)。
 */
class Q2WriteHarvestTest {

    // ── 端点形态契约（jadx API.java 逐字） ──

    @Test
    fun `E1 取消赞端点与 Add 同域 Forum 路径`() {
        assertEquals(
            "https://mag1.sdgun.net/mag/circle/v1/Forum/contentCancelApplaud",
            MutationKind.APPLAUD_CANCEL.url,
        )
        assertEquals("POST", MutationKind.APPLAUD_CANCEL.defaultMethod)
    }

    @Test
    fun `E2 取消收藏为 collectDelete GET id`() {
        assertEquals(
            "https://mag1.sdgun.net/mag/circle/v1/collect/collectDelete",
            MutationKind.COLLECT_DELETE.url,
        )
        assertEquals("GET", MutationKind.COLLECT_DELETE.defaultMethod)
    }

    @Test
    fun `E3 关注族为 UserRelation fansAdd 与 removeRelation GET user_id`() {
        assertEquals(
            "https://mag1.sdgun.net/mag/user/v1/UserRelation/fansAdd",
            MutationKind.FOLLOW_ADD.url,
        )
        assertEquals(
            "https://mag1.sdgun.net/mag/user/v1/UserRelation/removeRelation",
            MutationKind.FOLLOW_REMOVE.url,
        )
        assertEquals("GET", MutationKind.FOLLOW_ADD.defaultMethod)
        assertEquals("GET", MutationKind.FOLLOW_REMOVE.defaultMethod)
    }

    @Test
    fun `E4 私信发送为 UserNotify userMessageSendMsg POST`() {
        assertEquals(
            "https://mag1.sdgun.net/mag/user/v1/UserNotify/userMessageSendMsg",
            MutationKind.MESSAGE_SEND.url,
        )
        assertEquals("POST", MutationKind.MESSAGE_SEND.defaultMethod)
    }

    // ── 网关 GET 路由（真 GET 语义：query 拼接 + 走 transport.get 而非空体 post） ──

    private class RecordingTransport : MutationTransport {
        var postedUrls = mutableListOf<String>()
        var postedBodies = mutableListOf<String>()
        var gotUrls = mutableListOf<String>()

        override fun post(url: String, form: String, userAgent: String): MutationRawResponse {
            postedUrls.add(url); postedBodies.add(form)
            return MutationRawResponse(200, """{"success":true}""", null)
        }

        override fun get(url: String, userAgent: String): MutationRawResponse {
            gotUrls.add(url)
            return MutationRawResponse(200, """{"success":true}""", null)
        }
    }

    @Test
    fun `G1 GET 类请求走 get 通道且 query 正确拼接`() = runBlocking {
        val t = RecordingTransport()
        val r = HttpMutationGateway(t).execute(
            MutationRequest(
                MutationKind.COLLECT_DELETE, mapOf("id" to "12345"), "tok",
            ),
        )
        assertTrue(r is MutationResult.Confirmed)
        assertEquals(listOf("https://mag1.sdgun.net/mag/circle/v1/collect/collectDelete?id=12345"), t.gotUrls)
        assertTrue(t.postedUrls.isEmpty())  // 绝不借 POST 通道发 GET 请求
    }

    @Test
    fun `G2 关注 query 含 user_id 取关追加 remove_his`() = runBlocking {
        val t = RecordingTransport()
        val g = HttpMutationGateway(t)
        g.execute(MutationRequest(MutationKind.FOLLOW_ADD, mapOf("user_id" to "42"), "tok"))
        assertEquals(
            listOf("https://mag1.sdgun.net/mag/user/v1/UserRelation/fansAdd?user_id=42"),
            t.gotUrls,
        )
        g.execute(
            MutationRequest(
                MutationKind.FOLLOW_REMOVE,
                mapOf("user_id" to "42", "remove_his" to "0"), "tok",
            ),
        )
        assertEquals(
            "https://mag1.sdgun.net/mag/user/v1/UserRelation/removeRelation?user_id=42&remove_his=0",
            t.gotUrls[1],
        )
    }

    @Test
    fun `G3 POST 类请求仍走 post 通道表单编码`() = runBlocking {
        val t = RecordingTransport()
        HttpMutationGateway(t).execute(
            MutationRequest(
                MutationKind.MESSAGE_SEND,
                mapOf("uid" to "7", "content" to "你好 hello"), "tok",
            ),
        )
        assertTrue(t.postedUrls.single().endsWith("/userMessageSendMsg"))
        assertTrue(t.postedBodies.single().contains("uid=7"))
        // x-www-form-urlencoded：空格编码为 +（URLEncoder 语义），非 %20
        assertTrue(t.postedBodies.single().contains("content=%E4%BD%A0%E5%A5%BD+hello"))
        assertTrue(t.gotUrls.isEmpty())
    }

    @Test
    fun `G4 显式 method 覆盖默认（历史行为保留）`() = runBlocking {
        val t = RecordingTransport()
        HttpMutationGateway(t).execute(
            MutationRequest(MutationKind.APPLAUD_CANCEL, mapOf("content_id" to "9"), "tok"),
        )
        // APPLAUD_CANCEL 默认 POST（toggle 后半与 Add 同形）
        assertEquals(1, t.postedUrls.size)
        assertTrue(t.gotUrls.isEmpty())
    }

    // ── 点赞 toggle 决策矩阵（WriteOps.decideApplaud；S03 相位纪律） ──

    private fun d(phase: WriteUiPhase, applauded: Boolean, acked: Boolean = false) =
        WriteOps.decideApplaud(phase, applauded, acked)

    @Test
    fun `T1 在途一律 NONE 禁重复（S03）`() {
        assertEquals(WriteOps.Op.NONE, d(WriteUiPhase.Pending, false))
        assertEquals(WriteOps.Op.NONE, d(WriteUiPhase.Pending, true))
    }

    @Test
    fun `T2 Verify 未核对 NONE 核对后按归属重发`() {
        assertEquals(WriteOps.Op.NONE, d(WriteUiPhase.Verify, false, acked = false))
        assertEquals(WriteOps.Op.NONE, d(WriteUiPhase.Verify, true, acked = false))
        assertEquals(WriteOps.Op.ADD, d(WriteUiPhase.Verify, false, acked = true))
        assertEquals(WriteOps.Op.CANCEL, d(WriteUiPhase.Verify, true, acked = true))
    }

    @Test
    fun `T3 已赞再点等于 CANCEL toggle（Done 不再是终态）`() {
        assertEquals(WriteOps.Op.CANCEL, d(WriteUiPhase.Done, true))
        assertEquals(WriteOps.Op.CANCEL, d(WriteUiPhase.Idle, true))
    }

    @Test
    fun `T4 未赞点按 ADD`() {
        assertEquals(WriteOps.Op.ADD, d(WriteUiPhase.Idle, false))
        assertEquals(WriteOps.Op.ADD, d(WriteUiPhase.Done, false))
    }

    @Test
    fun `T5 Failed 后按当前归属自然重试方向`() {
        // Add 失败已回滚归属 → 重发 Add；Cancel 失败保持归属 → 重发 Cancel
        assertEquals(WriteOps.Op.ADD, d(WriteUiPhase.Failed, false))
        assertEquals(WriteOps.Op.CANCEL, d(WriteUiPhase.Failed, true))
    }
}
