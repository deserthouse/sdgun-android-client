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

/**
 * 写操作传输网关（G4 契约的落地基座）：
 * - [HttpMutationGateway] 真端点（正式）
 * - [FakeMutationGateway] 离线假服务（debug 装机验证用：Confirmed/Rejected/超时三分支脚本化，
 *   绝不触网——真实写入=用户本人 MANUAL，红线）
 */
interface MutationGateway {
    suspend fun execute(request: MutationRequest): MutationResult
}

/** 真端点网关：POST 表单 + MAGAPPX UA（token 第 8 段，与读面同源）。 */
class HttpMutationGateway : MutationGateway {

    override suspend fun execute(request: MutationRequest): MutationResult =
        withContext(Dispatchers.IO) {
            try {
                val body = request.params.entries.joinToString("&") { (k, v) ->
                    "${URLEncoder.encode(k, "UTF-8")}=${URLEncoder.encode(v, "UTF-8")}"
                }
                val conn = URL(request.kind.url).openConnection() as HttpURLConnection
                conn.requestMethod = "POST"
                conn.doOutput = true
                conn.connectTimeout = 10000
                conn.readTimeout = 12000
                conn.setRequestProperty("User-Agent", magappxUa(request.token))
                conn.setRequestProperty("Content-Type", "application/x-www-form-urlencoded")
                try {
                    conn.outputStream.use { it.write(body.toByteArray(Charsets.UTF_8)) }
                    val code = conn.responseCode
                    val text = (conn.inputStream ?: conn.errorStream)
                        ?.bufferedReader()?.readText() ?: ""
                    if (code in 200..299) {
                        // 信封无权威文档：HTTP 2xx 视为服务端受理（Confirmed）；失败信封优先解析
                        parseReject(text) ?: MutationResult.Confirmed(text.take(120))
                    } else {
                        parseReject(text) ?: MutationResult.Rejected(code, "HTTP $code")
                    }
                } finally {
                    conn.disconnect()
                }
            } catch (e: Exception) {
                MutationResult.OutcomeUnknown
            }
        }

    /** 服务端明确拒绝信封（success=false+msg）→ Rejected；非该形态返回 null */
    private fun parseReject(text: String): MutationResult.Rejected? = try {
        val obj = org.json.JSONObject(text)
        if (obj.optBoolean("success", true) == false) {
            MutationResult.Rejected(
                obj.optInt("code", 0),
                obj.optString("msg").ifBlank { "服务端拒绝" },
            )
        } else null
    } catch (e: Exception) {
        null
    }

    private fun magappxUa(token: String): String =
        "Mozilla/5.0 (Linux; Android 16; Pixel 8) MAGAPPX|6.4.0-2.90-30042|Android 16 google Pixel 8|shuidan|koPx4DY3tw7hrg5nKpSmc3|88af41c4c24832860d97d44e436f8842|174e1e66702c31aa7e949f5ab30db60b|$token"
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
