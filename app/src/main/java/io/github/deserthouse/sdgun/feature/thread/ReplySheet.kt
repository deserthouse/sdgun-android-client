package io.github.deserthouse.sdgun.feature.thread

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import io.github.deserthouse.sdgun.core.model.MutationState

/**
 * 回复编辑器（G4）：ModalBottomSheet + 纯文本（富文本降级后续批）。
 * 草稿持久化在路由层（按 tid 隔离，DraftStore）；本组件只管文本与状态呈现。
 *
 * 状态语义（MutationState）：
 * - Submitting：发送钮禁用（防双击，不冒充幂等）
 * - Confirmed：提示后由路由关页（草稿已清）
 * - Rejected：服务端理由原文透出
 * - OutcomeUnknown：结果待确认——**明确劝阻盲目重发**（人工核对后再操作）
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReplySheet(
    initialDraft: String,
    state: MutationState,
    onDraftChange: (String) -> Unit,
    onSend: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    var text by remember { mutableStateOf(initialDraft) }

    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            Modifier.fillMaxWidth().padding(horizontal = 16.dp).padding(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text("回复", style = MaterialTheme.typography.titleMedium)
            OutlinedTextField(
                value = text,
                onValueChange = {
                    text = it
                    onDraftChange(it)
                },
                modifier = Modifier.fillMaxWidth().height(140.dp),
                placeholder = { Text("说点什么…（草稿自动保存）") },
                enabled = state !is MutationState.Submitting,
            )
            when (val s = state) {
                is MutationState.Submitting -> Text(
                    "提交中…", style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                is MutationState.Rejected -> Text(
                    "发送失败：${s.reason}", style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.error,
                )
                is MutationState.OutcomeUnknown -> Text(
                    "结果待确认：超时或断网，请回到帖子内核对后再决定是否重发",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.error,
                )
                else -> {}
            }
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Button(
                    onClick = {
                        onDraftChange(text)
                        onSend(text)
                    },
                    enabled = text.isNotBlank() && state !is MutationState.Submitting,
                ) {
                    Text(if (state is MutationState.Submitting) "提交中…" else "发送")
                }
            }
        }
    }
}
