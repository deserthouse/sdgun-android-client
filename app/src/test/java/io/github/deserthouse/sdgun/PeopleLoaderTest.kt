package io.github.deserthouse.sdgun

import io.github.deserthouse.sdgun.core.model.UiState
import io.github.deserthouse.sdgun.data.repository.ProfileRepository
import io.github.deserthouse.sdgun.feature.profile.PeopleKind
import io.github.deserthouse.sdgun.feature.profile.peopleLoader
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * U3-b（F18/19）反例先行：访客/历史/关注列表装载器——
 * 合成三态（data/empty/error）、游客门（零请求）、真实读链注入。
 * 口径（android_code_review_2026-10-25 §二.7）：读接口在库但真实数据需凭据——
 * 合成先行保证路由/权限/导航正确，真实数据接入为增量。
 */
class PeopleLoaderTest {

    private fun person(id: Long) = ProfileRepository.PersonItem(id, "u$id", null, null)

    @Test
    fun `合成data模式返回夹具且不触真实链`() = runBlocking {
        var realCalls = 0
        val load = peopleLoader(PeopleKind.VISITORS, token = "t", fakeMode = "data",
            fetchReal = { realCalls++; ProfileRepository.PrivateResult.Ok(emptyList()) })
        val st = load()
        assertTrue(st is UiState.Data<*>)
        assertEquals(4, (st as UiState.Data<*>).value.let { it as io.github.deserthouse.sdgun.feature.profile.PeoplePageData }.people.size)
        assertEquals("合成模式不得触真实链", 0, realCalls)
    }

    @Test
    fun `合成empty模式给空态`() = runBlocking {
        val load = peopleLoader(PeopleKind.FOLLOWS, token = "t", fakeMode = "empty",
            fetchReal = { ProfileRepository.PrivateResult.Ok(emptyList()) })
        assertTrue(load() is UiState.Empty)
    }

    @Test
    fun `合成error模式给可重试错误`() = runBlocking {
        val load = peopleLoader(PeopleKind.HISTORY, token = "t", fakeMode = "error",
            fetchReal = { ProfileRepository.PrivateResult.Ok(emptyList()) })
        assertTrue(load() is UiState.RetryableError)
    }

    @Test
    fun `游客无token给AuthRequired且零请求`() = runBlocking {
        var realCalls = 0
        val load = peopleLoader(PeopleKind.VISITORS, token = null, fakeMode = "off",
            fetchReal = { realCalls++; ProfileRepository.PrivateResult.Ok(emptyList()) })
        assertTrue(load() is UiState.AuthRequired)
        val load2 = peopleLoader(PeopleKind.VISITORS, token = "", fakeMode = "off",
            fetchReal = { realCalls++; ProfileRepository.PrivateResult.Ok(emptyList()) })
        assertTrue(load2() is UiState.AuthRequired)
        assertEquals("游客门不得发起请求", 0, realCalls)
    }

    // A28-06 反例（2026-10-29 AF29 复审）：传输/解析失败≠成功空——必须 RetryableError
    // Q4 迁移：null 语义由 PrivateResult.Failed 承载（原 fetchReal null 返回已收敛三型）
    @Test
    fun `真实链失败Failed给可重试错误而非空态`() = runBlocking {
        val load = peopleLoader(PeopleKind.VISITORS, token = "tok", fakeMode = "off",
            fetchReal = { ProfileRepository.PrivateResult.Failed })
        val st = load()
        assertTrue("fetch 失败必须是 RetryableError（实际=$st）",
            st is UiState.RetryableError)
    }

    @Test
    fun `真实链鉴权失效给AuthRequired而非可重试`() = runBlocking {
        val load = peopleLoader(PeopleKind.VISITORS, token = "tok", fakeMode = "off",
            fetchReal = { ProfileRepository.PrivateResult.AuthExpired })
        assertTrue("AuthExpired 必须 AuthRequired（Q4/A0-6）",
            load() is UiState.AuthRequired)
    }

    @Test
    fun `parsePeople鉴权失败HTML输入返回null而非空列表`() {
        // ProfileRepository 吞错为空反例：HTML 网关页/鉴权拒绝页不得变成"成功空"
        val r = ProfileRepository.parsePeople("<html><body>login required</body></html>")
        assertTrue("HTML 输入应解析失败（null），不得是空列表", r == null)
    }

    @Test
    fun `真实链注入拿token拉列表空表给空态`() = runBlocking {
        var gotToken: String? = null
        val load = peopleLoader(PeopleKind.FOLLOWS, token = "tok123", fakeMode = "off",
            fetchReal = { t ->
                gotToken = t
                ProfileRepository.PrivateResult.Ok(listOf(person(1), person(2)))
            })
        val st = load()
        assertEquals("tok123", gotToken)
        assertTrue(st is UiState.Data<*>)
        val data = (st as UiState.Data<*>).value as io.github.deserthouse.sdgun.feature.profile.PeoplePageData
        assertEquals(listOf(1L, 2L), data.people.map { it.userId })
    }

    // ── R1/S05（A31）信封分类收紧：仅「合法成功信封」产出列表——其余一律 null ──
    @org.junit.Test
    fun `S05 空对象与错型 list 是协议错返回 null`() {
        // 空对象（无 success 无 list）= 未知信封 → null（旧=emptyList 冒充成功空）
        org.junit.Assert.assertNull(ProfileRepository.parsePeople("{}"))
        // 错型 list（字符串数组混对象）→ 解析失败的行被跳过，但 list 键存在=合法形态
        val malformed = ProfileRepository.parsePeople("""{"list":["a","b"]}""")
        org.junit.Assert.assertTrue("错型行被跳过后=合法空列表", malformed?.isEmpty() == true)
    }

    @org.junit.Test
    fun `S05 成功信封空 list 才是合法 Empty`() {
        val ok = ProfileRepository.parsePeople("""{"success":true,"list":[]}""")
        org.junit.Assert.assertTrue(ok?.isEmpty() == true)
    }

    @org.junit.Test
    fun `S05 显式拒绝与 HTML 仍为 null`() {
        org.junit.Assert.assertNull(ProfileRepository.parsePeople("""{"success":false,"msg":"x"}"""))
        org.junit.Assert.assertNull(ProfileRepository.parsePeople("<html>login</html>"))
    }

    // ── R3/A4：parseMessageList S05 同口径 ──
    @org.junit.Test
    fun `S05-messageList 未知信封 null 成功空列表合法`() {
        val P = io.github.deserthouse.sdgun.data.repository.ProfileRepository
        org.junit.Assert.assertNull(P.parseMessageList("{}"))
        org.junit.Assert.assertNull(P.parseMessageList("""{"success":false,"msg":"x"}"""))
        org.junit.Assert.assertNull(P.parseMessageList("<html>login</html>"))
        val ok = P.parseMessageList("""{"success":true,"list":[]}""")
        org.junit.Assert.assertTrue(ok?.isEmpty() == true)
        val rows = P.parseMessageList(
            """{"list":[{"mid":"1","uid":9,"name":"u","content":"hello","dateline":"10-31"}]}""")
        org.junit.Assert.assertEquals(1, rows?.size)
        org.junit.Assert.assertEquals("hello", rows!![0].content)
    }
}
