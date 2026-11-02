package io.github.deserthouse.sdgun.feature.community

import androidx.compose.foundation.clickable
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
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import io.github.deserthouse.sdgun.core.model.UiState
import io.github.deserthouse.sdgun.data.repository.BoardListRepository
import io.github.deserthouse.sdgun.data.repository.ProfileRepository
import kotlinx.coroutines.launch

/**
 * Q5（A1-10）搜索原生页：圈子（allTopicSearch）+ 用户（userSearch）双通道。
 * 帖子全文搜索官方无 API（api_survey 定案）——页内给站内 H5 搜索入口（诚实桥接）。
 * fetch 函数注入（JVM 可测由 Repository 纯函数承担；本页=组装+状态）。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SearchPage(
    onBack: () -> Unit,
    onOpenUrl: (String) -> Unit,
    onOpenUser: (Long) -> Unit,
    searchTopics: suspend (String) -> List<BoardListRepository.TopicSearchRow>?,
    searchUsers: suspend (String) -> List<ProfileRepository.PersonItem>?,
) {
    var mode by remember { mutableStateOf(0) }  // 0=圈子 1=用户
    var query by remember { mutableStateOf("") }
    var topicsState by remember {
        mutableStateOf<UiState<List<BoardListRepository.TopicSearchRow>>>(UiState.Empty("输入关键词搜索圈子"))
    }
    var usersState by remember {
        mutableStateOf<UiState<List<ProfileRepository.PersonItem>>>(UiState.Empty("输入关键词搜索用户"))
    }
    val scope = rememberCoroutineScope()

    fun runSearch() {
        val kw = query.trim()
        if (kw.isBlank()) return
        when (mode) {
            0 -> {
                topicsState = UiState.InitialLoading
                scope.launch {
                    topicsState = when (val r = searchTopics(kw)) {
                        null -> UiState.RetryableError("圈子搜索失败，可重试")
                        else -> if (r.isEmpty()) UiState.Empty("未找到相关圈子")
                        else UiState.Data(r)
                    }
                }
            }
            1 -> {
                usersState = UiState.InitialLoading
                scope.launch {
                    usersState = when (val r = searchUsers(kw)) {
                        null -> UiState.RetryableError("用户搜索失败，可重试")
                        else -> if (r.isEmpty()) UiState.Empty("未找到相关用户")
                        else UiState.Data(r)
                    }
                }
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("搜索", maxLines = 1) },
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
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp),
                label = { Text(if (mode == 0) "搜索圈子" else "搜索用户") },
                singleLine = true,
                trailingIcon = {
                    TextButton(onClick = { runSearch() }, enabled = query.isNotBlank()) { Text("搜索") }
                },
            )
            TabRow(selectedTabIndex = mode) {
                Tab(selected = mode == 0, onClick = { mode = 0 }, text = { Text("圈子") })
                Tab(selected = mode == 1, onClick = { mode = 1 }, text = { Text("用户") })
            }
            when (mode) {
                0 -> when (val st = topicsState) {
                    is UiState.InitialLoading -> Column(
                        Modifier.fillMaxSize().padding(16.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) { Text("搜索中…", style = MaterialTheme.typography.bodyLarge) }
                    is UiState.Empty -> EmptyHint(st.message ?: "暂无结果")
                    is UiState.RetryableError -> Column(
                        Modifier.fillMaxSize().padding(16.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Text(st.error, style = MaterialTheme.typography.bodyLarge)
                        TextButton(onClick = { runSearch() }) { Text("重试") }
                    }
                    is UiState.Data<*> -> LazyColumn(
                        Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(vertical = 4.dp),
                    ) {
                        @Suppress("UNCHECKED_CAST")
                        items(st.value as List<BoardListRepository.TopicSearchRow>,
                            key = { it.link ?: it.name }) { row ->
                            ListItem(
                                headlineContent = { Text(row.name) },
                                supportingContent = { row.link?.let { Text(it, maxLines = 1) } },
                                modifier = Modifier.fillMaxWidth(),
                            )
                        }
                        item(key = "topic_hint") {
                            Text(
                                "圈子行点击经链接打开（站方板块链接自动升级原生页）",
                                Modifier.padding(16.dp),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                    else -> {}
                }
                1 -> when (val st = usersState) {
                    is UiState.InitialLoading -> Column(
                        Modifier.fillMaxSize().padding(16.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) { Text("搜索中…", style = MaterialTheme.typography.bodyLarge) }
                    is UiState.Empty -> EmptyHint(st.message ?: "暂无结果")
                    is UiState.RetryableError -> Column(
                        Modifier.fillMaxSize().padding(16.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Text(st.error, style = MaterialTheme.typography.bodyLarge)
                        TextButton(onClick = { runSearch() }) { Text("重试") }
                    }
                    is UiState.Data<*> -> LazyColumn(
                        Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(vertical = 4.dp),
                    ) {
                        @Suppress("UNCHECKED_CAST")
                        items(st.value as List<ProfileRepository.PersonItem>,
                            key = { it.userId }) { u ->
                            ListItem(
                                headlineContent = { Text(u.name) },
                                modifier = Modifier.fillMaxWidth()
                                    .clickable(enabled = u.userId > 0) { onOpenUser(u.userId) },
                            )
                        }
                        item(key = "user_hint") {
                            Text(
                                "点击用户行进入其主页",
                                Modifier.padding(16.dp),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                    else -> {}
                }
            }
            // 帖子全文搜索：官方无 API（api_survey 定案）——站内 H5 诚实桥接
            Row(
                Modifier.fillMaxWidth().padding(12.dp),
                horizontalArrangement = Arrangement.Center,
            ) {
                TextButton(onClick = {
                    onOpenUrl("https://bbs.sdgun.com.cn/search.php?mod=forum&mobile=2")
                }) { Text("帖子全文搜索（站内 H5）→") }
            }
        }
    }
}

@Composable
private fun EmptyHint(text: String) {
    Column(
        Modifier.fillMaxSize().padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) { Text(text, style = MaterialTheme.typography.bodyLarge) }
}
