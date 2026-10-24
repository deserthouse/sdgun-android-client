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
}

/**
 * 一次写操作的请求（表单参数键值对；值经 URLEncoder 编码）。
 */
data class MutationRequest(
    val kind: MutationKind,
    val params: Map<String, String>,
    val token: String,
)

/** 传输层原始应答（A24-01：状态与体分离交付，判定与传输解耦可测）。 */
data class MutationRawResponse(val httpCode: Int, val body: String?, val transportError: Exception? = null)

/**
 * 传输层抽象：真实实现=HttpURLConnection；测试注入合成应答（零触网）。
 */
fun interface MutationTransport {
    fun post(url: String, form: String, userAgent: String): MutationRawResponse
}

/** 写操作判定结果：Confirmed 仅出自已验证的成功信封；未知形态一律 Unknown（保稿，A24-01）。 */
sealed class MutationVerdict {
    data class Confirmed(val detail: String) : MutationVerdict()
    data class Rejected(val code: Int, val msg: String) : MutationVerdict()
    object Unknown : MutationVerdict()
}

/**
 * 响应判定（A24-01）：先按 HTTP 状态定语义，再做信封验证；
 * **无 success 字段/非 JSON/空体/HTML 网关页 = Unknown**——绝不因"2xx 且没看到拒绝"而判成功。
 * 信封模式依据：全站读端点统一 `{success:bool, code, msg}`（circleTree/forumView 等实测）；
 * replyAdd 真实成功形态无权威文档，未知即保守，MANUAL 回传报文后收紧判据（只加规则不改行为）。
 */
object MutationResponseAdapter {

    fun judge(httpCode: Int, body: String?): MutationVerdict {
        val obj = body?.let { b ->
            runCatching { org.json.JSONObject(b) }.getOrNull()
        } // HTML/空体/截断 → null
        if (obj == null || !obj.has("success")) {
            return if (httpCode in 200..299) MutationVerdict.Unknown
            else MutationVerdict.Rejected(httpCode, "HTTP $httpCode")
        }
        val msg = obj.optString("msg").takeIf { it.isNotBlank() }
        return when (obj.optBoolean("success")) {
            true -> MutationVerdict.Confirmed(msg ?: "已受理")
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
                transport.post(request.kind.url, body, magappxUa(request.token))
            } catch (e: Exception) {
                MutationRawResponse(0, null, e)
            }
            when (raw.transportError) {
                null -> when (val v = MutationResponseAdapter.judge(raw.httpCode, raw.body)) {
                    is MutationVerdict.Confirmed -> MutationResult.Confirmed(v.detail)
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
