package io.github.deserthouse.sdgun.feature.feed

import androidx.compose.foundation.background
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
    Scaffold(
        modifier = modifier,
        topBar = {
            Column(Modifier.statusBarsPadding()) {
                ChannelBar(channels, currentChannel, onChannelSelect)
            }
        },
        containerColor = MaterialTheme.colorScheme.surface,
    ) { padding ->
        when (val s = ui) {
            is UiState.InitialLoading -> Column(
                Modifier.fillMaxSize().padding(padding),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) { CircularProgressIndicator() }

            is UiState.Empty -> Column(
                Modifier.fillMaxSize().padding(padding),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                Text(s.message ?: "暂无内容", style = MaterialTheme.typography.bodyLarge)
                TextButton(onClick = onRetry) { Text("重试") }
            }

            is UiState.RetryableError -> Column(
                Modifier.fillMaxSize().padding(padding),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                Text(s.error, style = MaterialTheme.typography.bodyLarge)
                TextButton(onClick = onRetry) { Text("重试") }
            }

            is UiState.AuthRequired -> Column(
                Modifier.fillMaxSize().padding(padding),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) { Text("需要登录", style = MaterialTheme.typography.bodyLarge) }

            is UiState.PermissionDenied -> Text(
                s.message, modifier = Modifier.padding(padding),
            )

            is UiState.Data<*> -> FeedList(
                items = @Suppress("UNCHECKED_CAST") (s as UiState.Data<List<NewsItem>>).value,
                stale = null, onOpenLink = onOpenLink, padding = padding,
                hasMore = hasMore, loadingMore = loadingMore, onLoadMore = onLoadMore,
                listState = listState,
            )

            is UiState.Refreshing<*> -> FeedList(
                items = @Suppress("UNCHECKED_CAST") (s as UiState.Refreshing<List<NewsItem>>).stale,
                stale = null, onOpenLink = onOpenLink, padding = padding,
                hasMore = hasMore, loadingMore = loadingMore, onLoadMore = onLoadMore,
                listState = listState,
            )

            is UiState.Stale<*> -> FeedList(
                items = @Suppress("UNCHECKED_CAST") (s as UiState.Stale<List<NewsItem>>).stale,
                stale = s.error, onOpenLink = onOpenLink, padding = padding,
                hasMore = hasMore, loadingMore = loadingMore, onLoadMore = onLoadMore,
                listState = listState,
            )
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
    val bmp = bitmap ?: return
    androidx.compose.foundation.Image(
        bitmap = bmp.asImageBitmap(),
        contentDescription = null,
        modifier = Modifier.fillMaxWidth(),
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
