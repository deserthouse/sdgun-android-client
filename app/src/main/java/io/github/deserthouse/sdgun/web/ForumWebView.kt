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

    /** 待处理外链（UI 层弹对话框），null=无 */
    var externalLink by mutableStateOf<String?>(null)

    /** 主文档加载失败的人话描述（UI 层错误覆盖层），null=正常 */
    var loadError by mutableStateOf<String?>(null)

    /** 原始错误码（错误页小字诊断用） */
    var loadErrorRaw by mutableStateOf<String?>(null)

    internal var webThemeEnabled: Boolean = true
    /** 页面加载完成时注入的完整主题脚本（applyScript+applyCall 组合，组合层生成） */
    internal var pageThemeJs: String = ""

    internal var pendingFileChooser: ValueCallback<Array<Uri>>? = null

    /** 由组合层注入：启动系统图片选择器 */
    var requestImagePick: (() -> Unit)? = null

    fun consumeFileChooserResult(uri: Uri?) {
        pendingFileChooser?.onReceiveValue(uri?.let { arrayOf(it) })
        pendingFileChooser = null
    }
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
                        try {
                            context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(v.url)))
                        } catch (_: Exception) {
                            // 无处理器的 scheme 静默忽略
                        }
                        true
                    }
                }
            }

            override fun onPageStarted(view: WebView, url: String, favicon: Bitmap?) {
                // S-3：保留旧标题直到 onPageFinished 覆写，消除导航间标题闪烁
                state.currentUrl = url
                state.loadError = null
                state.loadErrorRaw = null
            }

            override fun onPageFinished(view: WebView, url: String?) {
                state.title = view.title.orEmpty()
                if (state.webThemeEnabled && state.pageThemeJs.isNotEmpty()) {
                    view.evaluateJavascript(state.pageThemeJs, null)
                }
                (view.parent as? androidx.swiperefreshlayout.widget.SwipeRefreshLayout)?.isRefreshing = false
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
                state.requestImagePick?.invoke() ?: run {
                    callback.onReceiveValue(null)
                    state.pendingFileChooser = null
                }
                return true
            }
        }
        startUrl?.let { loadUrl(it) }
    }
}
