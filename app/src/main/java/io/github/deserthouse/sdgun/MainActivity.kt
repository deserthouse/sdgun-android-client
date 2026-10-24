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
import io.github.deserthouse.sdgun.feature.community.BoardViewModel
import io.github.deserthouse.sdgun.feature.thread.ReplySheet
import io.github.deserthouse.sdgun.feature.thread.ThreadPage
import io.github.deserthouse.sdgun.feature.thread.ThreadViewModel
import io.github.deserthouse.sdgun.feature.account.AccountScreen
import io.github.deserthouse.sdgun.feature.account.LoginScreen
import io.github.deserthouse.sdgun.feature.account.ProfilePage
import io.github.deserthouse.sdgun.feature.messages.MessagesPage
import io.github.deserthouse.sdgun.core.designsystem.AppBottomBar
import io.github.deserthouse.sdgun.core.mutation.FakeMutationGateway
import io.github.deserthouse.sdgun.core.mutation.HttpMutationGateway
import io.github.deserthouse.sdgun.core.mutation.MutationGateway
import io.github.deserthouse.sdgun.core.mutation.MutationKind
import io.github.deserthouse.sdgun.core.mutation.MutationRequest
import io.github.deserthouse.sdgun.core.mutation.MutationRunner
import io.github.deserthouse.sdgun.core.model.MutationState
import io.github.deserthouse.sdgun.core.model.WriteUi
import io.github.deserthouse.sdgun.core.model.WriteUiPhase
import io.github.deserthouse.sdgun.data.repository.DraftStore
import io.github.deserthouse.sdgun.BuildConfig
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
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.Text
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.IconButton
import androidx.compose.material3.Icon
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.toArgb
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateListOf
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
        val uri = intent.data ?: return false
        // A29：站方 scheme（shuidan://forumThreadList?fid=&circleId=）→ 原生板块页；
        // 未知 shuidan host 一律消费（不让死链接落到浏览器）
        if (uri.scheme == "shuidan") {
            val fid = uri.getQueryParameter("fid")?.toLongOrNull()
            if (fid != null && fid > 0L) {
                state.pendingNativeRoute = "board/$fid"
                state.nativeRouteTick++
            }
            return true
        }
        val url = uri.toString()
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
    // R4：搜索入口——落兼容壳搜索页（WebView 已载 site search；mag1 无帖子搜索 API）
    fun openCompatSearch() {
        webView.loadUrl(UrlRules.normalizeInternal("https://bbs.sdgun.com.cn/search.php?mod=forum&mobile=2"))
        navController.navigate("home") { launchSingleTop = true }
    }

    fun navigateTab(index: Int) {
        val route = when (index) {
            0 -> "focus"
            1 -> "community"
            2 -> "messages"
            3 -> "profile"
            else -> return
        }
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

    // A29：shuidan:// 等命中原生路由时跳转（板块/帖子参数路由）
    LaunchedEffect(state.nativeRouteTick) {
        val route = state.pendingNativeRoute
        if (state.nativeRouteTick > 0 && route != null) {
            navController.navigate(route) { launchSingleTop = true }
            state.pendingNativeRoute = null
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
            TabShell(activeIdx = 0, onTab = { navigateTab(it) }, onSearch = { openCompatSearch() }) {
                FocusPage(
                    state = vm.state(channel),
                    channels = FocusChannel.entries.toList(),
                    currentChannel = channel,
                    onChannelSelect = { ch -> channel = ch; vm.select(ch) },
                    onOpenLink = { url -> openLink(url) },
                    onRetry = { vm.load(channel) },
                    hasMore = vm.hasMore(channel),
                    loadingMore = vm.loadingMore(channel),
                    onLoadMore = { vm.loadMore(channel) },
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
            TabShell(activeIdx = 1, onTab = { navigateTab(it) }, onSearch = { openCompatSearch() }) {
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
            // A24-06：状态迁入 BoardViewModel（代际+互斥+空尾页原子；MainActivity 只留路由接线）
            val bvmScope = rememberCoroutineScope()
            val bvm = remember(fid) { BoardViewModel(bvmScope) }
            LaunchedEffect(fid) { if (fid > 0L) bvm.start(fid) }
            if (fid <= 0L) {
                LaunchedEffect(Unit) { navController.popBackStack() }
            } else {
                // ── U3-c 发帖（F14 前半）：表单+MutationRunner 五态（假服务三分支走查）──
                val postToken by prefs.apiToken.collectAsState()
                val postFake by prefs.mutationFake.collectAsState()
                val postGateway: MutationGateway =
                    if (BuildConfig.DEBUG && postFake != "off") FakeMutationGateway(postFake)
                    else HttpMutationGateway()
                val postScope = rememberCoroutineScope()
                var postOpen by remember(fid) { mutableStateOf(false) }
                var postState by remember(fid) {
                    mutableStateOf<MutationState>(MutationState.Draft)
                }
                val uploadTasks = remember(fid) {
                    mutableStateListOf<io.github.deserthouse.sdgun.feature.thread.UploadTaskItem>()
                }
                val pickImages = androidx.activity.compose.rememberLauncherForActivityResult(
                    androidx.activity.result.contract.ActivityResultContracts.PickMultipleVisualMedia(4),
                ) { uris ->
                    uris.forEachIndexed { i, u ->
                        uploadTasks.add(
                            io.github.deserthouse.sdgun.feature.thread.UploadTaskItem(
                                id = (fid % 1000).toInt() * 100 + i,
                                name = u.lastPathSegment ?: "image_$i",
                                state = io.github.deserthouse.sdgun.feature.thread.UploadState.WAITING,
                            ))
                    }
                }
                BoardPage(
                    state = bvm.listState,
                    meta = bvm.meta ?: BoardListRepository.BoardMeta(
                        fid = fid, name = fallbackName, iconUrl = null, postToday = null,
                        threadCount = null, postCount = null,
                        orders = emptyList(), threadTypes = emptyList(),
                    ),
                    onOpenThread = { tid, _ -> navController.navigate("thread/$tid/$fid") },
                    onBack = { navController.popBackStack() },
                    onLoadMore = { bvm.loadMore(fid) },
                    currentOrder = bvm.order,
                    currentTypeId = bvm.typeId,
                    onFilterChange = { o, t -> bvm.setFilter(fid, o, t) },
                    onPostThread = {
                        postState = MutationState.Draft
                        postOpen = true
                    },
                )
                if (postOpen) {
                    io.github.deserthouse.sdgun.feature.thread.ThreadPostSheet(
                        boardName = bvm.meta?.name ?: fallbackName,
                        authed = postToken.isNotBlank(),
                        submitting = postState is MutationState.Submitting,
                        mutationState = postState,
                        onDismiss = { postOpen = false },
                        onSubmit = { title, content ->
                            val form = io.github.deserthouse.sdgun.feature.thread.ThreadPostForm(
                                title = title, content = content,
                                fid = fid, circleId = fid,
                            )
                            val token = postToken
                            if (token.isBlank()) {
                                postState = MutationState.Rejected("请先登录")
                            } else {
                                postScope.launch {
                                    val epoch = io.github.deserthouse.sdgun.core.session
                                        .SessionManager.store.epoch.value
                                    postState = io.github.deserthouse.sdgun.core.mutation
                                        .MutationRunner.submit(
                                            MutationRequest(
                                                kind = MutationKind.THREAD_POST,
                                                params = form.toParams(),
                                                token = token,
                                            ),
                                            postGateway, epoch,
                                            { captured -> io.github.deserthouse.sdgun.core.session
                                                .SessionManager.store.epoch.isCurrent(captured) },
                                        )
                                }
                            }
                        },
                        onPickImages = {
                            pickImages.launch(
                                androidx.activity.result.PickVisualMediaRequest(
                                    androidx.activity.result.contract
                                        .ActivityResultContracts.PickVisualMedia.ImageOnly))
                        },
                        onRetryUpload = { t ->
                            postScope.launch {
                                io.github.deserthouse.sdgun.feature.thread.UploadQueueLogic.runTask(
                                    t, io.github.deserthouse.sdgun.feature.thread
                                        .HonestUnavailableUpload,
                                ) { upd ->
                                    val idx = uploadTasks.indexOfFirst { it.id == upd.id }
                                    if (idx >= 0) uploadTasks[idx] = upd
                                }
                            }
                        },
                        onRemoveUpload = { t -> uploadTasks.removeAll { it.id == t.id } },
                        uploadTasks = uploadTasks.toList(),
                    )
                }
            }
        }
        // ── 二级：帖子（G2-A05）——主楼+楼层流，返回钮回板块 ──
        composable("thread/{tid}/{fid}") { entry ->
            val tid = entry.arguments?.getString("tid")?.toLongOrNull() ?: 0L
            val fid = entry.arguments?.getString("fid")?.toLongOrNull() ?: 0L
            val scope = rememberCoroutineScope()
            // ── G4 写操作（真实写入验收=用户 MANUAL；AVD 走查用 debug 假服务，零触网）──
            val mutContext = LocalContext.current
            val mutToken by prefs.apiToken.collectAsState()
            val fakeMode by prefs.mutationFake.collectAsState()
            val gateway: MutationGateway =
                if (BuildConfig.DEBUG && fakeMode != "off") FakeMutationGateway(fakeMode)
                else HttpMutationGateway()
            var replyOpen by remember(tid) { mutableStateOf(false) }
            var replyState by remember(tid) { mutableStateOf<MutationState>(MutationState.Draft) }
            // A24-02：WriteUi 相位模型（Pending 保留乐观态 / Verify 禁直发需核对）
            val applaudStates = remember(tid) { mutableStateMapOf<Long, WriteUi>() }
            val applaudedIds = remember(tid) { mutableStateOf(setOf<Long>()) }
            val verifyAcked = remember(tid) { mutableStateOf(setOf<Long>()) }
            var verifyDialogFor by remember(tid) { mutableStateOf<Long?>(null) }
            var collected by remember(tid) { mutableStateOf(false) }
            var collectUi by remember(tid) { mutableStateOf(WriteUi()) }
            var collectVerifyAcked by remember(tid) { mutableStateOf(false) }
            // A24-03：草稿按 所有者+帖+回复目标 隔离
            val mutUserId by prefs.apiUserId.collectAsState()
            val draftOwner = DraftStore.ownerOf(mutUserId)

            fun submitMutation(kind: MutationKind, params: Map<String, String>, onState: (MutationState) -> Unit) {
                val t = mutToken
                if (t.isBlank()) return
                val cap = io.github.deserthouse.sdgun.core.session.SessionManager.store.epoch.value
                scope.launch {
                    MutationRunner.submit(
                        MutationRequest(kind, params, t), gateway, cap,
                        { captured -> io.github.deserthouse.sdgun.core.session.SessionManager.store.epoch.isCurrent(captured) },
                    ) { st -> onState(st) }
                }
            }
            // A24-06：读态迁入 ThreadViewModel（代际+取消+互斥+空尾页原子）
            val tvmScope = rememberCoroutineScope()
            val tvm = remember(tid) { ThreadViewModel(tvmScope) }
            val opState = tvm.opState
            val floorsState = tvm.floorsState
            fun doApplaud(pid: Long) {
                if (mutToken.isBlank()) { navigateTab(3); return }
                val ui = applaudStates[pid]
                // 在途禁重复；Verify 未核对→弹核对对话框（A24-02：禁直发重试）
                if (ui?.phase == WriteUiPhase.Pending) return
                if (ui?.phase == WriteUiPhase.Verify && pid !in verifyAcked.value) {
                    verifyDialogFor = pid
                    return
                }
                applaudedIds.value = applaudedIds.value + pid  // 乐观 +1（Pending 保留）
                applaudStates[pid] = WriteUi(WriteUiPhase.Pending)
                submitMutation(MutationKind.APPLAUD, mapOf("content_id" to "$pid")) { st ->
                    when (st) {
                        is MutationState.Confirmed ->
                            applaudStates[pid] = WriteUi(WriteUiPhase.Done)
                        is MutationState.Rejected -> {
                            applaudStates[pid] = WriteUi(WriteUiPhase.Failed, st.reason)
                            applaudedIds.value = applaudedIds.value - pid  // 回滚
                        }
                        is MutationState.OutcomeUnknown -> {
                            // 结果未知：回滚乐观显示 + Verify（核对后经对话框才可重发）
                            applaudStates[pid] = WriteUi(WriteUiPhase.Verify)
                            applaudedIds.value = applaudedIds.value - pid
                        }
                        else -> {}
                    }
                }
            }

            fun doCollect() {
                if (mutToken.isBlank()) { navigateTab(3); return }
                if (collectUi.phase == WriteUiPhase.Pending) return
                if (collectUi.phase == WriteUiPhase.Verify && !collectVerifyAcked) {
                    collectVerifyAcked = true
                }
                val op = (opState as? UiState.Data<ThreadRepository.ThreadOP>)?.value
                collectUi = WriteUi(WriteUiPhase.Pending)
                submitMutation(
                    MutationKind.COLLECT,
                    mapOf(
                        "title" to (op?.title ?: ""),
                        "url" to "https://bbs.sdgun.com.cn/forum.php?mod=viewthread&tid=$tid",
                        "content_type" to "thread",
                        "id" to "$fid",
                        "content_id" to "$tid",
                    ),
                ) { st ->
                    when (st) {
                        is MutationState.Confirmed -> {
                            collectUi = WriteUi(WriteUiPhase.Done)
                            collected = true
                        }
                        is MutationState.Rejected ->
                            collectUi = WriteUi(WriteUiPhase.Failed, st.reason)
                        is MutationState.OutcomeUnknown ->
                            collectUi = WriteUi(WriteUiPhase.Verify)
                        else -> {}
                    }
                }
            }

            LaunchedEffect(tid) { if (tid > 0L) tvm.start(tid, fid) }

            ThreadPage(
                opState = opState,
                floorsState = floorsState,
                descending = tvm.descending,
                posterOnly = tvm.posterOnly,
                onToggleOrder = { tvm.toggleOrder(tid) },
                onTogglePoster = { tvm.togglePoster(tid) },
                onLoadMore = { tvm.loadMore(tid) },
                onRetryFloors = { tvm.retryFloors(tid) },
                onBack = { navController.popBackStack() },
                isLoggedIn = mutToken.isNotBlank(),
                applaudStates = applaudStates,
                applaudedIds = applaudedIds.value,
                verifyDialogFor = verifyDialogFor,
                onVerifyAck = { pid ->
                    verifyDialogFor = null
                    verifyAcked.value = verifyAcked.value + pid
                    doApplaud(pid)  // 用户显式核对后允许重发（A24-02）
                },
                onVerifyDismiss = { verifyDialogFor = null },
                onApplaud = { pid -> doApplaud(pid) },
                isCollected = collected,
                collectState = collectUi,
                onCollect = { doCollect() },
                onReply = {
                    if (mutToken.isBlank()) { navigateTab(3) } else { replyOpen = true }
                },
            )
            if (replyOpen) {
                ReplySheet(
                    initialDraft = DraftStore.load(mutContext, draftOwner, tid),
                    state = replyState,
                    onDraftChange = { DraftStore.save(mutContext, draftOwner, tid, it) },
                    onSend = { text ->
                        // auth=同盾参数（静态编目不可复刻，空值提交；真实验收=用户 MANUAL）
                        submitMutation(
                            MutationKind.REPLY,
                            mapOf(
                                "tid" to "$tid",
                                "reply_id" to "",
                                "circle_id" to "",
                                "content" to text,
                                "video" to "",
                                "auth" to "",
                                "users" to "",
                            ),
                        ) { st ->
                            replyState = st
                            if (st is MutationState.Confirmed) {
                                DraftStore.clear(mutContext, draftOwner, tid)
                                replyOpen = false
                                tvm.retryFloors(tid)  // 回复成功刷新楼层（当前筛选代际）
                            }
                        }
                    },
                    onDismiss = { replyOpen = false },
                )
            }
        }
        // ── 一级 tab 2：消息（G3）——游客态 tab 内嵌登录面（门禁不跳路由，选中态不漂移）──
        composable("messages") {
            val token by prefs.apiToken.collectAsState()
            TabShell(activeIdx = 2, onTab = { navigateTab(it) }, onSearch = { openCompatSearch() }) {
                if (token.isBlank()) {
                    LoginScreen(
                        prefs = prefs,
                        onBack = { navController.popBackStack() },
                        onLoggedIn = { /* token 翻转自动切到消息面 */ },
                        onOpenSettings = { navController.navigate("settings") },
                        embedded = true,
                    )
                } else {
                    val profileFake by prefs.profileFake.collectAsState()
                    var groups by remember(token) {
                        mutableStateOf<List<ProfileRepository.MessageGroup>>(emptyList())
                    }
                    var loading by remember(token) { mutableStateOf(true) }
                    LaunchedEffect(token, profileFake) {
                        loading = true
                        // U3-b：合成模式走夹具（零触网），off=真端点
                        groups = if (profileFake == "data") {
                            listOf(
                                ProfileRepository.MessageGroup("g1", "（合成）私信：弓友A", 2),
                                ProfileRepository.MessageGroup("g2", "（合成）私信：皮筋交流群", 5),
                            )
                        } else if (profileFake == "empty") emptyList()
                        else if (profileFake == "error") emptyList()
                        else ProfileRepository.fetchMessageGroups(token)
                        loading = false
                    }
                    MessagesPage(
                        groups = groups,
                        isLoading = loading,
                        onOpenGroup = { g ->
                            navController.navigate(
                                "messages/conversation/${g.id}?name=${Uri.encode(g.name)}")
                        },
                        onOpenNotifications = { navController.navigate("notifications") },
                        modifier = Modifier.fillMaxSize(),
                    )
                }
            }
        }
        // ── 一级 tab 3：我的（G3）——游客态内嵌登录面；齿轮=设置（设置属于"我的"）──
        composable("profile") {
            val token by prefs.apiToken.collectAsState()
            val userId by prefs.apiUserId.collectAsState()
            TabShell(activeIdx = 3, onTab = { navigateTab(it) }, onSearch = { openCompatSearch() }) {
                if (token.isBlank()) {
                    LoginScreen(
                        prefs = prefs,
                        onBack = { navController.popBackStack() },
                        onLoggedIn = { /* token 翻转自动切到个人面 */ },
                        onOpenSettings = { navController.navigate("settings") },
                        embedded = true,
                    )
                } else {
                    val profileFake by prefs.profileFake.collectAsState()
                    var pState by remember(token) {
                        mutableStateOf<UiState<ProfileRepository.UserProfile>>(UiState.InitialLoading)
                    }
                    LaunchedEffect(token, userId, profileFake) {
                        // A70-05：POST {id:userId} 官方契约；旧残留会话无 id → null=失败引导重登
                        // U3-b：合成模式给夹具资料（零触网走查）
                        pState = if (profileFake == "data") {
                            UiState.Data(ProfileRepository.UserProfile(
                                userId = 99999L, name = "sdgfake（合成）", head = null,
                                groupName = "合成用户组", groupLevel = null, score = "0",
                                sex = null, sign = "合成模式：设置→开发者→合成个人数据",
                                hasPhone = false, isVip = 0,
                            ))
                        } else {
                            val p = ProfileRepository.fetchMyInfo(token, userId)
                            if (p == null) {
                                UiState.RetryableError("个人资料拉取失败，请退出后重新登录")
                            } else {
                                UiState.Data(p)
                            }
                        }
                    }
                    ProfilePage(
                        state = pState,
                        onBack = {},
                        onOpenSettings = { navController.navigate("settings") },
                        onLogout = {
                            io.github.deserthouse.sdgun.core.session.SessionManager.store.onLogout()
                        },
                        onOpenPeople = { kind ->
                            navController.navigate("profile/people/${kind.name}")
                        },
                        onOpenCollect = { navController.navigate("profile/collect") },
                        onOpenServices = { navController.navigate("services") },
                    )
                }
            }
        }
        // ── U3-b（F18/19）：访客/历史/关注/收藏（合成先行，off=真实读链）──
        composable("profile/people/{kind}") { entry ->
            val kindName = entry.arguments?.getString("kind") ?: ""
            val kind = io.github.deserthouse.sdgun.feature.profile.PeopleKind.entries
                .firstOrNull { it.name == kindName }
            if (kind == null) {
                LaunchedEffect(Unit) { navController.popBackStack() }
            } else {
                val token by prefs.apiToken.collectAsState()
                val profileFake by prefs.profileFake.collectAsState()
                // kind 决定真实读链（loader 工厂按 kind 分流三端点）
                val loader = remember(kind, token, profileFake) {
                    io.github.deserthouse.sdgun.feature.profile.peopleLoader(
                        kind, token.ifBlank { null }, profileFake,
                    ) { t ->
                        when (kind) {
                            io.github.deserthouse.sdgun.feature.profile.PeopleKind.VISITORS ->
                                ProfileRepository.fetchVisitors(t)
                            io.github.deserthouse.sdgun.feature.profile.PeopleKind.HISTORY ->
                                ProfileRepository.fetchViewUsers(t)
                            io.github.deserthouse.sdgun.feature.profile.PeopleKind.FOLLOWS ->
                                ProfileRepository.fetchFollows(t)
                        }
                    }
                }
                var peopleState by remember(kind, token, profileFake) {
                    mutableStateOf<UiState<io.github.deserthouse.sdgun.feature.profile.PeoplePageData>>(
                        UiState.InitialLoading)
                }
                LaunchedEffect(kind, token, profileFake) {
                    peopleState = UiState.InitialLoading
                    peopleState = loader()
                }
                io.github.deserthouse.sdgun.feature.profile.PeopleScaffold(
                    title = kind.title,
                    state = peopleState,
                    onBack = { navController.popBackStack() },
                    onRetry = { peopleState = UiState.InitialLoading },
                    listContent = {
                        io.github.deserthouse.sdgun.feature.profile.PeopleListBody(it)
                    },
                )
            }
        }
        composable("profile/collect") {
            val token by prefs.apiToken.collectAsState()
            val profileFake by prefs.profileFake.collectAsState()
            var collectState by remember(token, profileFake) {
                mutableStateOf<UiState<io.github.deserthouse.sdgun.feature.profile.CollectPageData>>(
                    UiState.InitialLoading)
            }
            LaunchedEffect(token, profileFake) {
                collectState = UiState.InitialLoading
                collectState = io.github.deserthouse.sdgun.feature.profile.collectLoader(
                    token.ifBlank { null }, profileFake,
                )()
            }
            io.github.deserthouse.sdgun.feature.profile.PeopleScaffold(
                title = "收藏",
                state = collectState,
                onBack = { navController.popBackStack() },
                onRetry = { collectState = UiState.InitialLoading },
                listContent = {
                    io.github.deserthouse.sdgun.feature.profile.CollectListBody(it) { tid ->
                        navController.navigate("thread/$tid/0")
                    }
                },
            )
        }
        // ── U3-b（F20）：会话详情（合成先行——详情端点参数测绘标注待用）──
        composable("messages/conversation/{id}?name={name}") { entry ->
            val name = entry.arguments?.getString("name") ?: "会话"
            val profileFake by prefs.profileFake.collectAsState()
            var convState by remember(name, profileFake) {
                mutableStateOf<UiState<io.github.deserthouse.sdgun.feature.messages.ConversationData>>(
                    UiState.InitialLoading)
            }
            LaunchedEffect(name, profileFake) {
                convState = if (profileFake == "data") {
                    UiState.Data(io.github.deserthouse.sdgun.feature.messages
                        .syntheticConversation(name))
                } else if (profileFake == "empty") {
                    UiState.Empty("暂无消息")
                } else if (profileFake == "error") {
                    UiState.RetryableError("（合成）加载失败，可重试")
                } else {
                    // 真实路径：详情端点待测绘——诚实未接入，不冒充空/失败
                    UiState.RetryableError("会话详情读取待接口接入")
                }
            }
            io.github.deserthouse.sdgun.feature.messages.ConversationDetailPage(
                groupName = name,
                data = (convState as? UiState.Data<*>)?.value
                    as? io.github.deserthouse.sdgun.feature.messages.ConversationData,
                isLoading = convState is UiState.InitialLoading,
                onBack = { navController.popBackStack() },
            )
        }
        // ── U3-b（F21）：通知中心（合成先行+权限拒绝态）──
        composable("notifications") {
            val token by prefs.apiToken.collectAsState()
            val profileFake by prefs.profileFake.collectAsState()
            var notifyState by remember(token, profileFake) {
                mutableStateOf<UiState<io.github.deserthouse.sdgun.feature.messages.NotifyData>>(
                    UiState.InitialLoading)
            }
            LaunchedEffect(token, profileFake) {
                notifyState = if (profileFake == "data") {
                    UiState.Data(io.github.deserthouse.sdgun.feature.messages.syntheticNotify())
                } else if (profileFake == "empty") {
                    UiState.Empty("暂无通知")
                } else if (profileFake == "error") {
                    UiState.RetryableError("（合成）加载失败，可重试")
                } else if (token.isBlank()) {
                    UiState.AuthRequired
                } else {
                    // userNotifyIndex 解析器待凭据实测后补——诚实未接入
                    UiState.RetryableError("通知读取待接口接入")
                }
            }
            io.github.deserthouse.sdgun.feature.messages.NotificationPage(
                state = notifyState,
                onBack = { navController.popBackStack() },
                onRetry = { notifyState = UiState.InitialLoading },
            )
        }
        // ── U3-c（F23~27）：H5 服务（配置驱动）+ 容器 ──
        composable("services") {
            val ctx = LocalContext.current
            io.github.deserthouse.sdgun.feature.h5.ServicesPage(
                onBack = { navController.popBackStack() },
                onOpenEntry = { e ->
                    when (e.mode) {
                        // AR-01 域外分流：EXTERNAL 交系统浏览器，不进带桥 WebView
                        io.github.deserthouse.sdgun.feature.h5.H5Mode.EXTERNAL ->
                            ctx.startActivity(android.content.Intent(
                                android.content.Intent.ACTION_VIEW, android.net.Uri.parse(e.url)))
                        else -> navController.navigate(
                            "h5/${Uri.encode(e.title)}?url=${Uri.encode(e.url)}")
                    }
                },
            )
        }
        composable("h5/{title}?url={url}") { entry ->
            val title = entry.arguments?.getString("title") ?: "H5"
            val url = entry.arguments?.getString("url") ?: return@composable
            val ctx = LocalContext.current
            io.github.deserthouse.sdgun.feature.h5.H5Container(
                title = title,
                url = url,
                state = state,
                webView = webView,
                onBack = { navController.popBackStack() },
                onOpenExternal = { u ->
                    ctx.startActivity(android.content.Intent(
                        android.content.Intent.ACTION_VIEW, android.net.Uri.parse(u)))
                },
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
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TabShell(
    activeIdx: Int,
    onTab: (Int) -> Unit,
    onSearch: () -> Unit = {},
    content: @Composable () -> Unit,
) {
    Scaffold(
        containerColor = MaterialTheme.colorScheme.surface,
        topBar = {
            // R4：原生导航搜索入口——落兼容壳搜索页（mag1 无帖子搜索 API，搜索永久岛）
            TopAppBar(
                title = { Text("SDGun") },
                actions = {
                    IconButton(onClick = onSearch) {
                        Icon(Icons.Filled.Search, contentDescription = "搜索")
                    }
                },
            )
        },
        bottomBar = { AppBottomBar(activeIdx = activeIdx, onTab = onTab) },
    ) { padding ->
        // 只消费 bottom：页内自持 Scaffold 的 TopAppBar 已处理状态栏 inset，
        // 顶部再套一层会造成双倍留白（A70 走查实测）
        Box(Modifier.fillMaxSize().padding(bottom = padding.calculateBottomPadding())) { content() }
    }
}