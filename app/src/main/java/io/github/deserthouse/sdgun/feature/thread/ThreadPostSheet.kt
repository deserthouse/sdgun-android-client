package io.github.deserthouse.sdgun.feature.thread

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.FilterChip
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
import io.github.deserthouse.sdgun.core.model.UiState
import kotlinx.coroutines.withContext

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
    /** Q3：分类 id（threadAddColumnInfoV2 data.types 选择；null=未选/板块无分类） */
    val typeId: Int? = null,
) {
    fun validate(): String? = when {
        title.isBlank() -> "请填写标题"
        content.isBlank() -> "请填写正文"
        fid <= 0 -> "缺少板块参数"
        else -> null
    }

    /** THREAD_POST 表单（jadx 同构：title/content/fid/circle_id/type_id；auth/users/vest 本版不带） */
    fun toParams(): Map<String, String> {
        val m = mutableMapOf(
            "title" to title.trim(),
            "content" to content,
            "fid" to fid.toString(),
            "circle_id" to circleId.toString(),
        )
        if (typeId != null) m["type_id"] = typeId.toString()
        return m
    }
}

/** 附件任务（R2/A3-1 升级：UUID 防 id 冲突；UPLOADED 态+url 供资源序列化；Q3：+uri 供真实传输读取） */
data class UploadTaskItem(
    val uuid: String,
    val name: String,
    val state: UploadState,
    val message: String? = null,
    val url: String? = null,
    /** Q3：内容 Uri（真实传输读取字节用；合成任务为 null） */
    val uri: String? = null,
)

enum class UploadState { WAITING, RUNNING, UPLOADED, FAILED }

/** Q3：上传结果（区别于 Boolean——成功必须携带服务端 URL 才算可用于序列化） */
sealed class UploadOutcome {
    data class Ok(val url: String?, val aid: String? = null) : UploadOutcome()
    data class Fail(val msg: String) : UploadOutcome()
}

/**
 * 传输后端抽象：Q3 起真实实现=[HttpUploadTransport]（uploadPicNew multipart，
 * jadx LocalFileUpload 实锤：字段 type=share/filename=imgs/imgs=文件，响应 data.aid+url）。
 * 测试/合成注入 [FakeUploadTransport]。
 */
fun interface UploadTransport {
    suspend fun upload(task: UploadTaskItem, onProgress: (Int) -> Unit): UploadOutcome
}

/** 合成不可用传输（游客/未带 token 时诚实失败，不假称可用） */
val HonestUnavailableUpload: UploadTransport = UploadTransport { _, _ ->
    UploadOutcome.Fail("传输后端未就绪（游客态不可上传）")
}

/** debug/合成验证：可脚本化的成功/失败假传输（零触网） */
class FakeUploadTransport(private val ok: Boolean = true, private val url: String = "fake://img") :
    UploadTransport {
    override suspend fun upload(task: UploadTaskItem, onProgress: (Int) -> Unit): UploadOutcome =
        if (ok) UploadOutcome.Ok(url) else UploadOutcome.Fail("（合成）上传失败")
}

object UploadMultipart {

    /**
     * multipart/form-data 体构造（纯函数，JVM 可测）：
     * 官方形态（jadx LocalFileUpload.onUpload）：type=share / filename=imgs / imgs=文件 / time_stamp。
     */
    fun build(
        boundary: String,
        fields: Map<String, String>,
        fileField: String,
        fileName: String,
        fileBytes: ByteArray,
        fileMime: String,
    ): ByteArray {
        val head = StringBuilder()
        for ((k, v) in fields) {
            head.append("--").append(boundary).append("\r\n")
                .append("Content-Disposition: form-data; name=\"").append(k).append("\"\r\n\r\n")
                .append(v).append("\r\n")
        }
        head.append("--").append(boundary).append("\r\n")
            .append("Content-Disposition: form-data; name=\"").append(fileField)
            .append("\"; filename=\"").append(fileName).append("\"\r\n")
            .append("Content-Type: ").append(fileMime).append("\r\n\r\n")
        val headBytes = head.toString().toByteArray(Charsets.UTF_8)
        val tail = "\r\n--$boundary--\r\n".toByteArray(Charsets.UTF_8)
        return headBytes + fileBytes + tail
    }

