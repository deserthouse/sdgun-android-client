package io.github.deserthouse.sdgun.feature.messages

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import io.github.deserthouse.sdgun.data.repository.ProfileRepository

/** 消息中心（G3 + U3-b）：组列表→会话详情入口 + 通知中心入口。 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MessagesPage(
    groups: List<ProfileRepository.MessageGroup>,
    isLoading: Boolean,
    onOpenGroup: (ProfileRepository.MessageGroup) -> Unit = {},
    onOpenNotifications: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(title = { Text("消息") },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface))
        },
        containerColor = MaterialTheme.colorScheme.surface,
    ) { padding ->
        if (isLoading) {
            Column(Modifier.fillMaxSize().padding(padding),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center) {
                CircularProgressIndicator()
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
                            headlineContent = { Text(g.name) },
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
