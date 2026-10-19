package io.github.deserthouse.sdgun.web

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import android.webkit.CookieManager
import android.webkit.ValueCallback
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

/**
 * WebView 状态桥：组合层持有、WebView 回调读写。
 * settings 快照（webThemeEnabled/themeCss）由组合层同步——WebViewClient 的闭包
 * 持有的是本对象（稳定引用），回调里读到的永远是最新值。
 */
class WebState {
    var progress by mutableFloatStateOf(1f)
        internal set

    var title by mutableStateOf("")
        internal set

    /** 当前主文档 URL（底栏选中态反推用） */
    var currentUrl by mutableStateOf(UrlRules.HOME)
        internal set

    /** 用户最近一次点击的底栏 tab（0=首页/1=消息/2=我的）。
     *  未登录进消息/我的会被站方 302 到 member.php 登录页，URL 特征全失——
     *  高亮回退依据这个意图值（F6），点 tab 时更新，不随导航失效。 */
    var intendedTab by mutableStateOf(0)

    /** 待处理外链（UI 层弹对话框），null=无 */
    var externalLink by mutableStateOf<String?>(null)

    /** 主文档加载失败的人话描述（UI 层错误覆盖层），null=正常 */
    var loadError by mutableStateOf<String?>(null)

    /** 原始错误码（错误页小字诊断用） */
    var loadErrorRaw by mutableStateOf<String?>(null)

    /** 导航代次（A-02）：onPageStarted / doUpdateVisitedHistory 递增。
     *  延时任务（标题回读/空壳探测）捕获代次，执行时不一致=旧页面任务，作废不写状态。 */
    var navGeneration: Int = 0
        internal set

    /** WebView 已销毁（A-02 残余）：onDestroy 置位，一切异步回调此后丢弃 */
    var destroyed: Boolean = false
        internal set

    /** 组合层注册的清理钩子（SV-2：释放 enrich 线程池等），onDestroy 触发 */
    var onCleared: (() -> Unit)? = null

    /** 深链计数（A-08）：handleViewIntent 消费论坛深链后递增，
     *  UI 层据此把路由切回 home（否则内容在 WebView 里加载而路由停在设置页）。 */
    var deepLinkTick by mutableIntStateOf(0)
        internal set

    /** 图片查看器（AF-03）：null=关闭；非 null=全屏查看器展示该图组 */
    var gallery by mutableStateOf<GalleryData?>(null)

    fun closeGallery() {
        gallery = null
    }

    internal var webThemeEnabled: Boolean = true
    /** 页面加载完成时注入的完整主题脚本（applyScript+applyCall 组合，组合层生成） */
    internal var pageThemeJs: String = ""
    /** onPageStarted 阶段的提前压暗脚本（组合层按当前主题色生成） */
    internal var earlyBgJs: String = ""

    internal var pendingFileChooser: ValueCallback<Array<Uri>>? = null

    /** 文件选择请求：从 FileChooserParams 提取的结构化描述（A-06） */
    data class FilePickRequest(val multiple: Boolean, val mimeType: String)

    /** 由组合层注入：启动系统文件/图片选择器 */
    var requestImagePick: ((FilePickRequest) -> Unit)? = null

    fun consumeFileChooserResult(uris: List<Uri>?) {
        pendingFileChooser?.onReceiveValue(uris?.toTypedArray())
        pendingFileChooser = null
    }
}

/** 从 FileChooserParams.acceptTypes 取主类型：首个非空条目；全空=任意文件。
 *  通配（image 加 / 加星）原样透传给系统选择器。 */
internal fun primaryAcceptType(acceptTypes: Array<out String?>?): String {
    val first = acceptTypes?.firstOrNull { !it.isNullOrBlank() }?.trim()
    return if (first.isNullOrBlank() || first == "*/*") "*/*" else first
}

