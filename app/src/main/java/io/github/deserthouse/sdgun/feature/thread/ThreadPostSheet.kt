package io.github.deserthouse.sdgun.feature.thread

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import io.github.deserthouse.sdgun.core.model.MutationState

/**
 * U3-c（F14 前半）：发帖表单（threadAdd，jadx ThreadPostActivity.toPost 同构）
 * + 附件任务队列 UI（选片/进度/重试/失败态；传输后端接口化——真实上传=MANUAL）。
 *
 * 诚实边界：
 * - 提交走 MutationRunner 五态（假服务三分支=装机走查；真端点无 auth=同盾未复刻，
 *   真实写入验收=用户 MANUAL，B2 先例）；
 * - 传输后端未接入：附件任务恒失败态+可重试+可移除，提交被附件阻塞时给明确说明。
 */

data class ThreadPostForm(
    val title: String,
    val content: String,
    val fid: Long,
    val circleId: Long,
) {
    fun validate(): String? = when {
        title.isBlank() -> "请填写标题"
        content.isBlank() -> "请填写正文"
        fid <= 0 -> "缺少板块参数"
        else -> null
    }

    /** THREAD_POST 表单（jadx 同构：title/content/fid/circle_id；auth/users/vest 本版不带） */
    fun toParams(): Map<String, String> = mapOf(
        "title" to title.trim(),
        "content" to content,
        "fid" to fid.toString(),
        "circle_id" to circleId.toString(),
    )
}

/** 附件任务：传输后端接口化——默认实现=诚实未接入（恒失败，可重试可移除） */
data class UploadTaskItem(
    val id: Int,
    val name: String,
    val state: UploadState,
    val message: String? = null,
)

enum class UploadState { WAITING, RUNNING, FAILED }

/** 传输后端抽象：真实 getUploadToken 流=MANUAL 实测后接入 */
fun interface UploadTransport {
    suspend fun upload(name: String, onProgress: (Int) -> Unit): Boolean
}

val HonestUnavailableUpload: UploadTransport = UploadTransport { _, _ -> false }

/** 附件队列状态机（组合外可测的纯推进逻辑） */
object UploadQueueLogic {

    /** 重试一轮：等待/失败→RUNNING→按 transport 结果收尾（false=诚实失败态） */
    suspend fun runTask(
        task: UploadTaskItem,
        transport: UploadTransport,
        update: (UploadTaskItem) -> Unit,
    ) {
        if (task.state == UploadState.RUNNING) return
        update(task.copy(state = UploadState.RUNNING, message = null))
        var lastPct = 0
        val ok = transport.upload(task.name) { pct -> lastPct = pct }
        update(
            if (ok) task.copy(state = UploadState.WAITING, message = "已完成（占位）")
            else task.copy(
                state = UploadState.FAILED,
                message = "传输后端待接入（真实上传=用户验收后开放）",
            )
        )
    }
}

/**
 * 发帖底部表单。提交链路：validate → onSubmit(form)（调用方接 MutationRunner）。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ThreadPostSheet(
    boardName: String,
    authed: Boolean,
    submitting: Boolean,
    mutationState: MutationState,
    onDismiss: () -> Unit,
    onSubmit: (title: String, content: String) -> Unit,
    onPickImages: () -> Unit = {},
    onRetryUpload: (UploadTaskItem) -> Unit = {},
    onRemoveUpload: (UploadTaskItem) -> Unit = {},
    uploadTasks: List<UploadTaskItem> = emptyList(),
) {
    var title by remember { mutableStateOf("") }
    var content by remember { mutableStateOf("") }
    val localError = remember { mutableStateOf<String?>(null) }

    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text(
                "发帖到「$boardName」",
                style = MaterialTheme.typography.titleMedium,
            )
            if (!authed) {
                Text(
                    "发帖需要登录后使用（真实写入验收=用户本人，当前为表单预览）",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                )
            }
            OutlinedTextField(
                value = title,
                onValueChange = { title = it },
                label = { Text("标题") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(
                value = content,
                onValueChange = { content = it },
                label = { Text("正文") },
                minLines = 4,
                modifier = Modifier.fillMaxWidth(),
            )

            // 附件队列（F16 前半）：选片入口 + 任务态（传输未接入=诚实失败可重试）
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("附件", style = MaterialTheme.typography.titleSmall)
                TextButton(onClick = onPickImages, enabled = authed) {
                    Icon(Icons.Filled.Add, contentDescription = null)
                    Text("选图")
                }
            }
            uploadTasks.forEach { t ->
                ListItem(
                    leadingContent = {
                        when (t.state) {
                            UploadState.RUNNING -> CircularProgressIndicator(
                                Modifier.height(18.dp))
                            UploadState.FAILED -> Text("!", color = MaterialTheme.colorScheme.error)
                            UploadState.WAITING -> Text("·")
                        }
                    },
                    headlineContent = { Text(t.name, maxLines = 1) },
                    supportingContent = { t.message?.let { Text(it) } },
                    trailingContent = {
                        Row {
                            if (t.state == UploadState.FAILED) {
                                TextButton(onClick = { onRetryUpload(t) }) { Text("重试") }
                            }
                            TextButton(onClick = { onRemoveUpload(t) }) { Text("移除") }
                        }
                    },
                )
            }

            localError.value?.let {
                Text(it, color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodySmall)
            }
            when (val st = mutationState) {
                is MutationState.Rejected -> Text(
                    "发布被拒：${st.reason}",
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodySmall,
                )
                MutationState.OutcomeUnknown -> Text(
                    "结果待确认（超时未获应答）——不自动重发，请稍后在板块列表核对",
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodySmall,
                )
                is MutationState.Confirmed -> Text(
                    "发布成功（假服务）",
                    color = MaterialTheme.colorScheme.primary,
                    style = MaterialTheme.typography.bodySmall,
                )
                else -> {}
            }
            Button(
                onClick = {
                    localError.value = when {
                        title.isBlank() -> "请填写标题"
                        content.isBlank() -> "请填写正文"
                        uploadTasks.any { it.state == UploadState.RUNNING } ->
                            "附件传输进行中，请等待或移除附件"
                        else -> null
                    }
                    if (localError.value == null) onSubmit(title.trim(), content)
                },
                enabled = !submitting && authed,
                modifier = Modifier.fillMaxWidth().height(48.dp),
            ) {
                if (submitting) {
                    CircularProgressIndicator(Modifier.height(22.dp), strokeWidth = 2.dp)
                } else {
                    Text(if (authed) "发布" else "需登录后发布")
                }
            }
            Text(
                "真实提交走官方同源端点（threadAdd）；结果未知时绝不自动重发。",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
