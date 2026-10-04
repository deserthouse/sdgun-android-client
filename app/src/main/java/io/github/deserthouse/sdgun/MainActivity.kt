package io.github.deserthouse.sdgun

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import android.os.Bundle
import android.util.Base64
import android.webkit.CookieManager
import android.webkit.WebView
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.toArgb
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import io.github.deserthouse.sdgun.data.AppPrefs
import io.github.deserthouse.sdgun.ui.AppBottomBar
import io.github.deserthouse.sdgun.ui.HomeScreen
import io.github.deserthouse.sdgun.ui.tabUrl
import io.github.deserthouse.sdgun.ui.SettingsScreen
import io.github.deserthouse.sdgun.ui.theme.SDGunTheme
import io.github.deserthouse.sdgun.web.ThemeInjector
import io.github.deserthouse.sdgun.web.UrlRules
import io.github.deserthouse.sdgun.web.WebState
import io.github.deserthouse.sdgun.web.createForumWebView

class MainActivity : ComponentActivity() {

    // Activity 级持有：后台冻结导致的 relaunch 用 saveState/restoreState 保住 WebView 历史
    private lateinit var state: WebState
    private lateinit var webView: WebView

    // 仅 debug 构建注册：b64→JS 求值桥，供 AVD 自动化驱动 WebView 表单
    // （release 不编译进签名产物；导出范围=本机模拟器调试面）
    private var debugJsReceiver: BroadcastReceiver? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        state = WebState()
        val restored = savedInstanceState?.getBundle(KEY_WEB_STATE)
        webView = createForumWebView(applicationContext, state, startUrl = null)
        restored?.let { webView.restoreState(it) }
        if (webView.copyBackForwardList().size == 0) {
            if (!handleViewIntent(intent)) {
                webView.loadUrl(UrlRules.HOME)
            }
        }

        if (applicationInfo.flags and android.content.pm.ApplicationInfo.FLAG_DEBUGGABLE != 0) {
            debugJsReceiver = object : BroadcastReceiver() {
                override fun onReceive(context: Context, i: Intent) {
                    val b64 = i.getStringExtra("b64") ?: return
                    runCatching {
                        val js = String(Base64.decode(b64, Base64.DEFAULT), Charsets.UTF_8)
                        webView.evaluateJavascript(js, null)
                    }
                }
            }
            val filter = IntentFilter("io.github.deserthouse.sdgun.JS")
            if (Build.VERSION.SDK_INT >= 33) {
                registerReceiver(debugJsReceiver, filter, Context.RECEIVER_EXPORTED)
            } else {
                @Suppress("UnspecifiedRegisterReceiverFlag")
                registerReceiver(debugJsReceiver, filter)
            }
        }

        val prefs = AppPrefs(applicationContext)
        setContent {
            val dynamicColor by prefs.dynamicColor.collectAsState()
            SDGunTheme(dynamicColor = dynamicColor) {
                AppRoot(prefs, state, webView)
            }
        }
    }

    override fun onDestroy() {
        debugJsReceiver?.let { runCatching { unregisterReceiver(it) } }
        CookieManager.getInstance().flush()
        webView.stopLoading()
        webView.destroy()
        super.onDestroy()
    }

    override fun onNewIntent(intent: android.content.Intent) {
        super.onNewIntent(intent)
        handleViewIntent(intent)
    }

    /** 处理论坛链接唤起；返回是否消费了 URL */
    private fun handleViewIntent(intent: android.content.Intent): Boolean {
        val url = intent.data?.toString() ?: return false
        return if (UrlRules.classify(url) is UrlRules.Verdict.Internal) {
            webView.loadUrl(UrlRules.normalizeInternal(url))
            true
        } else false
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        val webBundle = Bundle().also { webView.saveState(it) }
        outState.putBundle(KEY_WEB_STATE, webBundle)
    }

    private companion object {
        const val KEY_WEB_STATE = "webview_state"
    }
}

@Composable
private fun AppRoot(prefs: AppPrefs, state: WebState, webView: WebView) {
    val navController = rememberNavController()

    // 发帖传图：系统图片选择器
    val imagePicker = rememberLauncherForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri -> state.consumeFileChooserResult(uri) }
    LaunchedEffect(Unit) { state.requestImagePick = { imagePicker.launch("image/*") } }

    // 设置快照同步进 WebState（WebView 回调读取的是这里的最新值）；
    // 主题/注入开关变更时对当前页即时生效（SDG_APPLY 只重发变量层 / 或移除全部节点）
    val webTheme by prefs.webTheme.collectAsState()
    val textZoom by prefs.textZoom.collectAsState()
    val colorScheme = MaterialTheme.colorScheme
    LaunchedEffect(colorScheme, webTheme) {
        state.webThemeEnabled = webTheme
        webView.setBackgroundColor(colorScheme.background.toArgb())
        state.pageThemeJs = ThemeInjector.applyScript() + "\n" + ThemeInjector.applyCall(colorScheme)
        state.earlyBgJs = ThemeInjector.earlyBgJs(colorScheme)
        val js = if (webTheme) state.pageThemeJs else ThemeInjector.removeCall()
        webView.evaluateJavascript(js, null)
    }
    LaunchedEffect(textZoom) { webView.settings.textZoom = textZoom }

    NavHost(navController = navController, startDestination = "home") {
        composable("home") {
            HomeScreen(
                prefs = prefs,
                state = state,
                webView = webView,
                onOpenSettings = { navController.navigate("settings") },
            )
        }
        composable("settings") {
            SettingsScreen(
                prefs = prefs,
                onBack = { navController.popBackStack() },
                onClearLoginState = {
                    CookieManager.getInstance().removeAllCookies(null)
                    CookieManager.getInstance().flush()
                    webView.loadUrl(UrlRules.HOME)
                },
                onTab = { i ->
                    navController.popBackStack()
                    tabUrl(i)?.let { webView.loadUrl(it) }
                },
            )
        }
    }
}
