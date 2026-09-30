package io.github.deserthouse.sdgun.web

import android.net.Uri

/**
 * URL 分流规则：站内导航 vs 外链 vs 非 Web scheme。
 */
object UrlRules {

    const val HOME = "https://bbs.sdgun.com.cn/forum.php?forumlist=1&mobile=2"
    const val SEARCH = "https://bbs.sdgun.com.cn/search.php?mod=forum&mobile=2"
    const val MESSAGES = "https://bbs.sdgun.com.cn/home.php?mod=space&do=pm&mobile=2"
    const val PROFILE = "https://bbs.sdgun.com.cn/home.php?mod=space&do=profile&mycenter=1&mobile=2"

    private val INTERNAL_HOSTS = setOf(
        "bbs.sdgun.com.cn",
        "www.sdgun.com.cn",
        "sdgun.com.cn",
    )

    /** 图片/静态资源域（WebView 自动加载，不经过导航拦截） */
    val ASSET_HOSTS = setOf("picapp.sdgun.net", "pic.sdgun.net")

    sealed interface Verdict {
        /** 论坛内部页面：直接在 WebView 里加载 */
        data object Internal : Verdict

        /** 外部 http(s) 链接：交给用户选择 */
        data class External(val url: String) : Verdict

        /** 非 Web scheme（如 bzsh://、mailto:）：交给系统，失败静默 */
        data class AppScheme(val url: String) : Verdict
    }

    fun classify(url: String): Verdict = when {
        url.isBlank() -> Verdict.Internal
        url.startsWith("http://") || url.startsWith("https://") -> {
            val host = Uri.parse(url).host?.lowercase() ?: ""
            if (host in INTERNAL_HOSTS) Verdict.Internal else Verdict.External(url)
        }
        url.startsWith("about:") || url.startsWith("data:") || url.startsWith("blob:") ->
            Verdict.Internal
        url.startsWith("javascript:") || url.startsWith("file:") -> Verdict.Internal
        else -> Verdict.AppScheme(url)
    }

    /** Discuz 站内地址统一强制触屏版参数（防“电脑版”入口逃逸后被 closedonpc 拦截） */
    fun normalizeInternal(url: String): String {
        val uri = Uri.parse(url)
        val host = uri.host?.lowercase() ?: return url
        if (host !in INTERNAL_HOSTS) return url
        // mobile=no 是“电脑版”链接：改回 mobile=2
        if (uri.getQueryParameter("mobile") == "no") {
            val fixed = url.replace("mobile=no", "mobile=2")
            return fixed
        }
        return url
    }
}
