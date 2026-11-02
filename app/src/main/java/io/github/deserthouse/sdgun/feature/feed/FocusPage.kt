package io.github.deserthouse.sdgun.feature.feed

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.deserthouse.sdgun.core.model.UiState
import io.github.deserthouse.sdgun.core.designsystem.B1PullToRefresh
import io.github.deserthouse.sdgun.data.repository.NewsItem
import io.github.deserthouse.sdgun.feature.thread.sampleSize
import kotlinx.coroutines.flow.StateFlow

/**
 * 聚焦页（G2-A02）：频道条+内容流。
 * UiState 八态全覆盖；切频道保留各自状态（频道状态由上层 Map 持有）。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FocusPage(
    state: StateFlow<UiState<List<NewsItem>>>,
    channels: List<FocusChannel>,
    currentChannel: FocusChannel,
    onChannelSelect: (FocusChannel) -> Unit,
    onOpenLink: (String) -> Unit,
    onRetry: () -> Unit,
    hasMore: Boolean,
    loadingMore: Boolean,
    onLoadMore: () -> Unit,
    modifier: Modifier = Modifier,
) {
    // A24-04/R2：每频道独立滚动位置——keyed 子组合件承载（频道切换即切换组合节点，
    // 各节点自有 rememberLazyListState；避免条件性 remember 的组合位错挂）
    key(currentChannel) {
        FocusFeed(
            state = state, currentChannel = currentChannel, channels = channels,
            onChannelSelect = onChannelSelect, onOpenLink = onOpenLink, onRetry = onRetry,
            hasMore = hasMore, loadingMore = loadingMore, onLoadMore = onLoadMore,
            modifier = modifier,
        )
    }
}

@Composable
private fun FocusFeed(
    state: StateFlow<UiState<List<NewsItem>>>,
    currentChannel: FocusChannel,
    channels: List<FocusChannel>,
    onChannelSelect: (FocusChannel) -> Unit,
    onOpenLink: (String) -> Unit,
    onRetry: () -> Unit,
    hasMore: Boolean,
    loadingMore: Boolean,
    onLoadMore: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val listState = rememberLazyListState()
    val ui by state.collectAsState()
    // A28-03 单一壳（审查补漏）：频道条不再借 topBar 槽（与 TabShell 顶栏构成结构双槽）；
    // status inset 由 TabShell 顶栏（M3 TopAppBar 默认 windowInsets）承担——A6 装机验证布局
    Scaffold(
        modifier = modifier,
        containerColor = MaterialTheme.colorScheme.surface,
    ) { padding ->
        // B0-1（V1 修复）：Scaffold content 是 Box——旧写法 Column(频道条) 与 when(内容)
        // 是兄弟节点互相叠放，fillMaxSize 的内容层把频道条盖死。改单列纵排。
        Column(Modifier.padding(padding).fillMaxSize()) {
            // R0-4（A31-S08）：inset 所有权归 TabShell（content 消费完整 padding）——
            // B0-1 的逐页 statusBarsPadding 属掩盖性修复，撤除防双倍留白
            ChannelBar(channels, currentChannel, onChannelSelect)
        when (val s = ui) {
            is UiState.InitialLoading -> io.github.deserthouse.sdgun.core.designsystem.SkeletonList(
                rows = 5, withMedia = true, modifier = Modifier.fillMaxSize())

            is UiState.Empty -> Column(
                Modifier.fillMaxSize(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                Text(s.message ?: "暂无内容", style = MaterialTheme.typography.bodyLarge)
                TextButton(onClick = onRetry) { Text("重试") }
            }

            is UiState.RetryableError -> Column(
                Modifier.fillMaxSize(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                Text(s.error, style = MaterialTheme.typography.bodyLarge)
                TextButton(onClick = onRetry) { Text("重试") }
            }

            is UiState.AuthRequired -> Column(
                Modifier.fillMaxSize(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) { Text("需要登录", style = MaterialTheme.typography.bodyLarge) }

            is UiState.PermissionDenied -> Text(
                s.message, modifier = Modifier.padding(16.dp),
            )

            is UiState.Data<*> -> B1PullToRefresh(
                refreshing = false, onRefresh = onRetry,
            ) {
                FeedList(
                    items = @Suppress("UNCHECKED_CAST") (s as UiState.Data<List<NewsItem>>).value,
                    stale = null, onOpenLink = onOpenLink, padding = androidx.compose.foundation.layout.PaddingValues(0.dp),
                    hasMore = hasMore, loadingMore = loadingMore, onLoadMore = onLoadMore,
                    listState = listState,
                )
            }

            is UiState.Refreshing<*> -> FeedList(
                items = @Suppress("UNCHECKED_CAST") (s as UiState.Refreshing<List<NewsItem>>).stale,
                stale = null, onOpenLink = onOpenLink, padding = androidx.compose.foundation.layout.PaddingValues(0.dp),
                hasMore = hasMore, loadingMore = loadingMore, onLoadMore = onLoadMore,
                listState = listState,
            )

            is UiState.Stale<*> -> FeedList(
                items = @Suppress("UNCHECKED_CAST") (s as UiState.Stale<List<NewsItem>>).stale,
                stale = s.error, onOpenLink = onOpenLink, padding = androidx.compose.foundation.layout.PaddingValues(0.dp),
                hasMore = hasMore, loadingMore = loadingMore, onLoadMore = onLoadMore,
                listState = listState,
            )
        }
        }
    }
}

@Composable
private fun FeedList(
    items: List<NewsItem>,
    stale: String?,
    onOpenLink: (String) -> Unit,
    padding: androidx.compose.foundation.layout.PaddingValues,
    hasMore: Boolean,
    loadingMore: Boolean,
    onLoadMore: () -> Unit,
    listState: androidx.compose.foundation.lazy.LazyListState,
) {
    // B1-4：尾部预取（倒数 3 项进视口即自动加载）——「加载更多」钮降级为状态指示
    io.github.deserthouse.sdgun.core.designsystem.InfiniteScrollHandler(
        listState = listState, isLoadingMore = loadingMore, hasMore = hasMore,
        onLoadMore = onLoadMore,
    )
    LazyColumn(
        state = listState,
        modifier = Modifier.fillMaxSize().padding(padding),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(vertical = 6.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        if (stale != null) {
            item(key = "stale") {
                Text(
                    stale,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onErrorContainer,
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(MaterialTheme.colorScheme.errorContainer)
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                )
            }
        }
        items(items, key = { it.id }) { item ->
            FeedCard(item) { onOpenLink(item.link) }
        }
        if (hasMore) {
            item(key = "load_more") {
                // B1-4：自动预取后仅余加载指示（点击保留兜底）
                TextButton(
                    onClick = onLoadMore,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(if (loadingMore) "加载中…" else "加载更多")
                }
            }
        }
    }
}

@Composable
private fun FeedCard(item: NewsItem, onClick: () -> Unit) {
    Card(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp),
    ) {
        Column(Modifier.padding(12.dp)) {
            // R3：封面渲染（有界解码，失败静默无占位——摘要为主视觉）
            item.coverUrl?.let { url ->
                FocusCover(url = url)
                Spacer(Modifier.height(6.dp))
            }
            Text(item.title, style = MaterialTheme.typography.titleSmall,
                maxLines = 2, overflow = TextOverflow.Ellipsis)
            if (item.summary.isNotBlank()) {
                Spacer(Modifier.height(4.dp))
                Text(item.summary, style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2, overflow = TextOverflow.Ellipsis)
            }
            Spacer(Modifier.height(6.dp))
            Text(
                listOfNotNull(
                    item.source.takeIf { it.isNotBlank() },
                    item.timeText,
                    item.comments?.let { "$it 评论" },
                ).joinToString(" · "),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/** R3：聚焦封面（有界解码，宽/高比修正沿用 A24-05 语义；失败=不渲染）。 */
