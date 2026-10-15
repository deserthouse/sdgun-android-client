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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import io.github.deserthouse.sdgun.data.NewsItem
import io.github.deserthouse.sdgun.data.NewsRepository

/**
 * 资讯流页（AF-01 / C5）：官方编辑资讯的只读列表（游客面）。
 * 卡片=封面+标题+摘要两行+来源/时间/评论数；点击经 onOpenLink 回 WebView 浏览详情。
 * 数据失败显示旧缓存/错误提示+重试；无后台轮询，进入才拉一页。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NewsScreen(
    onBack: () -> Unit,
    onOpenLink: (String) -> Unit,
) {
    var items by remember { mutableStateOf(NewsRepository.cached(1) ?: emptyList()) }
    var loading by remember { mutableStateOf(items.isEmpty()) }
    var error by remember { mutableStateOf<String?>(null) }
    var attempt by remember { mutableIntStateOf(0) }

    LaunchedEffect(attempt) {
        if (items.isNotEmpty()) return@LaunchedEffect
        loading = true
        error = null
        val fetched = loadNews()
        if (fetched != null) {
            items = fetched
            NewsRepository.putCache(1, fetched)
        } else {
            error = "资讯拉取失败（服务器或网络波动）"
        }
        loading = false
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
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                ),
            )
        },
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding)) {
            when {
                loading && items.isEmpty() -> CircularProgressIndicator(Modifier.align(Alignment.Center))
                items.isEmpty() -> Column(
                    Modifier.align(Alignment.Center).fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text(error ?: "暂无资讯", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    androidx.compose.material3.TextButton(onClick = { attempt++ }) { Text("重试") }
                }
                else -> LazyColumn(Modifier.fillMaxSize()) {
                    items(items, key = { it.id }) { item ->
                        NewsCard(item) { onOpenLink(item.link) }
                    }
                }
            }
        }
    }
}

/** 工作线程拉取（复用 Gallery 加载器线程池纪律：独立小池+主线程回传由调用方处理） */
private suspend fun loadNews(): List<NewsItem>? =
    kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
        val out = ArrayList<NewsItem>()
        for (url in NewsRepository.pageUrls(1, 1, NewsRepository.MAX_PAGES_PER_REQUEST)) {
            val body = fetchNews(url) ?: break
            val parsed = NewsRepository.parseNewsList(body)
            out.addAll(parsed)
        }
        out.ifEmpty { null }
    }

private fun fetchNews(url: String): String? = runCatching {
    val conn = java.net.URL(url).openConnection() as java.net.HttpURLConnection
    conn.connectTimeout = 12000
    conn.readTimeout = 15000
    conn.setRequestProperty(
        "User-Agent",
        "Mozilla/5.0 (Linux; Android 16; Pixel 8) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/130.0.0.0 Mobile Safari/537.36",
    )
    conn.setRequestProperty("Accept", "application/json")
    conn.inputStream.use { it.bufferedReader().readText() }
}.getOrNull()

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
        GalleryBitmapLoader.load(url, { bmp = it }, {})
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
