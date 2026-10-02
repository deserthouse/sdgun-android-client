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

    sealed interface Verdict {
        /** 论坛内部页面：直接在 WebView 里加载 */
        data object Internal : Verdict

        /** 外部 http(s) 链接：交给用户选择 */
        data class External(val url: String) : Verdict

        /** 非 Web scheme（如 bzsh://、mailto:）：交给系统，失败静默 */
        data class AppScheme(val url: String) : Verdict
    }

    fun classify(url: String): Verdict =
        classifyPure(url, Uri.parse(url).host?.lowercase())

    /** 纯函数核心（host 由调用方从 url 解出；独立出来供 JVM 单测） */
    internal fun classifyPure(url: String, host: String?): Verdict = when {
        url.isBlank() -> Verdict.Internal
        url.startsWith("http://") || url.startsWith("https://") ->
            if ((host ?: "").lowercase() in INTERNAL_HOSTS) Verdict.Internal else Verdict.External(url)
        url.startsWith("about:") || url.startsWith("data:") || url.startsWith("blob:") ->
            Verdict.Internal
        url.startsWith("javascript:") -> Verdict.Internal
        // file: 拒绝在 WebView 内加载（allowFileAccess=false 的语义补全），交系统处理则静默失败
        else -> Verdict.AppScheme(url)
    }

    /** Discuz 站内地址统一强制触屏版参数（防“电脑版”入口逃逸后被 closedonpc 拦截） */
    fun normalizeInternal(url: String): String =
        normalizeInternalPure(url, Uri.parse(url).host?.lowercase())

    /** 纯函数核心：host/query 均为纯字符串，JVM 单测直测 */
    internal fun normalizeInternalPure(url: String, host: String?): String {
        if ((host ?: "").lowercase() !in INTERNAL_HOSTS) return url
        val qStart = url.indexOf('?')
        if (qStart < 0) return url
        val query = url.substring(qStart + 1)
        val rewritten = forceTouchVersionQuery(query) ?: return url
        if (rewritten == query) return url
        return url.substring(0, qStart + 1) + rewritten
    }

    /** 参数级重写：仅精确等于 mobile=no 的参数改 mobile=2，其余（含值含子串的）一律不动 */
    internal fun forceTouchVersionQuery(query: String?): String? {
        if (query.isNullOrEmpty()) return query
        return query.split('&').joinToString("&") { part ->
            if (part == "mobile=no") "mobile=2" else part
        }
    }
}
