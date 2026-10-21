package io.github.deserthouse.sdgun.feature.messages

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import io.github.deserthouse.sdgun.data.repository.ProfileRepository

/** 消息中心（G3）：私信会话+通知分类摘要。 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MessagesPage(
    groups: List<ProfileRepository.MessageGroup>,
    isLoading: Boolean,
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
        } else if (groups.isEmpty()) {
            Column(Modifier.fillMaxSize().padding(padding),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center) {
                Text("暂无消息", style = MaterialTheme.typography.bodyLarge)
            }
        } else {
            LazyColumn(Modifier.fillMaxSize().padding(padding),
                contentPadding = PaddingValues(vertical = 6.dp)) {
                items(groups, key = { it.id }) { g ->
                    ListItem(headlineContent = { Text(g.name) },
                        supportingContent = { Text("${g.count} 条") })
                }
            }
        }
    }
}

private typealias PaddingValues = androidx.compose.foundation.layout.PaddingValues
