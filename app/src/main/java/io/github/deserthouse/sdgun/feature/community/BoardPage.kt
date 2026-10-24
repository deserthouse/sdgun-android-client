package io.github.deserthouse.sdgun.feature.community

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
 * R5（U3-a）：orders/threadTypes chips 接 VM（筛选变化=新代际重拉）；meta 在手即常驻显示
 * （筛选切换的 InitialLoading 期间 chips 不消失，防行高跳变）。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BoardPage(
    state: UiState<BoardListRepository.PageResult>,
    meta: BoardListRepository.BoardMeta?,
    onOpenThread: (Long, String) -> Unit,
    onBack: () -> Unit = {},
    onLoadMore: (() -> Unit)? = null,
    currentOrder: String = "",
    currentTypeId: String = "",
    onFilterChange: ((String, String) -> Unit)? = null,
    onPostThread: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                    }
                },
                title = {
                    Text(
                        meta?.name ?: "板块",
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                },
                actions = {
                    if (onPostThread != null) {
                        IconButton(onClick = onPostThread) {
                            Icon(Icons.Filled.Edit, contentDescription = "发帖")
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                ),
            )
        },
        containerColor = MaterialTheme.colorScheme.surface,
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            val m = meta
            if (m != null && (m.orders.isNotEmpty() || m.threadTypes.isNotEmpty())) {
                BoardFilterBar(
                    meta = m,
                    currentOrder = currentOrder,
                    currentTypeId = currentTypeId,
                    enabled = onFilterChange != null,
                    onFilter = { o, t -> onFilterChange?.invoke(o, t) },
                )
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
                ) {
                    Text("该板块暂无帖子", style = MaterialTheme.typography.bodyLarge)
                }

                is UiState.RetryableError -> Column(
                    Modifier.fillMaxSize(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                ) {
                    Text(state.error, style = MaterialTheme.typography.bodyLarge)
                }

                is UiState.AuthRequired -> Column(
                    Modifier.fillMaxSize(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                ) {
                    Text("该板块需要登录后查看", style = MaterialTheme.typography.bodyLarge)
                }

                is UiState.Stale<*> -> {
                    val pr = state.stale as? BoardListRepository.PageResult
                    if (pr != null) {
                        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
                            Text(
                                state.error,
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.error,
                                modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                            )
                            pr.rows.forEach { row ->
                                ThreadRowItem(row) { onOpenThread(row.tid, row.title) }
                            }
                            MoreRow(pr, onLoadMore)
                        }
                    }
                }

                is UiState.Data<*> -> {
                    val pr = state.value as? BoardListRepository.PageResult
                    if (pr != null) {
                        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
                            pr.rows.forEach { row ->
                                ThreadRowItem(row) { onOpenThread(row.tid, row.title) }
                            }
                            MoreRow(pr, onLoadMore)
                        }
                    }
                }

                else -> {}
            }
        }
    }
}

/** R5 筛选条：排序一行+类型一行（类型含"全部"空选）；组内单选，同值点击 no-op（VM 层拦截）。 */
@Composable
private fun BoardFilterBar(
    meta: BoardListRepository.BoardMeta,
    currentOrder: String,
    currentTypeId: String,
    enabled: Boolean,
    onFilter: (String, String) -> Unit,
) {
    if (meta.orders.isNotEmpty()) {
        // 服务端 orders 可能不含空值默认项，前置补齐
        val orders = if (meta.orders.any { it.first.isEmpty() }) meta.orders
        else listOf("" to "默认") + meta.orders
        LazyRow(
            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            items(orders, key = { "o_${it.first}" }) { (value, label) ->
                FilterChip(
                    selected = currentOrder == value,
                    onClick = { onFilter(value, currentTypeId) },
                    enabled = enabled,
                    label = { Text(label) },
                )
            }
        }
    }
    if (meta.threadTypes.isNotEmpty()) {
        val types = listOf("" to "全部") + meta.threadTypes
        LazyRow(
            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            items(types, key = { "t_${it.first}" }) { (value, label) ->
                FilterChip(
                    selected = currentTypeId == value,
                    onClick = { onFilter(currentOrder, value) },
                    enabled = enabled,
                    label = { Text(label) },
                )
            }
        }
    }
}

/** 下一页入口：仓库 total_page 权威（A70-06 前置：板块不因首页渲染而丢失第二页） */
@Composable
private fun MoreRow(pr: BoardListRepository.PageResult, onLoadMore: (() -> Unit)?) {
    if (onLoadMore != null && pr.page < pr.totalPages) {
        TextButton(
            onClick = onLoadMore,
            modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
        ) { Text("加载更多") }
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
