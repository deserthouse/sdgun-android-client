package io.github.deserthouse.sdgun.feature.community

import androidx.compose.runtime.setValue
import androidx.compose.runtime.getValue
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.background
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
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
import androidx.compose.ui.input.nestedscroll.nestedScroll
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
    onRefresh: (() -> Unit)? = null,
    isLoadingMore: Boolean = false,
    currentOrder: String = "",
    currentTypeId: String = "",
    onFilterChange: ((String, String) -> Unit)? = null,
    onPostThread: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    // B1-2：顶栏随滚动收起（enterAlways）——列表回顶不遮挡（M3 列表页范式）
    val scrollBehavior = TopAppBarDefaults.enterAlwaysScrollBehavior()
    Scaffold(
        modifier = modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            TopAppBar(
                scrollBehavior = scrollBehavior,
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
                actions = {},  // WP2-A/V02：投稿动作唯一化（FAB 保留，顶栏入口退役）
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                ),
            )
        },
        containerColor = MaterialTheme.colorScheme.surface,
        floatingActionButton = {
            if (onPostThread != null) {
                androidx.compose.material3.ExtendedFloatingActionButton(
                    onClick = onPostThread,
                    icon = { Icon(Icons.Filled.Edit, contentDescription = null) },
                    text = { Text("发帖") },
                )
            }
        },
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
                is UiState.InitialLoading -> io.github.deserthouse.sdgun.core.designsystem.SkeletonList(
                    rows = 6, modifier = Modifier.fillMaxSize())

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
                        BoardRowsList(pr, onOpenThread, onLoadMore, staleError = state.error)
                    }
                }

                is UiState.Data<*>, is UiState.Refreshing<*> -> {
                    @Suppress("UNCHECKED_CAST")
                    val pr = (state as? UiState.Data<BoardListRepository.PageResult>)?.value
                        ?: (state as? UiState.Refreshing<BoardListRepository.PageResult>)?.stale
                    if (pr != null) {
                        io.github.deserthouse.sdgun.core.designsystem.B1PullToRefresh(
                            refreshing = state is UiState.Refreshing<*>,
                            onRefresh = { onRefresh?.invoke() },
                        ) {
                            BoardRowsList(pr, onOpenThread, onLoadMore, isLoadingMore = isLoadingMore)
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

/** B1-4：板块行 LazyColumn+尾部预取（替换 Column+verticalScroll+MoreRow 手动挡） */
@Composable
private fun BoardRowsList(
    pr: BoardListRepository.PageResult,
    onOpenThread: (Long, String) -> Unit,
    onLoadMore: (() -> Unit)?,
    staleError: String? = null,
    isLoadingMore: Boolean = false,
) {
    val listState = rememberLazyListState()
    io.github.deserthouse.sdgun.core.designsystem.InfiniteScrollHandler(
        listState = listState,
        isLoadingMore = isLoadingMore,
        hasMore = onLoadMore != null && pr.page < pr.totalPages,
        onLoadMore = { onLoadMore?.invoke() },
    )
    LazyColumn(
        state = listState,
        modifier = Modifier.fillMaxSize(),
        // WP2-A/V02：底栏余量防 FAB 遮末行
        contentPadding = androidx.compose.foundation.layout.PaddingValues(
            top = 6.dp, bottom = 120.dp),
    ) {
        if (staleError != null) {
            item(key = "stale") {
                Text(
                    staleError,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                )
            }
        }
        items(pr.rows, key = { it.tid }) { row ->
            ThreadRowItem(row) { onOpenThread(row.tid, row.title) }
        }
        if (onLoadMore != null && pr.page < pr.totalPages) {
            item(key = "load_more") {
                TextButton(
                    onClick = onLoadMore,
                    modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                ) { Text("加载更多") }
            }
        }
    }
}

@Composable
private fun ThreadRowItem(
    row: BoardListRepository.ThreadRow,
    onClick: () -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp),
        onClick = onClick,
    ) {
        Column(Modifier.padding(12.dp)) {
            // WP2-A：chips 独立（置顶/精华不拼标题）
            if (row.isTop || row.isDigest) {
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    if (row.isTop) RowBadge("置顶", MaterialTheme.colorScheme.error)
                    if (row.isDigest) RowBadge("精华", MaterialTheme.colorScheme.primary)
                }
                Spacer(Modifier.height(4.dp))
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                // 头像 36dp（无图=首字语义占位，复用 BoardIcon）
                BoardIcon(url = row.avatarUrl, fallbackName = row.authorName ?: row.title)
                Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        row.title,
                        style = MaterialTheme.typography.titleSmall,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                    // 摘要：仅有值才显示（0-2 行；不捏造、不 subject 回退）
                    row.summary?.let {
                        Spacer(Modifier.height(2.dp))
                        Text(
                            it,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
                // 右列封面 88×60（首图；多图角标；无图不留洞）
                row.coverUrl?.let { url ->
                    Spacer(Modifier.width(10.dp))
                    Box {
                        RowCover(url = url)
                        if (row.picCount > 1) {
                            androidx.compose.material3.Surface(
                                shape = MaterialTheme.shapes.small,
                                color = MaterialTheme.colorScheme.surface.copy(alpha = 0.85f),
                                modifier = Modifier.align(Alignment.BottomEnd).padding(3.dp),
                            ) {
                                Text(
                                    "${row.picCount}图",
                                    style = MaterialTheme.typography.labelSmall,
                                    modifier = Modifier.padding(horizontal = 5.dp, vertical = 1.dp),
                                )
                            }
                        }
                    }
                }
            }
            Spacer(Modifier.height(6.dp))
            // meta 独立一行（阅读语义去重：N 阅读）
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                row.authorName?.let {
                    Text(it, style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                row.replyCount?.let {
                    Text("回复 $it", style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                row.viewCount?.let {
                    Text("$it 阅读", style = MaterialTheme.typography.labelSmall,
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

/** WP2-A：行内小徽章（置顶/精华） */
@Composable
private fun RowBadge(text: String, color: androidx.compose.ui.graphics.Color) {
    androidx.compose.material3.Surface(
        shape = MaterialTheme.shapes.small,
        color = color.copy(alpha = 0.14f),
    ) {
        Text(
            text,
            style = MaterialTheme.typography.labelSmall,
            color = color,
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
        )
    }
}

/** WP2-A：有界行封面（88×60，解码降采样，失败不留洞） */
@Composable
private fun RowCover(url: String) {
    var bitmap by androidx.compose.runtime.remember {
        androidx.compose.runtime.mutableStateOf<android.graphics.Bitmap?>(null)
    }
    var failed by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf(false) }
    androidx.compose.runtime.LaunchedEffect(url) {
        bitmap = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
            try {
                val conn = java.net.URL(url).openConnection() as java.net.HttpURLConnection
                conn.connectTimeout = 8000
                conn.readTimeout = 12000
                try {
                    val bytes = conn.inputStream.use { it.readBytes() }
                    val opts = android.graphics.BitmapFactory.Options().apply { inSampleSize = 2 }
                    android.graphics.BitmapFactory.decodeByteArray(bytes, 0, bytes.size, opts)
                } finally {
                    conn.disconnect()
                }
            } catch (e: Exception) {
                null
            }
        }
        if (bitmap == null) failed = true
    }
    val bmp = bitmap
    if (bmp != null) {
        androidx.compose.foundation.Image(
            bitmap = bmp.asImageBitmap(),
            contentDescription = null,
            modifier = Modifier
                .width(88.dp)
                .height(60.dp)
                .clip(MaterialTheme.shapes.small),
        )
    } else if (!failed) {
        Box(
            Modifier
                .width(88.dp)
                .height(60.dp)
                .clip(MaterialTheme.shapes.small)
                .background(MaterialTheme.colorScheme.surfaceVariant),
        )
    } else {
        Box(
            Modifier
                .width(88.dp)
                .height(60.dp)
                .clip(MaterialTheme.shapes.small),
        ) {
            Text(
                "图",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.align(Alignment.Center),
            )
        }
    }
}