/** 网络层/HTTP 层错误码 → 用户可读文案 */
internal fun humanizeError(raw: String, httpStatus: Int? = null): String {
    if (httpStatus != null) {
        return when {
            httpStatus == 404 -> "页面不存在或已被删除"
            httpStatus in 500..599 -> "服务器暂时不可用"
            httpStatus == 403 -> "没有访问权限"
            else -> "服务器返回错误（$httpStatus）"
        }
    }
    return when {
        "ERR_CONNECTION_REFUSED" in raw || "ERR_CONNECTION_RESET" in raw ||
            "ERR_CONNECTION_TIMED_OUT" in raw || "ERR_TIMED_OUT" in raw -> "无法连接到论坛服务器"
        "ERR_INTERNET_DISCONNECTED" in raw || "ERR_ADDRESS_UNREACHABLE" in raw -> "当前无网络连接"
        "ERR_NAME_NOT_RESOLVED" in raw -> "域名解析失败"
        else -> "页面加载失败"
    }
}

/** 空壳探测（批次 A，对齐扩展 popup 口径）：论坛首页 .sub_forum 骨架存在但一条板块链接都没有
 *  = 服务器 200 却只回了骨架。收窄到 forumlist 页判定，板块/帖子页不做（防子版块区误伤）。 */
/** 论坛首页判定（A-07）：forum.php 且无 mod 参数（forum.php?forumlist=1）、
 *  或 mod=forumlist。板块/帖子/登录页一律 false——板块页的空子版块区
 *  （.sub_forum 无链接）是正常形态，不参与空壳判定。 */
internal fun isForumlistUrl(url: String): Boolean {
    // 纯字符串解析（JVM 单测可用，不依赖 android.net.Uri stub）
    if (!url.contains("forum.php")) return false
    val query = url.substringAfter('?', "")
    var mod: String? = null
    query.split('&').forEach { part ->
        val kv = part.split('=', limit = 2)
        if (kv.size == 2 && kv[0] == "mod") mod = kv[1]
    }
    return mod == null || mod == "forumlist"
}

/** 空壳探测表达式：调用方已用 isForumlistUrl 把门，这里只判容器与链接。 */
internal const val SHELL_PROBE_JS =
    "(function(){var sf=document.querySelector('.sub_forum');" +
        "if(!sf)return 'n/a';" +
        "return sf.querySelector('a')?'ok':'empty'})()"

/** 解析探测回值 → 是否空壳。evaluateJavascript 回 JSON 字符串（带引号）或 "null"。
 *  只有明确的 "empty" 判空壳；n/a / ok / 解析失败一律不算（漏报好过误报）。 */
internal fun shellVerdict(jsonResult: String?): Boolean = when (jsonResult?.trim()) {
    "\"empty\"" -> true
    else -> false
}

/**
 * 论坛 WebView 工厂（Activity 级单例，随 AppRoot 的 remember 创建/销毁）：
 * - 干净的 Chrome Mobile UA（不带 wv 后缀；closedonpc 只拦 PC UA，移动 UA 实测放行）
 * - MixedContent 放行：图床 picapp.sdgun.net 为 http，https 页面内混载必须允许
 * - Cookie 持久化（登录态）；站内导航强制 mobile=2；onPageFinished 注入 M3E 主题
 */
