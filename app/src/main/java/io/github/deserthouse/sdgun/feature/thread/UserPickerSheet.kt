package io.github.deserthouse.sdgun.feature.thread

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import io.github.deserthouse.sdgun.core.model.UiState
import io.github.deserthouse.sdgun.data.repository.ProfileRepository

/**
 * A3-4/X4：@ 用户选择器（共用组件——ReplySheet + ThreadPostSheet 均接入）。
 * 数据源=userSearch（Q5 已在 SearchPage 验证）；搜索→选取→回调 uid+name。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun UserPickerSheet(
    onDismiss: () -> Unit,
    onPick: (uid: Long, name: String) -> Unit,
    searchUsers: suspend (String) -> List<ProfileRepository.PersonItem>?,
) {
    var query by remember { mutableStateOf("") }
    var results by remember { mutableStateOf<List<ProfileRepository.PersonItem>?>(null) }
    var loading by remember { mutableStateOf(false) }

    LaunchedEffect(query) {
        if (query.isBlank()) {
            results = null; loading = false
            return@LaunchedEffect
        }
        loading = true
        results = searchUsers(query)
        loading = false
    }

    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            Modifier.fillMaxWidth().padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text("提及用户（@）", style = MaterialTheme.typography.titleMedium)
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("搜索用户名") },
                singleLine = true,
            )
            when {
                loading -> Text(
                    "搜索中…",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                query.isBlank() -> Text(
                    "输入关键词搜索用户",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                results == null -> Text(
                    "搜索失败，可重试",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                )
                results!!.isEmpty() -> Text(
                    "未找到用户",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                else -> LazyColumn(
                    Modifier.fillMaxWidth().height(280.dp),
                ) {
                    items(results!!, key = { it.userId }) { u ->
                        ListItem(
                            headlineContent = { Text(u.name) },
                            modifier = Modifier.fillMaxWidth()
                                .clickable {
                                    onPick(u.userId, u.name)
                                    onDismiss()
                                },
                        )
                    }
                }
            }
            Spacer(Modifier.height(16.dp))
        }
    }
}
