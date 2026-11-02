package io.github.deserthouse.sdgun.core.mutation

import io.github.deserthouse.sdgun.core.model.MutationResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

/**
 * 写操作种类（G4，端点=tests/probe_out_login/write_catalog_static.json 静态编目）。
 */
enum class MutationKind(val url: String) {
    /** 回帖：POST 表单 tid/reply_id/circle_id/content/video/auth/users */
    REPLY("https://mag1.sdgun.net/mag/circle/v1/Forum/replyAdd"),

    /** 点赞：POST 表单 content_id（楼层 pid 或主楼 pid） */
    APPLAUD("https://mag1.sdgun.net/mag/circle/v1/Forum/contentAddApplaud"),

    /** 收藏：POST 表单 title/url/content_type/id/content_id（参数序=静态编目；真实字段验收=用户 MANUAL） */
    COLLECT("https://mag1.sdgun.net/mag/user/v1/User/userCollectAdd"),

    /**
     * 发帖：POST 表单 title/content/fid/circle_id（jadx ThreadPostActivity.toPost 同构）；
     * 编辑态另带 tid+modthreadkey（→editContent 端点，本版不做编辑 UI——modthreadkey 取值链未实测）；
     * auth=同盾参数未复刻（B2 先例：不假称可用），真实提交验收=用户 MANUAL。
     */
    THREAD_POST("https://mag1.sdgun.net/mag/circle/v1/Forum/threadAdd"),

    /**
     * 投票（R2/P1 新口径：合成关闭即关闭）：jadx VoteListDataView 实证
     * GET addVote?content_id&option_id（非 POST）——HttpGateway 需 GET 分支；
     * option_id 来源=vote 列表解析（选项数据源=content 域，端点形态待真帖样本）。
     */
    VOTE("https://mag1.sdgun.net/mag/circle/v1/show/addVote"),

    /**
     * Q2 取消赞（toggle 后半）：POST 表单 content_id。
     * jadx FroumDataView.addApplaud 实锤：isApplaud ? contentCancelApplaud : contentAddApplaud，
     * toggle 语义（同一按钮按当前态切换端点），content_id=楼层 pid（与 Add 同键）。
     */
    APPLAUD_CANCEL("https://mag1.sdgun.net/mag/circle/v1/Forum/contentCancelApplaud"),

    /**
     * Q2 取消收藏：GET query id（=收藏记录 id，userCollectList 行 id 键）。
     * jadx OperationHelper.collectDelete 实锤：url.param("id").get(task)。
     */
    COLLECT_DELETE("https://mag1.sdgun.net/mag/circle/v1/collect/collectDelete"),

    /**
     * Q2 关注：GET query user_id（jadx UserHomeFragment.attentionToNet：
     * SocializeConstants.TENCENT_UID="user_id"——扩展侧旧记"uid"不精确，以 jadx 为准）。
     */
    FOLLOW_ADD("https://mag1.sdgun.net/mag/user/v1/UserRelation/fansAdd"),

    /**
     * Q2 取消关注：GET query user_id + remove_his（0=保留关系记录 / 1=连历史一并移除；
     * 官方 UI 默认 0——jadx UserHomeFragment :947）。
     */
    FOLLOW_REMOVE("https://mag1.sdgun.net/mag/user/v1/UserRelation/removeRelation"),

    /**
     * Q2 私信发送：POST 表单 uid + content。
     * jadx LetterChatActivity.sendMessage 实锤（成功后 500ms 刷新列表——本端也走重拉）。
     */
    MESSAGE_SEND("https://mag1.sdgun.net/mag/user/v1/UserNotify/userMessageSendMsg"),

    // ── 编目区（端点形态已勘定、功能未接线）──
    // 编辑帖：POST Forum/editContent {tid, modthreadkey, title, content, fid, circle_id[, users, vest_id,
    //   classified_id, type_id]}（jadx ThreadPostActivity.toPost 编辑分支同构）；
    // 编辑详情回填：GET Forum/editThreadDetail {content_id, modthreadkey}。
    // **modthreadkey 官方 App 内不可达**（仅 UrlScheme 外部注入 EditThreadPost intent；threadView 40
    // 字段无此键、全 App 无赋值点）→ 编辑 UI 无键可用，如实挂起（2026-11-02 Q2 勘定）。
    // 删除帖：API.java 无 delThread/delComment 端点（F 域编辑删除族=编目缺口维持）。
    ;