fun createForumWebView(
    context: Context,
    state: WebState,
    startUrl: String? = UrlRules.HOME,
): WebView {
    val wv = WebView(context)
    // AF-03/AF-02：桥按导航域装卸（AR-01）——仅在 bbs 家族页面挂载；
    // mag1 等域外页面导航开始即摘除，页面加载时不再注入任何桥对象
    val galleryBridge = GalleryBridge(state)
    val enrichBridge = EnrichBridge(state) { js -> wv.post { wv.evaluateJavascript(js, null) } }
    state.onCleared = { enrichBridge.dispose() }
    var bridgesAttached = true
    wv.addJavascriptInterface(galleryBridge, "sdgGallery")
    wv.addJavascriptInterface(enrichBridge, "sdgEnrich")
    return wv.apply {
        @Suppress("SetJavaScriptEnabled")
        settings.apply {
            javaScriptEnabled = true
            domStorageEnabled = true
            userAgentString = ("Mozilla/5.0 (Linux; Android 13; Pixel 7) "
                    + "AppleWebKit/537.36 (KHTML, like Gecko) "
                    + "Chrome/129.0.0.0 Mobile Safari/537.36")
            mixedContentMode = WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
            setSupportMultipleWindows(false)
            allowFileAccess = false
            allowContentAccess = false
            mediaPlaybackRequiresUserGesture = true
        }
        CookieManager.getInstance().apply {
            setAcceptCookie(true)
            setAcceptThirdPartyCookies(wv, true)
        }
        webViewClient = object : WebViewClient() {
            override fun shouldOverrideUrlLoading(
                view: WebView, request: WebResourceRequest,
            ): Boolean {
                val url = request.url.toString()
                return when (val v = UrlRules.classify(url)) {
                    is UrlRules.Verdict.Internal -> {
                        val normalized = UrlRules.normalizeInternal(url)
                        if (normalized != url) {
                            view.loadUrl(normalized)
                            true
                        } else {
                            false
                        }
                    }
                    is UrlRules.Verdict.External -> {
                        state.externalLink = v.url
                        true
                    }
                    is UrlRules.Verdict.AppScheme -> {
                        // A-03：Application Context 发起必须有 NEW_TASK；失败给用户可见反馈而非静默
                        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(v.url)).apply {
                            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        }
                        runCatching { context.startActivity(intent) }.onFailure {
                            android.widget.Toast.makeText(
                                context, "无法在手机上打开此链接", android.widget.Toast.LENGTH_SHORT,
                            ).show()
                        }
                        true
                    }
                }
            }

            override fun onPageStarted(view: WebView, url: String, favicon: Bitmap?) {
                // 门户死端守卫（对齐扩展 navguard）：search.php 等路径被站方跳门户，
                // 检测到即拉回论坛首页（HOME 带 forumlist 参数，不会循环）
                if (UrlRules.isPortalDeadEnd(url)) {
                    view.loadUrl(UrlRules.HOME)
                    return
                }
                // AR-01/SV-4：桥按域装卸——仅 bbs.sdgun.com.cn 精确匹配挂载
                //（mag1/app 等其他域的页面加载后 window 上不存在任何桥对象）
                val onBbs = Uri.parse(url).host == "bbs.sdgun.com.cn"
                if (!onBbs && bridgesAttached) {
                    view.removeJavascriptInterface("sdgGallery")
                    view.removeJavascriptInterface("sdgEnrich")
                    bridgesAttached = false
                } else if (onBbs && !bridgesAttached) {
                    view.addJavascriptInterface(galleryBridge, "sdgGallery")
                    view.addJavascriptInterface(enrichBridge, "sdgEnrich")
                    bridgesAttached = true
                }
                // S-3：保留旧标题直到 onPageFinished 覆写，消除导航间标题闪烁
                syncNavigationState(view, url)
            }

            /** 导航状态收口（A-02）：URL/错误态/代次统一在这里更新，
             *  完整加载与同文档导航（doUpdateVisitedHistory）都走这条路径。 */
            private fun syncNavigationState(view: WebView, url: String) {
                state.navGeneration++
                state.currentUrl = url
                state.loadError = null
                state.loadErrorRaw = null
                // 白→黑闪变修复：HTML 流式阶段先把根元素底色压暗（正式注入在 onPageFinished）
                if (state.earlyBgJs.isNotEmpty()) {
                    view.evaluateJavascript(state.earlyBgJs, null)
                }
            }

            /** 同文档导航（pushState/replaceState/历史跳转）：帖子页无限翻页走这里，
             *  只更新 URL 与代次——AJAX 导航不重置错误态（页面没重载）。 */
            override fun doUpdateVisitedHistory(view: WebView, url: String?, isReload: Boolean) {
                if (url != null) {
                    state.navGeneration++
                    state.currentUrl = url
                }
            }

            override fun onPageFinished(view: WebView, url: String?) {
                state.title = view.title.orEmpty()
                if (state.webThemeEnabled && state.pageThemeJs.isNotEmpty()) {
                    view.evaluateJavascript(state.pageThemeJs, null)
                    // 注入脚本会把 <title> 换成页面自身的干净名称（站方标题是"品牌+板块+标题+SEO串"的拼接体）。
                    // 观察器的回写不触发 onReceivedTitle，故此处主动回读一次拿最终值。
                    val genTitle = state.navGeneration
                    scheduleDelayed(view, 1200) {
                        view.evaluateJavascript("document.title") { result ->
                            // A-02 残余：evaluateJavascript 的结果回调同样晚于导航可达，需再校验
                            if (state.destroyed || state.navGeneration != genTitle) return@evaluateJavascript
                            val t = runCatching {
                                org.json.JSONTokener(result).nextValue() as? String
                            }.getOrNull()
                            if (!t.isNullOrBlank()) state.title = t
                        }
                    }
                }
                // 空壳探测（批次 A / A-07 收紧）：仅论坛首页判定（isForumlistUrl 把门，
                // 板块页的空子版块区不参与）；服务器 200 但只回骨架时网络层无错误、覆盖层不触发。
                if (url != null && state.loadError == null && isForumlistUrl(url)) {
                    val genProbe = state.navGeneration
                    scheduleDelayed(view, 2500) {
                        view.evaluateJavascript(SHELL_PROBE_JS) { r ->
                            if (state.destroyed || state.navGeneration != genProbe) return@evaluateJavascript
                            if (shellVerdict(r) && state.loadError == null) {
                                state.loadError = "服务器只回了个空壳，内容没跟上"
                                state.loadErrorRaw = "HTTP 200 · empty shell (.sub_forum without links)"
                            }
                        }
                    }
                }
                (view.parent as? androidx.swiperefreshlayout.widget.SwipeRefreshLayout)?.isRefreshing = false
            }

            /** 带导航代次校验的延时任务（A-02）：页面切换/同文档跳转后旧任务作废，
             *  不得把旧页面的探测/标题写进新页面状态。 */
            private fun scheduleDelayed(view: WebView, delayMs: Long, task: () -> Unit) {
                val gen = state.navGeneration
                view.postDelayed({
                    if (state.destroyed || state.navGeneration != gen) return@postDelayed
                    runCatching { task() }
                }, delayMs)
            }

            override fun onReceivedError(
                view: WebView, request: android.webkit.WebResourceRequest?, error: android.webkit.WebResourceError?,
            ) {
                // 只处理主文档失败（子资源裂图交给网页自身）；服务器不稳定是常态，给重试入口
                if (request?.isForMainFrame == true) {
                    val raw = error?.description?.toString() ?: "网络错误"
                    state.loadError = humanizeError(raw)
                    state.loadErrorRaw = raw
                }
            }

            override fun onReceivedHttpError(
                view: WebView,
                request: android.webkit.WebResourceRequest,
                errorResponse: android.webkit.WebResourceResponse,
            ) {
                // HTTP 层错误（404/5xx）：服务器有响应但页面不可用，同样给重试入口
                if (request.isForMainFrame && errorResponse.statusCode >= 400) {
                    state.loadError = humanizeError("", errorResponse.statusCode)
                    state.loadErrorRaw = "HTTP ${errorResponse.statusCode}"
                }
            }
        }
        webChromeClient = object : WebChromeClient() {
            override fun onProgressChanged(view: WebView?, newProgress: Int) {
                if (newProgress >= 0) state.progress = newProgress / 100f
            }

            override fun onShowFileChooser(
                view: WebView?,
                callback: ValueCallback<Array<Uri>>,
                params: FileChooserParams?,
            ): Boolean {
                state.pendingFileChooser?.onReceiveValue(null)
                state.pendingFileChooser = callback
                // A-06：按网页声明的 mode/acceptTypes 结构化请求，不把所有 input 固定成图片单选
                val request = WebState.FilePickRequest(
                    multiple = params?.mode == FileChooserParams.MODE_OPEN_MULTIPLE,
                    mimeType = primaryAcceptType(params?.acceptTypes),
                )
                state.requestImagePick?.invoke(request) ?: run {
                    callback.onReceiveValue(null)
                    state.pendingFileChooser = null
                }
                return true
            }
        }
        startUrl?.let { loadUrl(it) }
    }
}