@Composable
private fun FocusCover(url: String) {
    var bitmap by remember { mutableStateOf<android.graphics.Bitmap?>(null) }
    LaunchedEffect(url) {
        bitmap = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
            try {
                val conn = java.net.URL(url).openConnection() as java.net.HttpURLConnection
                conn.connectTimeout = 8000
                conn.readTimeout = 12000
                try {
                    val bytes = conn.inputStream.use { it.readBytes() }
                    val bounds = android.graphics.BitmapFactory.Options().apply { inJustDecodeBounds = true }
                    android.graphics.BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
                    val opts = android.graphics.BitmapFactory.Options().apply {
                        inSampleSize = sampleSize(bounds.outWidth, bounds.outHeight, 1600)
                    }
                    android.graphics.BitmapFactory.decodeByteArray(bytes, 0, bytes.size, opts)
                } finally {
                    conn.disconnect()
                }
            } catch (e: Exception) {
                null
            }
        }
    }
    if (bitmap == null) {
        Box(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp)
                .aspectRatio(16f / 9f)
                .clip(MaterialTheme.shapes.medium)
                .background(MaterialTheme.colorScheme.surfaceVariant),
        )
        return
    }
    val bmp = bitmap ?: return
    // N5/V01（A31）：封面上限 16:9 裁切（ContentScale.Crop）——防竖图占满整屏；
    // 加载期固定占位盒防行高跳变（R3 时代「封面渲染」的布局债一并清）
    androidx.compose.foundation.Image(
        bitmap = bmp.asImageBitmap(),
        contentDescription = null,
        contentScale = androidx.compose.ui.layout.ContentScale.Crop,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp)
            .aspectRatio(16f / 9f)
            .clip(MaterialTheme.shapes.medium),
    )
}

@Composable
private fun ChannelBar(
    channels: List<FocusChannel>,
    current: FocusChannel,
    onSelect: (FocusChannel) -> Unit,
) {
    androidx.compose.foundation.lazy.LazyRow(
        contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 12.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        items(channels, key = { it.catId }) { ch ->
            FilterChip(
                selected = ch == current,
                onClick = { onSelect(ch) },
                label = { Text(ch.label) },
            )
        }
    }
}
