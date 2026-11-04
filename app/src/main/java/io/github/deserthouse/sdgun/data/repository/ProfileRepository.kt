package io.github.deserthouse.sdgun.data.repository

import io.github.deserthouse.sdgun.core.model.UiState
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

    /**
     * Q4（A0-6）：私有读统一三型——鉴权失效必须与传输失败分型：
     * AuthExpired→UI 呈 AuthRequired（引导重登，不串私有内容、不误导重试）；
     * Failed→RetryableError（可重试）。null 语义自本版起不再外泄到调用方。
     */
    sealed class PrivateResult<out T> {
        data class Ok<T>(val value: T) : PrivateResult<T>()
        object AuthExpired : PrivateResult<Nothing>()
        object Failed : PrivateResult<Nothing>()
    }

    /** WP3/A1104-S01：私有资料结果→UI 态映射（纯函数，JVM 可测； MainActivity user 页消费） */
    fun mapUserProfileResult(r: PrivateResult<UserProfile>): UiState<UserProfile> = when (r) {
        is PrivateResult.Ok -> UiState.Data(r.value)
        PrivateResult.AuthExpired -> UiState.AuthRequired
        PrivateResult.Failed -> UiState.RetryableError("资料加载失败（可能需要登录或该用户不存在）")
    }

    /**
     * 鉴权拒绝识别（纯函数，JVM 可测）：success:false 且 code==1001（token 失效，
     * api_survey 实测形态）或 msg 含登录/过期/身份——BoardListRepository A70-05
     * 消息分型先例的统一化。
     */
    fun isAuthRejected(json: String): Boolean {
        return try {
            val obj = JSONObject(json)
            // success 严格布尔（A28-02 同口径：optBoolean 会把字符串 "false" parse 成
            // 拒绝——字符串形态属未知信封，不参与鉴权分型）
            val success = obj.opt("success")
            if (success is Boolean && !success) {
                obj.optInt("code", 0) == 1001 ||
                    obj.optString("msg").let {
                        it.contains("登录") || it.contains("过期") || it.contains("身份")
                    }
            } else false
        } catch (e: Exception) { false }
    }

    const val MY_INFO = "https://mag1.sdgun.net/mag/user/v1/User/getUserInfo"
    const val MY_VISITOR = "https://mag1.sdgun.net/mag/user/v3/User/myVisitor"
    const val MY_VIEW_USER = "https://mag1.sdgun.net/mag/user/v3/User/myViewUser"
    const val MY_FOLLOW = "https://mag1.sdgun.net/mag/user/v5/user/myFollowUserList"
    const val MY_TOPIC = "https://mag1.sdgun.net/mag/circle/v1/show/myTopic"
    const val MY_AUDITED = "https://mag1.sdgun.net/mag/user/v1/user/myAuditedContent"
    const val MESSAGE_GROUP = "https://mag1.sdgun.net/mag/user/v1/UserNotify/userMessageGroupList"
    const val NOTIFY_INDEX = "https://mag1.sdgun.net/mag/user/v1/UserNotify/userNotifyIndex"
    /** R1/A2-1：收藏列表（C4-3 静态取证，GET p 分页；信封待登录实测） */
    const val MY_COLLECT_LIST = "https://mag1.sdgun.net/mag/user/v1/User/userCollectList"
    /** R3/A4：私信会话历史（C4-4 静态取证，GET uid+p 分页；read-may-mutate 契约披露） */
    const val USER_MESSAGE_LIST = "https://mag1.sdgun.net/mag/user/v1/UserNotify/userMessageList"
    /** R3/A4-4：通知历史（C4-1 静态取证 v3 路径，POST query p 分页；read-may-mutate 披露） */
    const val NOTIFY_LIST_V3 = "https://mag1.sdgun.net/mag/user/v3/UserNotify/notifyList"

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
        /** Q2：relation_status（1=已关注；缺失=null=未知→关注钮不渲染，不猜） */
        val relationStatus: Int? = null,
        /** A5-2：一次性 setuserlogin 桥 URL（getUserInfo 响应内嵌；null=响应未携带） */
        val webSessionUrl: String? = null,
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
        /** R3：对方 uid（userMessageList 参数）；<=0=合成/未知（真链分支不触发） */
        val uid: Long = 0L,
    )

    /** 解析 getUserInfo 明文 dict → UserProfile（纯函数，JVM 可测） */
    fun parseUserInfo(json: String): UserProfile? {
        return try {
            val obj = JSONObject(json)
            val d = obj.optJSONObject("data") ?: return null
            val uid = d.optLong("user_id", 0L)
            val name = d.optString("name")
            if (uid <= 0 || name.isBlank()) return null
            // A5-2：桥 URL 形态=服务器动态下发（server_topology §三：一次性
            // bbs.../magmobileapi.php?mag_fuc=setuserlogin&uid&t&k；jadx 无此常量）——
            // 对原始体做宽容正则提取，缺失=诚实 null（不构造、不猜测）
            val webSessionUrl = Regex(
                "https?://[^\"]*mag_fuc=setuserlogin[^\"]*",
            ).find(json)?.value?.takeIf { it.isNotBlank() }
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
                // Q2：relation_status 仅在服务端携带时成立（optInt 缺键会回默认值=猜）
                relationStatus = if (d.has("relation_status")) d.optInt("relation_status") else null,
                webSessionUrl = webSessionUrl,
            )
        } catch (e: Exception) { null }
    }

    /**
     * 解析列表 JSON → PersonItem 列表（纯函数；A28-06 修正 typed 语义）：
     * null=解析失败/服务端拒绝（HTML 网关页、success:false、畸形 JSON）；
     * emptyList=仅服务端确认的空结果（成功信封无 list）。两者必须可区分——
     * 旧实现把失败吞成空列表，鉴权失败被 UI 呈现为"暂无内容"。
     */
    fun parsePeople(json: String): List<PersonItem>? {
        return try {
            val obj = JSONObject(json)
            // 鉴权/权限拒绝信封（success:false）不得伪装成空列表
            if (obj.has("success") && !obj.optBoolean("success")) return null
            // R1/S05：未知信封（无 success 且无 list 键=空对象/畸形）→ null（协议错），
            // 仅「合法成功信封（含 list 键，可为空数组）」产出列表
            val arr = obj.optJSONArray("list") ?: return null
            (0 until arr.length()).mapNotNull { i ->
                val o = arr.optJSONObject(i) ?: return@mapNotNull null
                val uid = o.optLong("user_id", o.optLong("id", 0L))
                val name = o.optString("user_name", o.optString("name"))
                if (uid <= 0) return@mapNotNull null
                PersonItem(userId = uid, name = name,
                    head = o.optString("user_head").takeIf { it.startsWith("http") },
                    extra = o.optString("time").takeIf { it.isNotBlank() })
            }
        } catch (e: Exception) { null }
    }

    /** R1（A2-1）收藏行（userCollectList：id/title/pic/catName/key/link/createTimeStr/name） */
    data class CollectRow(
        val key: String,
        val title: String,
        val link: String?,
        val catName: String?,
        val pic: String?,
        val timeText: String?,
    )

    /**
     * 解析收藏列表 JSON → CollectRow 列表（纯函数；S05 口径：null=失败/拒绝/未知信封，
     * emptyList=合法成功空）。行字段缺失容忍（schema 全 optional）。
     */
    fun parseCollectList(json: String): List<CollectRow>? {
        return try {
            val obj = JSONObject(json)
            if (obj.has("success") && !obj.optBoolean("success")) return null
            val arr = obj.optJSONArray("list") ?: return null  // 未知信封（无 list 键）
            (0 until arr.length()).mapNotNull { i ->
                val o = arr.optJSONObject(i) ?: return@mapNotNull null
                val title = o.optString("title").takeIf { it.isNotBlank() && it != "null" }
                    ?: o.optString("name").takeIf { it.isNotBlank() && it != "null" }
                    ?: return@mapNotNull null
                CollectRow(
                    key = o.optString("id").ifBlank { o.optString("key").ifBlank { title } },
                    title = title,
                    link = o.optString("link").takeIf { it.startsWith("http") },
                    catName = o.optString("catName").takeIf { it.isNotBlank() && it != "null" },
                    pic = o.optString("pic").takeIf { it.startsWith("http") },
                    timeText = o.optString("createTimeStr").takeIf { it.isNotBlank() && it != "null" },
                )
            }
        } catch (e: Exception) { null }
    }

    /** R3/A4：私信历史行（LetterChatBean：mid/uid/name/head/title/content/dateline） */
    data class MessageRow(
        val mid: String,
        val uid: Long,
        val name: String,
        val head: String?,
        val title: String?,
        val content: String,
        val timeText: String?,
    )

    /**
     * 解析私信历史 JSON（S05 口径：null=失败/拒绝/未知信封）。行字段缺失容忍。
     * ⚠️ read-may-mutate：读取会话历史可能标记已读（契约披露；真探=不做，合成关闭）。
     */
    fun parseMessageList(json: String): List<MessageRow>? {
        return try {
            val obj = JSONObject(json)
            if (obj.has("success") && !obj.optBoolean("success")) return null
            val arr = obj.optJSONArray("list") ?: return null
            (0 until arr.length()).mapNotNull { i ->
                val o = arr.optJSONObject(i) ?: return@mapNotNull null
                val content = o.optString("content")
                if (content.isBlank()) return@mapNotNull null
                MessageRow(
                    mid = o.optString("mid").ifBlank { o.optString("id") },
                    uid = o.optLong("uid", 0L),
                    name = o.optString("name").takeIf { it.isNotBlank() && it != "null" } ?: "匿名",
                    head = o.optString("head").takeIf { it.startsWith("http") },
                    title = o.optString("title").takeIf { it.isNotBlank() && it != "null" },
                    content = content,
                    timeText = o.optString("dateline").takeIf { it.isNotBlank() && it != "null" },
                )
            }
        } catch (e: Exception) { null }
    }

    /**
     * 私信会话历史（userMessageList，GET uid+page；token 必需）——null=传输/解析失败。
     * ⚠️ read-may-mutate（可能标已读）：真探被用户令驳回，本方法保留生产接线、不安排调用。
     */
    suspend fun fetchMessageList(token: String, peerUid: Long, page: Int): PrivateResult<List<MessageRow>> =
        withContext(Dispatchers.IO) {
            if (token.isBlank() || peerUid <= 0) return@withContext PrivateResult.Failed
            val body = get("$USER_MESSAGE_LIST?uid=$peerUid&p=$page", token)
                ?: return@withContext PrivateResult.Failed
            if (isAuthRejected(body)) return@withContext PrivateResult.AuthExpired
            val rows = parseMessageList(body) ?: return@withContext PrivateResult.Failed
            PrivateResult.Ok(rows)
        }

    /** R3/A4-4：通知历史行（SystemNotifyDbBean 子集） */
    data class NotifyRow(
        val id: String,
        val title: String,
        val content: String,
        val time: String?,
        val link: String?,
    )

    /** 解析通知历史（S05 口径）；行字段全 optional 容忍 */
    fun parseNotifyList(json: String): List<NotifyRow>? {
        return try {
            val obj = JSONObject(json)
            if (obj.has("success") && !obj.optBoolean("success")) return null
            val arr = obj.optJSONArray("list") ?: return null
            (0 until arr.length()).mapNotNull { i ->
                val o = arr.optJSONObject(i) ?: return@mapNotNull null
                val title = o.optString("title").takeIf { it.isNotBlank() && it != "null" }
                    ?: return@mapNotNull null
                NotifyRow(
                    id = o.optString("id").ifBlank { o.optString("notifyId").ifBlank { title } },
                    title = title,
                    content = o.optString("content"),
                    time = o.optString("time").takeIf { it.isNotBlank() && it != "null" },
                    link = o.optString("link").takeIf { it.startsWith("http") },
                )
            }
        } catch (e: Exception) { null }
    }

    /**
     * 通知历史（notifyList v3，POST query p 分页；token 必需）——null=传输/解析失败。
     * ⚠️ read-may-mutate（列表读取可能触发已读上报，契约披露）：真探不做，接线保留。
     * WP6/S08：**分类 code 契约门控**——扩展 soak 实证（2026-11-03 messages.tsx）无 code=
     * 「通知编码不可为空」code0 拒；本客户端未知分类码→零请求（不发必败读）。
     * code=null=受限态（调用方呈说明）；已知分类码后按 code 请求。
     */
    suspend fun fetchNotifyList(token: String, page: Int, code: String? = null): PrivateResult<List<NotifyRow>> =
        withContext(Dispatchers.IO) {
            if (token.isBlank()) return@withContext PrivateResult.Failed
            // WP6/S08：未知分类码=零请求（不发必败读伪装成故障）
            if (code.isNullOrBlank()) return@withContext PrivateResult.Failed
            try {
                val conn = URL("$NOTIFY_LIST_V3?p=$page&code=${java.net.URLEncoder.encode(code, "UTF-8")}")
                    .openConnection() as HttpURLConnection
                conn.requestMethod = "POST"
                conn.connectTimeout = 12000
                conn.readTimeout = 15000
                conn.setRequestProperty("User-Agent", magappxUa(token))
                val body = try {
                    conn.inputStream.bufferedReader().readText()
                } finally { conn.disconnect() }
                if (isAuthRejected(body)) return@withContext PrivateResult.AuthExpired
                val rows = parseNotifyList(body) ?: return@withContext PrivateResult.Failed
                PrivateResult.Ok(rows)
            } catch (e: Exception) { PrivateResult.Failed }
        }

    /** 收藏列表（userCollectList，GET p 分页；token 必需）——null=传输/解析失败 */
    suspend fun fetchCollectList(token: String, page: Int): PrivateResult<List<CollectRow>> =
        withContext(Dispatchers.IO) {
            if (token.isBlank()) return@withContext PrivateResult.Failed
            val body = get("$MY_COLLECT_LIST?p=$page", token)
                ?: return@withContext PrivateResult.Failed
            if (isAuthRejected(body)) return@withContext PrivateResult.AuthExpired
            val rows = parseCollectList(body) ?: return@withContext PrivateResult.Failed
            PrivateResult.Ok(rows)
        }


    fun parseMessageGroups(json: String): List<MessageGroup>? {
        return try {
            val obj = JSONObject(json)
            if (obj.has("success") && !obj.optBoolean("success")) return null
            val arr = obj.optJSONArray("list") ?: return null
            (0 until arr.length()).mapNotNull { i ->
                val o = arr.optJSONObject(i) ?: return@mapNotNull null
                MessageGroup(
                    id = o.optString("id"),
                    name = o.optString("name") ?: "",
                    count = o.optInt("count", 0),
                    // R3：组行 uid（官 APK LetterChatActivity 入参形态；缺失=0 不可进真链）
                    uid = o.optLong("uid", o.optLong("to_user_id", 0L)),
                )
            }
        } catch (e: Exception) { null }
    }

    private fun get(url: String, token: String): String? {
        return try {
            val conn = URL(url).openConnection() as HttpURLConnection
            conn.connectTimeout = 12000
            conn.readTimeout = 15000
            conn.setRequestProperty("User-Agent", magappxUa(token))
            try {
                conn.inputStream.bufferedReader().readText()
            } finally { conn.disconnect() }
        } catch (e: Exception) { null }
    }

    private fun magappxUa(token: String): String =
        "Mozilla/5.0 (Linux; Android 16; Pixel 8) MAGAPPX|6.4.0-2.90-30042|Android 16 google Pixel 8|shuidan|koPx4DY3tw7hrg5nKpSmc3|88af41c4c24832860d97d44e436f8842|174e1e66702c31aa7e949f5ab30db60b|$token"

    /** A70-05：官方 getUserInfo 走 POST form（nextclient auth.ts 已验证路径）；GET 无 id 不可用 */
    private fun postForm(url: String, token: String, form: String): String? {
        return try {
            val conn = URL(url).openConnection() as HttpURLConnection
            conn.requestMethod = "POST"
            conn.doOutput = true
            conn.connectTimeout = 12000
            conn.readTimeout = 15000
            conn.setRequestProperty("User-Agent", magappxUa(token))
            conn.setRequestProperty("Content-Type", "application/x-www-form-urlencoded")
            try {
                conn.outputStream.use { it.write(form.toByteArray(Charsets.UTF_8)) }
                conn.inputStream.bufferedReader().readText()
            } finally { conn.disconnect() }
        } catch (e: Exception) { null }
    }

    /**
     * 本人/他人资料（A70-05）：POST {id: userId}——userId 来自登录响应（SessionStore 持久化）。
     * userId<=0（旧会话残留无 id）→ Failed，UI 呈失败并引导重登，不冒充空资料。
     */
    suspend fun fetchMyInfo(token: String, userId: Long): PrivateResult<UserProfile> =
        withContext(Dispatchers.IO) {
            if (userId <= 0L) return@withContext PrivateResult.Failed
            val body = postForm(MY_INFO, token, "id=$userId")
                ?: return@withContext PrivateResult.Failed
            if (isAuthRejected(body)) return@withContext PrivateResult.AuthExpired
            val p = parseUserInfo(body) ?: return@withContext PrivateResult.Failed
            PrivateResult.Ok(p)
        }

    suspend fun fetchVisitors(token: String): PrivateResult<List<PersonItem>> =
        withContext(Dispatchers.IO) {
            val body = get(MY_VISITOR, token) ?: return@withContext PrivateResult.Failed
            if (isAuthRejected(body)) return@withContext PrivateResult.AuthExpired
            val rows = parsePeople(body) ?: return@withContext PrivateResult.Failed
            PrivateResult.Ok(rows)
        }

    suspend fun fetchViewUsers(token: String): PrivateResult<List<PersonItem>> =
        withContext(Dispatchers.IO) {
            val body = get(MY_VIEW_USER, token) ?: return@withContext PrivateResult.Failed
            if (isAuthRejected(body)) return@withContext PrivateResult.AuthExpired
            val rows = parsePeople(body) ?: return@withContext PrivateResult.Failed
            PrivateResult.Ok(rows)
        }

    suspend fun fetchFollows(token: String): PrivateResult<List<PersonItem>> =
        withContext(Dispatchers.IO) {
            val body = get(MY_FOLLOW, token) ?: return@withContext PrivateResult.Failed
            if (isAuthRejected(body)) return@withContext PrivateResult.AuthExpired
            val rows = parsePeople(body) ?: return@withContext PrivateResult.Failed
            PrivateResult.Ok(rows)
        }

    suspend fun fetchMessageGroups(token: String): PrivateResult<List<MessageGroup>> =
        withContext(Dispatchers.IO) {
            val body = get(MESSAGE_GROUP, token) ?: return@withContext PrivateResult.Failed
            if (isAuthRejected(body)) return@withContext PrivateResult.AuthExpired
            val rows = parseMessageGroups(body) ?: return@withContext PrivateResult.Failed
            PrivateResult.Ok(rows)
        }

    // ── Q5（A1-10）：用户搜索（userSearch GET name，DataPage 族 singlePage；
    // jadx SearchPeopleActivity param name——旧「用户搜索缺口」翻案：端点在册可用）──
    const val USER_SEARCH = "https://mag1.sdgun.net/mag/user/v1/User/userSearch"

    suspend fun searchUsers(token: String, name: String): List<PersonItem>? =
        withContext(Dispatchers.IO) {
            if (name.isBlank()) return@withContext emptyList<PersonItem>()
            try {
                val url = "$USER_SEARCH?name=" +
                    java.net.URLEncoder.encode(name, "UTF-8")
                val conn = URL(url).openConnection() as HttpURLConnection
                conn.connectTimeout = 12000
                conn.readTimeout = 15000
                conn.setRequestProperty("User-Agent", magappxUa(token))
                try {
                    parsePeople(conn.inputStream.bufferedReader().readText())
                } finally { conn.disconnect() }
            } catch (e: Exception) { null }
        }
}
