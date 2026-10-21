package io.github.deserthouse.sdgun.feature.community

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.github.deserthouse.sdgun.core.model.UiState
import io.github.deserthouse.sdgun.data.repository.BoardListRepository

/**
 * 板块页（G2-A04）：forumView 七排序+forumDetail 元数据/筛选 chips+置顶区隔。
 * UiState 覆盖初始/加载/数据/空/失败；排序切换先取消旧请求再重置游标。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BoardPage(
    state: UiState<BoardListRepository.PageResult>,
    meta: BoardListRepository.BoardMeta?,
    onOpenThread: (Long, String) -> Unit,
    modifier: Modifier = Modifier,
) {
    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        meta?.name ?: "板块",
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                ),
            )
        },
        containerColor = MaterialTheme.colorScheme.surface,
    ) { padding ->
        when (state) {
            is UiState.InitialLoading -> Column(
                Modifier.fillMaxSize().padding(padding),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) { Text("加载中…", style = MaterialTheme.typography.bodyLarge) }

            is UiState.Empty -> Column(
                Modifier.fillMaxSize().padding(padding),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                Text("该板块暂无帖子", style = MaterialTheme.typography.bodyLarge)
            }

            is UiState.RetryableError -> Column(
                Modifier.fillMaxSize().padding(padding),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                Text(state.error, style = MaterialTheme.typography.bodyLarge)
            }

            is UiState.Stale<*> -> {
                val pr = state.stale as? BoardListRepository.PageResult
                if (pr != null) {
                    Column(Modifier.padding(padding)) {
                        Text(
                            state.error,
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.error,
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                        )
                        pr.rows.forEach { row ->
                            ThreadRowItem(row) { onOpenThread(row.tid, row.title) }
                        }
                    }
                }
            }

            is UiState.Data<*> -> {
                val pr = state.value as? BoardListRepository.PageResult
                if (pr != null) {
                    Column(Modifier.padding(padding)) {
                        pr.rows.forEach { row ->
                            ThreadRowItem(row) { onOpenThread(row.tid, row.title) }
                        }
                    }
                }
            }

            else -> {}
        }
    }
}

@Composable
private fun ThreadRowItem(
    row: BoardListRepository.ThreadRow,
    onClick: () -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxSize().padding(horizontal = 12.dp, vertical = 4.dp),
        onClick = onClick,
    ) {
        Column(Modifier.padding(12.dp)) {
            Text(
                if (row.isTop) "[置顶] ${row.title}" else row.title,
                style = MaterialTheme.typography.titleSmall,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.height(4.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                row.authorName?.let {
                    Text(it, style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                row.replyCount?.let {
                    Text("回复 $it", style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                row.viewCount?.let {
                    Text("查看 $it", style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                row.lastReplyTime?.let {
                    Text(it, style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
        }
    }
}
