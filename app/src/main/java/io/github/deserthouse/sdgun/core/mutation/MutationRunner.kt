package io.github.deserthouse.sdgun.core.mutation

import io.github.deserthouse.sdgun.core.model.MutationState
import io.github.deserthouse.sdgun.core.model.MutationResult
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.withTimeout

/**
 * 写操作状态机 runner（G4）：Draft→Submitting→(Confirmed|Rejected|OutcomeUnknown)。
 *
 * 契约（总设计 §4 / MutationState）：
 * - 超时 → OutcomeUnknown（"结果待确认"）：**绝不自动重发**，由人工核对后决定；
 * - UI 防双击由状态判断（Submitting 中忽略新提交），不冒充服务器幂等；
 * - epoch 捕获-比对：登出/换号后迟到结果一律丢弃（SessionManager 单一事实源）。
 */
object MutationRunner {

    const val TIMEOUT_MS = 15_000L

    /**
     * 提交一次写操作。返回最终 MutationState（调用方负责渲染）；
     * 中间态经 onState 回调（Submitting）。
     *
     * @param epochCurrent 提交时捕获的 epoch 是否仍为当前（SessionManager.store.epoch::isCurrent）
     */
    suspend fun submit(
        request: MutationRequest,
        gateway: MutationGateway,
        epochCapture: Int,
        epochCurrent: (Int) -> Boolean,
        timeoutMs: Long = TIMEOUT_MS,
        onState: (MutationState) -> Unit = {},
    ): MutationState {
        onState(MutationState.Submitting)
        val result = try {
            withTimeout(timeoutMs) {
                gateway.execute(request)
            }
        } catch (e: TimeoutCancellationException) {
            null
        }
        // epoch 已换代：本结果属于旧会话，一律丢弃（不推进状态、不落 UI）
        if (!epochCurrent(epochCapture)) return MutationState.Draft
        return when (result) {
            null -> MutationState.OutcomeUnknown.also { onState(it) }
            is MutationResult.Confirmed -> MutationState.Confirmed.also { onState(it) }
            is MutationResult.Rejected -> MutationState.Rejected(result.msg).also { onState(it) }
            is MutationResult.OutcomeUnknown -> MutationState.OutcomeUnknown.also { onState(it) }
        }
    }
}
