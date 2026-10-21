package io.github.deserthouse.sdgun.data.repository

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

/**
 * 帖子阅读客户端（G2-A05，R-A0 实测）：
 * - 主楼 = app 域 threadView（tid+fid → 40 字段独立端点，G0-C01a 契约）
 * - 楼层 = app 域 commentList（tid/p/order/authorid；floor 恒从 2 起；total_page 权威；step 忽略）
 * - 只看楼主 = commentList + authorid=(发帖人 uid，从主楼 user_id 取)
 * - order 枚举：2=正序 1=倒序
 * - 正文 HTML 块化解析由 UI 层处理（G2-A05 页面规格）
 */
object ThreadRepository {

    const val THREAD_VIEW_URL = "https://app.sdgun.com.cn/mag/circle/v1/forum/threadView"
    const val COMMENT_LIST_URL = "https://app.sdgun.com.cn/mag/circle/v1/Forum/commentList"

    /** 主楼（threadView data 40 字段的关键子集） */
    data class ThreadOP(
        val tid: Long,
        val pid: Long,
        val fid: Long,
        val title: String,
        val contentHtml: String,
        val authorName: String?,
        val authorId: Long?,
        val avatarUrl: String?,
        val createTime: String?,
        val viewCount: String?,
        val replyCount: String?,
        val pics: List<String>,
    )

    /** 楼层（commentList 关键子集） */
    data class Floor(
        val pid: Long,
        val floor: Int,
        val authorName: String?,
        val authorId: Long?,
        val avatarUrl: String?,
        val contentHtml: String,
        val pics: List<String>,
        val createTime: String?,
        val applaudCount: Int?,
        val isPublisher: Boolean,
    )

    data class FloorsPage(
        val floors: List<Floor>,
        val page: Int,
        val totalPages: Int,
    )

    /** 解析 threadView JSON → 主楼（纯函数，JVM 可测） */
    fun parseOP(json: String): ThreadOP? {
        return try {
            val obj = JSONObject(json)
            val d = obj.optJSONObject("data") ?: return null
            val tid = d.optLong("tid", 0L)
            val title = d.optString("title").trim()
            if (tid <= 0 || title.isBlank()) return null
            val user = d.optJSONObject("user")
            ThreadOP(
                tid = tid,
                pid = d.optLong("pid", 0L),
                fid = d.optLong("fid", 0L),
                title = title,
                contentHtml = d.optString("content"),
                authorName = d.optString("user_name").takeIf { it.isNotBlank() && it != "null" },
                authorId = user?.optLong("user_id", 0L)?.takeIf { it > 0 }
                    ?: d.optLong("user_id", 0L).takeIf { it > 0 },
                avatarUrl = (user?.optString("head") ?: d.optString("user_head"))
                    .takeIf { it.startsWith("http") },
                createTime = d.optString("create_time").takeIf { it.isNotBlank() && it != "null" },
                viewCount = d.optString("click").takeIf { it.isNotBlank() && it != "null" },
                replyCount = d.optString("reply_count").takeIf { it.isNotBlank() && it != "null" },
                pics = emptyList(),
            )
        } catch (e: Exception) {
            null
        }
    }

    /** 解析 commentList JSON → 楼层页（纯函数，JVM 可测） */
    fun parseFloors(json: String, page: Int): FloorsPage? {
        return try {
            val obj = JSONObject(json)
            val arr = obj.optJSONArray("list") ?: return null
            val floors = (0 until arr.length()).mapNotNull { i ->
                val f = arr.optJSONObject(i) ?: return@mapNotNull null
                val pid = f.optLong("id", 0L)
                if (pid <= 0) return@mapNotNull null
                Floor(
                    pid = pid,
                    floor = f.optInt("floor", 0),
                    authorName = f.optString("user_name").takeIf { it.isNotBlank() && it != "null" },
                    authorId = f.optLong("user_id", 0L).takeIf { it > 0 },
                    avatarUrl = f.optString("user_head").takeIf { it.startsWith("http") },
                    contentHtml = f.optString("content"),
                    pics = emptyList(),
                    createTime = f.optString("time").takeIf { it.isNotBlank() && it != "null" },
                    applaudCount = f.optInt("applaud_count", -1).takeIf { it >= 0 },
                    isPublisher = f.optBoolean("is_publisher"),
                )
            }
            FloorsPage(
                floors = floors,
                page = page,
                totalPages = obj.optInt("total_page", 1).takeIf { it > 0 } ?: 1,
            )
        } catch (e: Exception) {
            null
        }
    }

    private fun get(url: String): String? {
        return try {
            val conn = URL(url).openConnection() as HttpURLConnection
            conn.connectTimeout = 12000
            conn.readTimeout = 15000
            conn.setRequestProperty("User-Agent", "Mozilla/5.0 (Linux; Android 16; Pixel 8)")
            try {
                conn.inputStream.bufferedReader().readText()
            } finally {
                conn.disconnect()
            }
        } catch (e: Exception) {
            null
        }
    }

    suspend fun fetchOP(tid: Long, fid: Long): ThreadOP? = withContext(Dispatchers.IO) {
        parseOP(get("$THREAD_VIEW_URL?tid=$tid&fid=$fid") ?: return@withContext null)
    }

    suspend fun fetchFloors(tid: Long, page: Int, descending: Boolean, authorId: Long?): FloorsPage? =
        withContext(Dispatchers.IO) {
            // 正序=order 2 / 倒序=order 1（R-A0 实测：正序从 floor=2 起，倒序从 floor=N 起）
            val order = if (descending) "1" else "2"
            val url = buildString {
                append("$COMMENT_LIST_URL?tid=$tid&p=$page&order=$order")
                authorId?.let { append("&authorid=$it") }
            }
            parseFloors(get(url) ?: return@withContext null, page)
        }
}
