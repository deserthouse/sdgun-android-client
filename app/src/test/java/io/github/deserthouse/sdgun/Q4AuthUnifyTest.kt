package io.github.deserthouse.sdgun

import io.github.deserthouse.sdgun.core.model.UiState
import io.github.deserthouse.sdgun.data.repository.ProfileRepository
import io.github.deserthouse.sdgun.feature.profile.PeopleKind
import io.github.deserthouse.sdgun.feature.profile.peopleLoader
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Q4 统一鉴权常驻回归（A0-6）：
 * isAuthRejected 识别矩阵（1001/登录/过期/身份 + 非鉴权拒绝不误判）+
 * peopleLoader 三型分流（AuthExpired→AuthRequired 不串私有内容不误导重试）。
 */
class Q4AuthUnifyTest {

    @Test
    fun `A1 code 1001 识别为鉴权失效`() {
        assertTrue(ProfileRepository.isAuthRejected("""{"success":false,"code":1001,"msg":"token无效"}"""))
    }

    @Test
    fun `A2 登录与过期消息识别`() {
        assertTrue(ProfileRepository.isAuthRejected("""{"success":false,"msg":"尚未登录"}"""))
        assertTrue(ProfileRepository.isAuthRejected("""{"success":false,"msg":"登录已过期"}"""))
        assertTrue(ProfileRepository.isAuthRejected("""{"success":false,"msg":"身份验证失败"}"""))
    }

    @Test
    fun `A3 非鉴权拒绝不误判（可重试语义保留）`() {
        assertFalse(ProfileRepository.isAuthRejected("""{"success":false,"code":403,"msg":"权限不足"}"""))
        assertFalse(ProfileRepository.isAuthRejected("""{"success":false,"code":500,"msg":"服务器开小差"}"""))
    }

    @Test
    fun `A4 成功信封与畸形输入不误判`() {
        assertFalse(ProfileRepository.isAuthRejected("""{"success":true,"list":[]}"""))
        assertFalse(ProfileRepository.isAuthRejected("<html>gateway</html>"))
        assertFalse(ProfileRepository.isAuthRejected(""))
        // 字符串 "true"（严格布尔口径）：success 非布尔 → 不按拒绝处理（Unknown 族语义）
        assertFalse(ProfileRepository.isAuthRejected("""{"success":"false","code":1001}"""))
    }

    // ── peopleLoader 三型分流 ──

    private fun people(list: List<ProfileRepository.PersonItem>) =
        ProfileRepository.PrivateResult.Ok(list)

    @Test
    fun `L1 AuthExpired 呈 AuthRequired 而非可重试错误`() = runBlocking {
        val load = peopleLoader(PeopleKind.FOLLOWS, "tok", "off") {
            ProfileRepository.PrivateResult.AuthExpired
        }
        assertEquals(UiState.AuthRequired, load())
    }

    @Test
    fun `L2 Failed 呈可重试 错误不串内容`() = runBlocking {
        val load = peopleLoader(PeopleKind.VISITORS, "tok", "off") {
            ProfileRepository.PrivateResult.Failed
        }
        assertTrue(load() is UiState.RetryableError)
    }

    @Test
    fun `L3 Ok 空与有值两态`() = runBlocking {
        val empty = peopleLoader(PeopleKind.HISTORY, "tok", "off") { people(emptyList()) }
        assertEquals(UiState.Empty("暂无内容"), empty())
        val data = peopleLoader(PeopleKind.HISTORY, "tok", "off") {
            people(listOf(ProfileRepository.PersonItem(1, "u", null, null)))
        }
        assertTrue(data() is UiState.Data<*>)
    }

    @Test
    fun `L4 游客门保持 AuthRequired`() = runBlocking {
        val load = peopleLoader(PeopleKind.FOLLOWS, null, "off") { people(emptyList()) }
        assertEquals(UiState.AuthRequired, load())
    }
// ── WP3/A1104-S01：user 页结果映射（旧 when 丢弃=永久加载的反例固化） ──

    @Test
    fun `M1 三态映射 Ok-AuthExpired-Failed`() {
        val up = ProfileRepository.UserProfile(1, "u", null, null, null, null, null, null, false, 0)
        assertTrue(ProfileRepository.mapUserProfileResult(ProfileRepository.PrivateResult.Ok(up))
            is UiState.Data<*>)
        assertEquals(UiState.AuthRequired,
            ProfileRepository.mapUserProfileResult(ProfileRepository.PrivateResult.AuthExpired))
        assertTrue(ProfileRepository.mapUserProfileResult(ProfileRepository.PrivateResult.Failed)
            is UiState.RetryableError)
    }

}