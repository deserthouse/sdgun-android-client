package io.github.deserthouse.sdgun.web

import android.os.Handler
import android.os.Looper
import android.webkit.JavascriptInterface
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import org.json.JSONArray

/** 图片查看器数据（AF-03）：帖子页可见图组 + 打开时的起始序号 */
data class GalleryData(val urls: List<String>, val startIndex: Int)

/**
 * WebView 图片点击 → 原生查看器的正式通道（AF-03）。
 * 通过 addJavascriptInterface 暴露为 window.sdgGallery；@JavascriptInterface 方法
 * 运行在 WebView 后台线程，状态更新经主线程 Handler 转发。
 * 安全面：唯一方法只解析图组 JSON（限数量/限长度），无任何凭据/文件/导航能力。
 */
class GalleryBridge(private val state: WebState) {

    private val main = Handler(Looper.getMainLooper())

    @JavascriptInterface
    fun open(payload: String?) {
        val data = parseGalleryPayload(payload) ?: return
        main.post {
            // AV-08：销毁后丢弃迟到的投递（onDestroy 置位 destroyed）
            if (state.destroyed) return@post
            state.gallery = data
        }
    }

    companion object {
        const val MAX_URLS = 300
        const val MAX_URL_LENGTH = 2048

        /** 纯函数：解析注入侧上报的 {"urls":[...], "index":n}；非法输入一律 null（fail-closed） */
        fun parseGalleryPayload(payload: String?): GalleryData? {
            if (payload.isNullOrBlank() || payload.length > MAX_URLS * MAX_URL_LENGTH) return null
            return runCatching {
                val obj = org.json.JSONObject(payload)
                val arr = obj.optJSONArray("urls") ?: return null
                val urls = ArrayList<String>(arr.length().coerceAtMost(MAX_URLS))
                for (i in 0 until arr.length()) {
                    if (urls.size >= MAX_URLS) break
                    val u = arr.optString(i)
                    if (u.startsWith("http") && u.length <= MAX_URL_LENGTH) urls.add(u)
                }
                if (urls.isEmpty()) return null
                val index = obj.optInt("index", 0).coerceIn(0, urls.size - 1)
                GalleryData(urls, index)
            }.getOrNull()
        }
    }
}