    /** 按文件名猜 MIME（仅图片族；其余 octet-stream） */
    fun mimeOf(name: String): String = when {
        name.endsWith(".png", true) -> "image/png"
        name.endsWith(".gif", true) -> "image/gif"
        name.endsWith(".webp", true) -> "image/webp"
        name.endsWith(".jpg", true) || name.endsWith(".jpeg", true) -> "image/jpeg"
        else -> "application/octet-stream"
    }
}

/** Q3 真实上传传输：uploadPicNew multipart（token 走 MAGAPPX UA 第 8 段，与写面同源）。 */
class HttpUploadTransport(
    private val context: android.content.Context,
    private val token: String,
) : UploadTransport {

    override suspend fun upload(task: UploadTaskItem, onProgress: (Int) -> Unit): UploadOutcome {
        val uriStr = task.uri ?: return UploadOutcome.Fail("任务缺少本地文件引用")
        val bytes = try {
            context.contentResolver.openInputStream(android.net.Uri.parse(uriStr))?.use { it.readBytes() }
        } catch (e: Exception) { null } ?: return UploadOutcome.Fail("读取本地文件失败")
        if (bytes.size > 8 * 1024 * 1024) return UploadOutcome.Fail("图片超过 8MB 限制")
        val boundary = "sdgun" + java.util.UUID.randomUUID().toString().replace("-", "")
        val fields = mapOf(
            "type" to "share",
            "filename" to "imgs",
            "time_stamp" to (System.currentTimeMillis() / 1000).toString(),
        )
        val body = UploadMultipart.build(
            boundary, fields, "imgs", task.name, bytes, UploadMultipart.mimeOf(task.name),
        )
        return withContext(kotlinx.coroutines.Dispatchers.IO) {
            try {
                val conn = java.net.URL(UPLOAD_PIC_NEW).openConnection()
                    as java.net.HttpURLConnection
                conn.requestMethod = "POST"
                conn.doOutput = true
                conn.connectTimeout = 15000
                conn.readTimeout = 30000
                conn.setRequestProperty("User-Agent", magappxUa(token))
                conn.setRequestProperty("Content-Type", "multipart/form-data; boundary=$boundary")
                conn.setFixedLengthStreamingMode(body.size)
                try {
                    conn.outputStream.use { out ->
                        var sent = 0
                        var i = 0
                        while (i < body.size) {
                            val n = minOf(8192, body.size - i)
                            out.write(body, i, n)
                            i += n; sent += n
                            onProgress(sent * 100 / body.size)
                        }
                    }
                    val code = conn.responseCode
                    val text = (if (code in 200..299) conn.inputStream else conn.errorStream)
                        ?.bufferedReader()?.readText()
                    parseUploadResponse(code, text)
                } finally { conn.disconnect() }
            } catch (e: Exception) {
                UploadOutcome.Fail("上传失败：${e.message ?: "网络错误"}")
            }
        }
    }

    companion object {
        /** jadx API.java:155 实锤 */
        const val UPLOAD_PIC_NEW = "https://mag1.sdgun.net/core/attachment/attachment/uploadPicNew"

        /** 响应判定（A28-02 同口径：非 2xx 不得 Ok；success 严格布尔；url 缺失=Ok(null) 上层显错） */
        fun parseUploadResponse(httpCode: Int, body: String?): UploadOutcome {
            if (httpCode !in 200..299) return UploadOutcome.Fail("HTTP $httpCode")
            return try {
                val obj = org.json.JSONObject(body ?: return UploadOutcome.Fail("响应为空"))
                val success = obj.opt("success")
                if (success !is Boolean) return UploadOutcome.Fail("响应信封不可辨")
                if (!success) return UploadOutcome.Fail(obj.optString("msg").ifBlank { "服务端拒绝" })
                val data = obj.optJSONObject("data")
                UploadOutcome.Ok(
                    url = data?.optString("url")?.takeIf { it.isNotBlank() }
                        ?: obj.optString("url").takeIf { it.isNotBlank() },
                    aid = data?.optString("aid")?.takeIf { it.isNotBlank() },
                )
            } catch (e: Exception) { UploadOutcome.Fail("响应解析失败") }
        }
    }

    private fun magappxUa(token: String): String =
        "Mozilla/5.0 (Linux; Android 16; Pixel 8) MAGAPPX|6.4.0-2.90-30042|Android 16 google Pixel 8|shuidan|koPx4DY3tw7hrg5nKpSmc3|88af41c4c24832860d97d44e436f8842|174e1e66702c31aa7e949f5ab30db60b|$token"
}

