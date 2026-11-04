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
import androidx.compose.ui.unit.dp
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
import androidx.compose.material3.TextButton
import androidx.compose.material3.Text
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.IconButton
import androidx.compose.material3.Icon
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.outlined.Settings
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
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.fillMaxWidth
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
        // Q1（A1-8）：shuidan:// typed Target——forumThreadList/forumThread/user 三形态原生直达；
        // 未知 shuidan host=可恢复回退（回聚焦 tab），不吞死链也不外漏浏览器
        if (uri.scheme == "shuidan") {
            when (val t = io.github.deserthouse.sdgun.core.navigation.TargetParser.parse(uri.toString())) {
                is io.github.deserthouse.sdgun.core.navigation.Target.Board ->
                    state.pendingNativeRoute = "board/${t.fid}"
                is io.github.deserthouse.sdgun.core.navigation.Target.Thread ->
                    state.pendingNativeRoute =
                        "thread/${t.tid}/0" + (t.pid?.let { "?pid=$it" } ?: "")
                is io.github.deserthouse.sdgun.core.navigation.Target.User ->
                    state.pendingNativeRoute = "user/${t.uid}"
                else -> state.pendingNativeRoute = "focus"
            }
            state.nativeRouteTick++
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
    // Q5（A1-10）：搜索入口——原生搜索页（圈子+用户双通道；帖子全文搜索=页内 H5 桥接）
    fun openSearch() {
        navController.navigate("search") { launchSingleTop = true }
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
    // Q1（A4-5/深链统一）：可识别的站方 web 形态（viewthread/forumdisplay/space-uid）升级为
    // 原生路由（通知行/正文链接块/媒体外开共用此入口）；其余仍走 WebView 壳
    fun openLink(url: String) {
        val t = io.github.deserthouse.sdgun.core.navigation.TargetParser.parse(url)
        if (t is io.github.deserthouse.sdgun.core.navigation.Target.Board) {
            navController.navigate("board/${t.fid}") { launchSingleTop = true }; return
        }
        if (t is io.github.deserthouse.sdgun.core.navigation.Target.Thread) {
            navController.navigate("thread/${t.tid}/0" + (t.pid?.let { "?pid=$it" } ?: "")) {
                launchSingleTop = true
            }
            return
        }
        if (t is io.github.deserthouse.sdgun.core.navigation.Target.User) {
            navController.navigate("user/${t.uid}") { launchSingleTop = true }; return
        }
        // Web/null：站内→兼容壳；域外→系统浏览器
        when (UrlRules.classify(url)) {
            is UrlRules.Verdict.Internal -> {
                webView.loadUrl(UrlRules.normalizeInternal(url))
                navController.navigate("home") { launchSingleTop = true }
                state.deepLinkTick++
            }
            else -> runCatching {
                appContext.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
            }
        }
    }

    /** A1-3：附件下载（系统 DownloadManager；带 WebView Cookie 供站方附件鉴权） */
    fun downloadAttachment(url: String, name: String) {
        runCatching {
            val req = android.app.DownloadManager.Request(Uri.parse(url)).apply {
                setTitle(name.ifBlank { "附件" })
                setDescription("SDGun 附件下载")
                setNotificationVisibility(
                    android.app.DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
                android.webkit.CookieManager.getInstance().getCookie(url)?.let {
                    addRequestHeader("Cookie", it)
                }
                setDestinationInExternalPublicDir(
                    android.os.Environment.DIRECTORY_DOWNLOADS,
                    name.ifBlank { "attachment_${System.currentTimeMillis()}" },
                )
            }
            (appContext.getSystemService(android.content.Context.DOWNLOAD_SERVICE)
                as android.app.DownloadManager).enqueue(req)
        }
    }

    // A70-01：新原生页全部接入真实导航；"home"（WebView 兼容壳）保留为设置分支/深链落点
    // Q2：写网关上提到 NavHost 级——thread/收藏/用户/会话四处写面共用同一 fake 门控
    val mutFakeMode by prefs.mutationFake.collectAsState()
    val mutGateway: MutationGateway =
        if (BuildConfig.DEBUG && mutFakeMode != "off") FakeMutationGateway(mutFakeMode)
        else HttpMutationGateway()
    NavHost(navController = navController, startDestination = "focus") {
        // ── 一级 tab 0：聚焦（G2-A02 原生资讯流，频道条+内容卡）──
        composable("focus") {
            val scope = rememberCoroutineScope()
            val vm = remember { FocusViewModel(FocusRepository(), scope) }
            var channel by remember { mutableStateOf(FocusChannel.LATEST) }
            LaunchedEffect(Unit) { vm.select(channel) }
            TabShell(activeIdx = 0, onTab = { navigateTab(it) }, onSearch = { openSearch() }) {
                val nfs by prefs.nativeFontScale.collectAsState()
                io.github.deserthouse.sdgun.core.designsystem.ReadingTheme(nfs) {
                FocusPage(
                    state = vm.state(channel),
                    channels = FocusChannel.entries.toList(),
                    currentChannel = channel,
                    onChannelSelect = { ch -> channel = ch; vm.select(ch) },
                    onOpenTarget = { target ->
                    when (target) {
                        is io.github.deserthouse.sdgun.data.repository.FeedTarget.Thread ->
                            navController.navigate(
                                "thread/${target.tid}/0" + (target.pid?.let { "?pid=$it" } ?: "")) {
                                launchSingleTop = true
                            }
                        is io.github.deserthouse.sdgun.data.repository.FeedTarget.Article ->
                            navController.navigate(
                                "h5/${Uri.encode("文章")}?url=${Uri.encode(target.url)}")
                        is io.github.deserthouse.sdgun.data.repository.FeedTarget.External ->
                            runCatching {
                                appContext.startActivity(
                                    Intent(Intent.ACTION_VIEW, Uri.parse(target.url)))
                            }
                        null -> {}
                    }
                },
                    onRetry = { vm.load(channel) },
                    hasMore = vm.hasMore(channel),
                    loadingMore = vm.loadingMore(channel),
                    onLoadMore = { vm.loadMore(channel) },
                    modifier = Modifier.fillMaxSize(),
                )
                }
            }
        }
        // ── 一级 tab 1：社区（G2-A03 板块树）──
        composable("community") {
            val scope = rememberCoroutineScope()
            var treeState by remember {
                mutableStateOf<UiState<List<BoardRepository.Group>>>(UiState.InitialLoading)
            }
            var myBoards by remember { mutableStateOf<List<BoardRepository.Board>>(emptyList()) }
            val myToken by prefs.apiToken.collectAsState()
            LaunchedEffect(myToken) {
                // A1-7：token 进 key——换号/登出重取我的板块（归属随会话变）
                // 实测游客 circleTree 返回完整 5 组树：空返回不可能是成功态，按失败呈现
                val tree = BoardRepository.fetchTree()
                treeState = if (tree.isEmpty()) {
                    UiState.RetryableError("板块树拉取失败，请检查网络后重进本页")
                } else {
                    UiState.Data(tree)
                }
                myBoards = BoardRepository.fetchMyCircles(myToken.ifBlank { null })
            }
            TabShell(activeIdx = 1, onTab = { navigateTab(it) }, onSearch = { openSearch() },
                title = "社区") {
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
                val mutUserId by prefs.apiUserId.collectAsState()
                val mutContext = LocalContext.current
                val postFake by prefs.mutationFake.collectAsState()
                val postGateway: MutationGateway =
                    if (BuildConfig.DEBUG && postFake != "off") FakeMutationGateway(postFake)
                    else HttpMutationGateway()
                val postScope = rememberCoroutineScope()
                var postOpen by remember(fid) { mutableStateOf(false) }
                var postState by remember(fid) {
                    mutableStateOf<MutationState>(MutationState.Draft)
                }
                // WP4-A/S03：草稿修订号（在途期间新键入使 rev 前移→成功不清洗新输入）
                var postDraftRev by remember(fid) { mutableIntStateOf(0) }
                // WP4-A/S02：任务恢复（进程/配置重建后按 URI 保留，RUNNING→WAITING 待补启）
                val uploadTasks = androidx.compose.runtime.saveable.rememberSaveable(
                    fid, saver = io.github.deserthouse.sdgun.feature.thread.UploadTasksSaver,
                ) {
                    mutableStateListOf<io.github.deserthouse.sdgun.feature.thread.UploadTaskItem>()
                }
                // Q3：column-info（circle_id 服务端换取[fid≠circle_id]+分类 types；
                // 面板打开且已登录时拉取，失败可重试——circleIdReady 门禁发布）
                var colInfoState by remember(fid) {
                    mutableStateOf<UiState<io.github.deserthouse.sdgun.data.repository
                        .ThreadRepository.PostColumnInfo>>(UiState.InitialLoading)
                }
                var colInfoRetry by remember(fid) { mutableIntStateOf(0) }
                LaunchedEffect(postOpen, postToken, colInfoRetry) {
                    if (!postOpen) return@LaunchedEffect
                    if (postToken.isBlank()) { colInfoState = UiState.AuthRequired; return@LaunchedEffect }
                    colInfoState = UiState.InitialLoading
                    val info = io.github.deserthouse.sdgun.data.repository.ThreadRepository
                        .fetchPostColumnInfo(postToken, fid)
                    colInfoState = if (info == null) {
                        UiState.RetryableError("板块参数获取失败（发帖需要服务端下发参数）")
                    } else UiState.Data(info)
                }
                // Q3：真实上传传输（登录态=uploadPicNew multipart；游客=诚实不可用）
                val uploadTransport: io.github.deserthouse.sdgun.feature.thread.UploadTransport =
                    if (postToken.isNotBlank()) {
                        io.github.deserthouse.sdgun.feature.thread.HttpUploadTransport(
                            mutContext, postToken)
                    } else io.github.deserthouse.sdgun.feature.thread.HonestUnavailableUpload
                // WP4-A/S02：队列所有者（enqueue 即启动；transport 随登录态重建）
                val uploadOwner = remember(fid, postToken) {
                    io.github.deserthouse.sdgun.feature.thread.UploadQueueOwner(
                        postScope, uploadTasks, uploadTransport)
                }
                val pickImages = androidx.activity.compose.rememberLauncherForActivityResult(
                    androidx.activity.result.contract.ActivityResultContracts.PickMultipleVisualMedia(4),
                ) { uris ->
                    uris.forEach { u ->
                        // WP4-A/S02：入列即启动（旧实现只 add WAITING=首次上传不可达）
                        uploadOwner.enqueue(
                            io.github.deserthouse.sdgun.feature.thread.UploadTaskItem(
                                uuid = java.util.UUID.randomUUID().toString(),
                                name = u.lastPathSegment ?: "image",
                                state = io.github.deserthouse.sdgun.feature.thread.UploadState.WAITING,
                                uri = u.toString(),
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
                    onRefresh = { bvm.refresh(fid) },
                    isLoadingMore = bvm.paging,
                    onPostThread = {
                        postState = MutationState.Draft
                        postOpen = true
                    },
                )
                if (postOpen) {
                    val draftOwner = io.github.deserthouse.sdgun.data.repository.DraftStore
                        .ownerOf(mutUserId)
                    io.github.deserthouse.sdgun.feature.thread.ThreadPostSheet(
                        boardName = bvm.meta?.name ?: fallbackName,
                        authed = postToken.isNotBlank(),
                        // W0（A1104-V04）：游客 CTA 接回——Q3 重写时 onLogin 漏传致「去登录」不可达
                        onLogin = { navController.navigate("login") },
                        submitting = postState is MutationState.Submitting,
                        mutationState = postState,
                        initialTitle = io.github.deserthouse.sdgun.data.repository.DraftStore
                            .loadPost(mutContext, draftOwner, fid).first,
                        initialContent = io.github.deserthouse.sdgun.data.repository.DraftStore
                            .loadPost(mutContext, draftOwner, fid).second,
                        onDraftChange = { t, c ->
                            io.github.deserthouse.sdgun.data.repository.DraftStore
                                .savePost(mutContext, draftOwner, fid, t, c)
                            postDraftRev++  // WP4-A/S03：修订计数（成功清稿按提交时修订判定）
                        },
                        onDismiss = { postOpen = false },
                        onSubmit = { title, content, typeId ->
                            // R2/A3-3：UPLOADED 附件序列化进正文（未上传被 sheet 阻断，此处防御同规）
                            val finalContent = io.github.deserthouse.sdgun.feature.thread.UploadQueueLogic
                                .serializeUploadedInto(content, uploadTasks.toList())
                            // Q3：circle_id 必须经 column-info 服务端换取（fid≠circle_id，jadx 实锤）；
                            // 装机取证修正：debug 假服务模式下走全合成（fid 直代 circle_id）——
                            // 否则假模式被真 column-info 门堵死，写状态机走查不可达（av12 实证）
                            val fakeActive = BuildConfig.DEBUG && postFake != "off"
                            val colInfo = (colInfoState as? UiState.Data<*>)?.value
                                as? io.github.deserthouse.sdgun.data.repository.ThreadRepository.PostColumnInfo
                            val form = io.github.deserthouse.sdgun.feature.thread.ThreadPostForm(
                                title = title, content = finalContent,
                                fid = fid,
                                circleId = colInfo?.circleId ?: if (fakeActive) fid else 0L,
                                typeId = typeId,
                            )
                            // WP4-A/S03：在途互斥（双击/键盘触发/关开面板请求=1）——
                            // lambda 无隐式标签，用 if/else 吞掉重复触发
                            if (postState is MutationState.Submitting) {
                                // 吞掉
                            } else {
                            val token = postToken
                            val submittedRev = postDraftRev
                            when {
                                token.isBlank() -> postState = MutationState.Rejected("请先登录")
                                // fake 模式旁路（与按钮门同口径）：全合成语义不查真 column-info
                                colInfo == null && !fakeActive -> postState =
                                    MutationState.Rejected("板块参数未就绪，请重试获取")
                                else -> postScope.launch {
                                    // 同步占位 Submitting（旧实现未接 onState=按钮终态前恒可点）
                                    postState = MutationState.Submitting
                                    val epoch = io.github.deserthouse.sdgun.core.session
                                        .SessionManager.store.epoch.value
                                    val st = io.github.deserthouse.sdgun.core.mutation
                                        .MutationRunner.submit(
                                            MutationRequest(
                                                kind = MutationKind.THREAD_POST,
                                                params = form.toParams(),
                                                token = token,
                                            ),
                                            postGateway, epoch,
                                            { captured -> io.github.deserthouse.sdgun.core.session
                                                .SessionManager.store.epoch.isCurrent(captured) },
                                            onState = { s -> postState = s },
                                        )
                                    postState = st
                                    if (st is MutationState.Confirmed) {
                                        // Q3 成功定位：关面板+刷新板块+（响应含 tid 时）直达新帖；
                                        // 清稿/清附件按修订锁（在途新键入 rev 前移→不清新输入）
                                        if (postDraftRev == submittedRev) {
                                            io.github.deserthouse.sdgun.data.repository.DraftStore
                                                .clearPost(mutContext, draftOwner, fid)
                                            uploadTasks.clear()
                                        }
                                        postOpen = false
                                        bvm.refresh(fid)
                                        val newTid = io.github.deserthouse.sdgun.data.repository
                                            .ThreadRepository.parseThreadAddTid(st.payload)
                                        if (newTid != null && newTid > 0) {
                                            navController.navigate("thread/$newTid/$fid")
                                        }
                                    }
                                }
                            }
                            }  // WP4-A/S03 else
                        },
                        onPickImages = {
                            pickImages.launch(
                                androidx.activity.result.PickVisualMediaRequest(
                                    androidx.activity.result.contract
                                        .ActivityResultContracts.PickVisualMedia.ImageOnly))
                        },
                        onRetryUpload = { t -> uploadOwner.retry(t.uuid) },
                        onRemoveUpload = { t -> uploadOwner.remove(t.uuid) },
                        uploadTasks = uploadTasks.toList(),
                        // Q3：分类 chips + circle_id 就绪门 + column-info 失败重试
                        postTypes = (colInfoState as? UiState.Data<*>)?.let {
                            (it.value as? io.github.deserthouse.sdgun.data.repository
                                .ThreadRepository.PostColumnInfo)?.types
                        } ?: emptyList(),
                        circleIdReady = colInfoState is UiState.Data<*> ||
                            (BuildConfig.DEBUG && postFake != "off"),
                        // 假模式下不显真 column-info 错误（全合成语义）
                        columnInfoError = if (BuildConfig.DEBUG && postFake != "off") null
                        else (colInfoState as? UiState.RetryableError)?.error,
                        onRetryColumnInfo = { colInfoRetry++ },
                    )
                }
            }
        }
        // ── 二级：帖子（G2-A05）——主楼+楼层流，返回钮回板块；Q1：可选 pid=楼层定位深链 ──
        composable("thread/{tid}/{fid}?pid={pid}") { entry ->
            val tid = entry.arguments?.getString("tid")?.toLongOrNull() ?: 0L
            val fid = entry.arguments?.getString("fid")?.toLongOrNull() ?: 0L
            val deepPid = entry.arguments?.getString("pid")?.toLongOrNull()?.takeIf { it > 0 }
            var deepPidConsumed by remember(tid) { mutableStateOf(deepPid == null) }
            val scope = rememberCoroutineScope()
            // ── G4 写操作（真实写入验收=用户 MANUAL；AVD 走查用 debug 假服务，零触网）──
            // Q2：网关上提 NavHost 级（mutGateway）——thread/收藏/用户/会话四处共用
            val mutContext = LocalContext.current
            val mutToken by prefs.apiToken.collectAsState()
            var replyOpen by remember(tid) { mutableStateOf(false) }
            var replyState by remember(tid) { mutableStateOf<MutationState>(MutationState.Draft) }
            // A1-5（AF29-03）：回复目标——null=根回复；被引楼层 pid=quote 回复
            // （草稿键 owner+tid+replyPid 已在 DraftStore 支持，此前 UI 只用 root）
            var replyTargetPid by remember(tid) { mutableStateOf<Long?>(null) }
            fun openReply(targetPid: Long?) {
                if (mutToken.isBlank()) { navigateTab(3) } else {
                    replyTargetPid = targetPid
                    // WP4-A/S09：Unknown 事实不降格（关开面板后仍须核对才能重发）；
                    // 终态（Confirmed/Rejected）与新打开仍重置 Draft（旧目标失败态不串）
                    if (replyState !is MutationState.OutcomeUnknown) {
                        replyState = MutationState.Draft
                    }
                    replyOpen = true
                }
            }
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
                        MutationRequest(kind, params, t), mutGateway, cap,
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
                // R0-1（A31-S03）：在途禁重复；Verify 未核对→核对对话框（A24-02 禁直发重试）
                // Q2：决策收敛到 WriteOps.decideApplaud（toggle：已赞再点=取消赞， Done 不再是终态）
                val acked = pid in verifyAcked.value
                when (io.github.deserthouse.sdgun.core.model.WriteOps
                    .decideApplaud(ui?.phase ?: WriteUiPhase.Idle, pid in applaudedIds.value, acked)) {
                    io.github.deserthouse.sdgun.core.model.WriteOps.Op.NONE -> {
                        if (ui?.phase == WriteUiPhase.Verify) verifyDialogFor = pid
                        return
                    }
                    io.github.deserthouse.sdgun.core.model.WriteOps.Op.ADD -> {
                        // 核对后重发=消费该次核对（再 Unknown 须再核对，不得一点即连环重发）
                        verifyAcked.value = verifyAcked.value - pid
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
                    io.github.deserthouse.sdgun.core.model.WriteOps.Op.CANCEL -> {
                        // Q2 取消赞（jadx FroumDataView.addApplaud toggle 后半）：失败/未知
                        // **不回滚归属**——取消失败=仍视为已赞，与 Add 失败的回滚方向相反
                        verifyAcked.value = verifyAcked.value - pid
                        applaudStates[pid] = WriteUi(WriteUiPhase.Pending)
                        submitMutation(MutationKind.APPLAUD_CANCEL, mapOf("content_id" to "$pid")) { st ->
                            when (st) {
                                is MutationState.Confirmed -> {
                                    applaudedIds.value = applaudedIds.value - pid
                                    applaudStates[pid] = WriteUi(WriteUiPhase.Idle)
                                }
                                is MutationState.Rejected ->
                                    applaudStates[pid] = WriteUi(WriteUiPhase.Failed, st.reason)
                                is MutationState.OutcomeUnknown ->
                                    applaudStates[pid] = WriteUi(WriteUiPhase.Verify)
                                else -> {}
                            }
                        }
                    }
                }
            }

            // R0-1（A31-S03）：收藏 Unknown 的显式核对对话框（旧实现点一下=自动当核对完成并重发）
            var collectVerifyDialog by remember(tid) { mutableStateOf(false) }
            fun doCollect() {
                if (mutToken.isBlank()) { navigateTab(3); return }
                // 在途/已确认禁重复 Add（取消收藏属 A3-6，未实现前 Done=终态）
                if (collectUi.phase == WriteUiPhase.Pending || collectUi.phase == WriteUiPhase.Done) return
                if (collectUi.phase == WriteUiPhase.Verify && !collectVerifyAcked) {
                    collectVerifyDialog = true  // 显式核对动作，不自动 ack、不继续提交
                    return
                }
                collectVerifyAcked = false  // 重发消费核对（再 Unknown 须再核对）
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
            // N1/A2-4：帖打开即记录本地阅读历史（标题随 opState Data 后补记/更新）
            LaunchedEffect(opState) {
                val op = (opState as? UiState.Data<*>)?.value as? ThreadRepository.ThreadOP
                if (op != null && op.tid > 0) {
                    io.github.deserthouse.sdgun.data.repository.ThreadHistoryStore.record(
                        mutContext, op.tid, op.title,
                    )
                }
            }

            val nfs2 by prefs.nativeFontScale.collectAsState()
            io.github.deserthouse.sdgun.core.designsystem.ReadingTheme(nfs2) {
            ThreadPage(
                // AF29-04（A1-2）：视频/附件/链接块外开分流（域内→壳内浏览，域外→系统浏览器）
                onOpenUrl = { url -> openLink(url) },
                // A1-3：附件块=系统下载器（带 WebView Cookie）
                onDownloadAttachment = { url, name -> downloadAttachment(url, name) },
                // WP5/S06：尾行状态机（FooterTailRow 唯一渲染）
                footer = tvm.footer,
                // R2/F17：举报（jadx ReportComp 同构：type=14 帖子、type_value=base64(json contentId)）
                onReport = { contentId ->
                    if (contentId.isNotBlank()) {
                        val payload = org.json.JSONObject()
                            .put("contentId", contentId).toString()
                        val b64 = android.util.Base64.encodeToString(
                            payload.toByteArray(), android.util.Base64.NO_WRAP)
                        val url = "https://mag1.sdgun.net/mag/user/v1/User/userReportView" +
                            "?type=14&type_value=" +
                            java.net.URLEncoder.encode(b64, "UTF-8")
                        openLink(url)
                    }
                },
                // A1-9：标准文本分享（系统分享面板）
                onShare = { text ->
                    val intent = Intent(Intent.ACTION_SEND).apply {
                        type = "text/plain"
                        putExtra(Intent.EXTRA_TEXT, text)
                    }
                    runCatching { mutContext.startActivity(Intent.createChooser(intent, "分享")) }
                },
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
                onReply = { openReply(null) },
onReplyToQuote = { pid -> openReply(pid) },
                // Q1：定位/跳楼统一走 VM（引用点击=pid 定位[已加载即时/未加载跨页扫描]；
                // 深链 pid 经 route ?pid= 同通路；楼层号输入=楼层号匹配）
                onLocateQuote = { pid -> tvm.jumpTo(tid, targetPid = pid) },
                jumpState = tvm.jumpState,
                onJumpToFloor = { n -> tvm.jumpTo(tid, targetFloor = n) },
                locateRequest = tvm.pendingLocate,
                onLocateHandled = { tvm.clearLocate() },
            )
            // Q1：深链 pid 首次进入——楼层首页到达后触发一次定位
            LaunchedEffect(opState, floorsState) {
                if (!deepPidConsumed && deepPid != null && floorsState is UiState.Data<*>) {
                    deepPidConsumed = true
                    tvm.jumpTo(tid, targetPid = deepPid)
                }
            }
            if (collectVerifyDialog) {
                androidx.compose.material3.AlertDialog(
                    onDismissRequest = { collectVerifyDialog = false },
                    title = { Text("结果待确认") },
                    text = {
                        Text("上次收藏请求超时，结果未知。请先到收藏列表核对是否已收藏，确认未收藏后才重发。")
                    },
                    confirmButton = {
                        androidx.compose.material3.TextButton(onClick = {
                            collectVerifyDialog = false
                            collectVerifyAcked = true
                            doCollect()
                        }) { Text("已核对，重发") }
                    },
                    dismissButton = {
                        androidx.compose.material3.TextButton(onClick = {
                            collectVerifyDialog = false
                        }) { Text("取消") }
                    },
                )
            }
            if (replyOpen) {
                val target = replyTargetPid
                // A1-5：草稿按实际目标（root vs 被引 pid 各自独立）
                ReplySheet(
                    replyKey = target,
                    initialDraft = DraftStore.load(mutContext, draftOwner, tid, target),
                    targetLabel = if (target != null) "引用楼层 #$target" else null,
                    state = replyState,
                    onDraftChange = { DraftStore.save(mutContext, draftOwner, tid, it, target) },
                    onSend = { text ->
                        // auth=同盾参数（静态编目不可复刻，空值提交；真实验收=用户 MANUAL）
                        submitMutation(
                            MutationKind.REPLY,
                            mapOf(
                                "tid" to "$tid",
                                "reply_id" to (target?.toString() ?: ""),
                                "circle_id" to "",
                                "content" to text,
                                "video" to "",
                                "auth" to "",
                                "users" to "",
                            ),
                        ) { st ->
                            replyState = st
                            if (st is MutationState.Confirmed) {
                                DraftStore.clear(mutContext, draftOwner, tid, target)
                                replyOpen = false
                                tvm.retryFloors(tid)  // 回复成功刷新楼层（当前筛选代际）
                            }
                        }
                    },
                    onDismiss = { replyOpen = false },
                )
            }
            }
        }
        // ── 一级 tab 2：消息（G3）——游客态 tab 内嵌登录面（门禁不跳路由，选中态不漂移）──
        composable("messages") {
            val token by prefs.apiToken.collectAsState()
            TabShell(activeIdx = 2, onTab = { navigateTab(it) }, onSearch = { openSearch() },
                title = "消息") {
                if (token.isBlank()) {
                    LoginScreen(
                        prefs = prefs,
                        onBack = { navController.popBackStack() },
                        onLoggedIn = { /* token 翻转自动切到消息面 */ },
                        onOpenSettings = { navController.navigate("settings") },
                        embedded = true,
                        onOpenUrl = { url -> navController.navigate(
                            "h5/${Uri.encode("账号服务")}?url=${Uri.encode(url)}") },
                    )
                } else {
                    val profileFake by prefs.profileFake.collectAsState()
                    // WP6/S07：完整 UiState（AuthRequired=重登引导；Failed=可重试；不串私有数据）
                    var groupsState by remember(token, profileFake) {
                        mutableStateOf<UiState<List<ProfileRepository.MessageGroup>>>(
                            UiState.InitialLoading)
                    }
                    var groupsRetry by remember(token) { mutableIntStateOf(0) }
                    LaunchedEffect(token, profileFake, groupsRetry) {
                        groupsState = if (profileFake == "data") {
                            UiState.Data(listOf(
                                ProfileRepository.MessageGroup("g1", "（合成）私信：弓友A", 2),
                                ProfileRepository.MessageGroup("g2", "（合成）私信：皮筋交流群", 5),
                            ))
                        } else if (profileFake == "empty") {
                            UiState.Empty("暂无消息")
                        } else if (profileFake == "error") {
                            UiState.RetryableError("（合成）加载失败，可重试")
                        } else if (token.isBlank()) {
                            UiState.AuthRequired
                        } else when (val r = ProfileRepository.fetchMessageGroups(token)) {
                            is ProfileRepository.PrivateResult.Ok -> when {
                                r.value.isEmpty() -> UiState.Empty("暂无消息")
                                else -> UiState.Data(r.value)
                            }
                            ProfileRepository.PrivateResult.AuthExpired -> UiState.AuthRequired
                            ProfileRepository.PrivateResult.Failed ->
                                UiState.RetryableError("消息加载失败，可重试")
                        }
                    }
                    MessagesPage(
                        groups = when (val gs = groupsState) {
                            is UiState.Data<*> ->
                                @Suppress("UNCHECKED_CAST")
                                (gs.value as? List<ProfileRepository.MessageGroup>) ?: emptyList()
                            else -> emptyList()
                        },
                        isLoading = groupsState is UiState.InitialLoading,
                        onOpenGroup = { g ->
                            navController.navigate(
                                "messages/conversation/${g.id}?name=${Uri.encode(g.name)}&uid=${g.uid}")
                        },
                        onOpenNotifications = { navController.navigate("notifications") },
                        // WP6/S07：失败/鉴权态给可执行恢复（重试/重登）
                        onRetry = { groupsRetry++ },
                        onGoLogin = { navController.navigate("login") },
                        errorText = when (val gs = groupsState) {
                            is UiState.RetryableError -> gs.error
                            UiState.AuthRequired -> "登录后可查看消息"
                            else -> null
                        },
                        modifier = Modifier.fillMaxSize(),
                    )
                }
            }
        }
        // ── 一级 tab 3：我的（G3）——游客态内嵌登录面；齿轮=设置（设置属于"我的"）──
        composable("profile") {
            val token by prefs.apiToken.collectAsState()
            val userId by prefs.apiUserId.collectAsState()
            TabShell(activeIdx = 3, onTab = { navigateTab(it) }, onSearch = { openSearch() },
                title = "我的",
                actions = {
                    IconButton(onClick = { navController.navigate("settings") }) {
                        Icon(Icons.Outlined.Settings, contentDescription = "设置")
                    }
                }) {
                if (token.isBlank()) {
                    LoginScreen(
                        prefs = prefs,
                        onBack = { navController.popBackStack() },
                        onLoggedIn = { /* token 翻转自动切到个人面 */ },
                        onOpenSettings = { navController.navigate("settings") },
                        embedded = true,
                        onOpenUrl = { url -> navController.navigate(
                            "h5/${Uri.encode("账号服务")}?url=${Uri.encode(url)}") },
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
                            when (val r = ProfileRepository.fetchMyInfo(token, userId)) {
                                // Q4：token 失效→AuthRequired 引导重登（不冒充可重试）
                                is ProfileRepository.PrivateResult.Ok -> UiState.Data(r.value)
                                ProfileRepository.PrivateResult.AuthExpired -> UiState.AuthRequired
                                ProfileRepository.PrivateResult.Failed ->
                                    UiState.RetryableError("个人资料拉取失败，请退出后重新登录")
                            }
                        }
                    }
                    ProfilePage(
                        state = pState,
                        onBack = {},
                                                onLogout = {
                            io.github.deserthouse.sdgun.core.session.SessionManager.store.onLogout()
                        },
                        onOpenPeople = { kind ->
                            navController.navigate("profile/people/${kind.name}")
                        },
                        onOpenCollect = { navController.navigate("profile/collect") },
                        onOpenServices = { navController.navigate("services") },
                        onOpenLocalHistory = { navController.navigate("profile/local_history") },
                        // A5-2：手动网页会话同步（桥 URL 一次性，WebView 加载即完成 Cookie 写入）
                        webSessionUrl = (pState as? UiState.Data<*>)?.let {
                            (it.value as? ProfileRepository.UserProfile)?.webSessionUrl
                        },
                        onOpenWebSession = { url -> openLink(url) },
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
                        }  // Q4：PrivateResult 直传，peopleLoader 内分型
                    }
                }
                var peopleState by remember(kind, token, profileFake) {
                    mutableStateOf<UiState<io.github.deserthouse.sdgun.feature.profile.PeoplePageData>>(
                        UiState.InitialLoading)
                }
                // A28-01：retry 必须重启加载 effect——只赋 InitialLoading 时
                // LaunchedEffect(key 不变)不会重跑，页面永久转圈（AF29 复审实锤）
                var peopleRetry by remember(kind) { mutableIntStateOf(0) }
                LaunchedEffect(kind, token, profileFake, peopleRetry) {
                    peopleState = UiState.InitialLoading
                    peopleState = loader()
                }
                io.github.deserthouse.sdgun.feature.profile.PeopleScaffold(
                    title = kind.title,
                    state = peopleState,
                    onBack = { navController.popBackStack() },
                    onRetry = { peopleRetry++ },
                    listContent = {
                        io.github.deserthouse.sdgun.feature.profile.PeopleListBody(
                            it,
                            onOpenUser = { uid ->
                                if (uid > 0) navController.navigate("user/$uid")
                            },
                        )
                    },
                )
            }
        }
        composable("profile/collect") {
            val token by prefs.apiToken.collectAsState()
            val profileFake by prefs.profileFake.collectAsState()
            val collectScope = rememberCoroutineScope()
            // R1/A2-1：真链累积分页（rows/page 状态持有；S05 口径 null=失败）
            var collectState by remember(token, profileFake) {
                mutableStateOf<UiState<io.github.deserthouse.sdgun.feature.profile.CollectPageData>>(
                    UiState.InitialLoading)
            }
            var collectPage by remember(token, profileFake) { mutableIntStateOf(1) }
            var collectRows = remember(token, profileFake) {
                mutableStateListOf<io.github.deserthouse.sdgun.feature.profile.CollectItem>()
            }
            var collectHasMore by remember(token, profileFake) { mutableStateOf(true) }
            var collectLoading by remember(token, profileFake) { mutableStateOf(false) }
            var collectRetry by remember { mutableIntStateOf(0) }

            LaunchedEffect(token, profileFake, collectRetry) {
                if (profileFake == "data") {
                    collectState = UiState.Data(io.github.deserthouse.sdgun.feature.profile.CollectPageData(
                        listOf(
                            io.github.deserthouse.sdgun.feature.profile.CollectItem(
                                0, "（合成）原载便携弓制作分享", "收藏于 2026-10-21"),
                            io.github.deserthouse.sdgun.feature.profile.CollectItem(
                                0, "（合成）新手入门：皮筋选购指南", "收藏于 2026-10-22"),
                        )))
                } else if (profileFake == "empty") {
                    collectState = UiState.Empty("暂无收藏")
                } else if (profileFake == "error") {
                    collectState = UiState.RetryableError("（合成）加载失败，可重试")
                } else if (token.isBlank()) {
                    collectState = UiState.AuthRequired
                } else {
                    // 真链（userCollectList，GET p 分页；Q4：鉴权失效→AuthRequired 分型）
                    collectPage = 1; collectRows.clear(); collectHasMore = true
                    collectLoading = true
                    val r = ProfileRepository.fetchCollectList(token, 1)
                    collectLoading = false
                    collectState = when (r) {
                        is ProfileRepository.PrivateResult.Ok -> {
                            val rows = r.value
                            collectHasMore = rows.isNotEmpty()
                            collectRows.addAll(rows.map {
                                io.github.deserthouse.sdgun.feature.profile.CollectItem(
                                    0, it.title, it.timeText, it.link, it.key)
                            })
                            UiState.Data(io.github.deserthouse.sdgun.feature.profile.CollectPageData(
                                collectRows.toList()))
                        }
                        ProfileRepository.PrivateResult.AuthExpired -> UiState.AuthRequired
                        ProfileRepository.PrivateResult.Failed ->
                            UiState.RetryableError("收藏加载失败，可重试")
                    }
                }
            }
            // ── Q2：取消收藏（collectDelete GET id，jadx OperationHelper.collectDelete）──
            // S03 纪律：破坏性动作先确认对话框；Unknown→Verify 核对后重发；Confirmed 才移行
            val collectRemoveUi = remember(token, profileFake) {
                mutableStateMapOf<String, WriteUi>()
            }
            var collectRemoveConfirmFor by remember(token, profileFake) {
                mutableStateOf<io.github.deserthouse.sdgun.feature.profile.CollectItem?>(null)
            }
            var collectRemoveVerifyFor by remember(token, profileFake) { mutableStateOf<String?>(null) }
            fun doCollectRemove(item: io.github.deserthouse.sdgun.feature.profile.CollectItem) {
                val t = token
                if (t.isBlank()) return
                val cap = io.github.deserthouse.sdgun.core.session.SessionManager.store.epoch.value
                collectRemoveUi[item.recordId] = WriteUi(WriteUiPhase.Pending)
                collectScope.launch {
                    val st = MutationRunner.submit(
                        MutationRequest(
                            MutationKind.COLLECT_DELETE, mapOf("id" to item.recordId), t,
                            method = "GET",
                        ),
                        mutGateway, cap,
                        { c -> io.github.deserthouse.sdgun.core.session.SessionManager.store.epoch.isCurrent(c) },
                    )
                    when (st) {
                        is MutationState.Confirmed -> {
                            collectRemoveUi.remove(item.recordId)
                            collectRows.removeAll { it.recordId == item.recordId }
                            collectState = UiState.Data(
                                io.github.deserthouse.sdgun.feature.profile.CollectPageData(
                                    collectRows.toList()))
                        }
                        is MutationState.Rejected ->
                            collectRemoveUi[item.recordId] = WriteUi(WriteUiPhase.Failed, st.reason)
                        is MutationState.OutcomeUnknown -> {
                            collectRemoveUi[item.recordId] = WriteUi(WriteUiPhase.Verify)
                            collectRemoveVerifyFor = item.recordId
                        }
                        else -> {}
                    }
                }
            }
            io.github.deserthouse.sdgun.feature.profile.PeopleScaffold(
                title = "收藏",
                state = collectState,
                onBack = { navController.popBackStack() },
                onRetry = { collectRetry++ },
                listContent = {
                    io.github.deserthouse.sdgun.feature.profile.CollectListBody(
                        it,
                        onOpenThread = { tid -> navController.navigate("thread/$tid/0") },
                        onOpenUrl = { url -> openLink(url) },
                        onRemove = { item -> collectRemoveConfirmFor = item },
                        removingIds = collectRemoveUi.filterValues { v ->
                            v.phase == WriteUiPhase.Pending || v.phase == WriteUiPhase.Verify
                        }.keys,
                    )
                    if (profileFake == "off" && collectHasMore && token.isNotBlank()) {
                        TextButton(
                            onClick = {
                                if (collectLoading || !collectHasMore) return@TextButton
                                collectLoading = true
                                val next = collectPage + 1
                                collectScope.launch {
                                    val r = ProfileRepository.fetchCollectList(token, next)
                                    collectLoading = false
                                    // WP6/S07：一次性归约（AuthRequired 后不再被 Data 覆盖）；
                                    // Failed 保旧行+Stale 重试；Ok 才推进
                                    when (r) {
                                        is ProfileRepository.PrivateResult.Ok -> {
                                            val fetched = r.value
                                            if (fetched.isNotEmpty()) {
                                                collectPage = next
                                                collectRows.addAll(fetched.map {
                                                    io.github.deserthouse.sdgun.feature.profile.CollectItem(
                                                        0, it.title, it.timeText, it.link, it.key)
                                                })
                                                collectHasMore = true
                                                collectState = UiState.Data(
                                                    io.github.deserthouse.sdgun.feature.profile.CollectPageData(
                                                        collectRows.toList()))
                                            } else {
                                                collectHasMore = false
                                                collectState = UiState.Data(
                                                    io.github.deserthouse.sdgun.feature.profile.CollectPageData(
                                                        collectRows.toList()))
                                            }
                                        }
                                        ProfileRepository.PrivateResult.AuthExpired ->
                                            collectState = UiState.AuthRequired
                                        ProfileRepository.PrivateResult.Failed ->
                                            collectState = UiState.Stale(
                                                io.github.deserthouse.sdgun.feature.profile.CollectPageData(
                                                    collectRows.toList()),
                                                "下一页加载失败，可重试",
                                            )
                                    }
                                }
                            },
                            modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                        ) { Text(if (collectLoading) "加载中…" else "加载更多") }
                    }
                },
            )
            // Q2：取消收藏确认（破坏性动作显式核对，不自动执行）
            collectRemoveConfirmFor?.let { item ->
                androidx.compose.material3.AlertDialog(
                    onDismissRequest = { collectRemoveConfirmFor = null },
                    title = { Text("取消收藏") },
                    text = { Text("确定取消收藏「${item.title}」？") },
                    confirmButton = {
                        androidx.compose.material3.TextButton(onClick = {
                            collectRemoveConfirmFor = null
                            doCollectRemove(item)
                        }) { Text("确定") }
                    },
                    dismissButton = {
                        androidx.compose.material3.TextButton(onClick = {
                            collectRemoveConfirmFor = null
                        }) { Text("取消") }
                    },
                )
            }
            // Q2：取消收藏 Unknown 核对对话框（S03：不自动当核对完成）
            if (collectRemoveVerifyFor != null) {
                androidx.compose.material3.AlertDialog(
                    onDismissRequest = { collectRemoveVerifyFor = null },
                    title = { Text("结果待确认") },
                    text = { Text("上次取消收藏请求超时，结果未知。请刷新列表核对后重试。") },
                    confirmButton = {
                        androidx.compose.material3.TextButton(onClick = {
                            val rid = collectRemoveVerifyFor
                            collectRemoveVerifyFor = null
                            val item = collectRows.firstOrNull { it.recordId == rid }
                            if (item != null) doCollectRemove(item)
                        }) { Text("已核对，重发") }
                    },
                    dismissButton = {
                        androidx.compose.material3.TextButton(onClick = {
                            collectRemoveVerifyFor = null
                        }) { Text("取消") }
                    },
                )
            }
        }
        // ── U3-b（F20）：会话详情（合成先行——详情端点参数测绘标注待用）──
        composable("messages/conversation/{id}?name={name}&uid={uid}") { entry ->
            val name = entry.arguments?.getString("name") ?: "会话"
            val peerUid = entry.arguments?.getString("uid")?.toLongOrNull() ?: 0L
            val profileFake by prefs.profileFake.collectAsState()
            val convToken by prefs.apiToken.collectAsState()
            var convState by remember(name, profileFake) {
                mutableStateOf<UiState<io.github.deserthouse.sdgun.feature.messages.ConversationData>>(
                    UiState.InitialLoading)
            }
            var convRetry by remember { mutableIntStateOf(0) }  // A28-01
            LaunchedEffect(name, profileFake, convRetry, peerUid, convToken) {
                convState = if (profileFake == "data") {
                    UiState.Data(io.github.deserthouse.sdgun.feature.messages
                        .syntheticConversation(name))
                } else if (profileFake == "empty") {
                    UiState.Empty("暂无消息")
                } else if (profileFake == "error") {
                    UiState.RetryableError("（合成）加载失败，可重试")
                } else if (peerUid <= 0 || convToken.isBlank()) {
                    // R3：组行无 uid（合成/旧缓存）→诚实未接入；游客走不到（消息面门禁）
                    UiState.RetryableError("会话详情读取待接口接入")
                } else {
                    // R3/A4-2：真链（userMessageList GET uid+p；⚠️ read-may-mutate——
                    // 新口径不安排真探，生产接线保留供登录态用户自然使用）
                    // Q2 修正：else 分支须为表达式（此前块内二次赋值使外层 if 值=Unit/Any，
                    // 编译不过——该结构进入树后从未真正编译过，幻绿窗口实证，见账本 Q2 行）
                    when (val r = ProfileRepository.fetchMessageList(convToken, peerUid, 1)) {
                        // Q4：token 失效→AuthRequired（会话内容不串入）
                        is ProfileRepository.PrivateResult.Ok -> when {
                            r.value.isEmpty() -> UiState.Empty("暂无消息")
                            else -> UiState.Data(
                                io.github.deserthouse.sdgun.feature.messages.ConversationData(
                                    r.value.map { row ->
                                        io.github.deserthouse.sdgun.feature.messages.ChatBubble(
                                            text = row.content,
                                            time = row.timeText ?: "",
                                            me = false,  // 方向判定=uid 对比（待真样本收紧）
                                        )
                                    }
                                ))
                        }
                        ProfileRepository.PrivateResult.AuthExpired -> UiState.AuthRequired
                        ProfileRepository.PrivateResult.Failed ->
                            UiState.RetryableError("会话加载失败，可重试")
                    }
                }
            }
            // A28-01 补充修正：state 整体传入（旧 nullable data/isLoading 消费把
            // Empty/RetryableError 压成空白/无反馈——AF29 复审点名）
            // Q2：私信发送（userMessageSendMsg POST uid+content；Confirmed 后重拉会话=
            // 官方 500ms refresh 同构语义）
            var sendState by remember(name, profileFake) { mutableStateOf(WriteUi()) }
            var sendVerify by remember(name, profileFake) { mutableStateOf(false) }
            var sendVerifyAcked by remember(name, profileFake) { mutableStateOf(false) }
            var sendTick by remember(name, profileFake) { mutableIntStateOf(0) }
            var lastSendText by remember(name, profileFake) { mutableStateOf("") }
            val convScope = rememberCoroutineScope()
            fun doSend(text: String) {
                val t = convToken
                if (t.isBlank() || peerUid <= 0 || text.isBlank()) return
                if (sendState.phase == WriteUiPhase.Pending) return
                if (sendState.phase == WriteUiPhase.Verify && !sendVerifyAcked) {
                    sendVerify = true  // S03：显式核对
                    return
                }
                sendVerifyAcked = false
                lastSendText = text
                val cap = io.github.deserthouse.sdgun.core.session.SessionManager.store.epoch.value
                sendState = WriteUi(WriteUiPhase.Pending)
                convScope.launch {
                    val st = MutationRunner.submit(
                        MutationRequest(
                            MutationKind.MESSAGE_SEND,
                            mapOf("uid" to "$peerUid", "content" to text), t,
                        ),
                        mutGateway, cap,
                        { c -> io.github.deserthouse.sdgun.core.session.SessionManager.store.epoch.isCurrent(c) },
                    )
                    when (st) {
                        is MutationState.Confirmed -> {
                            sendState = WriteUi(WriteUiPhase.Done)
                            sendTick++  // 触发会话重拉（LaunchedEffect key 含 sendTick）
                        }
                        is MutationState.Rejected -> sendState = WriteUi(WriteUiPhase.Failed, st.reason)
                        is MutationState.OutcomeUnknown -> {
                            sendState = WriteUi(WriteUiPhase.Verify)
                            sendVerify = true
                        }
                        else -> {}
                    }
                }
            }
            LaunchedEffect(sendTick) {
                if (sendTick > 0 && peerUid > 0 && convToken.isNotBlank() && profileFake == "off") {
                    // 发送成功重拉（读端点 read-may-mutate 披露在案；仅 Confirmed 后触发）
                    convState = UiState.InitialLoading
                    convState = when (val r = ProfileRepository.fetchMessageList(convToken, peerUid, 1)) {
                        is ProfileRepository.PrivateResult.Ok -> when {
                            r.value.isEmpty() -> UiState.Empty("暂无消息")
                            else -> UiState.Data(
                                io.github.deserthouse.sdgun.feature.messages.ConversationData(
                                    r.value.map { row ->
                                        io.github.deserthouse.sdgun.feature.messages.ChatBubble(
                                            text = row.content, time = row.timeText ?: "", me = false,
                                        )
                                    }
                                ))
                        }
                        ProfileRepository.PrivateResult.AuthExpired -> UiState.AuthRequired
                        ProfileRepository.PrivateResult.Failed ->
                            UiState.RetryableError("会话加载失败，可重试")
                    }
                }
            }
            io.github.deserthouse.sdgun.feature.messages.ConversationDetailPage(
                groupName = name,
                state = convState,
                onBack = { navController.popBackStack() },
                onRetry = { convRetry++ },
                canSend = peerUid > 0 && convToken.isNotBlank() && profileFake == "off",
                sendState = sendState,
                onSend = { text -> doSend(text) },
            )
            if (sendVerify) {
                androidx.compose.material3.AlertDialog(
                    onDismissRequest = { sendVerify = false },
                    title = { Text("结果待确认") },
                    text = { Text("上次发送请求超时，结果未知。请核对会话记录后重试。") },
                    confirmButton = {
                        androidx.compose.material3.TextButton(onClick = {
                            sendVerify = false
                            sendVerifyAcked = true
                            doSend(lastSendText)
                        }) { Text("已核对，重发") }
                    },
                    dismissButton = {
                        androidx.compose.material3.TextButton(onClick = {
                            sendVerify = false
                        }) { Text("取消") }
                    },
                )
            }
        }
        // ── U3-b（F21）：通知中心（合成先行+权限拒绝态）──
        composable("notifications") {
            val token by prefs.apiToken.collectAsState()
            val profileFake by prefs.profileFake.collectAsState()
            var notifyState by remember(token, profileFake) {
                mutableStateOf<UiState<io.github.deserthouse.sdgun.feature.messages.NotifyData>>(
                    UiState.InitialLoading)
            }
            var notifyRetry by remember { mutableIntStateOf(0) }  // A28-01
            val ctx = LocalContext.current
            // R3/A4-4：真链行（NotifyRow→分类行形态复用）；link 消费=行点击外开
            var notifyRows = remember(token, profileFake) {
                mutableStateListOf<ProfileRepository.NotifyRow>()
            }
            LaunchedEffect(token, profileFake, notifyRetry) {
                notifyState = if (profileFake == "data") {
                    UiState.Data(io.github.deserthouse.sdgun.feature.messages.syntheticNotify())
                } else if (profileFake == "empty") {
                    UiState.Empty("暂无通知")
                } else if (profileFake == "error") {
                    UiState.RetryableError("（合成）加载失败，可重试")
                } else if (token.isBlank()) {
                    UiState.AuthRequired
                } else {
                    // WP6/S08：分类 code 契约门控——未知分类码零请求（扩展 soak 实证
                    // 无 code=code0 拒），受限态如实说明；已知分类后按 code 请求
                    val notifyCode: String? = null  // 待官方对照勘定（U-10 复核项）
                    if (notifyCode.isNullOrBlank()) {
                        UiState.Empty("通知历史需要分类入口参数（待勘定）——本地提醒不受影响")
                    } else
                    // 真链（notifyList v3 POST query p+code；⚠️ read-may-mutate 披露——不安排真探）
                    when (val r = ProfileRepository.fetchNotifyList(token, 1, code = notifyCode)) {
                        is ProfileRepository.PrivateResult.Ok -> when {
                            r.value.isEmpty() -> UiState.Empty("暂无通知")
                            else -> {
                                notifyRows.clear(); notifyRows.addAll(r.value)
                                UiState.Data(io.github.deserthouse.sdgun.feature.messages.NotifyData(
                                    r.value.map { row ->
                                        io.github.deserthouse.sdgun.feature.messages.NotifyCategory(
                                            name = row.title,
                                            summary = row.content.ifBlank { row.time ?: "" },
                                            route = row.link ?: "",
                                        )
                                    }
                                ))
                            }
                        }
                        // Q4：token 失效→AuthRequired
                        ProfileRepository.PrivateResult.AuthExpired -> UiState.AuthRequired
                        ProfileRepository.PrivateResult.Failed ->
                            UiState.RetryableError("通知加载失败，可重试")
                    }
                }
            }
            // A4-6：本地投递验证通道（debug 专属——渠道创建/点击深链/去重全链实测；
            // release 只保留渠道注册，投递由真实事件触发[推送服务端=外部依赖不在范围]）
            if (io.github.deserthouse.sdgun.BuildConfig.DEBUG && notifyRows.isNotEmpty()) {
                androidx.compose.runtime.LaunchedEffect(notifyRows.size) {
                    val first = notifyRows.first()
                    io.github.deserthouse.sdgun.feature.notifications.LocalNotifier.post(
                        ctx, 1001,
                        first.title, first.content, first.link,
                    )
                }
            }
            io.github.deserthouse.sdgun.feature.messages.NotificationPage(
                state = notifyState,
                onBack = { navController.popBackStack() },
                onRetry = { notifyRetry++ },
                onOpenLink = { url -> openLink(url) },
            )
        }
        // ── N1/A2-3：他人主页（getUserInfo POST id= 官方契约复用）──
        composable("user/{uid}") { entry ->
            val uid = entry.arguments?.getString("uid")?.toLongOrNull() ?: 0L
            val userToken by prefs.apiToken.collectAsState()
            val userScope = rememberCoroutineScope()
            var userState by remember(uid) {
                mutableStateOf<UiState<ProfileRepository.UserProfile>>(UiState.InitialLoading)
            }
            var userRetry by remember(uid) { mutableIntStateOf(0) }
            // Q2：关注/取消关注（fansAdd GET user_id / removeRelation GET user_id+remove_his，
            // jadx UserHomeFragment.attentionToNet 同构；S03 相位纪律同款）
            var followState by remember(uid) { mutableStateOf(WriteUi()) }
            var followOverride by remember(uid) { mutableStateOf<Boolean?>(null) }
            var followVerify by remember(uid) { mutableStateOf(false) }
            var followVerifyAcked by remember(uid) { mutableStateOf(false) }
            LaunchedEffect(uid, userToken, userRetry) {
                if (uid <= 0) { userState = UiState.RetryableError("无效用户"); return@LaunchedEffect }
                followOverride = null; followState = WriteUi()  // 重取资料=关注态回到响应口径
                userState = UiState.InitialLoading
                // WP3/A1104-S01：结果必须写回 userState（旧 when 丢弃=永久加载）；
                // 映射收敛 ProfileRepository.mapUserProfileResult（JVM 反例在案）
                userState = ProfileRepository.mapUserProfileResult(
                    ProfileRepository.fetchMyInfo(userToken, uid))
            }
            fun doFollowToggle() {
                val t = userToken
                if (t.isBlank()) { navController.navigate("login"); return }
                if (followState.phase == WriteUiPhase.Pending) return
                if (followState.phase == WriteUiPhase.Verify && !followVerifyAcked) {
                    followVerify = true  // S03：显式核对动作，不自动当核对完成
                    return
                }
                followVerifyAcked = false  // 核对消费（再 Unknown 须再核对）
                val parsed = (userState as? UiState.Data<*>)?.value as? ProfileRepository.UserProfile
                val cur = followOverride ?: parsed?.relationStatus?.let { it == 1 } ?: return
                val cap = io.github.deserthouse.sdgun.core.session.SessionManager.store.epoch.value
                followState = WriteUi(WriteUiPhase.Pending)
                userScope.launch {
                    val (kind, params) = if (cur) {
                        MutationKind.FOLLOW_REMOVE to mapOf("user_id" to "$uid", "remove_his" to "0")
                    } else {
                        MutationKind.FOLLOW_ADD to mapOf("user_id" to "$uid")
                    }
                    val st = MutationRunner.submit(
                        MutationRequest(kind, params, t), mutGateway, cap,
                        { c -> io.github.deserthouse.sdgun.core.session.SessionManager.store.epoch.isCurrent(c) },
                    )
                    when (st) {
                        is MutationState.Confirmed -> {
                            followOverride = !cur
                            followState = WriteUi(WriteUiPhase.Done)
                        }
                        is MutationState.Rejected ->
                            followState = WriteUi(WriteUiPhase.Failed, st.reason)
                        is MutationState.OutcomeUnknown -> {
                            // 不翻转本地态：现状未知保持显示，核对后按当前归属重发
                            followState = WriteUi(WriteUiPhase.Verify)
                            followVerify = true
                        }
                        else -> {}
                    }
                }
            }
            val followDisplay: Boolean? = followOverride ?: (userState as? UiState.Data<*>)?.let {
                (it.value as? ProfileRepository.UserProfile)?.relationStatus?.let { rs -> rs == 1 }
            }
            io.github.deserthouse.sdgun.feature.profile.UserProfilePage(
                uid = uid,
                state = userState,
                onBack = { navController.popBackStack() },
                onRetry = { userRetry++ },
                // WP3/A1104-S01：AuthRequired 相位的去登录动作
                onLogin = { navController.navigate("login") },
                following = followDisplay,
                followState = followState,
                onFollowToggle = { doFollowToggle() },
            )
            if (followVerify) {
                androidx.compose.material3.AlertDialog(
                    onDismissRequest = { followVerify = false },
                    title = { Text("结果待确认") },
                    text = { Text("上次关注请求超时，结果未知。请核对当前关注状态后重试。") },
                    confirmButton = {
                        androidx.compose.material3.TextButton(onClick = {
                            followVerify = false
                            followVerifyAcked = true
                            doFollowToggle()
                        }) { Text("已核对，重发") }
                    },
                    dismissButton = {
                        androidx.compose.material3.TextButton(onClick = {
                            followVerify = false
                        }) { Text("取消") }
                    },
                )
            }
        }
        // ── N1/A2-4：本地帖阅读历史（纯本机 SharedPreferences）──
        composable("profile/local_history") {
            val ctx = LocalContext.current
            var entries by remember {
                mutableStateOf(io.github.deserthouse.sdgun.data.repository.ThreadHistoryStore.load(ctx))
            }
            io.github.deserthouse.sdgun.feature.profile.LocalHistoryPage(
                entries = entries,
                onBack = { navController.popBackStack() },
                onOpenThread = { tid -> navController.navigate("thread/$tid/0") },
                onRemove = { tid ->
                    io.github.deserthouse.sdgun.data.repository.ThreadHistoryStore.remove(ctx, tid)
                    entries = io.github.deserthouse.sdgun.data.repository.ThreadHistoryStore.load(ctx)
                },
            )
        }
        // ── Q5（A1-10）：搜索原生页（圈子 allTopicSearch + 用户 userSearch；帖子=H5 桥接）──
        composable("search") {
            val searchToken by prefs.apiToken.collectAsState()
            io.github.deserthouse.sdgun.feature.community.SearchPage(
                onBack = { navController.popBackStack() },
                onOpenUrl = { url -> openLink(url) },
                onOpenUser = { uid -> if (uid > 0) navController.navigate("user/$uid") },
                searchTopics = { kw ->
                    io.github.deserthouse.sdgun.data.repository.BoardListRepository
                        .searchTopics(searchToken, kw)
                },
                searchUsers = { kw ->
                    io.github.deserthouse.sdgun.data.repository.ProfileRepository
                        .searchUsers(searchToken, kw)
                },
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
                onOpenThread = { tid, pid ->
                    navController.navigate(
                        "thread/$tid/0" + (pid?.let { "?pid=$it" } ?: "")) {
                        launchSingleTop = true
                    }
                },
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
                onOpenUrl = { url -> navController.navigate(
                    "h5/${Uri.encode("账号服务")}?url=${Uri.encode(url)}") },
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
    // A28-03 单一壳契约：顶栏只此一层——页级标题/动作经此槽传入，
    // 页内 Scaffold 一律不再持有 TopAppBar（双顶栏曾在此叠出）
    title: String = "SDGun",
    actions: @Composable androidx.compose.foundation.layout.RowScope.() -> Unit = {},
    content: @Composable () -> Unit,
) {
    // U4 补真实现（A-R3）：宽屏三档——≥600dp 底栏换 NavigationRail（U2 声明兑现）
    val tier = io.github.deserthouse.sdgun.core.designsystem.widthTier(
        androidx.compose.ui.platform.LocalConfiguration.current.screenWidthDp)
    if (tier == io.github.deserthouse.sdgun.core.designsystem.NavTier.COMPACT) {
        Scaffold(
            containerColor = MaterialTheme.colorScheme.surface,
            topBar = {
                // R4：原生导航搜索入口——落兼容壳搜索页（mag1 无帖子搜索 API，搜索永久岛）
                TopAppBar(
                    title = { Text(title) },
                    actions = {
                        actions()
                        IconButton(onClick = onSearch) {
                            Icon(Icons.Filled.Search, contentDescription = "搜索")
                        }
                    },
                )
            },
            bottomBar = { AppBottomBar(activeIdx = activeIdx, onTab = onTab) },
        ) { padding ->
            // R0-4（A31-S08）单一 inset 所有者：TabShell 顶栏是根页唯一 topBar，
            // content 消费完整 padding（旧只消费 bottom=根页首行被顶栏盖，审查实锤）。
            // 详情页不经 TabShell，自带完整壳不受影响。
            Box(Modifier.fillMaxSize().padding(padding)) { content() }
        }
    } else {
        Row(Modifier.fillMaxSize()) {
            io.github.deserthouse.sdgun.core.designsystem.AppSideRail(
                activeIdx = activeIdx, onTab = onTab)
            Scaffold(
                modifier = Modifier.weight(1f),
                containerColor = MaterialTheme.colorScheme.surface,
                topBar = {
                    TopAppBar(
                        title = { Text(title) },
                        actions = {
                            actions()
                            IconButton(onClick = onSearch) {
                                Icon(Icons.Filled.Search, contentDescription = "搜索")
                            }
                        },
                    )
                },
            ) { padding ->
                Box(Modifier.fillMaxSize().padding(padding)) { content() }  // R0-4 同 compact
            }
        }
    }
}