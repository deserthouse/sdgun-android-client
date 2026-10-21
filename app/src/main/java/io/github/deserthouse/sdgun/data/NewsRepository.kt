package io.github.deserthouse.sdgun.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject

/** 资讯条目（AF-01）：infoListByCatId 字段归一化 */
data class NewsItem(
    val id: Long,
    val title: String,
    val summary: String,
    val coverUrl: String?,
    val source: String,
    val timeText: String?,
    val comments: Int?,
    val link: String,
)

/**
 * mag1 资讯流只读客户端（游客面，无凭据；legal_boundary 10-09 定性 + 10-15 复测可用）。
 * 分页：p= 递增；该端点不返回 total_page——以返回空列表为终点（每页条数固定拉 20）。
 */
object NewsRepository {

    const val ENDPOINT = "https://mag1.sdgun.net/mag/info/v2/channel/infoListByCatId"
    const val PAGE_STEP = 20
    const val MAX_PAGES_PER_REQUEST = 2

    private val cache = HashMap<Int, Pair<Long, List<NewsItem>>>()
    private const val TTL_MS = 30L * 60 * 1000

    fun cached(catId: Int): List<NewsItem>? {
        synchronized(cache) {
            val (stamp, list) = cache[catId] ?: return null
            return if (System.currentTimeMillis() - stamp < TTL_MS) list else null
        }
    }

    fun putCache(catId: Int, list: List<NewsItem>) {
        synchronized(cache) { cache[catId] = System.currentTimeMillis() to list }
    }

    fun pageUrls(catId: Int, startPage: Int, pageCount: Int): List<String> =
        (startPage until startPage + pageCount.coerceIn(1, MAX_PAGES_PER_REQUEST))
            .map { "$ENDPOINT?cat_id=$catId&step=$PAGE_STEP&p=$it" }

    /**
     * AV-02：网络必须在 IO 线程（主线程联网抛 NetworkOnMainThreadException，
     * 曾被 runCatching 吞成 null 后文案甩锅"服务器波动"）。
     * 返回 null=请求失败（调用方保留旧内容）；空列表=成功但无数据（真分页终点）。
     */
    /**
     * 测试注入点：JVM 测试用它模拟失败/空页语义。
     * 真机链路无法可靠注入失败——HttpURLConnection 对代理失败会自动 failover 直连，
     * "死代理"在直连通的环境里等于没死（2026-10-21 AVD 实测）。
     */
    internal var fetchOverride: ((String) -> String?)? = null

    suspend fun fetchPage(catId: Int, page: Int, pageCount: Int = 1): List<NewsItem>? =
        withContext(Dispatchers.IO) {
            var anySuccess = false
            val out = ArrayList<NewsItem>()
            for (url in pageUrls(catId, page, pageCount)) {
                val body = (fetchOverride ?: ::fetchNews)(url) ?: continue
                anySuccess = true
                out.addAll(parseNewsList(body))
            }
            if (anySuccess) out else null
        }

    private fun fetchNews(url: String): String? = runCatching {
        val conn = java.net.URL(url).openConnection() as java.net.HttpURLConnection
        conn.connectTimeout = 12000
        conn.readTimeout = 15000
        conn.setRequestProperty(
            "User-Agent",
            "Mozilla/5.0 (Linux; Android 16; Pixel 8) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/130.0.0.0 Mobile Safari/537.36",
        )
        conn.setRequestProperty("Accept", "application/json")
        conn.inputStream.use { it.bufferedReader().readText() }
    }.onFailure {
        android.util.Log.d("SDGNews", "fetch fail ${it.javaClass.simpleName}: ${it.message}")
    }.getOrNull()

    /** 纯函数：按 id 去重合并（旧在前）；返回合并结果与是否有新增 */
    fun mergeById(old: List<NewsItem>, fresh: List<NewsItem>): Pair<List<NewsItem>, Boolean> {
        val merged = LinkedHashMap<Long, NewsItem>()
        old.forEach { merged[it.id] = it }
        var added = false
        fresh.forEach { if (merged.put(it.id, it) == null) added = true }
        return merged.values.toList() to added
    }

    /** 纯函数：解析资讯列表；fail-closed 空列表 */
    fun parseNewsList(json: String): List<NewsItem> {
        if (json.isBlank()) return emptyList()
        return runCatching {
            val obj = JSONObject(json)
            if (!obj.optBoolean("success", false)) return emptyList()
            val arr = obj.optJSONArray("list") ?: return emptyList()
            val out = ArrayList<NewsItem>()
            for (i in 0 until arr.length()) {
                parseItem(arr.optJSONObject(i) ?: continue)?.let { out.add(it) }
            }
            out
        }.getOrDefault(emptyList())
    }

    /** link 缺失（无可读详情页）的条目丢弃——壳只做只读浏览，无详情不进列表 */
    internal fun parseItem(o: JSONObject): NewsItem? {
        val id = o.optLong("id", 0L)
        val link = o.optString("link")
        if (id <= 0 || !link.startsWith("http")) return null
        return NewsItem(
            id = id,
            title = o.optString("title").trim(),
            summary = o.optString("des").trim(),
            coverUrl = firstCover(o),
            source = o.optString("forum_name").trim(),
            timeText = o.optString("publish_time_str").takeIf { it.isNotBlank() },
            comments = if (o.has("comment_count")) o.optInt("comment_count") else null,
            link = link,
        )
    }

    private fun firstCover(o: JSONObject): String? {
        val arr = o.optJSONArray("pics_arr") ?: return null
        for (i in 0 until arr.length()) {
            val p = arr.optJSONObject(i) ?: continue
            // C1：实际字段是 url/tburl（实测 API 无 pic 字段——此前恒空，封面从未显示）
            val u = p.optString("url").ifBlank { p.optString("tburl") }
            if (u.startsWith("http")) return u
        }
        return null
    }
}