/** 附件队列状态机（组合外可测的纯推进逻辑） */
object UploadQueueLogic {

    /**
     * 重试一轮：等待/失败→RUNNING→按 transport 结果收尾。
     * Q3：Ok(url)→UPLOADED+url（url 缺失=失败——无服务端地址无法序列化，显错不假称成功）。
     */
    suspend fun runTask(
        task: UploadTaskItem,
        transport: UploadTransport,
        update: (UploadTaskItem) -> Unit,
    ) {
        if (task.state == UploadState.RUNNING || task.state == UploadState.UPLOADED) return
        update(task.copy(state = UploadState.RUNNING, message = null))
        val outcome = transport.upload(task) { }
        update(
            when (outcome) {
                is UploadOutcome.Ok ->
                    if (!outcome.url.isNullOrBlank()) task.copy(
                        state = UploadState.UPLOADED, message = null, url = outcome.url,
                    ) else task.copy(
                        state = UploadState.FAILED, message = "上传响应缺少图片地址",
                    )
                is UploadOutcome.Fail -> task.copy(state = UploadState.FAILED, message = outcome.msg)
            }
        )
    }

    /**
     * R2/A3-3：投稿资源序列化（纯函数）——UPLOADED 资源以附件行并入正文尾部；
     * 未上传资源不序列化（由调用方阻断提交）。无资源=原样返回。
     */
    fun serializeUploadedInto(content: String, tasks: List<UploadTaskItem>): String {
        val uploaded = tasks.filter { it.state == UploadState.UPLOADED && !it.url.isNullOrBlank() }
        if (uploaded.isEmpty()) return content
        val lines = uploaded.joinToString("\n") { "[附件] ${it.name} ${it.url}" }
        return content.trimEnd() + "\n\n" + lines
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
    initialTitle: String = "",
    initialContent: String = "",
    onDraftChange: ((String, String) -> Unit)? = null,
    onDismiss: () -> Unit,
    /** Q3：typeId 参数贯通（threadAdd type_id；jadx toPost 同构） */
    onSubmit: (title: String, content: String, typeId: Int?) -> Unit,
    /** Q3：分类 chips（threadAddColumnInfoV2 data.types；空=板块无分类不显示） */
    postTypes: List<Pair<Int, String>> = emptyList(),
    /** Q3：column-info 就绪门——false 时禁发布（circle_id 必须经服务端换取，fid≠circle_id） */
    circleIdReady: Boolean = true,
    /** Q3：column-info 拉取失败提示+重试（板块页 onPostThread 打开时触发拉取） */
    columnInfoError: String? = null,
    onRetryColumnInfo: () -> Unit = {},
    onPickImages: () -> Unit = {},
    onRetryUpload: (UploadTaskItem) -> Unit = {},
    onRemoveUpload: (UploadTaskItem) -> Unit = {},
    onLogin: (() -> Unit)? = null,
    uploadTasks: List<UploadTaskItem> = emptyList(),
) {
    var title by remember { mutableStateOf(initialTitle) }
    var content by remember { mutableStateOf(initialContent) }
    val localError = remember { mutableStateOf<String?>(null) }
    var selectedTypeId by remember(postTypes) { mutableStateOf<Int?>(null) }
    var preview by remember { mutableStateOf(false) }
    // Q3（S03 同款）：Unknown 核对门——首次 Unknown 禁直发重试，显式核对后放行一次
    var unknownAcked by remember { mutableStateOf(false) }
    if (mutationState !is MutationState.OutcomeUnknown) unknownAcked = false
    // R2/A3-4：草稿自动保存（title/content 任一变化即落盘）
    fun pushDraft() { onDraftChange?.invoke(title, content) }

    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 8.dp)
                // 装机取证修正（Q3）：Q3 增高表单后发布钮被压进手势条区
                // （bounds y2355-2400，tap 落导航条）——导航栏避让+底部余量
                .navigationBarsPadding()
                .padding(bottom = 16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text(
                "发帖到「$boardName」",
                style = MaterialTheme.typography.titleMedium,
            )
            if (!authed) {
                // N5/V03（A31）：游客给可执行下一步——登录 CTA（表单内容已自动保存，登录回来恢复）
                Column {
                    Text(
                        "登录后即可发布（当前输入已自动保存）",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                    )
                    onLogin?.let { login ->
                        TextButton(onClick = login) { Text("去登录") }
                    }
                }
            }
            OutlinedTextField(
                value = title,
                onValueChange = { title = it; onDraftChange?.invoke(title, content) },
                label = { Text("标题") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            // Q3：分类 chips（threadAddColumnInfoV2 data.types；jadx TypesBean id+name）
            if (postTypes.isNotEmpty()) {
                Text("分类", style = MaterialTheme.typography.titleSmall)
                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    items(
                        listOf(-1 to "不选") + postTypes,
                        key = { "pt_${it.first}" },
                    ) { (id, name) ->
                        FilterChip(
                            selected = selectedTypeId == if (id == -1) null else id,
                            onClick = { selectedTypeId = if (id == -1) null else id },
                            label = { Text(name) },
                        )
                    }
                }
            }
            // Q3：预览切换（编辑/预览双模式；预览=纯文本视图，不渲染 BBCode/HTML）
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                TextButton(onClick = { preview = !preview }) {
                    Text(if (preview) "返回编辑" else "预览")
                }
            }
            if (preview) {
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(
                            title.ifBlank { "（无标题）" },
                            style = MaterialTheme.typography.titleMedium,
                        )
                        selectedTypeId?.let { id ->
                            Text(
                                "分类：" + (postTypes.firstOrNull { it.first == id }?.second ?: "$id"),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        Text(
                            content.ifBlank { "（无正文）" },
                            style = MaterialTheme.typography.bodyMedium,
                        )
                        uploadTasks.filter { it.state == UploadState.UPLOADED }.forEach {
                            Text(
                                "[附件] ${it.name}",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            } else {
            OutlinedTextField(
                value = content,
                onValueChange = { content = it; onDraftChange?.invoke(title, content) },
                label = { Text("正文") },
                minLines = 4,
                modifier = Modifier.fillMaxWidth(),
            )
            }

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
                            UploadState.UPLOADED -> Text(
                                "已上传", color = MaterialTheme.colorScheme.primary)
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
            columnInfoError?.let {
                Column {
                    Text(it, color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall)
                    TextButton(onClick = onRetryColumnInfo) { Text("重试获取板块参数") }
                }
            }
            when (val st = mutationState) {
                is MutationState.Rejected -> Text(
                    "发布被拒：${st.reason}",
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodySmall,
                )
                MutationState.OutcomeUnknown -> Column {
                    Text(
                        "结果待确认（超时未获应答）——不自动重发，请先核对是否已发布成功",
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall,
                    )
                    if (!unknownAcked) {
                        TextButton(onClick = { unknownAcked = true }) {
                            Text("我已核对，未发布成功（允许重发一次）")
                        }
                    }
                }
                is MutationState.Confirmed -> Text(
                    "发布成功",
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
                    if (localError.value == null) onSubmit(title.trim(), content, selectedTypeId)
                },
                enabled = !submitting && authed && circleIdReady &&
                    (mutationState !is MutationState.OutcomeUnknown || unknownAcked),
                modifier = Modifier.fillMaxWidth().height(48.dp),
            ) {
                if (submitting) {
                    CircularProgressIndicator(Modifier.height(22.dp), strokeWidth = 2.dp)
                } else {
                    Text(
                        when {
                            !authed -> "需登录后发布"
                            !circleIdReady -> "板块参数获取中"
                            else -> "发布"
                        }
                    )
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
