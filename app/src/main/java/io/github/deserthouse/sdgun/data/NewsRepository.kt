package io.github.deserthouse.sdgun.data

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
            val u = p.optString("pic")
            if (u.startsWith("http")) return u
        }
        return null
    }
}
