package io.github.deserthouse.sdgun.ui

import android.view.ViewGroup
import android.webkit.WebView
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Chat
import androidx.compose.material.icons.filled.Forum
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.outlined.Chat
import androidx.compose.material.icons.outlined.Forum
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.viewinterop.AndroidView
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout
import io.github.deserthouse.sdgun.data.AppPrefs
import io.github.deserthouse.sdgun.web.UrlRules
import io.github.deserthouse.sdgun.web.WebState

private data class TabSpec(
    val label: String,
    val url: String?,
    val selectedIcon: androidx.compose.ui.graphics.vector.ImageVector,
    val unselectedIcon: androidx.compose.ui.graphics.vector.ImageVector,
)

private val TABS = listOf(
    TabSpec("首页", UrlRules.HOME, Icons.Filled.Forum, Icons.Outlined.Forum),
    TabSpec("消息", UrlRules.MESSAGES, Icons.Filled.Chat, Icons.Outlined.Chat),
    TabSpec("我的", UrlRules.PROFILE, Icons.Filled.Person, Icons.Outlined.Person),
)

/** 剥掉 Discuz 标题的模板长尾，只留页面主体（含 "-  手机版" 双空格变体，见 PrettyTitleTest） */

/** 底栏选中态由当前 URL 反推（页内导航也能正确高亮；消息/我的之外一律视为首页） */
internal fun activeTab(currentUrl: String): Int = when {
    currentUrl.contains("do=pm") -> 1
    currentUrl.contains("mycenter=1") -> 2
    else -> 0
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    prefs: AppPrefs,
    state: WebState,
    webView: WebView,
    onOpenSettings: () -> Unit,
) {
    val context = LocalContext.current
    val primaryArgb = MaterialTheme.colorScheme.primary.toArgb()

    // 返回手势：事件到来时实时读历史——Discuz 触屏版的 AJAX/pushState 导航
    // 不触发 onPageFinished，任何基于加载回调的 canGoBack 缓存都会过期
    BackHandler {
        if (webView.canGoBack()) {
            webView.goBack()
        } else {
            (context as? android.app.Activity)?.moveTaskToBack(true)
        }
    }

    state.externalLink?.let { url ->
        ExternalLinkDialog(url = url, onDismiss = { state.externalLink = null })
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.surface,
        topBar = {
            Column {
                TopAppBar(
                    title = {
                        Text(
                            prettyTitle(state.title).ifBlank { "SDGun 社区" },
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    },
                    actions = {
                        IconButton(onClick = { webView.loadUrl(UrlRules.SEARCH) }) {
                            Icon(Icons.Filled.Search, contentDescription = "搜索")
                        }
                        IconButton(onClick = { webView.reload() }) {
                            Icon(Icons.Filled.Refresh, contentDescription = "刷新")
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.surface,
                    ),
                )
                AnimatedVisibility(visible = state.progress < 1f) {
                    LinearProgressIndicator(
                        progress = { state.progress },
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
        },
        bottomBar = {
            NavigationBar {
                TABS.forEachIndexed { i, tab ->
                    NavigationBarItem(
                        selected = activeTab(state.currentUrl) == i,
                        onClick = { tab.url?.let { webView.loadUrl(it) } },
                        icon = {
                            Icon(
                                if (activeTab(state.currentUrl) == i) tab.selectedIcon else tab.unselectedIcon,
                                contentDescription = tab.label,
                            )
                        },
                        label = { Text(tab.label) },
                    )
                }
                NavigationBarItem(
                    selected = false,
                    onClick = onOpenSettings,
                    icon = { Icon(Icons.Outlined.Settings, contentDescription = "设置") },
                    label = { Text("设置") },
                )
            }
        },
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                // 键盘修复：edge-to-edge 下 adjustResize 失效（API 30+），必须自行消费 IME insets，
                // 否则键盘盖住 WebView 底部的回复框/登录表单
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

            state.loadError?.let { err ->
                ErrorOverlay(
                    err = err,
                    raw = state.loadErrorRaw,
                    onRetry = { webView.reload() },
                    modifier = Modifier.matchParentSize(),
                )
            }
        }
    }
}
