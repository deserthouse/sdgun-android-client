package io.github.deserthouse.sdgun

import android.content.BroadcastReceiver
import io.github.deserthouse.sdgun.feature.settings.SettingsScreen
import io.github.deserthouse.sdgun.feature.feed.NewsScreen
import io.github.deserthouse.sdgun.feature.feed.FocusPage
import io.github.deserthouse.sdgun.feature.feed.FocusChannel
import io.github.deserthouse.sdgun.feature.feed.FocusRepository
import io.github.deserthouse.sdgun.feature.feed.FocusViewModel
import io.github.deserthouse.sdgun.feature.community.HomeScreen
import io.github.deserthouse.sdgun.feature.community.CommunityPage
import io.github.deserthouse.sdgun.feature.community.BoardPage
import io.github.deserthouse.sdgun.feature.thread.ThreadPage
import io.github.deserthouse.sdgun.feature.account.AccountScreen
import io.github.deserthouse.sdgun.feature.account.LoginScreen
import io.github.deserthouse.sdgun.feature.account.ProfilePage
import io.github.deserthouse.sdgun.feature.messages.MessagesPage
import io.github.deserthouse.sdgun.core.designsystem.AppBottomBar
import io.github.deserthouse.sdgun.core.model.UiState
import io.github.deserthouse.sdgun.data.repository.BoardRepository
import io.github.deserthouse.sdgun.data.repository.BoardListRepository
import io.github.deserthouse.sdgun.data.repository.ProfileRepository
import io.github.deserthouse.sdgun.data.repository.ThreadRepository
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
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.toArgb
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalView
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import io.github.deserthouse.sdgun.core.designsystem.SDGunTheme
import kotlinx.coroutines.launch
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
        // A70-03：会话单入口初始化（登录/退出唯一写路=SessionStore.onLogin/onLogout）
        io.github.deserthouse.sdgun.core.session.SessionManager.init(prefs)
        val debugBridgeEnabled = prefs.debugBridge.value
        if (applicationInfo.flags and android.content.pm.ApplicationInfo.FLAG_DEBUGGABLE != 0 && debugBridgeEnabled) {
            debugJsReceiver = object : BroadcastReceiver() {
                override fun onReceive(context: Context, i: Intent) {
                    val b64 = i.getStringExtra("b64") ?: return
                    android.util.Log.d("JsBridge", "recv ${b64.length} chars")
                    runCatching {
                        val js = String(Base64.decode(b64, Base64.DEFAULT), Charsets.UTF_8)
                        webView.evaluateJavascript(js, null)
                    }.onFailure { android.util.Log.w("JsBridge", "eval fail", it) }
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

    // A70-02：根目的地=四 tab 本身（聚焦/社区/消息/我的），全局选中由 route 单源推导；
    // 网页 URL 只描述兼容 WebView 页内部状态，不再反推 tab。
    // launchSingleTop=重选当前 tab 不重复入栈（不重载）；saveState/restoreState 保各 tab 状态
    fun navigateTab(index: Int) {
        val route = when (index) {
            0 -> "focus"
            1 -> "community"
            2 -> "messages"
            3 -> "profile"
            else -> return
        }
        android.util.Log.d("NavTab", "tap$index -> $route stack=${navController.currentBackStack.value.joinToString(",") { it.destination.route ?: "null" }}")
        navController.navigate(route) {
            popUpTo("focus") { saveState = true }
            launchSingleTop = true
            restoreState = true
        }
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

    // A-08：论坛深链发生时切到兼容壳路由（WebView 已加载目标内容）。
    // A70：home 不再是 startDestination——栈上没有 home 可回退，改用 navigate 入栈
    // （冷启深链：起始在 focus，效果触发后落到兼容壳；Back 再回 tab）
    LaunchedEffect(state.deepLinkTick) {
        if (state.deepLinkTick > 0 &&
            navController.currentBackStackEntry?.destination?.route != "home"
        ) {
            navController.navigate("home") { launchSingleTop = true }
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

    // 聚焦/资讯卡链接分流（AR-01 同源）：bbs 域内→兼容 WebView 壳内浏览；域外→系统浏览器
    fun openLink(url: String) {
        when (UrlRules.classify(url)) {
            is UrlRules.Verdict.Internal -> {
                webView.loadUrl(UrlRules.normalizeInternal(url))
                navController.navigate("home") { launchSingleTop = true }
            }
            else -> runCatching {
                appContext.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
            }
        }
    }

    // A70-01：新原生页全部接入真实导航；"home"（WebView 兼容壳）保留为设置分支/深链落点
    NavHost(navController = navController, startDestination = "focus") {
        // ── 一级 tab 0：聚焦（G2-A02 原生资讯流，频道条+内容卡）──
        composable("focus") {
            val scope = rememberCoroutineScope()
            val vm = remember { FocusViewModel(FocusRepository(), scope) }
            var channel by remember { mutableStateOf(FocusChannel.LATEST) }
            LaunchedEffect(Unit) { vm.select(channel) }
            TabShell(activeIdx = 0, onTab = { navigateTab(it) }) {
                FocusPage(
                    state = vm.state(channel),
                    channels = FocusChannel.entries.toList(),
                    currentChannel = channel,
                    onChannelSelect = { ch -> channel = ch; vm.select(ch) },
                    onOpenLink = { url -> openLink(url) },
                    onRetry = { vm.load(channel) },
                    modifier = Modifier.fillMaxSize(),
                )
            }
        }
        // ── 一级 tab 1：社区（G2-A03 板块树）──
        composable("community") {
            val scope = rememberCoroutineScope()
            var treeState by remember {
                mutableStateOf<UiState<List<BoardRepository.Group>>>(UiState.InitialLoading)
            }
            var myBoards by remember { mutableStateOf<List<BoardRepository.Board>>(emptyList()) }
            LaunchedEffect(Unit) {
                // 实测游客 circleTree 返回完整 5 组树：空返回不可能是成功态，按失败呈现
                val tree = BoardRepository.fetchTree()
                treeState = if (tree.isEmpty()) {
                    UiState.RetryableError("板块树拉取失败，请检查网络后重进本页")
                } else {
                    UiState.Data(tree)
                }
                myBoards = BoardRepository.fetchMyCircles()
            }
            TabShell(activeIdx = 1, onTab = { navigateTab(it) }) {
                CommunityPage(
                    state = treeState,
                    myBoards = myBoards,
                    onOpenBoard = { fid, name ->
                        navController.navigate("board/$fid?name=${Uri.encode(name)}")
                    },
                    modifier = Modifier.fillMaxSize(),
                )
            }
        }
        // ── 二级：板块（G2-A04）——挂社区栈，底栏隐藏，返回钮回社区 ──
        composable("board/{fid}?name={name}") { entry ->
            val fid = entry.arguments?.getString("fid")?.toLongOrNull() ?: 0L
            val fallbackName = entry.arguments?.getString("name") ?: ""
            val scope = rememberCoroutineScope()
            var listState by remember(fid) {
                mutableStateOf<UiState<BoardListRepository.PageResult>>(UiState.InitialLoading)
            }
            var meta by remember(fid) { mutableStateOf<BoardListRepository.BoardMeta?>(null) }
            var rows by remember(fid) {
                mutableStateOf<List<BoardListRepository.ThreadRow>>(emptyList())
            }
            var page by remember(fid) { mutableIntStateOf(1) }
            var totalPages by remember(fid) { mutableIntStateOf(1) }

            fun load(reset: Boolean) {
                val p = if (reset) 1 else page + 1
                scope.launch {
                    val pr = BoardListRepository.fetchPage(fid, p)
                    if (reset) {
                        listState = when {
                            pr == null -> UiState.RetryableError("板块拉取失败，请重试")
                            pr.authRequired -> UiState.AuthRequired
                            pr.errorMsg != null -> UiState.RetryableError(pr.errorMsg)
                            pr.rows.isEmpty() -> UiState.Empty()
                            else -> {
                                page = 1; totalPages = pr.totalPages; rows = pr.rows
                                UiState.Data(pr)
                            }
                        }
                    } else when {
                        pr == null -> // null=仅传输层失败：保留旧页，游标不推进
                            listState = UiState.Stale(
                                BoardListRepository.PageResult(rows, page, totalPages),
                                "下一页加载失败，可重试",
                            )
                        pr.rows.isEmpty() -> // 服务端空页=末页（实测契约）：静默收尾
                            totalPages = page
                        pr.errorMsg != null ->
                            listState = UiState.Stale(
                                BoardListRepository.PageResult(rows, page, totalPages),
                                pr.errorMsg,
                            )
                        else -> {
                            page = pr.page
                            totalPages = pr.totalPages
                            rows = (rows + pr.rows).distinctBy { it.tid }
                            listState = UiState.Data(
                                BoardListRepository.PageResult(rows, page, totalPages)
                            )
                        }
                    }
                }
            }

            LaunchedEffect(fid) {
                if (fid > 0L) {
                    load(true)
                    meta = BoardListRepository.fetchBoardMeta(fid)
                }
            }
            if (fid <= 0L) {
                LaunchedEffect(Unit) { navController.popBackStack() }
            } else {
                BoardPage(
                    state = listState,
                    meta = meta ?: BoardListRepository.BoardMeta(
                        fid = fid, name = fallbackName, iconUrl = null, postToday = null,
                        threadCount = null, postCount = null,
                        orders = emptyList(), threadTypes = emptyList(),
                    ),
                    onOpenThread = { tid, _ -> navController.navigate("thread/$tid/$fid") },
                    onBack = { navController.popBackStack() },
                    onLoadMore = { load(false) },
                )
            }
        }
        // ── 二级：帖子（G2-A05）——主楼+楼层流，返回钮回板块 ──
        composable("thread/{tid}/{fid}") { entry ->
            val tid = entry.arguments?.getString("tid")?.toLongOrNull() ?: 0L
            val fid = entry.arguments?.getString("fid")?.toLongOrNull() ?: 0L
            val scope = rememberCoroutineScope()
            var opState by remember(tid) {
                mutableStateOf<UiState<ThreadRepository.ThreadOP>>(UiState.InitialLoading)
            }
            var floorsState by remember(tid) {
                mutableStateOf<UiState<ThreadRepository.FloorsPage>>(UiState.InitialLoading)
            }
            var descending by remember(tid) { mutableStateOf(false) }
            var posterOnly by remember(tid) { mutableStateOf(false) }
            var authorId by remember(tid) { mutableStateOf<Long?>(null) }
            var floors by remember(tid) {
                mutableStateOf<List<ThreadRepository.Floor>>(emptyList())
            }
            var floorPage by remember(tid) { mutableIntStateOf(1) }
            var floorTotal by remember(tid) { mutableIntStateOf(1) }

            fun loadFloors(reset: Boolean) {
                val p = if (reset) 1 else floorPage + 1
                val aid = if (posterOnly) authorId else null
                scope.launch {
                    val fp = ThreadRepository.fetchFloors(tid, p, descending, aid)
                    if (fp == null) {
                        floorsState = UiState.Stale(
                            ThreadRepository.FloorsPage(floors, floorPage, floorTotal),
                            "楼层加载失败，可重试",
                        )
                    } else {
                        floorPage = fp.page
                        floorTotal = fp.totalPages
                        floors = if (reset) fp.floors
                        else (floors + fp.floors).distinctBy { it.pid }
                        floorsState = UiState.Data(
                            ThreadRepository.FloorsPage(floors, floorPage, floorTotal)
                        )
                    }
                }
            }

            LaunchedEffect(tid) {
                if (tid <= 0L) return@LaunchedEffect
                // G0-C01a：主楼在 threadView 独立端点；主楼成功后才拉楼层（authorId 供只看楼主）
                val op = ThreadRepository.fetchOP(tid, fid)
                opState = if (op == null) {
                    UiState.RetryableError("主楼加载失败，请返回重试")
                } else {
                    UiState.Data(op)
                }
                if (op != null) {
                    authorId = op.authorId
                    loadFloors(true)
                }
            }

            ThreadPage(
                opState = opState,
                floorsState = floorsState,
                descending = descending,
                posterOnly = posterOnly,
                onToggleOrder = { descending = !descending; loadFloors(true) },
                onTogglePoster = { posterOnly = !posterOnly; loadFloors(true) },
                onLoadMore = { loadFloors(false) },
                onBack = { navController.popBackStack() },
            )
        }
        // ── 一级 tab 2：消息（G3）——游客态 tab 内嵌登录面（门禁不跳路由，选中态不漂移）──
        composable("messages") {
            val token by prefs.apiToken.collectAsState()
            TabShell(activeIdx = 2, onTab = { navigateTab(it) }) {
                if (token.isBlank()) {
                    LoginScreen(
                        prefs = prefs,
                        onBack = { navController.popBackStack() },
                        onLoggedIn = { /* token 翻转自动切到消息面 */ },
                        onOpenSettings = { navController.navigate("settings") },
                    )
                } else {
                    var groups by remember(token) {
                        mutableStateOf<List<ProfileRepository.MessageGroup>>(emptyList())
                    }
                    var loading by remember(token) { mutableStateOf(true) }
                    LaunchedEffect(token) {
                        loading = true
                        groups = ProfileRepository.fetchMessageGroups(token)
                        loading = false
                    }
                    MessagesPage(
                        groups = groups,
                        isLoading = loading,
                        modifier = Modifier.fillMaxSize(),
                    )
                }
            }
        }
        // ── 一级 tab 3：我的（G3）——游客态内嵌登录面；齿轮=设置（设置属于"我的"）──
        composable("profile") {
            val token by prefs.apiToken.collectAsState()
            val userId by prefs.apiUserId.collectAsState()
            TabShell(activeIdx = 3, onTab = { navigateTab(it) }) {
                if (token.isBlank()) {
                    LoginScreen(
                        prefs = prefs,
                        onBack = { navController.popBackStack() },
                        onLoggedIn = { /* token 翻转自动切到个人面 */ },
                        onOpenSettings = { navController.navigate("settings") },
                    )
                } else {
                    var pState by remember(token) {
                        mutableStateOf<UiState<ProfileRepository.UserProfile>>(UiState.InitialLoading)
                    }
                    LaunchedEffect(token, userId) {
                        // A70-05：POST {id:userId} 官方契约；旧残留会话无 id → null=失败引导重登
                        val p = ProfileRepository.fetchMyInfo(token, userId)
                        pState = if (p == null) {
                            UiState.RetryableError("个人资料拉取失败，请退出后重新登录")
                        } else {
                            UiState.Data(p)
                        }
                    }
                    ProfilePage(
                        state = pState,
                        onBack = {},
                        onOpenSettings = { navController.navigate("settings") },
                        onLogout = {
                            io.github.deserthouse.sdgun.core.session.SessionManager.store.onLogout()
                        },
                    )
                }
            }
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
        // ── 兼容壳（A70-01 保留分支）：WebView 域内深链/资讯卡链接落点；不在 tab 模型内 ──
        composable("home") {
            HomeScreen(
                prefs = prefs,
                state = state,
                webView = webView,
                onOpenNews = { navController.navigate("news") },
                onNavigateTab = { navigateTab(it) },
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

/** 一级 tab 壳（A70-01）：底栏唯一挂载点——四个根路由共用；detail/兼容页不挂底栏，
 *  保证任何时刻底栏可见时恰好一个选中项（A70-02）。 */
@Composable
private fun TabShell(
    activeIdx: Int,
    onTab: (Int) -> Unit,
    content: @Composable () -> Unit,
) {
    Scaffold(
        containerColor = MaterialTheme.colorScheme.surface,
        bottomBar = { AppBottomBar(activeIdx = activeIdx, onTab = onTab) },
    ) { padding ->
        // 只消费 bottom：页内自持 Scaffold 的 TopAppBar 已处理状态栏 inset，
        // 顶部再套一层会造成双倍留白（A70 走查实测）
        Box(Modifier.fillMaxSize().padding(bottom = padding.calculateBottomPadding())) { content() }
    }
}