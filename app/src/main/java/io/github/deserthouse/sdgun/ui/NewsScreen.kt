package io.github.deserthouse.sdgun.ui

import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import io.github.deserthouse.sdgun.data.NewsItem
import io.github.deserthouse.sdgun.data.NewsRepository
import kotlinx.coroutines.launch

/**
 * 资讯流页（AF-01 / C5）：官方编辑资讯的只读列表（游客面）。
 * 卡片=封面+标题+摘要两行+来源/时间/评论数；点击经 onOpenLink 回 WebView 浏览详情。
 *
 * AV-02 状态机（诚实语义）：
 *  - 网络全部在 Repository 的 IO 协程执行，UI 线程零网络；
 *  - 抓取走 rememberCoroutineScope（composition 绑定，离开页面即取消，无游离任务写状态）；
 *  - 失败≠endReached：网络失败保留旧内容+横幅/重试本页，只有成功空页（或全重复）才"已到底"；
 *  - 首拉失败在空态给"网络异常"文案与重试，不甩锅服务器。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NewsScreen(
    onBack: () -> Unit,
    onOpenLink: (String) -> Unit,
) {
    var items by remember { mutableStateOf(NewsRepository.cached(1) ?: emptyList()) }
    var firstLoading by remember { mutableStateOf(items.isEmpty()) }
    var firstError by remember { mutableStateOf(false) }
    var stale by remember { mutableStateOf(false) }
    var endReached by remember { mutableStateOf(false) }
    var loadingMore by remember { mutableStateOf(false) }
    var pageError by remember { mutableStateOf(false) }
    var nextPage by remember { mutableIntStateOf(2) }
    val scope = rememberCoroutineScope()

    // force=true 供首拉使用：firstLoading 初值即 true（items 空、进页面就转圈），
    // 重入守卫不能把首拉自己挡死（否则 spinner 永转、协程永不启动）
    fun refresh(force: Boolean = false) {
        if (firstLoading && !force) return
        firstLoading = true
        firstError = false
        stale = false
        scope.launch {
            val fetched = NewsRepository.fetchPage(1, 1)
            firstLoading = false
            if (fetched == null) {
                if (items.isEmpty()) firstError = true else stale = true
            } else {
                val (merged, _) = NewsRepository.mergeById(items, fetched)
                items = merged
                NewsRepository.putCache(1, merged)
                if (fetched.isEmpty()) endReached = true
            }
        }
    }

    // AR-08：首拉仅一页；有未过期缓存时直接展示，不闪加载态
    LaunchedEffect(Unit) {
        if (items.isEmpty()) refresh(force = true)
    }

    // AR-08：滚动到底触发 load-more（单频道只读）；失败保留"重试本页"
    fun loadMore() {
        if (loadingMore || endReached || firstLoading) return
        loadingMore = true
        pageError = false
        val page = nextPage
        scope.launch {
            val fetched = NewsRepository.fetchPage(1, page)
            loadingMore = false
            when {
                fetched == null -> pageError = true
                fetched.isEmpty() -> endReached = true
                else -> {
                    val (merged, added) = NewsRepository.mergeById(items, fetched)
                    if (added) {
                        items = merged
                        nextPage = page + 1
                    } else {
                        endReached = true
                    }
                }
            }
        }
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.surface,
        topBar = {
            TopAppBar(
                title = { Text("资讯") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                    }
                },
                // W3-0：手动刷新入口——刷新失败保留旧内容+stale 横幅（AV-02 状态机的触发路径）
                // C2：进行中给忙碌态（转圈），否则点击后最长十几秒零反馈
                actions = {
                    if (firstLoading) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(24.dp).padding(2.dp),
                            strokeWidth = 2.5.dp,
                        )
                    } else {
                        IconButton(onClick = { refresh() }) {
                            Icon(Icons.Filled.Refresh, contentDescription = "刷新")
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                ),
            )
        },
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding)) {
            when {
                firstLoading && items.isEmpty() -> CircularProgressIndicator(Modifier.align(Alignment.Center))
                items.isEmpty() && firstError -> Column(
                    Modifier.align(Alignment.Center).fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text("资讯拉取失败（网络异常，请重试）", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    androidx.compose.material3.TextButton(onClick = { refresh() }) { Text("重试") }
                }
                items.isEmpty() -> Text(
                    "暂无资讯",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.align(Alignment.Center),
                )
                else -> LazyColumn(Modifier.fillMaxSize()) {
                    if (stale) {
                        item(key = "stale") {
                            Text(
                                "刷新失败，展示的是缓存内容",
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
                        NewsCard(item) { onOpenLink(item.link) }
                    }
                    item {
                        Box(Modifier.fillMaxWidth().padding(16.dp), contentAlignment = Alignment.Center) {
                            when {
                                loadingMore -> CircularProgressIndicator(Modifier.size(22.dp))
                                pageError -> androidx.compose.material3.TextButton(onClick = { loadMore() }) {
                                    Text("本页加载失败，重试")
                                }
                                endReached -> Text(
                                    "已加载全部资讯",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                                else -> androidx.compose.material3.TextButton(onClick = { loadMore() }) {
                                    Text("加载更多")
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun NewsCard(item: NewsItem, onClick: () -> Unit) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 6.dp)
            .clickable(onClick = onClick),
    ) {
        Row(Modifier.padding(12.dp)) {
            Column(Modifier.weight(1f)) {
                Text(
                    item.title,
                    style = MaterialTheme.typography.titleSmall,
                    maxLines = 2,
                    overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                )
                if (item.summary.isNotBlank()) {
                    Spacer(Modifier.height(4.dp))
                    Text(
                        item.summary,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 2,
                        overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                    )
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
            item.coverUrl?.let { cover ->
                NewsCover(cover, Modifier.size(width = 108.dp, height = 76.dp))
            }
        }
    }
}

@Composable
private fun NewsCover(url: String, modifier: Modifier) {
    var bmp by remember(url) { mutableStateOf<Bitmap?>(null) }
    LaunchedEffect(url) {
        // AV-07：封面用 512 档目标（卡片缩略图不需要相册级精度）
        GalleryBitmapLoader.load(url, 0, target = 512, { bmp = it }, {})
    }
    DisposableEffect(url) {
        onDispose { GalleryBitmapLoader.cancel(url, 0) }
    }
    Box(modifier.background(MaterialTheme.colorScheme.surfaceVariant)) {
        bmp?.let {
            Image(
                bitmap = it.asImageBitmap(),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
        }
    }
}
