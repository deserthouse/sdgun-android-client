package io.github.deserthouse.sdgun.data.repository

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

/**
 * circleTree/板块发现只读客户端（G2-A03，2026-10-21/22 游客探针实测）。
 *
 * - circleTree (mag1 v1)：5 分组全树，circle_list[].type_value = 论坛 fid，
 *   id = mag1 内部 circleId（两套 ID 严格分存，fid 才是论坛路由键）。
 * - userCircleList (mag1)：登录态"我的板块"（isjoined/new_content）。
 * - 板块排序/详情走 app 域 forumView/forumDetail（ForumRepository 已有）。
 *
 * 游客可用（登录态同端点带 token 即可，个人化字段由服务端按会话返回）。
 */
object BoardRepository {

    const val TREE_URL = "https://mag1.sdgun.net/mag/circle/v1/Circle/circleTree"
    const val MY_CIRCLES_URL = "https://mag1.sdgun.net/mag/circle/v1/circle/userCircleList"

    /** 分组树节点：name=分组名，boards=组内板块（fid 才是论坛路由键） */
    data class Group(val id: Long, val name: String, val boards: List<Board>)

    data class Board(
        val circleId: Long,
        val fid: Long,
        val name: String,
        val iconUrl: String?,
        val description: String?,
        val postToday: String?,
        val isJoined: Boolean,
    )

    /** 解析 circleTree JSON → 分组列表（纯函数，JVM 可测） */
    fun parseTree(json: String): List<Group> {
        return try {
            val obj = JSONObject(json)
            val arr = obj.optJSONArray("list") ?: return emptyList()
            (0 until arr.length()).mapNotNull { i ->
                val g = arr.optJSONObject(i) ?: return@mapNotNull null
                val id = g.optLong("id", 0L)
                val name = g.optString("name")
                if (id <= 0 || name.isBlank()) return@mapNotNull null
                val boards = mutableListOf<Board>()
                val bl = g.optJSONArray("circle_list")
                if (bl != null) {
                    for (j in 0 until bl.length()) {
                        val b = bl.optJSONObject(j) ?: continue
                        val cid = b.optLong("id", 0L)
                        val bname = b.optString("name")
                        val tv = b.optString("type_value").toLongOrNull() ?: continue
                        if (cid <= 0 || bname.isBlank()) continue
                        boards.add(
                            Board(
                                circleId = cid, fid = tv, name = bname,
                                iconUrl = b.optString("icon_url").takeIf { it.startsWith("http") },
                                description = b.optString("des").takeIf { it.isNotBlank() },
                                postToday = b.optString("post_today").takeIf { it.isNotBlank() },
                                isJoined = b.optBoolean("isjoined"),
                            ),
                        )
                    }
                }
                Group(id, name, boards)
            }
        } catch (e: Exception) {
            emptyList()
        }
    }

    /** 解析 userCircleList JSON → 我的板块列表（纯函数，JVM 可测） */
    fun parseMyCircles(json: String): List<Board> {
        return try {
            val obj = JSONObject(json)
            val arr = obj.optJSONArray("list") ?: return emptyList()
            (0 until arr.length()).mapNotNull { i ->
                val b = arr.optJSONObject(i) ?: return@mapNotNull null
                val cid = b.optLong("id", 0L)
                val name = b.optString("name")
                val tv = b.optString("type_value").toLongOrNull()
                if (cid <= 0 || name.isBlank()) return@mapNotNull null
                Board(
                    circleId = cid, fid = tv ?: 0, name = name,
                    iconUrl = b.optString("icon_url").takeIf { it.startsWith("http") },
                    description = b.optString("simple_des").takeIf { it.isNotBlank() },
                    postToday = null,
                    isJoined = true,
                )
            }
        } catch (e: Exception) {
            emptyList()
        }
    }

    suspend fun fetchTree(): List<Group> = withContext(Dispatchers.IO) {
        try {
            val conn = URL(TREE_URL).openConnection() as HttpURLConnection
            conn.connectTimeout = 12000
            conn.readTimeout = 15000
            conn.setRequestProperty("User-Agent", "Mozilla/5.0 (Linux; Android 16; Pixel 8)")
            try {
                parseTree(conn.inputStream.bufferedReader().readText())
            } finally {
                conn.disconnect()
            }
        } catch (e: Exception) {
            emptyList()
        }
    }

    suspend fun fetchMyCircles(): List<Board> = withContext(Dispatchers.IO) {
        try {
            val conn = URL(MY_CIRCLES_URL).openConnection() as HttpURLConnection
            conn.connectTimeout = 12000
            conn.readTimeout = 15000
            conn.setRequestProperty("User-Agent", "Mozilla/5.0 (Linux; Android 16; Pixel 8)")
            try {
                parseMyCircles(conn.inputStream.bufferedReader().readText())
            } finally {
                conn.disconnect()
            }
        } catch (e: Exception) {
            emptyList()
        }
    }
}
