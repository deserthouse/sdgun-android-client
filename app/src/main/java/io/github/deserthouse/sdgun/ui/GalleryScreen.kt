package io.github.deserthouse.sdgun.ui

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.os.Handler
import android.os.Looper
import android.util.LruCache
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import io.github.deserthouse.sdgun.web.GalleryData
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.Executors
import kotlin.math.roundToInt

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

    fun load(url: String, onReady: (Bitmap) -> Unit, onError: () -> Unit) {
        cache.get(url)?.let { onReady(it); return }
        pool.execute {
            val bmp = runCatching {
                val conn = URL(url).openConnection() as HttpURLConnection
                conn.connectTimeout = 15000
                conn.readTimeout = 20000
                conn.setRequestProperty("User-Agent", "Mozilla/5.0 (Linux; Android 16) Chrome/130.0.0.0 Mobile Safari/537.36")
                conn.inputStream.use { BitmapFactory.decodeStream(it) }
            }.getOrNull()
            main.post {
                if (bmp != null) {
                    cache.put(url, bmp)
                    onReady(bmp)
                } else onError()
            }
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
        Box(
            Modifier
                .fillMaxSize()
                .background(androidx.compose.ui.graphics.Color.Black.copy(alpha = 0.96f)),
        ) {
            HorizontalPager(
                state = pagerState,
                modifier = Modifier.fillMaxSize(),
                key = { data.urls[it] },
            ) { page ->
                GalleryPage(url = data.urls[page])
            }
            IconButton(
                onClick = onClose,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(12.dp),
            ) {
                Icon(Icons.Filled.Close, contentDescription = "关闭", tint = MaterialTheme.colorScheme.onSurface)
            }
            Text(
                "${pagerState.currentPage + 1} / ${data.urls.size}",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(16.dp),
            )
        }
    }
}

@Composable
private fun GalleryPage(url: String) {
    var bmp by remember(url) { mutableStateOf<Bitmap?>(null) }
    var failed by remember(url) { mutableStateOf(false) }
    var attempt by remember(url) { mutableIntStateOf(0) }
    var scale by remember(url) { mutableStateOf(1f) }
    var offset by remember(url) { mutableStateOf(Offset.Zero) }

    LaunchedEffect(url, attempt) {
        if (bmp == null && !failed) {
            GalleryBitmapLoader.load(url, { bmp = it }, { failed = true })
        }
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
                    .pointerInput(url) {
                        detectTransformGestures { _, pan, zoom, _ ->
                            val next = (scale * zoom).coerceIn(1f, 5f)
                            scale = next
                            offset = if (next > 1f) offset + pan else Offset.Zero
                        }
                    },
            )
            failed -> FailureHint(attempt) { attempt++ }
            else -> CircularProgressIndicator()
        }
    }
}

@Composable
private fun FailureHint(attempt: Int, retry: () -> Unit) {
    androidx.compose.foundation.layout.Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text("加载失败", color = MaterialTheme.colorScheme.onSurface)
        androidx.compose.material3.TextButton(onClick = retry) { Text("重试") }
        if (attempt > 0) Text("已重试 $attempt 次", color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
