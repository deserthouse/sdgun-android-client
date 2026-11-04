package io.github.deserthouse.sdgun.data.repository

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

/**
 * forumView 板块帖子列表客户端（G2-A04，R-A0 实测）：
 * - app 域 forumView 游客/登录态均通（fid/p/step/order）。
 * - forumDetail 给板块元数据+orders 枚举+threadtypes 筛选。
 * - order 官方枚举：空=默认/最新 lastpost/热门 heat/热帖 hot/发帖时间 dateline/回复 replies/查看 views。
 * - step 被服务端忽略（恒返满页 ~49/页），total_page 为权威分页。
 */
object BoardListRepository {

    const val BASE = "https://app.sdgun.com.cn/mag/circle/v1/forum"

    /** 官方排序枚举（forumDetail.orders 同源） */
    val ORDERS = listOf(
        "" to "默认",
        "lastpost" to "最新",
        "heat" to "热门",
        "hot" to "热帖",
        "dateline" to "发帖时间",
        "replies" to "回复",
        "views" to "查看",
    )

    data class ThreadRow(
        val tid: Long,
        val title: String,
        val authorName: String?,
        val authorId: Long?,
        val avatarUrl: String?,
        val coverUrl: String?,
        val replyCount: String?,
        val viewCount: String?,
        val lastReplyTime: String?,
        val isTop: Boolean,
        val isDigest: Boolean,
        val applaudCount: String?,
        /** WP2-A：真实 des 摘要（仅服务端携带且非空；不捏造、不 subject 回退） */
        val summary: String? = null,
        /** WP2-A：pics 数组计数（>1 行内角标；coverUrl=首图） */
        val picCount: Int = 0,
    )

    data class BoardMeta(
        val fid: Long,
        val name: String,
        val iconUrl: String?,
        val postToday: String?,
        val threadCount: String?,
        val postCount: String?,
        val orders: List<Pair<String, String>>, // value to label
        val threadTypes: List<Pair<String, String>>, // typeid to name
    )

    data class PageResult(
        val rows: List<ThreadRow>,
        val page: Int,
        val totalPages: Int,
        /** 2026-10-22 实测：登录限定板块 success=false + msg="尚未登录"（无 list 字段） */
        val authRequired: Boolean = false,
        /** success=false 时的服务端消息（权限不足等） */
        val errorMsg: String? = null,
    )

    /** 解析 forumView JSON → 行列表（纯函数，JVM 可测） */
    fun parseRows(json: String): List<ThreadRow> {
        return try {
            val obj = JSONObject(json)
            val arr = obj.optJSONArray("list") ?: return emptyList()
            (0 until arr.length()).mapNotNull { i ->
                val r = arr.optJSONObject(i) ?: return@mapNotNull null
                val tid = r.optLong("tid", 0L)
                // 标题含裸 HTML（实测 <img src=…> 直出上屏）——剥标签只留文本
                val title = r.optString("title")
                    .replace(Regex("<[^>]*>"), "").trim()
                if (tid <= 0 || title.isBlank()) return@mapNotNull null
                ThreadRow(
                    tid = tid,
                    title = title,
                    authorName = r.optString("user_name").takeIf { it.isNotBlank() && it != "null" },
                    authorId = r.optLong("user_id", 0L).takeIf { it > 0 },
                    avatarUrl = r.optString("user_head").takeIf { it.startsWith("http") },
                    coverUrl = firstPic(r),
                    // A24-07 字段实测（forumView 真响应）：reply_count（字符串）而非 replies；
                    // last_reply_time=unix 秒需格式化（原样泄漏时间戳上屏属缺陷）
                    replyCount = r.optString("reply_count").takeIf { it.isNotBlank() && it != "null" },
                    viewCount = r.optString("click").takeIf { it.isNotBlank() && it != "null" },
                    lastReplyTime = r.optString("last_reply_time")
                        .takeIf { it.isNotBlank() && it != "null" }
                        ?.let { relativeTime(it) },
                    isTop = r.optInt("is_top", 0) > 0,
                    isDigest = r.optInt("digest", 0) > 0,
                    applaudCount = r.optString("applaud_count").takeIf { it.isNotBlank() && it != "null" },
                    summary = r.optString("des").takeIf { it.isNotBlank() && it != "null" },
                    picCount = r.optJSONArray("pics")?.length() ?: 0,
                )
            }
        } catch (e: Exception) {
            emptyList()
        }
    }

