package io.github.deserthouse.sdgun.ui

import android.content.Intent
import android.net.Uri
import android.webkit.WebView
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Chat
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.Forum
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.outlined.Chat
import androidx.compose.material.icons.outlined.Forum
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    prefs: AppPrefs,
    state: WebState,
    webView: WebView,
    onOpenSettings: () -> Unit,
) {
    var currentTab by rememberSaveable { mutableStateOf(0) }
    val clipboard = LocalClipboardManager.current
    val context = LocalContext.current

    // 返回手势：事件到来时实时读历史——Discuz 触屏版的 AJAX/pushState 导航
    // 不触发 onPageFinished，任何基于加载回调的 canGoBack 缓存都会过期
    BackHandler {
        if (webView.canGoBack()) {
            webView.goBack()
        } else {
            (context as? android.app.Activity)?.moveTaskToBack(true)
        }
    }

    val external = state.externalLink
    if (external != null) {
        AlertDialog(
            onDismissRequest = { state.externalLink = null },
            title = { Text("外部链接") },
            text = { Text(external, maxLines = 4, overflow = TextOverflow.Ellipsis) },
            confirmButton = {
                TextButton(onClick = {
                    runCatching {
                        context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(external)))
                    }
                    state.externalLink = null
                }) { Text("浏览器打开") }
            },
            dismissButton = {
                Column {
                    TextButton(onClick = {
                        clipboard.setText(AnnotatedString(external))
                        state.externalLink = null
                    }) { Text("复制链接") }
                    TextButton(onClick = { state.externalLink = null }) { Text("取消") }
                }
            },
        )
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.surface,
        topBar = {
            Column {
                TopAppBar(
                    title = {
                        Text(
                            state.title.ifBlank { "SDGun 社区" },
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
                        selected = currentTab == i,
                        onClick = {
                            currentTab = i
                            tab.url?.let { webView.loadUrl(it) }
                        },
                        icon = {
                            Icon(
                                if (currentTab == i) tab.selectedIcon else tab.unselectedIcon,
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
                .padding(padding),
        ) {
            AndroidView(factory = { webView }, modifier = Modifier.fillMaxSize())

            // 主文档加载失败覆盖层：服务器不稳定是常态（全局宕机实测过数分钟~数小时），
            // 系统默认错误页无重试入口，这里给 M3E 错误卡片 + 重试
            state.loadError?.let { err ->
                Surface(
                    modifier = Modifier.matchParentSize(),
                    color = MaterialTheme.colorScheme.surface,
                ) {
                    Column(
                        modifier = Modifier.fillMaxSize().padding(32.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center,
                    ) {
                        Icon(
                            Icons.Filled.CloudOff,
                            contentDescription = null,
                            modifier = Modifier.size(56.dp),
                            tint = MaterialTheme.colorScheme.outline,
                        )
                        Spacer(Modifier.height(16.dp))
                        Text(
                            "页面加载失败",
                            style = MaterialTheme.typography.titleLarge,
                        )
                        Text(
                            err,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center,
                        )
                        Text(
                            "论坛服务器偶尔不稳定，稍后重试通常可恢复",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center,
                        )
                        Spacer(Modifier.height(24.dp))
                        Button(onClick = { webView.reload() }) {
                            Icon(Icons.Filled.Refresh, contentDescription = null)
                            Spacer(Modifier.width(8.dp))
                            Text("重试")
                        }
                    }
                }
            }
        }
    }
}
