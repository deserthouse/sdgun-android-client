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
            fetchReal = { realCalls++; emptyList() })
        val st = load()
        assertTrue(st is UiState.Data<*>)
        assertEquals(4, (st as UiState.Data<*>).value.let { it as io.github.deserthouse.sdgun.feature.profile.PeoplePageData }.people.size)
        assertEquals("合成模式不得触真实链", 0, realCalls)
    }

    @Test
    fun `合成empty模式给空态`() = runBlocking {
        val load = peopleLoader(PeopleKind.FOLLOWS, token = "t", fakeMode = "empty",
            fetchReal = { emptyList() })
        assertTrue(load() is UiState.Empty)
    }

    @Test
    fun `合成error模式给可重试错误`() = runBlocking {
        val load = peopleLoader(PeopleKind.HISTORY, token = "t", fakeMode = "error",
            fetchReal = { emptyList() })
        assertTrue(load() is UiState.RetryableError)
    }

    @Test
    fun `游客无token给AuthRequired且零请求`() = runBlocking {
        var realCalls = 0
        val load = peopleLoader(PeopleKind.VISITORS, token = null, fakeMode = "off",
            fetchReal = { realCalls++; emptyList() })
        assertTrue(load() is UiState.AuthRequired)
        val load2 = peopleLoader(PeopleKind.VISITORS, token = "", fakeMode = "off",
            fetchReal = { realCalls++; emptyList() })
        assertTrue(load2() is UiState.AuthRequired)
        assertEquals("游客门不得发起请求", 0, realCalls)
    }

    @Test
    fun `真实链注入拿token拉列表空表给空态`() = runBlocking {
        var gotToken: String? = null
        val load = peopleLoader(PeopleKind.FOLLOWS, token = "tok123", fakeMode = "off",
            fetchReal = { t -> gotToken = t; listOf(person(1), person(2)) })
        val st = load()
        assertEquals("tok123", gotToken)
        assertTrue(st is UiState.Data<*>)
        val data = (st as UiState.Data<*>).value as io.github.deserthouse.sdgun.feature.profile.PeoplePageData
        assertEquals(listOf(1L, 2L), data.people.map { it.userId })
    }
}
