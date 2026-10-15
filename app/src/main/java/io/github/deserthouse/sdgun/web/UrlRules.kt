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

    /** 帖子 tid 提取（AF-07）：mod=viewthread 页的 tid 参数；非帖子页返回 null */
    fun threadTid(url: String): String? {
        if (!url.contains("mod=viewthread")) return null
        return Regex("[?&]tid=(\\d+)").find(url)?.groupValues?.get(1)
    }

    /**
     * H5 分享链接（AF-07）：官方 threadWapPage 单参形态（tid 即可，服务端自解析板块，
     * 实测无需 circle_id——2026-10-15 探针 HTTP 200 实渲染 + 10-09 记录一致）。
     * 已知限制：app 域 WAF 拦 PC UA（RemoteDisconnected），该链接面向手机端打开，
     * UI 文案须如实标注。非帖子页返回 null。
     */
    const val H5_WAP_PAGE = "https://app.sdgun.com.cn/mag/circle/v1/forum/threadWapPage"

    fun threadH5Url(url: String): String? =
        threadTid(url)?.let { "$H5_WAP_PAGE?tid=$it" }

    /**
     * 门户死端判定（对齐扩展 navguard）：移动版 search.php 等路径会被站方跳到
     * 门户页（portal.php 或无参 forum.php），该页对壳内导航是死端——检测到即回 HOME。
     * HOME 自身带 forumlist=1 参数，不会命中。
     */
    fun isPortalDeadEnd(url: String): Boolean {
        if (!url.contains("bbs.sdgun.com.cn")) return false
        if (url.contains("portal.php")) return true
        if (!url.contains("forum.php")) return false
        val query = url.substringAfter("forum.php", "").removePrefix("?")
        val meaningful = query.split('&').filter {
            it.isNotBlank() && !it.startsWith("mobile=")
        }
        return meaningful.isEmpty()
    }
}
