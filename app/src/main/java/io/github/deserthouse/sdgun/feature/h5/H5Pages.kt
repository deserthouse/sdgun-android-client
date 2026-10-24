package io.github.deserthouse.sdgun.feature.h5

import androidx.compose.foundation.clickable
import androidx.compose.ui.graphics.toArgb
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import android.view.ViewGroup
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout
import io.github.deserthouse.sdgun.core.designsystem.ErrorOverlay
import io.github.deserthouse.sdgun.web.WebState

/**
 * U3-c（F23~27）：H5 容器 + 服务入口（配置驱动）。
 * URL 契约 2026-10-25 经代理实采（签到/注册/找回 200 实证，防猜 URL）；
 * 商城=官方外链 mall（域外→系统浏览器，AR-01 域外不进带桥 WebView）；
 * 发现/附近=官方原生功能，原生实现前诚实占位不开放。
 */

enum class H5Mode { BRIDGED, EXTERNAL, PLACEHOLDER }

data class H5Entry(
    val title: String,
    val subtitle: String,
    val url: String,
    val mode: H5Mode,
)

object H5Catalog {
    const val BASE = "https://bbs.sdgun.com.cn/"

    val entries: List<H5Entry> = listOf(
        H5Entry("论坛导读", "站方触屏版首页", "${BASE}forum.php?forumlist=1&mobile=2", H5Mode.BRIDGED),
        // 签到行撤回（2026-10-25 装机实拍反例）：dsu_paulsign 插件不存在于本站
        // （HTTP 200 实为 Discuz"插件不存在"错误页——状态码探测≠内容在）；
        // mag1 原生签到端点未在静态编目（clockIncommentDel 为其删评论），真实签到=待测绘
        H5Entry("注册账号", "站方注册页", "${BASE}member.php?mod=register", H5Mode.BRIDGED),
        H5Entry("找回密码", "站方找回流程", "${BASE}member.php?mod=logging&action=lostpasswd", H5Mode.BRIDGED),
        H5Entry("商城", "官方外链商城，系统浏览器打开", "https://vmall.hhg365.cn/", H5Mode.EXTERNAL),
        H5Entry("发现", "官方原生功能——原生实现前不开放", "", H5Mode.PLACEHOLDER),
        H5Entry("附近", "需定位权限——原生实现前不开放", "", H5Mode.PLACEHOLDER),
    )
}

/** 服务列表页（配置驱动；EXTERNAL 标注外开、PLACEHOLDER 禁用行） */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ServicesPage(
    onBack: () -> Unit,
    onOpenEntry: (H5Entry) -> Unit,
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("社区服务") },
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
        LazyColumn(
            Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(vertical = 6.dp),
        ) {
            items(H5Catalog.entries, key = { it.title }) { e ->
                val open = e.mode != H5Mode.PLACEHOLDER
                ListItem(
                    headlineContent = {
                        Text(
                            e.title,
                            color = if (open) MaterialTheme.colorScheme.onSurface
                            else MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    },
                    supportingContent = { Text(e.subtitle) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable(enabled = open) { onOpenEntry(e) },
                )
                HorizontalDivider(Modifier.padding(horizontal = 16.dp))
            }
        }
    }
}

/**
 * H5 容器：共享 WebView 重挂（HomeScreen 同模式）+ 原生顶栏（返回/刷新/外部打开）
 * + 失败覆盖层重试。进入即 loadUrl；swipe 下拉刷新。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun H5Container(
    title: String,
    url: String,
    state: WebState,
    webView: android.webkit.WebView,
    onBack: () -> Unit,
    onOpenExternal: (String) -> Unit,
) {
    val primaryArgb = MaterialTheme.colorScheme.primary.toArgb()
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(title, maxLines = 1) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                    }
                },
                actions = {
                    IconButton(onClick = { webView.loadUrl(url) }) {
                        Icon(Icons.Filled.Refresh, contentDescription = "刷新")
                    }
                    IconButton(onClick = { onOpenExternal(url) }) {
                        Icon(
                            Icons.AutoMirrored.Filled.OpenInNew,
                            contentDescription = "系统浏览器打开",
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface),
            )
        },
        containerColor = MaterialTheme.colorScheme.surface,
    ) { padding ->
        Box(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .imePadding(),
        ) {
            AndroidView(
                factory = { ctx ->
                    SwipeRefreshLayout(ctx).apply {
                        (webView.parent as? ViewGroup)?.removeView(webView)
                        addView(
                            webView,
                            ViewGroup.LayoutParams(
                                ViewGroup.LayoutParams.MATCH_PARENT,
                                ViewGroup.LayoutParams.MATCH_PARENT,
                            ),
                        )
                        setColorSchemeColors(primaryArgb)
                        setOnRefreshListener { webView.reload() }
                    }
                },
                modifier = Modifier.fillMaxSize(),
            )
            LaunchedEffect(url) { webView.loadUrl(url) }
            state.loadError?.let { err ->
                ErrorOverlay(
                    err = err,
                    raw = state.loadErrorRaw,
                    onRetry = { webView.loadUrl(url) },
                    modifier = Modifier.fillMaxSize(),
                )
            }
        }
    }
}