    /** 请求方法默认值单一事实源（Q2：GET 类端点收敛至此，MutationRequest 默认取本值） */
    val defaultMethod: String
        get() = when (this) {
            VOTE, COLLECT_DELETE, FOLLOW_ADD, FOLLOW_REMOVE -> "GET"
            else -> "POST"
        }
}

/**
 * 一次写操作的请求（表单参数键值对；值经 URLEncoder 编码）。
 */
data class MutationRequest(
    val kind: MutationKind,
    val params: Map<String, String>,
    val token: String,
    /** 方法默认取 kind.defaultMethod（Q2 单一事实源；历史显式传参仍被尊重） */
    val method: String = kind.defaultMethod,
)

/** 传输层原始应答（A24-01：状态与体分离交付，判定与传输解耦可测）。 */
data class MutationRawResponse(val httpCode: Int, val body: String?, val transportError: Exception? = null)

/**
 * 传输层抽象：真实实现=HttpURLConnection；测试注入合成应答（零触网）。
 * 注意：默认 get() 借 post 通道仅为编译兼容——**生产传输必须覆写 get() 为真 GET**
 * （Q2 前的默认实现把 GET 变体发成空体 POST，VOTE 未真探故未暴露；Q2 引入三个
 * 真 GET 端点[取消收藏/关注/取关]后按实线语义修正）。
 */
fun interface MutationTransport {
    fun post(url: String, form: String, userAgent: String): MutationRawResponse

    /** R2/Q2：GET 变体（VOTE/取消收藏/关注族；query 串由调用方拼好） */
    fun get(url: String, userAgent: String): MutationRawResponse = post(url, "", userAgent)
}

/** 写操作判定结果：Confirmed 仅出自已验证的成功信封；未知形态一律 Unknown（保稿，A24-01）。 */
sealed class MutationVerdict {
    /** payload=原始响应体（Q3：THREAD_POST 成功定位用） */
    data class Confirmed(val detail: String, val payload: String? = null) : MutationVerdict()
    data class Rejected(val code: Int, val msg: String) : MutationVerdict()
    object Unknown : MutationVerdict()
}

/**
 * 响应判定（A24-01 / A28-02 修正）：先按 HTTP 状态分流，再做严格类型信封验证；
 * **无 success 字段/非 JSON/空体/HTML 网关页/success 非布尔类型 = Unknown**——绝不因
 * "2xx 且没看到拒绝"而判成功；**非 2xx 一律不得 Confirmed**（403/500 即使携带
 * success:true 载荷也是服务端矛盾形态，按传输层拒绝处理）。
 * 信封模式依据：全站读端点统一 `{success:bool, code, msg}`（circleTree/forumView 等实测）；
 * replyAdd 真实成功形态无权威文档，未知即保守，MANUAL 回传报文后收紧判据（只加规则不改行为）。
 */
object MutationResponseAdapter {

    fun judge(httpCode: Int, body: String?): MutationVerdict {
        if (httpCode !in 200..299) {
            // A28-02：非成功状态码绝不 Confirmed；body 若携带业务拒绝信封则透出其 msg
            val obj = body?.let { b -> runCatching { org.json.JSONObject(b) }.getOrNull() }
            val msg = obj?.optString("msg")?.takeIf { it.isNotBlank() }
            return MutationVerdict.Rejected(httpCode, msg ?: "HTTP $httpCode")
        }
        val obj = body?.let { b ->
            runCatching { org.json.JSONObject(b) }.getOrNull()
        } // HTML/空体/截断 → null
        if (obj == null || !obj.has("success")) return MutationVerdict.Unknown
        // A28-02：严格布尔类型——org.json.optBoolean 会把字符串 "true" parseBoolean，
        // 服务端若返回字符串形态属未知信封，保守 Unknown（保稿）
        val successRaw = obj.opt("success")
        if (successRaw !is Boolean) return MutationVerdict.Unknown
        val msg = obj.optString("msg").takeIf { it.isNotBlank() }
        return when (successRaw) {
            true -> MutationVerdict.Confirmed(msg ?: "已受理", body)
            false -> MutationVerdict.Rejected(obj.optInt("code", httpCode), msg ?: "服务端拒绝")
        }
    }
}

/**
 * 写操作传输网关（G4 契约落地）：
 * - [HttpMutationGateway] 真端点（正式；可注入传输层供 JVM 反例）
 * - [FakeMutationGateway] 离线假服务（debug 装机验证：Confirmed/Rejected/超时三分支脚本化，
 *   绝不触网——真实写入=用户本人 MANUAL，红线）
 */
