package io.github.deserthouse.sdgun.feature.messages

import android.Manifest
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import io.github.deserthouse.sdgun.core.model.UiState

/**
 * U3-b（F20/21）：消息会话详情+通知中心（合成先行）。
 * 会话详情读端点参数测绘标注「待用」（server_topology §5）——真实数据=凭据增量；
 * 发送=写面封存（诚实禁用，同 G4 发布诚实禁用先例）。
 * 通知中心形状按 G3 实测：userNotifyIndex=分类摘要对象、无 list。
 * 权限拒绝态：POST_NOTIFICATIONS 运行时权限被拒→横幅+系统设置入口（F21 路由项）。
 */

/** 会话气泡（合成夹具同形）：me=true=自己发的（右侧） */
data class ChatBubble(val text: String, val time: String, val me: Boolean)

data class ConversationData(val bubbles: List<ChatBubble>)

fun syntheticConversation(groupName: String): ConversationData = ConversationData(
    listOf(
        ChatBubble("（合成）你好，想请教一下 $groupName 里提到的装备", "10-21 09:12", me = false),
        ChatBubble("（合成）可以的，具体看帖子里的配置单", "10-21 12:30", me = true),
        ChatBubble("（合成）好的，感谢！", "10-21 12:31", me = false),
    )
)

/** 通知分类摘要（userNotifyIndex 形状：分类对象无 list） */
data class NotifyCategory(val name: String, val summary: String, val route: String)

data class NotifyData(val categories: List<NotifyCategory>)

fun syntheticNotify(): NotifyData = NotifyData(
    listOf(
        NotifyCategory("回复我的", "（合成）3 条新回复，最新：你的帖子有新回复", "route:focus"),
        NotifyCategory("收到的赞", "（合成）5 个新赞", "route:focus"),
        NotifyCategory("系统通知", "（合成）社区公告：新版客户端内测", "route:focus"),
    )
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ConversationDetailPage(
    groupName: String,
    data: ConversationData?,
    isLoading: Boolean,
    onBack: () -> Unit,
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(groupName, maxLines = 1) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface),
            )
        },
        containerColor = MaterialTheme.colorScheme.surface,
        bottomBar = {
            // 写面封存（真实发送=用户 MANUAL 验收后解锁）——诚实禁用不冒充可用
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                OutlinedTextField(
                    value = "",
                    onValueChange = {},
                    modifier = Modifier.weight(1f),
                    enabled = false,
                    placeholder = { Text("消息发送待真实验收后开放") },
                    singleLine = true,
                )
                Icon(
                    Icons.AutoMirrored.Filled.Send,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.38f),
                )
            }
        },
    ) { padding ->
        when {
            isLoading -> Column(
                Modifier.fillMaxSize().padding(padding),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) { Text("加载中…", style = MaterialTheme.typography.bodyLarge) }

            data == null -> Column(
                Modifier.fillMaxSize().padding(padding),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) { Text("会话详情读取待接口接入", style = MaterialTheme.typography.bodyLarge) }

            else -> LazyColumn(
                Modifier.fillMaxSize().padding(padding),
                contentPadding = PaddingValues(12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                items(data.bubbles) { b ->
                    Row(
                        Modifier.fillMaxWidth(),
                        horizontalArrangement = if (b.me) Arrangement.End else Arrangement.Start,
                    ) {
                        Surface(
                            color = if (b.me) MaterialTheme.colorScheme.primaryContainer
                            else MaterialTheme.colorScheme.surfaceVariant,
                            shape = MaterialTheme.shapes.medium,
                        ) {
                            Column(Modifier.padding(10.dp)) {
                                Text(b.text)
                                Text(
                                    b.time + if (b.me) "（合成）" else "",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NotificationPage(
    state: UiState<NotifyData>,
    onBack: () -> Unit,
    onRetry: () -> Unit,
) {
    val ctx = LocalContext.current
    // POST_NOTIFICATIONS 运行时权限（targetSdk 33+）：拒绝态给系统设置入口；
    // 声明在 manifest 缺席时 checkUriPermission 路径不适用——按声明与否呈现横幅
    val granted = androidx.core.content.ContextCompat.checkSelfPermission(
        ctx, Manifest.permission.POST_NOTIFICATIONS,
    ) == android.content.pm.PackageManager.PERMISSION_GRANTED
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("通知中心") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface),
            )
        },
        containerColor = MaterialTheme.colorScheme.surface,
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            if (!granted) {
                // F21 权限拒绝态：可辨、可恢复（系统设置入口），不静默
                Card(
                    Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
                    colors = androidx.compose.material3.CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.errorContainer),
                ) {
                    Column(Modifier.padding(12.dp)) {
                        Text(
                            "通知权限未开启，无法接收新消息提醒",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onErrorContainer,
                        )
                        TextButton(onClick = {
                            ctx.startActivity(Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
                                .setData(Uri.parse("package:" + ctx.packageName)))
                        }) { Text("去系统设置开启") }
                    }
                }
            }
            when (state) {
                is UiState.InitialLoading -> Column(
                    Modifier.fillMaxSize(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                ) { Text("加载中…", style = MaterialTheme.typography.bodyLarge) }

                is UiState.Empty -> Column(
                    Modifier.fillMaxSize(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                ) { Text(state.message ?: "暂无通知", style = MaterialTheme.typography.bodyLarge) }

                is UiState.RetryableError -> Column(
                    Modifier.fillMaxSize().padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                ) {
                    Text(state.error, style = MaterialTheme.typography.bodyLarge)
                    Button(onClick = onRetry, modifier = Modifier.padding(top = 12.dp)) {
                        Text("重试")
                    }
                }

                is UiState.AuthRequired -> Column(
                    Modifier.fillMaxSize(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                ) { Text("登录后可查看通知", style = MaterialTheme.typography.bodyLarge) }

                is UiState.Data<*> -> {
                    val d = state.value as NotifyData
                    LazyColumn(
                        contentPadding = PaddingValues(vertical = 6.dp),
                    ) {
                        items(d.categories, key = { it.name }) { c ->
                            ListItem(
                                headlineContent = { Text(c.name) },
                                supportingContent = { Text(c.summary) },
                            )
                        }
                    }
                }

                else -> {}
            }
        }
    }
}
