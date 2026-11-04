package io.github.deserthouse.sdgun.feature.messages

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import io.github.deserthouse.sdgun.data.repository.ProfileRepository

/** 消息中心（G3 + U3-b）：组列表→会话详情入口 + 通知中心入口。 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MessagesPage(
    groups: List<ProfileRepository.MessageGroup>?,
    isLoading: Boolean,
    onOpenGroup: (ProfileRepository.MessageGroup) -> Unit = {},
    onOpenNotifications: () -> Unit = {},
    /** WP6/S07：失败/鉴权态恢复动作（null=不显失败面） */
    onRetry: (() -> Unit)? = null,
    onGoLogin: (() -> Unit)? = null,
    errorText: String? = null,
    modifier: Modifier = Modifier,
) {
    // A28-03 单一壳契约：顶栏归 TabShell（title="消息"），页内不再持有 TopAppBar
    Scaffold(
        modifier = modifier,
        containerColor = MaterialTheme.colorScheme.surface,
    ) { padding ->
        if (isLoading) {
            Column(Modifier.fillMaxSize().padding(padding),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center) {
                CircularProgressIndicator()
            }
        } else if (groups == null) {
            // A28-06：fetch 失败不再吞成"暂无消息"；WP6/S07：给重试/重登动作
            Column(Modifier.fillMaxSize().padding(padding),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center) {
                Text(errorText ?: "消息加载失败", style = MaterialTheme.typography.bodyLarge)
                if (errorText == "登录后可查看消息") {
                    onGoLogin?.let { login ->
                        Button(onClick = login, modifier = Modifier.padding(top = 12.dp)) {
                            Text("去登录")
                        }
                    }
                } else {
                    onRetry?.let { retry ->
                        TextButton(onClick = retry, modifier = Modifier.padding(top = 12.dp)) {
                            Text("重试")
                        }
                    }
                }
            }
        } else {
            LazyColumn(Modifier.fillMaxSize().padding(padding),
                contentPadding = PaddingValues(vertical = 6.dp)) {
                // F21 通知中心入口（置顶）——userNotifyIndex 分类摘要页
                item(key = "notifications") {
                    ListItem(
                        headlineContent = { Text("通知中心") },
                        supportingContent = { Text("回复/赞/系统通知分类摘要") },
                        modifier = Modifier.clickableRow { onOpenNotifications() },
                    )
                }
                if (groups.isEmpty()) {
                    item(key = "empty") {
                        Column(Modifier.fillMaxWidth().padding(top = 120.dp),
                            horizontalAlignment = Alignment.CenterHorizontally) {
                            Text("暂无消息", style = MaterialTheme.typography.bodyLarge)
                        }
                    }
                } else {
                    items(groups, key = { it.id }) { g ->
                        ListItem(
                            headlineContent = {
                                // B1-6：未读感=加粗+尾部数字徽章（真实未读位=凭据门挂账）
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(
                                        g.name,
                                        fontWeight = if (g.count > 0) FontWeight.SemiBold else null,
                                    )
                                    Spacer(Modifier.width(8.dp))
                                    if (g.count > 0) {
                                        androidx.compose.material3.BadgedBox(
                                            badge = {
                                                androidx.compose.material3.Badge { Text("${g.count}") }
                                            },
                                        ) { Spacer(Modifier.width(2.dp)) }
                                    }
                                }
                            },
                            supportingContent = { Text("${g.count} 条") },
                            modifier = Modifier.clickableRow { onOpenGroup(g) },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun Modifier.clickableRow(onClick: () -> Unit): Modifier =
    this.clickable(onClick = onClick)
