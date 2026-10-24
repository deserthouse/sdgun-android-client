package io.github.deserthouse.sdgun.core.model

/**
 * 写操作 UI 相位（A24-02）：比 MutationState 更贴 UI 的乐观态模型。
 *
 * - Idle：未操作
 * - Pending：提交在途——**保留乐观展示**（不回滚），同操作禁重复提交
 * - Done：服务端已确认（乐观态转正）
 * - Failed：服务端明确拒绝——回滚乐观态，reason=服务端理由
 * - Verify：结果未知（超时/断网/未知信封）——**回滚乐观态**，同操作禁直发重试，
 *   需用户显式核对（确认对话框）后才允许再次提交
 */
enum class WriteUiPhase { Idle, Pending, Done, Failed, Verify }

data class WriteUi(
    val phase: WriteUiPhase = WriteUiPhase.Idle,
    val reason: String? = null,
)