    /** unix 秒 → "yyyy-M-d HH:mm"；非纯数字（已是格式化文本）原样保留（JVM 可测） */
    fun formatUnixTime(raw: String): String =
        runCatching {
            val sec = raw.toLong()
            val cal = java.util.Calendar.getInstance().apply { timeInMillis = sec * 1000 }
            java.text.SimpleDateFormat("yyyy-M-d HH:mm", java.util.Locale.CHINA).format(cal.time)
        }.getOrDefault(raw)

    /**
     * B1-7：相对时间——刚刚/N 分钟前/N 小时前/N 天前，≥7 天回落绝对时间。
     * 非纯数字原样保留；未来时间（时钟偏差）按「刚刚」处理不出现负数。
     * nowMillis 注入供 JVM 测试。
     */
    fun relativeTime(raw: String, nowMillis: Long = System.currentTimeMillis()): String {
        val sec = raw.toLongOrNull() ?: return raw
        val diffMin = (nowMillis / 1000 - sec) / 60
        return when {
            diffMin < 1 -> "刚刚"
            diffMin < 60 -> "$diffMin 分钟前"
            diffMin < 60 * 24 -> "${diffMin / 60} 小时前"
            diffMin < 60 * 24 * 7 -> "${diffMin / 60 / 24} 天前"
            else -> formatUnixTime(raw)
        }
    }

    private fun firstPic(r: JSONObject): String? {
        val arr = r.optJSONArray("pics") ?: return null
        for (i in 0 until arr.length()) {
            val u = arr.optString(i)
            if (u.startsWith("http")) return u
        }
        return null
    }

    /** 解析 forumDetail JSON → 板块元数据（纯函数，JVM 可测） */
    fun parseBoardMeta(json: String): BoardMeta? {
        return try {
            val obj = JSONObject(json)
            val d = obj.optJSONObject("data") ?: return null
            val fid = d.optLong("fid", 0L)
            val name = d.optString("name")
            if (fid <= 0 || name.isBlank()) return null
            val orders = mutableListOf<Pair<String, String>>()
            d.optJSONArray("orders")?.let { arr ->
                for (i in 0 until arr.length()) {
                    val o = arr.optJSONObject(i) ?: continue
                    orders.add((o.optString("value") to o.optString("name")))
                }
            }
            val types = mutableListOf<Pair<String, String>>()
            d.optJSONObject("threadtypes")?.let { tt ->
                tt.keys().asSequence().forEach { k ->
                    val v = tt.optJSONObject(k)
                    if (v != null) types.add(k to v.optString("name"))
                }
            }
            BoardMeta(
                fid = fid, name = name,
                iconUrl = d.optString("icon").takeIf { it.startsWith("http") },
                postToday = d.optString("post_today").takeIf { it.isNotBlank() },
                threadCount = d.optString("thread").takeIf { it.isNotBlank() },
                postCount = d.optString("post").takeIf { it.isNotBlank() },
                orders = orders, threadTypes = types,
            )
        } catch (e: Exception) {
            null
        }
    }

    fun listUrl(fid: Long, page: Int, order: String, typeId: String = ""): String =
        buildString {
            append("$BASE/forumView?fid=$fid&p=$page")
            if (order.isNotBlank()) append("&order=$order")
            if (typeId.isNotBlank()) append("&typeid=$typeId")
        }

    fun detailUrl(fid: Long): String = "$BASE/forumDetail?fid=$fid"