interface MutationGateway {
    suspend fun execute(request: MutationRequest): MutationResult
}

/** 真端点网关：POST 表单 + MAGAPPX UA（token 第 8 段，与读面同源）。 */
class HttpMutationGateway(private val transport: MutationTransport = HttpURLConnectionTransport()) :
    MutationGateway {

    override suspend fun execute(request: MutationRequest): MutationResult =
        withContext(Dispatchers.IO) {
            val body = request.params.entries.joinToString("&") { (k, v) ->
                "${URLEncoder.encode(k, "UTF-8")}=${URLEncoder.encode(v, "UTF-8")}"
            }
            val raw = try {
                if (request.method == "GET") {
                    val qs = request.params.entries.joinToString("&") { (k, v) ->
                        "${URLEncoder.encode(k, "UTF-8")}=${URLEncoder.encode(v, "UTF-8")}"
                    }
                    transport.get(request.kind.url + "?" + qs, magappxUa(request.token))
                } else {
                    transport.post(request.kind.url, body, magappxUa(request.token))
                }
            } catch (e: Exception) {
                MutationRawResponse(0, null, e)
            }
            when (raw.transportError) {
                null -> when (val v = MutationResponseAdapter.judge(raw.httpCode, raw.body)) {
                    is MutationVerdict.Confirmed -> MutationResult.Confirmed(v.detail, v.payload)
                    is MutationVerdict.Rejected -> MutationResult.Rejected(v.code, v.msg)
                    MutationVerdict.Unknown -> MutationResult.OutcomeUnknown
                }
                else -> MutationResult.OutcomeUnknown
            }
        }

    private fun magappxUa(token: String): String =
        "Mozilla/5.0 (Linux; Android 16; Pixel 8) MAGAPPX|6.4.0-2.90-30042|Android 16 google Pixel 8|shuidan|koPx4DY3tw7hrg5nKpSmc3|88af41c4c24832860d97d44e436f8842|174e1e66702c31aa7e949f5ab30db60b|$token"
}

/** 真实传输：HttpURLConnection（A24-01 修正：先按状态码选流，再解析）。 */
class HttpURLConnectionTransport : MutationTransport {

    override fun post(url: String, form: String, userAgent: String): MutationRawResponse {
        var code = 0
        return try {
            val conn = URL(url).openConnection() as HttpURLConnection
            conn.requestMethod = "POST"
            conn.doOutput = true
            conn.connectTimeout = 10000
            conn.readTimeout = 12000
            conn.setRequestProperty("User-Agent", userAgent)
            conn.setRequestProperty("Content-Type", "application/x-www-form-urlencoded")
            try {
                conn.outputStream.use { it.write(form.toByteArray(Charsets.UTF_8)) }
                code = conn.responseCode
                // A24-01：4xx/5xx 的可读体在 errorStream——先按状态选流再读
                val stream = if (code in 200..299) conn.inputStream else conn.errorStream
                val text = stream?.bufferedReader()?.readText()
                MutationRawResponse(code, text, null)
            } finally {
                conn.disconnect()
            }
        } catch (e: Exception) {
            MutationRawResponse(0, null, e)
        }
    }

    /** Q2：真 GET（取消收藏/关注族/VOTE）——不再借 POST 通道发空体。 */
    override fun get(url: String, userAgent: String): MutationRawResponse {
        var code = 0
        return try {
            val conn = URL(url).openConnection() as HttpURLConnection
            conn.requestMethod = "GET"
            conn.connectTimeout = 10000
            conn.readTimeout = 12000
            conn.setRequestProperty("User-Agent", userAgent)
            try {
                code = conn.responseCode
                val stream = if (code in 200..299) conn.inputStream else conn.errorStream
                val text = stream?.bufferedReader()?.readText()
                MutationRawResponse(code, text, null)
            } finally {
                conn.disconnect()
            }
        } catch (e: Exception) {
            MutationRawResponse(0, null, e)
        }
    }
}

/** 离线假服务（debug 装机验证）：分支脚本化，零网络。 */
class FakeMutationGateway(private val branch: String) : MutationGateway {

    override suspend fun execute(request: MutationRequest): MutationResult =
        withContext(Dispatchers.IO) {
            Thread.sleep(400) // 模拟 RTT，让 Submitting 态可见
            when (branch) {
                "reject" -> MutationResult.Rejected(403, "假服务：权限不足（模拟）")
                "timeout" -> MutationResult.OutcomeUnknown
                else -> MutationResult.Confirmed("假服务受理：${request.kind.name}")
            }
        }
}
