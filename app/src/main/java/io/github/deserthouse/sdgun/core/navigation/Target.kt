package io.github.deserthouse.sdgun.core.navigation

/**
 * Q1 深链 typed Target（A1-8/A4-5）：shuidan:// scheme 族与 bbs web URL 的统一解析。
 * 纯字符串解析（java.net.URI 对 shuidan://xxx? query 的 host 语义不可靠），JVM 可测。
 *
 * 契约：
 * - shuidan://forumThreadList?fid=N（官方 circleTree link 形态，A29 实采）→ Board
 * - shuidan://forumThread?tid=N[&pid=P] → Thread（pid 可选=楼层定位）
 * - shuidan://user?uid=N / userHome?uid=N → User
 * - https bbs：forum.php?mod=viewthread&tid=N[&pid=/#pid] → Thread；
 *   space-uid-N.html → User；forum.php?mod=forumdisplay&fid=N → Board
 * - 其余 shuidan host → null（调用方=可恢复回退，不吞不外漏浏览器）；
 *   其余 http(s) → Web（走既有 WebView 兼容壳/外开分流）
 */
sealed class Target {
    data class Board(val fid: Long) : Target()
    data class Thread(val tid: Long, val pid: Long? = null) : Target()
    data class User(val uid: Long) : Target()

    /** 可识别为站内但无原生路由——交给 WebView 兼容壳 */
    data class Web(val url: String) : Target()
}

object TargetParser {

    /** 解析原始 URL 字符串；非 http(s)/shuidan 或无法识别 → null */
    fun parse(raw: String?): Target? {
        if (raw.isNullOrBlank()) return null
        val trimmed = raw.trim()
        return when {
            trimmed.startsWith("shuidan://") -> parseShuidan(trimmed.removePrefix("shuidan://"))
            trimmed.startsWith("https://") || trimmed.startsWith("http://") -> parseWeb(trimmed)
            else -> null
        }
    }

    /** shuidan:// 后体：host?query（host 到 ? 或行尾；query 为 k=v& 串） */
    private fun parseShuidan(body: String): Target? {
        val q = body.indexOf('?')
        val host = (if (q >= 0) body.substring(0, q) else body).trimEnd('/').lowercase()
        val query = if (q >= 0) body.substring(q + 1) else ""
        val params = parseQuery(query)
        return when (host) {
            "forumthreadlist" -> params["fid"]?.toLongOrNull()
                ?.takeIf { it > 0 }?.let { Target.Board(it) }
            "forumthread" -> params["tid"]?.toLongOrNull()
                ?.takeIf { it > 0 }?.let { tid ->
                    Target.Thread(tid, params["pid"]?.toLongOrNull()?.takeIf { it > 0 })
                }
            "user", "userhome" -> params["uid"]?.toLongOrNull()
                ?.takeIf { it > 0 }?.let { Target.User(it) }
            else -> null
        }
    }

    /** https bbs 帖/用户/板块三形态；**host 校验失败一律 Web**（防外站同形参数误路由）；其余 → Web */
    private fun parseWeb(rawUrl: String): Target? {
        // 锚点先摘（#pidN 可出现在 query 之后，不清掉会让 tid 解析失败）
        val fragIdx = rawUrl.indexOf('#')
        val anchor = if (fragIdx >= 0) rawUrl.substring(fragIdx) else ""
        val url = if (fragIdx >= 0) rawUrl.substring(0, fragIdx) else rawUrl
        val afterScheme = url.substringAfter("//")
        val host = afterScheme.substringBefore('/').lowercase()
        // 仅站方论坛域参与原生升级（sdgun.com.cn 及其子域）
        if (!(host == "bbs.sdgun.com.cn" || host.endsWith(".sdgun.com.cn"))) return Target.Web(rawUrl)
        val q = url.indexOf('?')
        val path = afterScheme.substringAfter('/').substringBefore('?')
        val lowerPath = path.lowercase()
        val params = parseQuery(if (q >= 0) url.substring(q + 1) else "")
        if (lowerPath.contains("forum.php") && params["mod"]?.lowercase() == "viewthread") {
            val tid = params["tid"]?.toLongOrNull()?.takeIf { it > 0 } ?: return Target.Web(rawUrl)
            // pid 源：显式 ?pid= 或锚点 #pidN（站方楼层锚形态）
            val pid = params["pid"]?.toLongOrNull()?.takeIf { it > 0 }
                ?: Regex("""#pid(\d+)""").find(anchor)?.groupValues?.get(1)?.toLongOrNull()
            return Target.Thread(tid, pid)
        }
        if (lowerPath.contains("forum.php") && params["mod"]?.lowercase() == "forumdisplay") {
            val fid = params["fid"]?.toLongOrNull()?.takeIf { it > 0 } ?: return Target.Web(rawUrl)
            return Target.Board(fid)
        }
        Regex("""space-uid-(\d+)\.html""", RegexOption.IGNORE_CASE).find(path)
            ?.groupValues?.get(1)?.toLongOrNull()?.takeIf { it > 0 }
            ?.let { return Target.User(it) }
        return Target.Web(rawUrl)
    }

    /** k=v&k2=v2 宽松解析（容忍空段/缺值；值不解码——路由参数均为数字形态） */
    private fun parseQuery(query: String): Map<String, String> = query
        .split('&')
        .mapNotNull {
            val i = it.indexOf('=')
            if (i <= 0) null else it.substring(0, i).lowercase() to it.substring(i + 1)
        }
        .toMap()
}
