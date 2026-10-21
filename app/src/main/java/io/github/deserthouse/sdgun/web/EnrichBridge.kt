package io.github.deserthouse.sdgun.web

import android.os.Handler
import io.github.deserthouse.sdgun.data.repository.ForumRepository
import io.github.deserthouse.sdgun.data.repository.ThreadSummary
import android.os.Looper
import android.webkit.JavascriptInterface
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
    private val state: WebState,
    private val evaluateOnUi: (String) -> Unit,
) {

    // lazy：JVM 单测构造实例时不触碰 android.os.Looper stub
    private val main: Handler by lazy { Handler(Looper.getMainLooper()) }
    private val pool: java.util.concurrent.ExecutorService by lazy { Executors.newSingleThreadExecutor() }

    /** 同 fid 在途去重（AV-08）：首个请求是持有者，后到的挂回调队列复用同一份结果 */
    private val inFlight = HashMap<Long, MutableList<(Map<Long, ThreadSummary>) -> Unit>>()

    /** SV-2：Activity 销毁时释放线程池（回调侧有 destroyed/页面校验兜底，仅止资源泄漏） */
    fun dispose() {
        synchronized(inFlight) { inFlight.clear() }
        pool.shutdown()
    }

    @JavascriptInterface
    fun enrich(payload: String?, themeEnabled: Boolean, pageUrl: String?) {
        val req = parseRequest(payload) ?: return
        val page = pageUrl ?: return
        // AV-08：themeEnabled 是 JS 传入的不可信快照（注入侧传字面 true），不作判据；
        // 权威状态=原生 WebState。入队捕获导航代次，回写前全量校验：
        // 销毁 / 代次已变（页面切换或重载）/ 主题已关 / 页面 URL 已变，一律丢弃旧结果。
        val gen = state.navGeneration
        val deliver: (Map<Long, ThreadSummary>) -> Unit = deliver@{ matched ->
            if (matched.isEmpty()) return@deliver
            val payloadOut = buildEnrichJson(matched)
            main.post {
                if (state.destroyed || state.navGeneration != gen ||
                    !state.webThemeEnabled || state.currentUrl != page
                ) return@post
                evaluateOnUi("if (window.SDG_ENRICH_APPLY) SDG_ENRICH_APPLY($payloadOut)")
            }
        }
        val isLeader = synchronized(inFlight) {
            val queue = inFlight[req.fid]
            if (queue == null) {
                inFlight[req.fid] = mutableListOf(deliver)
                true
            } else {
                queue.add(deliver)
                false
            }
        }
        if (!isLeader) return
        pool.execute {
            val result = runCatching { loadMatches(req) }.getOrDefault(emptyMap())
            val waiters = synchronized(inFlight) { inFlight.remove(req.fid) } ?: emptyList()
            waiters.forEach { it(result) }
        }
    }

    /** AR-06：全命中零请求；部分命中从服务端下一页续拉；失败/空结果不写缓存时间戳 */
    private fun loadMatches(req: EnrichRequest): Map<Long, ThreadSummary> {
        val cached = ForumRepository.cached(req.fid)
        val allHit = cached != null && req.tids.all { cached.containsKey(it) }
        val merged = HashMap(cached ?: emptyMap())
        if (!allHit) {
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
            if (merged.isNotEmpty()) ForumRepository.putCache(req.fid, merged)
        }
        return req.tids.mapNotNull { t -> merged[t]?.let { t to it } }.toMap()
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