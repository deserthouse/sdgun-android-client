package io.github.deserthouse.sdgun.core.model

/**
 * MutationGateway 骨架（G4）：写操作统一状态管理接口。
 * 实际网络调用由具体 Mutation 类实现；本接口定义契约。
 */
interface MutationGateway {
    suspend fun execute(): MutationResult
}

sealed class MutationResult {

    data class Confirmed(val value: String) : MutationResult()
    data class Rejected(val code: Int, val msg: String) : MutationResult()
    object OutcomeUnknown : MutationResult()
}
