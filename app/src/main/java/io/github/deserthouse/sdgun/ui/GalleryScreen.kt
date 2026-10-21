package io.github.deserthouse.sdgun.ui

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.os.Handler
import android.os.Looper
import android.util.LruCache
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChanged
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import io.github.deserthouse.sdgun.web.GalleryData
import kotlinx.coroutines.launch
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.Executors
import kotlin.math.roundToInt

/** AV-07：采样纯函数（顶层=JVM 可测，不触碰 object 的 Looper 依赖）。
 *  最长边与总像素双约束，"当前尺寸超标即减半"（目标=解码结果入界）：
 *  1000×30000 一类长图旧条件（宽高同时够大才采样）永不采样，恰是复审反例；
 *  4000×3000 在减半后才判的旧写法下也会漏采（解码 12M 像素超预算）。
 *  上限 64 防御异常输入死循环。 */
internal fun computeSampleSize(
    w: Int,
    h: Int,
    maxDimen: Int,
    maxPixels: Long,
): Int {
    var s = 1
    var cw = w
    var ch = h
    while (s < 64 && (maxOf(cw, ch) > maxDimen || cw.toLong() * ch > maxPixels)) {
        s *= 2
        cw /= 2
        ch /= 2
    }
    return s
}

/**
 * 帖子图片查看器（AF-03）：全屏 HorizontalPager + 双指缩放/拖动 + 序号指示。
 * 按需加载：只解码当前页±1 张（不预下载全帖）；内存 LruCache 复用已解码位图。
 * 图片经 HttpURLConnection 直取（无新增第三方依赖）；失败显示占位可重试。
 */
object GalleryBitmapLoader {

    private val cache = object : LruCache<String, Bitmap>((Runtime.getRuntime().maxMemory() / 4).toInt()) {
        override fun sizeOf(key: String, value: Bitmap) = value.byteCount
    }
    private val pool = Executors.newFixedThreadPool(3)
    private val main = Handler(Looper.getMainLooper())
    private const val MAX_DIMEN = 2048
    private const val MAX_PIXELS = 8_000_000L

    /** AV-07：在途请求表 键 -> [(reqId, 分发回调)]——同键去重复用，cancel 按 reqId 摘除。
     *  C3：键含 target——封面(512)与相册(2048)同 URL 各自解码，先到者不再决定另一方的质量。 */
    private val inFlight = HashMap<String, MutableList<Pair<Int, (Bitmap?) -> Unit>>>()

    private fun cacheKey(target: Int, url: String) = "$target:$url"

