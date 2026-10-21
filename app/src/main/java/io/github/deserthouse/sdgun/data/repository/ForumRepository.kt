package io.github.deserthouse.sdgun.data.repository
import org.json.JSONArray
import org.json.JSONObject

/** 板块帖子摘要（AF-02 方案 A）：forumView WAP 接口字段的归一化视图 */
data class ThreadSummary(
    val tid: Long,
    val title: String,
    val author: String,
    val avatarUrl: String?,
    val coverUrls: List<String>,
    val applauseCount: Int?,
    val lastPostText: String?,
)

/**
 * forumView WAP 只读客户端（游客面）：无凭据、移动浏览器 UA、与 WebView Cookie 隔离。
 * 分页一律 p= 参数（2026-10-15 实证：page= 被服务端忽略）；预算=每次至多 3 页。
 */
object ForumRepository {

    const val ENDPOINT = "https://app.sdgun.com.cn/mag/circle/v1/forum/forumView"
    const val MAX_PAGES_PER_REQUEST = 3
    const val PAGE_SIZE_ASSUMED = 20

    /** fid → (页码 → 解析结果) 的会话内存缓存；TTL 由 stamp 控制 */
    private val cache = HashMap<String, Pair<Long, Map<Long, ThreadSummary>>>()
    private const val TTL_MS = 30L * 60 * 1000

    fun cached(fid: Long): Map<Long, ThreadSummary>? {
        synchronized(cache) {
            val (stamp, map) = cache[fid.toString()] ?: return null
            return if (System.currentTimeMillis() - stamp < TTL_MS) map else null
        }
    }

    fun putCache(fid: Long, map: Map<Long, ThreadSummary>) {
        synchronized(cache) { cache[fid.toString()] = System.currentTimeMillis() to map }
    }

    /** 抓取 URL（fid + 从第 startPage 起 count 页，p= 参数） */
    fun pageUrls(fid: Long, startPage: Int, pageCount: Int): List<String> =
        (startPage until startPage + pageCount.coerceIn(1, MAX_PAGES_PER_REQUEST))
            .map { "$ENDPOINT?fid=$fid&p=$it&step=$PAGE_SIZE_ASSUMED&order=lastpost" }

    /**
     * 纯函数：解析 forumView 响应 JSON → tid 归一化映射。
     * 服务端元数据（page/total_page）原样返回给调用方判断分页终点；
     * 任何结构异常 fail-closed 为空映射（不抛）。
     */
    fun parseForumView(json: String): Pair<Map<Long, ThreadSummary>, Int> {
        val empty = emptyMap<Long, ThreadSummary>() to 0
        if (json.isBlank()) return empty
        return runCatching {
            val obj = JSONObject(json)
            if (!obj.optBoolean("success", false)) return empty
            val arr = obj.optJSONArray("list") ?: return empty
            val totalPages = obj.optInt("total_page", 0)
            val out = HashMap<Long, ThreadSummary>()
            for (i in 0 until arr.length()) {
                val it = parseItem(arr.optJSONObject(i) ?: continue) ?: continue
                out[it.tid] = it
            }
            out to totalPages
        }.getOrDefault(empty)
    }

    /** 单条归一化；tid 缺失/非法=丢弃 */
    internal fun parseItem(o: JSONObject): ThreadSummary? {
        val tid = o.optLong("tid", 0L)
        if (tid <= 0) return null
        val covers = ArrayList<String>()
        val pics = o.optJSONArray("pics")
        if (pics != null) collectHttpUrls(pics, covers, 3)
        return ThreadSummary(
            tid = tid,
            title = o.optString("title").trim(),
            author = o.optString("user_name").trim(),
            avatarUrl = o.optString("user_head").takeIf { it.startsWith("http") },
            coverUrls = covers,
            applauseCount = if (o.has("applaud_count")) o.optInt("applaud_count") else null,
            lastPostText = o.optString("lastpost").takeIf { it.isNotBlank() },
        )
    }

    private fun collectHttpUrls(arr: JSONArray, out: MutableList<String>, limit: Int) {
        for (i in 0 until arr.length()) {
            if (out.size >= limit) break
            val u = arr.optString(i)
            if (u.startsWith("http")) out.add(u)
        }
    }
}
