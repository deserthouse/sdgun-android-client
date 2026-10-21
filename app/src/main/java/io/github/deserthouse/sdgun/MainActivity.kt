package io.github.deserthouse.sdgun

import android.content.BroadcastReceiver
import io.github.deserthouse.sdgun.feature.settings.SettingsScreen
import io.github.deserthouse.sdgun.feature.feed.NewsScreen
import io.github.deserthouse.sdgun.feature.community.activeTab
import io.github.deserthouse.sdgun.feature.community.HomeScreen
import io.github.deserthouse.sdgun.feature.account.AccountScreen
import io.github.deserthouse.sdgun.feature.account.LoginScreen
import io.github.deserthouse.sdgun.core.designsystem.AppBottomBar
import io.github.deserthouse.sdgun.core.network.SdApi
import io.github.deserthouse.sdgun.data.repository.AppPrefs
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.ui.platform.LocalContext
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
import androidx.compose.ui.platform.LocalView
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import io.github.deserthouse.sdgun.core.designsystem.tabUrl
import io.github.deserthouse.sdgun.core.designsystem.SDGunTheme
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
        io.github.deserthouse.sdgun.core.network.SdApi.appContext = applicationContext
        state = WebState()
        val restored = savedInstanceState?.getBundle(KEY_WEB_STATE)
        // AV-01：WebView 必须 Activity Context——applicationContext 构建的实例缺窗口 token，
        // 站方原生 select 弹层（SelectPopup.show）全部 BadTokenException
        webView = createForumWebView(this, state, startUrl = null)
        restored?.let { webView.restoreState(it) }
        if (webView.copyBackForwardList().size == 0) {
            if (!handleViewIntent(intent)) {
                webView.loadUrl(UrlRules.HOME)
            }
        }

        // S1-9：调试桥可经设置页关闭（默认开）；release 构建永不注册
        val prefs = AppPrefs(applicationContext)
        val debugBridgeEnabled = prefs.debugBridge.value
        if (applicationInfo.flags and android.content.pm.ApplicationInfo.FLAG_DEBUGGABLE != 0 && debugBridgeEnabled) {
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
                // A-03 收窄实验结论（2026-10-15 实测）：RECEIVER_NOT_EXPORTED 连 adb shell
                // 广播一并拦截，自动化探针（tests/jsprobe.py）整体失效。保持 EXPORTED——
                // 威胁面=与 debug 构建同设备的第三方 app（release 不注册本 receiver），
                // 实际风险极小；正式收窄走"开发者选项开关"方案，见 android_work_plan。
                registerReceiver(debugJsReceiver, filter, Context.RECEIVER_EXPORTED)
            } else {
                @Suppress("UnspecifiedRegisterReceiverFlag")
                registerReceiver(debugJsReceiver, filter)
            }
        }

        setContent {
            val dynamicColor by prefs.dynamicColor.collectAsState()
            SDGunTheme(dynamicColor = dynamicColor) {
                AppRoot(prefs, state, webView)
            }
        }
    }

    override fun onDestroy() {
        debugJsReceiver?.let { runCatching { unregisterReceiver(it) } }
        state.destroyed = true
        state.onCleared?.invoke()
        // A-06 残余：销毁时完成挂起的文件选择回调（恰好一次，取消=null）
        state.consumeFileChooserResult(null)
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
            // A-08：深链可能发生在设置页路由上——通知 UI 把路由切回 home，
            // 否则目标内容已在 WebView 里加载而用户还看着设置页
            state.deepLinkTick++
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

    // A-08：所有 tab 入口共用（Home 底栏 + 设置页底栏），先记意图再加载；
    // 已在该 tab 时重复点击不重载（保留页面内容与表单）
    fun navigateTab(index: Int) {
        // A-08 回归修复：用“旧意图”判定是否已在目标 tab——登录页（member.php）上
        // activeTab 会回退到 intendedTab，若先写新意图会让任意 tab 都被当成“当前”而拒载
        val alreadyThere = activeTab(state.currentUrl, state.intendedTab) == index
        state.intendedTab = index
        if (alreadyThere) return
        // R-A1：我的 tab 走原生（API 会话）——无 token 进登录页，有 token 进账号页；
        // 网页会员面（消息等）仍可经其他路径到达，渐进期共存
        if (index == 3) {
            navController.navigate(if (prefs.apiToken.value.isBlank()) "login" else "account")
            return
        }
        if (index == 0) {
            // G2 前：聚焦 tab 暂由资讯流承载（R-A2 原生首页替换）
            navController.navigate("news")
            return
        }
        tabUrl(index)?.let { webView.loadUrl(it) }
    }

    // A-06：按网页声明的契约选系统选择器——多选契约与任意 MIME 直传
    val singlePicker = rememberLauncherForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri -> state.consumeFileChooserResult(uri?.let { listOf(it) }) }
    val multiPicker = rememberLauncherForActivityResult(
        ActivityResultContracts.GetMultipleContents()
    ) { uris -> state.consumeFileChooserResult(uris.toList()) }
    LaunchedEffect(Unit) {
        state.requestImagePick = { req ->
            if (req.multiple) multiPicker.launch(req.mimeType) else singlePicker.launch(req.mimeType)
        }
    }

    // A-08：论坛深链发生在设置页路由时切回 home（WebView 已加载目标内容）
    LaunchedEffect(state.deepLinkTick) {
        if (state.deepLinkTick > 0 &&
            navController.currentBackStackEntry?.destination?.route != "home"
        ) {
            navController.popBackStack("home", inclusive = false)
        }
    }

    // 设置快照同步进 WebState（WebView 回调读取的是这里的最新值）；
    // 主题/注入开关变更时对当前页即时生效（SDG_APPLY 只重发变量层 / 或移除全部节点）
    val webTheme by prefs.webTheme.collectAsState()
    val textZoom by prefs.textZoom.collectAsState()
    val colorScheme = MaterialTheme.colorScheme
    val rootView = LocalView.current
    val appContext = LocalContext.current
    LaunchedEffect(colorScheme, webTheme) {
        state.webThemeEnabled = webTheme
        webView.setBackgroundColor(colorScheme.background.toArgb())
        state.pageThemeJs = ThemeInjector.applyScript(appContext) + "\n" + ThemeInjector.applyCall(colorScheme)
        // AR-05：earlyBg 受开关门控——关闭后 onPageStarted 不再注入底色
        state.earlyBgJs = if (webTheme) ThemeInjector.earlyBgJs(colorScheme) else ""
        val js = if (webTheme) state.pageThemeJs else ThemeInjector.removeCall()
        webView.evaluateJavascript(js, null)
        // AV-09：uiMode 在 configChanges（Activity 不重建），enableEdgeToEdge 只在
        // onCreate 决定一次栏图标明暗——明暗热切换后必须随实际色板更新，
        // 否则深→浅后状态栏白图标融进浅背景。亮度公式与 ThemeInjector.variableCss 一致。
        (rootView.context as? android.app.Activity)?.window?.insetsController?.let { ctrl ->
            val argb = colorScheme.surface.toArgb()
            val lum = 0.299 * ((argb shr 16) and 0xFF) +
                0.587 * ((argb shr 8) and 0xFF) + 0.114 * (argb and 0xFF)
            val mask = android.view.WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS or
                android.view.WindowInsetsController.APPEARANCE_LIGHT_NAVIGATION_BARS
            ctrl.setSystemBarsAppearance(if (lum >= 128) mask else 0, mask)
        }
    }
    LaunchedEffect(textZoom) { webView.settings.textZoom = textZoom }

    NavHost(navController = navController, startDestination = "home") {
        composable("home") {
            HomeScreen(
                prefs = prefs,
                state = state,
                webView = webView,
                onOpenSettings = { navController.navigate("settings") },
                onOpenNews = { navController.navigate("news") },
                onNavigateTab = ::navigateTab,
            )
        }
        composable("news") {
            val context = LocalContext.current
            NewsScreen(
                onBack = { navController.popBackStack() },
                onOpenLink = { url ->
                    navController.popBackStack()
                    // AR-01：资讯详情与其他 loadUrl 入口同一分流——bbs 域内壳内浏览，
                    // 域外（mag1 等）交系统浏览器，不进带桥的 WebView
                    when (UrlRules.classify(url)) {
                        is UrlRules.Verdict.Internal ->
                            webView.loadUrl(UrlRules.normalizeInternal(url))
                        else -> state.externalLink = url
                    }
                },
            )
        }
        composable("settings") {
            SettingsScreen(
                prefs = prefs,
                onBack = { navController.popBackStack() },
                // A-04：清除完成（回调）才反馈成功、才回首页——避免带着未删完的
                // Cookie 发出首页请求（服务器可能按登录态响应，观感"清了又登回"）
                onClearLoginState = { onDone ->
                    CookieManager.getInstance().removeAllCookies { _ ->
                        runCatching {
                            CookieManager.getInstance().flush()
                            webView.loadUrl(UrlRules.HOME)
                        }
                        onDone()
                    }
                },
                onTab = { i ->
                    navController.popBackStack()
                    navigateTab(i)
                },
            )
        }
        composable("login") {
            LoginScreen(
                prefs = prefs,
                onBack = { navController.popBackStack() },
                onLoggedIn = {
                    navController.popBackStack("home", inclusive = false)
                    navController.navigate("account")
                },
            )
        }
        composable("account") {
            AccountScreen(
                prefs = prefs,
                onBack = { navController.popBackStack() },
            )
        }
    }
}