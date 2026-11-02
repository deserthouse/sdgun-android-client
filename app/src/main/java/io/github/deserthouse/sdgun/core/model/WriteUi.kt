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

/**
 * Q2 toggle 意图决策（纯函数，JVM 可测）：当前相位 + 乐观归属 → 下一次点按应发什么。
 *
 * - Pending 在途一律 NONE（S03 禁重复）；
 * - Verify 未核对 → NONE（路由层弹核对对话框）；Verify 已核对 → 按当前归属重发
 *   （已赞=重发取消、未赞=重发 Add——核对消费后允许一次重试）；
 * - 其余按归属分流：已赞 → CANCEL（Q2 toggle 后半），未赞 → ADD。
 *   Failed 后的语义自然成立：Add 失败已回滚归属（再点=重发 Add），
 *   Cancel 失败保持归属（再点=重发 Cancel）。
 */
object WriteOps {
    enum class Op { ADD, CANCEL, NONE }

    fun decideApplaud(phase: WriteUiPhase, applauded: Boolean, verifyAcked: Boolean): Op = when {
        phase == WriteUiPhase.Pending -> Op.NONE
        phase == WriteUiPhase.Verify && !verifyAcked -> Op.NONE
        applauded -> Op.CANCEL
        else -> Op.ADD
    }
}