    suspend fun fetchPage(
        fid: Long, page: Int, order: String = "", typeId: String = "",
    ): PageResult? = withContext(Dispatchers.IO) {
        try {
            val conn = URL(listUrl(fid, page, order, typeId)).openConnection() as HttpURLConnection
            conn.connectTimeout = 12000
            conn.readTimeout = 15000
            conn.setRequestProperty("User-Agent", "Mozilla/5.0 (Linux; Android 16; Pixel 8)")
            try {
                val body = conn.inputStream.bufferedReader().readText()
                val obj = runCatching { JSONObject(body) }.getOrNull()
                // 信封先行：success=false 是服务端明确拒绝（登录限定板块/权限不足），
                // 不得吞成"空板块"——按消息分型（A70-05 失败分型）
                if (obj?.optBoolean("success", true) == false) {
                    val msg = obj.optString("msg").takeIf { it.isNotBlank() } ?: "服务端拒绝访问"
                    return@withContext PageResult(
                        rows = emptyList(), page = page, totalPages = page,
                        authRequired = msg.contains("登录"), errorMsg = msg,
                    )
                }
                val rows = parseRows(body)
                // forumView 信封无 total_page 字段（2026-10-22 实测 fid=198 翻到 p=49 空页）：
                // 末页以空 list 标记，翻页依据=本页有行则可尝试下一页；
                // 空页返回结果对象（null 只代表传输层失败，两者必须可区分）
                val totalPages = if (rows.isEmpty()) page else page + 1
                PageResult(rows, page, totalPages)
            } finally {
                conn.disconnect()
            }
        } catch (e: Exception) {
            null
        }
    }

    suspend fun fetchBoardMeta(fid: Long): BoardMeta? = withContext(Dispatchers.IO) {
        try {
            val conn = URL(detailUrl(fid)).openConnection() as HttpURLConnection
            conn.connectTimeout = 12000
            conn.readTimeout = 15000
            conn.setRequestProperty("User-Agent", "Mozilla/5.0 (Linux; Android 16; Pixel 8)")
            try {
                parseBoardMeta(conn.inputStream.bufferedReader().readText())
            } finally {
                conn.disconnect()
            }
        } catch (e: Exception) {
            null
        }
    }

    // ── Q5（A1-10）：圈子搜索（allTopicSearch，DataPage 族=GET p；jadx
    // AllTopicSearchActivity：param keyword，DataViewType icon_name_line→icon/name/link 行）──
    const val ALL_TOPIC_SEARCH = "https://mag1.sdgun.net/mag/circle/v1/show/allTopicSearch"

    data class TopicSearchRow(
        val name: String,
        val icon: String?,
        val link: String?,
    )

    /** 纯函数解析（S05 口径：null=失败/拒绝/未知信封；合法空=空列表） */
    fun parseTopicSearch(json: String): List<TopicSearchRow>? {
        return try {
            val obj = JSONObject(json)
            if (obj.has("success") && !obj.optBoolean("success")) return null
            val arr = obj.optJSONArray("list") ?: return null
            (0 until arr.length()).mapNotNull { i ->
                val o = arr.optJSONObject(i) ?: return@mapNotNull null
                val name = o.optString("name").takeIf { it.isNotBlank() && it != "null" }
                    ?: return@mapNotNull null
                TopicSearchRow(
                    name = name,
                    icon = o.optString("icon").takeIf { it.startsWith("http") },
                    link = o.optString("link").takeIf { it.startsWith("http") },
                )
            }
        } catch (e: Exception) { null }
    }

    /** 圈子搜索（GET keyword+p；游客可用性=使用自然验证，失败诚实呈现） */
    suspend fun searchTopics(token: String, keyword: String, page: Int = 1): List<TopicSearchRow>? =
        withContext(Dispatchers.IO) {
            if (keyword.isBlank()) return@withContext emptyList<TopicSearchRow>()
            try {
                val url = "$ALL_TOPIC_SEARCH?keyword=" +
                    java.net.URLEncoder.encode(keyword, "UTF-8") + "&p=$page"
                val conn = URL(url).openConnection() as HttpURLConnection
                conn.connectTimeout = 12000
                conn.readTimeout = 15000
                conn.setRequestProperty(
                    "User-Agent",
                    "Mozilla/5.0 (Linux; Android 16; Pixel 8) MAGAPPX|6.4.0-2.90-30042|" +
                        "Android 16 google Pixel 8|shuidan|koPx4DY3tw7hrg5nKpSmc3|" +
                        "88af41c4c24832860d97d44e436f8842|174e1e66702c31aa7e949f5ab30db60b|$token",
                )
                try {
                    parseTopicSearch(conn.inputStream.bufferedReader().readText())
                } finally { conn.disconnect() }
            } catch (e: Exception) { null }
        }
}
