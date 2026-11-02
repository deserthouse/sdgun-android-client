package io.github.deserthouse.sdgun.core.model

/**
 * MutationGateway 骨架（G4）：写操作统一状态管理接口。
 * 实际网络调用由具体 Mutation 类实现；本接口定义契约。
 */
interface MutationGateway {
    suspend fun execute(): MutationResult
}

sealed class MutationResult {

    /** payload=成功响应原始体（Q3：THREAD_POST 成功定位解析新帖 tid 用；其余 kind 为 null 语义不变） */
    data class Confirmed(val value: String, val payload: String? = null) : MutationResult()
    data class Rejected(val code: Int, val msg: String) : MutationResult()
    object OutcomeUnknown : MutationResult()
}
