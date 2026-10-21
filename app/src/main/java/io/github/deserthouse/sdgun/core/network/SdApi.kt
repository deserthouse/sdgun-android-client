package io.github.deserthouse.sdgun.core.network
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.security.KeyFactory
import java.security.MessageDigest
import java.security.spec.X509EncodedKeySpec
import javax.crypto.Cipher

/**
 * magapp 协议客户端核心（R-A1，2026-10-21 测绘定案见 docs/api_survey_2026-10-21.md）。
 *
 * 认证模型（jadx 源码级复现）：
 *  - token 携带 = MAGAPPX UA 串第 8 段（不进参数、不进独立头）；无 token=7 段。
 *  - sig1=md5(app_code+"magapp"+utdid+app_sign_secret)、sig2=md5(...+"nanjinglinyan")
 *    （服务端宽松不校验，仍按源码忠实构造）。
 *  - mag-ss-key = Base64(RSA_PKCS1v15("{app_code},{unix秒}"))，每请求现算。
 *  - 登录密码 = Base64(RSA_PKCS1v15(明文))。加密链已假凭据探针验证（服务端正常解密比对）。
 *  - code=1001 token 失效（调用方清会话）；1009 图形验证码（登录 UI 弹 security_code 输入）。
 *
 * 纯函数全部 JVM 可测；网络函数挂起、IO 线程执行。
 */
object SdApi {

    // jadx 硬编码常量（net.duohuo.magappx.API / MagRsaEncryptUtil / res strings）
    const val APP_CODE = "shuidan"
    const val APP_SIGN_SECRET = "nanjingxinxi"
    const val MAG_VERSION = "Android-6.4.0-2.90-30042"
    private const val SIG2_SALT = "nanjinglinyan"
    private val RSA_PUBKEY_B64 = ("MIGfMA0GCSqGSIb3DQEBAQUAA4GNADCBiQKBgQC7qPCn9w81CW7I9dbc+0YReFNEkiP/"
        + "x7nTs5lAw+hLXmLsxNZtE/2kFN2XK3jcFOw6TSq/EbGhC9CD51q6n1B4AyfId+aGgB52ejR"
        + "UrxvK+JOEWJrzXCbIOZg0QLfai+tFkmTi5jK91mQpX2di6xhiMk18gwnBmmNQQrSlEr1YgwIDAQAB")

    const val LOGIN_URL = "https://mag1.sdgun.net/mag/user/v1/user/login"
    private const val WEB_UA = ("Mozilla/5.0 (Linux; Android 16; Pixel 8) AppleWebKit/537.36 "
        + "(KHTML, like Gecko) Chrome/130.0.0.0 Mobile Safari/537.36")

    // ---- 纯函数（JVM 测试覆盖） ----

    fun md5Hex(s: String): String =
        MessageDigest.getInstance("MD5").digest(s.toByteArray(Charsets.UTF_8))
            .joinToString("") { "%02x".format(it) }

    fun sig1(utdid: String) = md5Hex("$APP_CODE" + "magapp" + "$utdid" + APP_SIGN_SECRET)
    fun sig2(utdid: String) = md5Hex("$APP_CODE" + "magapp" + "$utdid" + SIG2_SALT)

    /** MAGAPPX UA 串：有 token 8 段 / 无 token 7 段（token 永远是最后一段） */
    fun magappxUa(utdid: String, token: String?): String {
        val segs = mutableListOf(
            "MAGAPPX", "6.4.0-2.90-30042", "Android 16 google Pixel 8",
            APP_CODE, utdid, sig1(utdid), sig2(utdid),
        )
        if (!token.isNullOrBlank()) segs.add(token)
        return segs.joinToString("|")
    }

    fun userAgent(utdid: String, token: String?): String = "$WEB_UA ${magappxUa(utdid, token)}"

    private fun rsaDoFinal(plain: ByteArray): ByteArray {
        val key = KeyFactory.getInstance("RSA")
            .generatePublic(X509EncodedKeySpec(java.util.Base64.getDecoder().decode(RSA_PUBKEY_B64)))
        val cipher = Cipher.getInstance("RSA/ECB/PKCS1Padding")
        cipher.init(Cipher.ENCRYPT_MODE, key)
        return cipher.doFinal(plain)
    }

    /**
     * 登录密码密文（与官方 App 逐字节同构）。
     * ★2026-10-21 官方抓包定案：MagRsaEncryptUtil 用 android.util.Base64.DEFAULT——
     * 密文 base64 每 76 字符换行、结尾补 \n；无换行版本服务端解密失败并回
     * "账号或密码错误"（真账号实测根因，非账号体系问题）。纯函数复刻 DEFAULT 语义。
     */
    fun encryptPassword(plain: String): String {
        val b64 = java.util.Base64.getEncoder().encodeToString(
            rsaDoFinal(plain.toByteArray(Charsets.UTF_8)),
        )
        return b64.chunked(76).joinToString("\n", postfix = "\n")
    }

    /** mag-ss-key 请求头值：Base64(RSA_PKCS1v15("{app_code},{unix秒}"))——官方为无换行单行 */
    fun magSsKey(nowSec: Long = System.currentTimeMillis() / 1000): String =
        java.util.Base64.getEncoder().encodeToString(
            rsaDoFinal("$APP_CODE,$nowSec".toByteArray(Charsets.UTF_8)),
        )

