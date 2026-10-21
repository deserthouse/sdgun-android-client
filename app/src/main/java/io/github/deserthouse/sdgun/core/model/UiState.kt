package io.github.deserthouse.sdgun.core.model

/**
 * 八态 UiState（总设计 §4）：所有页面数据流的统一状态表达。
 * 失败保留旧数据（Stale/RetryableError 携带 data）；缓存是来源标记不是成功提示。
 */
sealed interface UiState<out T> {
    /** 首次加载，无旧数据 */
    data object InitialLoading : UiState<Nothing>

    /** 正常数据 */
    data class Data<T>(val value: T) : UiState<T>

    /** 服务端确认的空结果（与网络失败严格区分） */
    data class Empty(val message: String? = null) : UiState<Nothing>

    /** 刷新中，旧内容仍可读可交互 */
    data class Refreshing<T>(val stale: T) : UiState<T>

    /** 刷新失败但保留了旧内容（AV-02 语义：横幅+旧数据，绝不冒充成功） */
    data class Stale<T>(val stale: T, val error: String) : UiState<T>

    /** 首拉失败（无旧数据可保留） */
    data class RetryableError(val error: String) : UiState<Nothing>

    /** 需要登录（1001 等认证失效） */
    data object AuthRequired : UiState<Nothing>

    /** 服务端明确拒绝（权限/用户组不足） */
    data class PermissionDenied(val message: String) : UiState<Nothing>
}

/**
 * 变更契约（G4 实装）：写操作五态。
 * 超时不自动重发；无法证明结果时显示 OutcomeUnknown（"结果待确认"）。
 * UI 防双击不冒充服务器幂等。
 */
sealed interface MutationState {
    data object Draft : MutationState
    data object Validating : MutationState
    data object Submitting : MutationState
    data object Confirmed : MutationState
    data class Rejected(val reason: String) : MutationState

    /** 超时/断网：结果未知——禁止自动重发，需人工核对 */
    data object OutcomeUnknown : MutationState
}
