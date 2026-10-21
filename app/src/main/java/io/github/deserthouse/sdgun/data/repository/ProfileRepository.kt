package io.github.deserthouse.sdgun.data.repository

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

/**
 * 登录态个人面端点客户端（G3，api_survey §六 已测绘）。
 * 全部需要 token（MAGAPPX UA 第 8 段）。
 */
object ProfileRepository {

    const val MY_INFO = "https://mag1.sdgun.net/mag/user/v1/User/getUserInfo"
    const val MY_VISITOR = "https://mag1.sdgun.net/mag/user/v3/User/myVisitor"
    const val MY_VIEW_USER = "https://mag1.sdgun.net/mag/user/v3/User/myViewUser"
    const val MY_FOLLOW = "https://mag1.sdgun.net/mag/user/v5/user/myFollowUserList"
    const val MY_TOPIC = "https://mag1.sdgun.net/mag/circle/v1/show/myTopic"
    const val MY_AUDITED = "https://mag1.sdgun.net/mag/user/v1/user/myAuditedContent"
    const val MESSAGE_GROUP = "https://mag1.sdgun.net/mag/user/v1/UserNotify/userMessageGroupList"
    const val NOTIFY_INDEX = "https://mag1.sdgun.net/mag/user/v1/UserNotify/userNotifyIndex"

    data class UserProfile(
        val userId: Long,
        val name: String,
        val head: String?,
        val groupName: String?,
        val groupLevel: String?,
        val score: String?,
        val sex: String?,
        val sign: String?,
        val hasPhone: Boolean,
        val isVip: Int,
    )

    data class PersonItem(
        val userId: Long,
        val name: String,
        val head: String?,
        val extra: String?,
    )

    data class MessageGroup(
        val id: String,
        val name: String,
        val count: Int,
    )

    /** 解析 getUserInfo 明文 dict → UserProfile（纯函数，JVM 可测） */
    fun parseUserInfo(json: String): UserProfile? {
        return try {
            val obj = JSONObject(json)
            val d = obj.optJSONObject("data") ?: return null
            val uid = d.optLong("user_id", 0L)
            val name = d.optString("name")
            if (uid <= 0 || name.isBlank()) return null
            UserProfile(
                userId = uid, name = name,
                head = d.optString("head").takeIf { it.startsWith("http") },
                groupName = d.optString("group_name").takeIf { it.isNotBlank() },
                groupLevel = d.optString("group_level").takeIf { it.isNotBlank() },
                score = d.optString("score").takeIf { it.isNotBlank() },
                sex = d.optString("sex").takeIf { it.isNotBlank() },
                sign = d.optString("sign").takeIf { it.isNotBlank() },
                hasPhone = d.optBoolean("has_phone"),
                isVip = d.optInt("is_vip", 0),
            )
        } catch (e: Exception) { null }
    }

    /** 解析列表 JSON → PersonItem 列表（纯函数） */
    fun parsePeople(json: String): List<PersonItem> {
        return try {
            val obj = JSONObject(json)
            val arr = obj.optJSONArray("list") ?: return emptyList()
            (0 until arr.length()).mapNotNull { i ->
                val o = arr.optJSONObject(i) ?: return@mapNotNull null
                val uid = o.optLong("user_id", o.optLong("id", 0L))
                val name = o.optString("user_name", o.optString("name"))
                if (uid <= 0) return@mapNotNull null
                PersonItem(userId = uid, name = name,
                    head = o.optString("user_head").takeIf { it.startsWith("http") },
                    extra = o.optString("time").takeIf { it.isNotBlank() })
            }
        } catch (e: Exception) { emptyList() }
    }

    /** 解析消息分组 JSON → MessageGroup 列表（纯函数） */
    fun parseMessageGroups(json: String): List<MessageGroup> {
        return try {
            val obj = JSONObject(json)
            val arr = obj.optJSONArray("list") ?: return emptyList()
            (0 until arr.length()).mapNotNull { i ->
                val o = arr.optJSONObject(i) ?: return@mapNotNull null
                MessageGroup(
                    id = o.optString("id"),
                    name = o.optString("name") ?: "",
                    count = o.optInt("count", 0),
                )
            }
        } catch (e: Exception) { emptyList() }
    }

    private fun get(url: String, token: String): String? {
        return try {
            val conn = URL(url).openConnection() as HttpURLConnection
            conn.connectTimeout = 12000
            conn.readTimeout = 15000
            conn.setRequestProperty("User-Agent",
                "Mozilla/5.0 (Linux; Android 16; Pixel 8) MAGAPPX|6.4.0-2.90-30042|Android 16 google Pixel 8|shuidan|koPx4DY3tw7hrg5nKpSmc3|88af41c4c24832860d97d44e436f8842|174e1e66702c31aa7e949f5ab30db60b|$token")
            try {
                conn.inputStream.bufferedReader().readText()
            } finally { conn.disconnect() }
        } catch (e: Exception) { null }
    }

    suspend fun fetchMyInfo(token: String): UserProfile? = withContext(Dispatchers.IO) {
        parseUserInfo(get(MY_INFO, token) ?: return@withContext null)
    }

    suspend fun fetchVisitors(token: String): List<PersonItem> = withContext(Dispatchers.IO) {
        parsePeople(get(MY_VISITOR, token) ?: return@withContext emptyList())
    }

    suspend fun fetchViewUsers(token: String): List<PersonItem> = withContext(Dispatchers.IO) {
        parsePeople(get(MY_VIEW_USER, token) ?: return@withContext emptyList())
    }

    suspend fun fetchFollows(token: String): List<PersonItem> = withContext(Dispatchers.IO) {
        parsePeople(get(MY_FOLLOW, token) ?: return@withContext emptyList())
    }

    suspend fun fetchMessageGroups(token: String): List<MessageGroup> = withContext(Dispatchers.IO) {
        parseMessageGroups(get(MESSAGE_GROUP, token) ?: return@withContext emptyList())
    }
}
