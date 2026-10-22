package io.github.deserthouse.sdgun

import io.github.deserthouse.sdgun.core.mutation.FakeMutationGateway
import io.github.deserthouse.sdgun.core.mutation.MutationGateway
import io.github.deserthouse.sdgun.core.mutation.MutationKind
import io.github.deserthouse.sdgun.core.mutation.MutationRequest
import io.github.deserthouse.sdgun.core.mutation.MutationRunner
import io.github.deserthouse.sdgun.core.model.MutationResult
import io.github.deserthouse.sdgun.core.model.MutationState
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 写操作状态机契约（G4）：Confirmed/Rejected/OutcomeUnknown 三分支 + epoch 丢弃 + 超时。
 * 全程假网关零触网（真实写入验收=用户 MANUAL，红线）。
 */
class MutationRunnerTest {

    private fun req() = MutationRequest(MutationKind.APPLAUD, mapOf("content_id" to "1"), "token")

    @Test
    fun `确认分支`() = runBlocking {
        val seen = mutableListOf<MutationState>()
        val final = MutationRunner.submit(req(), FakeMutationGateway("confirm"), 7, { true }) {
            seen.add(it)
        }
        assertEquals(MutationState.Confirmed, final)
        assertEquals(listOf(MutationState.Submitting, MutationState.Confirmed), seen)
    }

    @Test
    fun `拒绝分支透出理由`() = runBlocking {
        val final = MutationRunner.submit(req(), FakeMutationGateway("reject"), 7, { true })
        assertTrue(final is MutationState.Rejected && final.reason.contains("权限不足"))
    }

    @Test
    fun `假服务超时分支为结果待确认`() = runBlocking {
        val final = MutationRunner.submit(req(), FakeMutationGateway("timeout"), 7, { true })
        assertEquals(MutationState.OutcomeUnknown, final)
    }

    @Test
    fun `真超时映射结果待确认`() = runBlocking {
        val hang = object : MutationGateway {
            override suspend fun execute(request: MutationRequest): MutationResult =
                io.github.deserthouse.sdgun.core.model.MutationResult.OutcomeUnknown.also {
                    delay(500) // 超过下方 80ms 预算
                }
        }
        val final = MutationRunner.submit(req(), hang, 7, { true }, timeoutMs = 80)
        assertEquals(MutationState.OutcomeUnknown, final)
    }

    @Test
    fun `epoch 换代后迟到结果丢弃`() = runBlocking {
        val seen = mutableListOf<MutationState>()
        // epochCurrent 恒 false = 提交后立刻登出/换号
        val final = MutationRunner.submit(req(), FakeMutationGateway("confirm"), 7, { false }) {
            seen.add(it)
        }
        assertEquals(MutationState.Draft, final)
        assertTrue("换代后不得向 UI 推进任何终态", seen.all { it is MutationState.Submitting })
    }
}