    /** AR-03：reqId=请求代次（同一 URL 快速翻页时旧任务回调由调用方按代次丢弃）；
     *  响应体上限 12MB 防异常大图；显式 disconnect。
     *  target=解码目标最长边（封面 512 档 / 相册默认档——两类图不必同精度）。 */
    fun load(url: String, reqId: Int, target: Int = MAX_DIMEN, onReady: (Bitmap) -> Unit, onError: () -> Unit) {
        val k = cacheKey(target, url)
        cache.get(k)?.let { onReady(it); return }
        val cb: (Bitmap?) -> Unit = { b -> if (b != null) onReady(b) else onError() }
        synchronized(inFlight) {
            val list = inFlight[k]
            if (list != null) {
                list.add(reqId to cb)
                return
            }
            inFlight[k] = mutableListOf(reqId to cb)
        }
        pool.execute {
            val bmp = runCatching {
                val conn = URL(url).openConnection() as HttpURLConnection
                conn.connectTimeout = 15000
                conn.readTimeout = 20000
                conn.setRequestProperty("User-Agent", "Mozilla/5.0 (Linux; Android 16) Chrome/130.0.0.0 Mobile Safari/537.36")
                try {
                    if (conn.contentLength > 12 * 1024 * 1024) return@runCatching null
                    conn.inputStream.use { stream ->
                        // SV-5：限长读取（64KB 块循环，超 12MB 即断）——峰值内存有界
                        val cap = 12 * 1024 * 1024
                        val buf = java.io.ByteArrayOutputStream()
                        val chunk = ByteArray(65536)
                        var total = 0
                        var n = stream.read(chunk)
                        while (n >= 0) {
                            total += n
                            if (total > cap) return@runCatching null
                            buf.write(chunk, 0, n)
                            n = stream.read(chunk)
                        }
                        val head = buf.toByteArray()
                        // 两段式：先用头部探测尺寸（decodeByteArray 不消耗网络流）
                        val opts = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                        BitmapFactory.decodeByteArray(head, 0, head.size, opts)
                        val sample = computeSampleSize(opts.outWidth, opts.outHeight, target, MAX_PIXELS)
                        val opts2 = BitmapFactory.Options().apply {
                            inSampleSize = sample
                            inPreferredConfig = Bitmap.Config.RGB_565
                        }
                        val bmp = BitmapFactory.decodeByteArray(head, 0, head.size, opts2)
                        // AV-07 验收通道：实际解码尺寸+采样率落 logcat（脚本断言长图被采样）
                        android.util.Log.d(
                            "SDGGallery",
                            "decode ${opts.outWidth}x${opts.outHeight} sample=$sample -> ${bmp?.width}x${bmp?.height} t=$target",
                        )
                        bmp
                    }
                } finally {
                    conn.disconnect()
                }
            }.getOrNull()
            val waiters = synchronized(inFlight) { inFlight.remove(k) } ?: emptyList()
            main.post {
                if (bmp != null) cache.put(k, bmp)
                waiters.forEach { it.second(bmp) }
            }
        }
    }

    /** AV-07：页面离开/重试换代时摘除回调——迟到结果不再写已离开的组合状态 */
    fun cancel(url: String, reqId: Int, target: Int = MAX_DIMEN) {
        synchronized(inFlight) {
            inFlight[cacheKey(target, url)]?.removeAll { it.first == reqId }
        }
    }
}

@Composable
fun GalleryScreen(data: GalleryData, onClose: () -> Unit) {
    Dialog(
        onDismissRequest = onClose,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        val pagerState = rememberPagerState(initialPage = data.startIndex) { data.urls.size }
        val scope = rememberCoroutineScope()
        // C4：全不透明背景（0.96 透底在浅色模式顶部露原页亮边）
        Box(
            Modifier
                .fillMaxSize()
                .background(androidx.compose.ui.graphics.Color.Black),
        ) {
            HorizontalPager(
                state = pagerState,
                modifier = Modifier.fillMaxSize(),
                key = { "$it:" + data.urls[it] },
            ) { page ->
                GalleryPage(url = data.urls[page])
            }
            // AV-03：按钮是可访问翻页入口，但不是手势修复的替代品
            if (pagerState.currentPage > 0) {
                IconButton(
                    onClick = {
                        scope.launch { pagerState.animateScrollToPage(pagerState.currentPage - 1) }
                    },
                    modifier = Modifier
                        .align(Alignment.CenterStart)
                        .padding(8.dp),
                ) {
                    // C4：半透明黑衬底——白底图上箭头仍可辨
                    Box(
                        Modifier
                            .size(40.dp)
                            .background(
                                androidx.compose.ui.graphics.Color.Black.copy(alpha = 0.45f),
                                androidx.compose.foundation.shape.CircleShape,
                            ),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "上一张",
                            tint = androidx.compose.ui.graphics.Color(0xFFE6E9F2),
                        )
                    }
                }
            }
            if (pagerState.currentPage < data.urls.size - 1) {
                IconButton(
                    onClick = {
                        scope.launch { pagerState.animateScrollToPage(pagerState.currentPage + 1) }
                    },
                    modifier = Modifier
                        .align(Alignment.CenterEnd)
                        .padding(8.dp),
                ) {
                    Box(
                        Modifier
                            .size(40.dp)
                            .background(
                                androidx.compose.ui.graphics.Color.Black.copy(alpha = 0.45f),
                                androidx.compose.foundation.shape.CircleShape,
                            ),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowForward,
                            contentDescription = "下一张",
                            tint = androidx.compose.ui.graphics.Color(0xFFE6E9F2),
                        )
                    }
                }
            }
            IconButton(
                onClick = onClose,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(12.dp),
            ) {
                Icon(Icons.Filled.Close, contentDescription = "关闭", tint = androidx.compose.ui.graphics.Color(0xFFE6E9F2))
            }
            Text(
                "${pagerState.currentPage + 1} / ${data.urls.size}",
                style = MaterialTheme.typography.labelLarge,
                color = androidx.compose.ui.graphics.Color(0xFFE6E9F2),
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(16.dp),
            )
        }
    }
}