    /** utdid 首次生成（22 位字母数字，客户端本地身份；服务端不校验真实性） */
    fun newUtdid(seed: Long): String {
        val alphabet = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789"
        var x = seed xor -0x61c8864680b583ebL // Knuth 乘法逆打散
        return buildString {
            repeat(22) {
                append(alphabet[((x ushr 16) and 0x3F).toInt().coerceIn(0, alphabet.length - 1)])
                x = x * 6364136223846793005L + 1442695040888963407L
            }
        }
    }

    // ---- 网络层 ----

    class ApiResponse(val success: Boolean, val code: Int, val msg: String?, val raw: JSONObject?)

    /**
     * AVD 调试环境代理：guest 的 settings global http_proxy 只对浏览器/OkHttp 生效，
     * HttpURLConnection 直连走 qemu slirip——本机 qemu 对外 socket 随机损坏（协议笔记
     * 红线，relay_proxy 存在的原因），登录等 POST 会被截断成通用错误。读系统代理设置
     * 显式走它；真机无 global proxy → 返回 null 走直连（真机不受影响）。
     */
    /** 宿主 Application context（MainActivity onCreate 注入，读系统代理设置用） */
    @Volatile
    internal var appContext: android.content.Context? = null

    private fun avdProxy(): java.net.Proxy? = runCatching {
        val resolver = appContext?.contentResolver ?: return null
        val host = android.provider.Settings.Global.getString(resolver, "http_proxy") ?: return null
        if (host.isBlank()) return null
        val (h, p) = host.split(":").let { it[0] to (it.getOrNull(1)?.toIntOrNull() ?: 80) }
        java.net.Proxy(java.net.Proxy.Type.HTTP, java.net.InetSocketAddress(h, p))
    }.getOrNull()

    private fun request(
        url: String,
        method: String,
        form: Map<String, String>? = null,
        utdid: String,
        token: String?,
    ): ApiResponse = withApiHeaders(utdid, token) { ua, ssKey ->
        val conn = (URL(url).openConnection(avdProxy()) as HttpURLConnection).apply {
            requestMethod = method
            connectTimeout = 12000
            readTimeout = 15000
            setRequestProperty("User-Agent", ua)
            setRequestProperty("mag-version", MAG_VERSION)
            setRequestProperty("pt", "1")
            setRequestProperty("mag-ss-key", ssKey)
            if (form != null) {
                doOutput = true
                setRequestProperty("Content-Type", "application/x-www-form-urlencoded")
                // R-A1 真凭据定案：form 必须完整 URL 编码（URLEncoder：空格/换行/+/=
                // 全部转义）——此前手工拼接只替换空格，中文账号与密文裸发即废
                val encoded = form.entries.joinToString("&") {
                    java.net.URLEncoder.encode(it.key, "UTF-8") + "=" +
                        java.net.URLEncoder.encode(it.value, "UTF-8")
                }
                outputStream.use { it.write(encoded.toByteArray(Charsets.UTF_8)) }
            }
        }
        try {
            val body = conn.inputStream.bufferedReader().readText()
            val obj = runCatching { JSONObject(body) }.getOrNull()
            ApiResponse(
                success = obj?.optBoolean("success") ?: false,
                code = obj?.optInt("code", obj?.opt("code") as? Int ?: 0) ?: 0,
                msg = obj?.optString("msg")?.takeIf { it != "null" },
                raw = obj,
            )
        } finally {
            conn.disconnect()
        }
    }

    private inline fun <T> withApiHeaders(utdid: String, token: String?, block: (ua: String, ssKey: String) -> T): T =
        block(userAgent(utdid, token), magSsKey())

    /** 登录结果：token / 需要验证码（1009）/ 凭据错误（含限速提示原文） */
    sealed class LoginResult {
        data class Ok(val token: String, val userId: Long, val userName: String?) : LoginResult()
        data class CaptchaRequired(val msg: String?) : LoginResult()
        data class Failed(val msg: String) : LoginResult()
        data class NetworkError(val throwable: Throwable) : LoginResult()
    }

    suspend fun login(
        account: String,
        password: String,
        utdid: String,
        securityCode: String = "",
    ): LoginResult =
        withContext(Dispatchers.IO) {
            try {
                val form = buildMap {
                    put("account", account)
                    put("password", encryptPassword(password))
                    if (securityCode.isNotBlank()) put("security_code", securityCode)
                }
                val r = request(LOGIN_URL, "POST", form = form, utdid = utdid, token = null)
                val raw = r.raw ?: return@withContext LoginResult.Failed(r.msg ?: "响应异常")
                when {
                    r.success -> {
                        val data = raw.optJSONObject("data") ?: raw
                        // token 字段名以实测为准（采集脚本待真账号定名）；主选 "token"，
                        // 兜底扫描长度>=20 的字符串值（登录响应里唯一的超长串即会话凭证）
                        val token = data.optString("token").ifBlank {
                            data.keys().asSequence()
                                .mapNotNull { k -> data.opt(k) as? String }
                                .firstOrNull { it.length >= 20 } ?: ""
                        }
                        if (token.isBlank()) {
                            LoginResult.Failed("登录成功但未解析到 token（data 键：${data.keys().asSequence().take(8).joinToString()}）")
                        } else {
                            LoginResult.Ok(
                                token = token,
                                userId = data.optLong("user_id", data.optLong("uid", 0L)),
                                userName = data.optString("username").takeIf { it.isNotBlank() && it != "null" },
                            )
                        }
                    }
                    r.code == 1009 -> LoginResult.CaptchaRequired(r.msg)
                    else -> LoginResult.Failed(r.msg ?: "登录失败（code=${r.code}）")
                }
            } catch (t: Throwable) {
                LoginResult.NetworkError(t)
            }
        }
}
