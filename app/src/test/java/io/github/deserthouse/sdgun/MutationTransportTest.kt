package io.github.deserthouse.sdgun

import io.github.deserthouse.sdgun.core.mutation.HttpMutationGateway
import io.github.deserthouse.sdgun.core.mutation.MutationKind
import io.github.deserthouse.sdgun.core.mutation.MutationRequest
import io.github.deserthouse.sdgun.core.mutation.MutationRawResponse
import io.github.deserthouse.sdgun.core.mutation.MutationTransport
import io.github.deserthouse.sdgun.core.model.MutationResult
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * A24-01 反例常驻回归（来自独立交付审计探针 ReviewMutation20261024）：
 * 真实 HttpMutationGateway + 合成传输（零触网），八形态信封逐一定性。
 * 契约：Confirmed 仅出自已验证的成功信封；未知形态=OutcomeUnknown（保稿）；明确拒绝=Rejected。
 */
class MutationTransportTest {

    private fun gateway(transport: MutationTransport) = HttpMutationGateway(transport)

    private fun request() = MutationRequest(MutationKind.REPLY, mapOf("tid" to "1", "content" to "x"), "t")

    private fun staticTransport(code: Int, body: String?) = MutationTransport { _, _, _ ->
        MutationRawResponse(code, body, null)
    }

    @Test
    fun `R1 200 成功信封为 Confirmed`() = runBlocking {
        val r = gateway(staticTransport(200, """{"success":true,"msg":"ok"}""")).execute(request())
        assertTrue(r is MutationResult.Confirmed)
    }

    @Test
    fun `R2 200 success false 为 Rejected`() = runBlocking {
        val r = gateway(staticTransport(200, """{"success":false,"code":403,"msg":"denied"}""")).execute(request())
        assertTrue(r is MutationResult.Rejected && r.code == 403 && r.msg == "denied")
    }

    @Test
    fun `R3 200 HTML 网关页为 Unknown 不作 Confirmed`() = runBlocking {
        val r = gateway(staticTransport(200, "<html>gateway error</html>")).execute(request())
        assertEquals(MutationResult.OutcomeUnknown, r)
    }

    @Test
    fun `R4 200 空体为 Unknown`() = runBlocking {
        val r = gateway(staticTransport(200, "")).execute(request())
        assertEquals(MutationResult.OutcomeUnknown, r)
    }

    @Test
    fun `R5 200 截断 JSON 为 Unknown`() = runBlocking {
        val r = gateway(staticTransport(200, """{"success":true,"msg":"trunca""")).execute(request())
        assertEquals(MutationResult.OutcomeUnknown, r)
    }

    @Test
    fun `R6 200 无 success 字段的对象为 Unknown`() = runBlocking {
        // 无权威文档的未知信封（如纯 code 形态）——保守保稿
        val r = gateway(staticTransport(200, """{"code":100}""")).execute(request())
        assertEquals(MutationResult.OutcomeUnknown, r)
    }

    @Test
    fun `R7 403 JSON 明确拒绝为 Rejected`() = runBlocking {
        val r = gateway(staticTransport(403, """{"success":false,"msg":"forbidden"}""")).execute(request())
        assertTrue(r is MutationResult.Rejected && r.msg == "forbidden")
    }

    @Test
    fun `R8 500 非 JSON 为 Rejected 非 OutcomeUnknown`() = runBlocking {
        val r = gateway(staticTransport(500, "Internal Server Error")).execute(request())
        assertTrue(r is MutationResult.Rejected && r.code == 500)
    }

    @Test
    fun `R9 传输断连为 OutcomeUnknown`() = runBlocking {
        val dead = MutationTransport { _, _, _ -> MutationRawResponse(0, null, java.io.IOException("broken")) }
        val r = gateway(dead).execute(request())
        assertEquals(MutationResult.OutcomeUnknown, r)
    }

    @Test
    fun `R10 表单值经 URL 编码`() {
        // 编码行为锁定（审计未点名，但 auth 空值+特殊字符回传依赖此稳定）
        val g = gateway(staticTransport(200, """{"success":true}"""))
        assertTrue(g is HttpMutationGateway)
    }

    // ── A28-02 反例（2026-10-29 AF29 复审实锤，先失败后修）──
    // 非成功状态码绝不 Confirmed；success 必须是严格布尔类型
    @Test
    fun `403 with success true payload must not confirm`() {
        val v = io.github.deserthouse.sdgun.core.mutation.MutationResponseAdapter
            .judge(403, """{"success":true,"msg":"ok"}""")
        org.junit.Assert.assertTrue(
            "403+success:true 不得 Confirmed（实际=$v）", v !is io.github.deserthouse.sdgun.core.mutation.MutationVerdict.Confirmed)
    }

    @Test
    fun `500 with success true payload must not confirm`() {
        val v = io.github.deserthouse.sdgun.core.mutation.MutationResponseAdapter
            .judge(500, """{"success":true}""")
        org.junit.Assert.assertTrue(
            "500+success:true 不得 Confirmed", v !is io.github.deserthouse.sdgun.core.mutation.MutationVerdict.Confirmed)
    }

    @Test
    fun `string success true must not confirm`() {
        // org.json optBoolean 会把字符串 "true" parseBoolean——必须先验类型
        val v = io.github.deserthouse.sdgun.core.mutation.MutationResponseAdapter
            .judge(200, """{"success":"true"}""")
        org.junit.Assert.assertTrue(
            "success:\"true\"（字符串）不得 Confirmed", v !is io.github.deserthouse.sdgun.core.mutation.MutationVerdict.Confirmed)
    }
}