/** 图片页加载状态机（AR-02）：三态互斥，重试真实重新发起请求 */
private enum class LoadState { LOADING, OK, FAILED }

@Composable
private fun GalleryPage(url: String) {
    var bmp by remember(url) { mutableStateOf<Bitmap?>(null) }
    var state by remember(url) { mutableStateOf(LoadState.LOADING) }
    var attempt by remember(url) { mutableIntStateOf(0) }
    var scale by remember(url) { mutableStateOf(1f) }
    var offset by remember(url) { mutableStateOf(Offset.Zero) }

    LaunchedEffect(url, attempt) {
        if (state == LoadState.OK) return@LaunchedEffect
        state = LoadState.LOADING
        val req = attempt
        // AR-03：reqId 随 attempt 递增——快速翻页后旧任务的回调因代次不符被丢弃
        GalleryBitmapLoader.load(url, req, onReady = { b ->
            if (req == attempt) {
                bmp = b
                state = LoadState.OK
            }
        }, onError = {
            if (req == attempt) state = LoadState.FAILED
        })
    }
    // AV-07：离开页面/翻页换图时摘除本代次回调，迟到结果不再写已离开的组合状态
    DisposableEffect(url, attempt) {
        onDispose { GalleryBitmapLoader.cancel(url, attempt) }
    }

    Box(
        Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center,
    ) {
        val bitmap = bmp
        when {
            bitmap != null -> Image(
                bitmap = bitmap.asImageBitmap(),
                contentDescription = null,
                contentScale = ContentScale.Fit,
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer {
                        scaleX = scale
                        scaleY = scale
                        translationX = offset.x
                        translationY = offset.y
                    }
                    // AV-03：手势仲裁。detectTransformGestures 在 scale=1 时也消费单指
                    // 平移，HorizontalPager 永远拿不到水平滑动（反例：60 图帖横滑不翻页）。
                    // 规则：单指且未放大 → 不消费（事件放行给 Pager 翻页）；
                    // 多指捏合或已放大 → 消费并应用缩放/平移；缩回 1× 即归位让出翻页。
                    .pointerInput(url) {
                        awaitEachGesture {
                            awaitFirstDown(requireUnconsumed = false)
                            do {
                                val event = awaitPointerEvent()
                                val multi = event.changes.count { it.pressed } > 1
                                if (multi || scale > 1.01f) {
                                    val zoom = event.calculateZoom()
                                    val pan = event.calculatePan()
                                    val next = (scale * zoom).coerceIn(1f, 5f)
                                    scale = next
                                    offset = if (next > 1f) offset + pan else Offset.Zero
                                    event.changes.forEach { change ->
                                        if (change.positionChanged()) change.consume()
                                    }
                                }
                            } while (event.changes.any { it.pressed })
                        }
                    },
            )
            state == LoadState.FAILED -> FailureHint(attempt) { attempt++ }
            else -> CircularProgressIndicator()
        }
    }
}

@Composable
private fun FailureHint(attempt: Int, retry: () -> Unit) {
    androidx.compose.foundation.layout.Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text("加载失败", color = androidx.compose.ui.graphics.Color(0xFFE6E9F2))
        androidx.compose.material3.TextButton(onClick = retry) { Text("重试") }
        if (attempt > 0) Text("已重试 $attempt 次", color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
