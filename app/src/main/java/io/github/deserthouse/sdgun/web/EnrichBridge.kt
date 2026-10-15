package io.github.deserthouse.sdgun.web

import android.os.Handler
import android.os.Looper
import android.webkit.JavascriptInterface
import io.github.deserthouse.sdgun.data.ForumRepository
import io.github.deserthouse.sdgun.data.ThreadSummary
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.Executors

/**
 * 列表增强通道（AF-02 方案 A）：网页列表行上报 fid+tids，原生拉取 forumView
 * 游客数据后回调注入侧回填（头像/封面/点赞）。
 * 与 GalleryBridge 同纪律：单方法、只解析有界 JSON、无凭据读取、不写 Cookie。
 */
class EnrichBridge(
    private val evaluateOnUi: (String) -> Unit,
) {

    // lazy：JVM 单测构造实例时不触碰 android.os.Looper stub
    private val main: Handler by lazy { Handler(Looper.getMainLooper()) }
    private val pool: java.util.concurrent.ExecutorService by lazy { Executors.newSingleThreadExecutor() }

    @JavascriptInterface
    fun enrich(payload: String?) {
        val req = parseRequest(payload) ?: return
        pool.execute {
            val cached = ForumRepository.cached(req.fid)
            val merged = HashMap(cached ?: emptyMap())
            val startPage = if (cached != null) 2 else 1
            for (url in ForumRepository.pageUrls(req.fid, startPage, ForumRepository.MAX_PAGES_PER_REQUEST)) {
                val body = fetch(url) ?: break
                val (parsed, totalPages) = ForumRepository.parseForumView(body)
                merged.putAll(parsed)
                // 分页终点以服务端 total_page 为准（不按 rows.length 猜）
                val page = Regex("[?&]p=(\\d+)").find(url)?.groupValues?.get(1)?.toIntOrNull() ?: 1
                if (totalPages in 1 until page) break
                if (req.tids.all { merged.containsKey(it) }) break
            }
            ForumRepository.putCache(req.fid, merged)
            val matched = req.tids.mapNotNull { t -> merged[t]?.let { t to it } }.toMap()
            if (matched.isEmpty()) return@execute
            val payloadOut = buildEnrichJson(matched)
            main.post { evaluateOnUi("if (window.SDG_ENRICH_APPLY) SDG_ENRICH_APPLY($payloadOut)") }
        }
    }

    internal fun fetch(url: String): String? = runCatching {
        val conn = URL(url).openConnection() as HttpURLConnection
        conn.connectTimeout = 12000
        conn.readTimeout = 15000
        conn.setRequestProperty(
            "User-Agent",
            "Mozilla/5.0 (Linux; Android 16; Pixel 8) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/130.0.0.0 Mobile Safari/537.36",
        )
        conn.inputStream.use { it.bufferedReader().readText() }
    }.getOrNull()

    internal data class EnrichRequest(val fid: Long, val tids: List<Long>)

    companion object {
        private fun jsonEscape(s: String): String =
            s.replace("\\", "\\\\").replace("\"", "\\\"")

        internal fun parseRequest(payload: String?): EnrichRequest? = runCatching {
            val o = JSONObject(payload ?: return null)
            val fid = o.optLong("fid", 0L)
            if (fid <= 0) return null
            val arr = o.optJSONArray("tids") ?: return null
            val tids = ArrayList<Long>()
            for (i in 0 until arr.length()) {
                if (tids.size >= 60) break
                val t = arr.optLong(i, 0L)
                if (t > 0) tids.add(t)
            }
            if (tids.isEmpty()) return null
            EnrichRequest(fid, tids)
        }.getOrNull()

        /** 输出注入侧可 eval 的 JSON 对象字面量（tid → {a:avatar, c:cover, z:applause}） */
        internal fun buildEnrichJson(map: Map<Long, ThreadSummary>): String {
            val sb = StringBuilder("{\"threads\":{")
            var first = true
            for ((tid, s) in map) {
                if (!first) sb.append(',')
                first = false
                sb.append('"').append(tid).append("\":{")
                sb.append("\"a\":\"").append(jsonEscape(s.avatarUrl ?: "")).append("\",")
                sb.append("\"c\":\"").append(jsonEscape(s.coverUrls.firstOrNull() ?: "")).append("\",")
                sb.append("\"z\":").append(s.applauseCount ?: -1).append('}')
            }
            sb.append("}}")
            return sb.toString()
        }
    }
}
