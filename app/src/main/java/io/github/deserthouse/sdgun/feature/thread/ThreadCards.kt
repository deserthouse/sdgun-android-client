package io.github.deserthouse.sdgun.feature.thread

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AttachFile
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Icon
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.Image
import io.github.deserthouse.sdgun.core.model.MutationState
import io.github.deserthouse.sdgun.core.model.WriteUi
import io.github.deserthouse.sdgun.core.model.WriteUiPhase
import io.github.deserthouse.sdgun.data.repository.ThreadRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** 头像占位：暂用首字母圆标（Coil 引入后替换为 AsyncImage）。 */
@Composable
fun AvatarPlaceholder(name: String?, sizeDp: Dp) {
    Box(
        Modifier
            .size(sizeDp)
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.surfaceVariant),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            name?.firstOrNull()?.toString() ?: "?",
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/** 内容块渲染（A70-04 + AF29-04）：文本/图片/视频/附件/链接；
 * 视频与附件真实可点击（onOpenUrl 外开），链接文本可点。 */
@Composable
fun BlockView(
    block: ContentBlock,
    group: List<String>,
    onOpenImage: (List<String>, Int) -> Unit,
    onOpenUrl: (String) -> Unit = {},
    /** A1-3：附件下载（DownloadManager+WebView Cookie；默认空实现保旧调用点） */
    onDownloadAttachment: (String, String) -> Unit = { _, _ -> },
) {
    when (block) {
        is ContentBlock.Text -> Text(
            block.text,
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
        )

        is ContentBlock.Image -> PostImage(block.url) {
            onOpenImage(group, group.indexOf(block.url).coerceAtLeast(0))
        }

        is ContentBlock.Video -> TextButton(
            onClick = { onOpenUrl(block.url) },
            modifier = Modifier.padding(horizontal = 12.dp),
        ) {
            Icon(
                androidx.compose.material.icons.Icons.Filled.PlayArrow,
                contentDescription = null,
                modifier = Modifier.size(18.dp),
            )
            Spacer(Modifier.width(6.dp))
            Text("视频（点击播放/打开）", style = MaterialTheme.typography.labelLarge)
        }

        is ContentBlock.Attachment -> Row(
            Modifier.padding(horizontal = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // A1-3：附件=文件语义，主钮=系统下载器（带 WebView Cookie 供站方附件鉴权）；
            // 「打开」次钮保留浏览器路径（无 Cookie 场景兜底）
            TextButton(onClick = { onDownloadAttachment(block.url, block.name) }) {
                Icon(
                    androidx.compose.material.icons.Icons.Filled.AttachFile,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp),
                )
                Spacer(Modifier.width(6.dp))
                Text(
                    "附件：${block.name}",
                    style = MaterialTheme.typography.labelLarge,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            TextButton(onClick = { onOpenUrl(block.url) }) {
                Text("打开", style = MaterialTheme.typography.labelSmall)
            }
        }

        is ContentBlock.Link -> TextButton(
            onClick = { onOpenUrl(block.url) },
            modifier = Modifier.padding(horizontal = 12.dp),
        ) {
            Text(
                block.text.ifBlank { block.url },
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

/**
 * 帖子图片：无图片库依赖的独立解码（Bitmap）；失败给语义占位框（A70-06 图标语义化同源）。
 * 解码协程绑 LaunchedEffect(url)——离开组合即取消（A70-04 离开零迟到写入）。
 */
@Composable
fun PostImage(url: String, onClick: () -> Unit) {
    var bitmap by remember(url) { mutableStateOf<android.graphics.Bitmap?>(null) }
    var failed by remember(url) { mutableStateOf(false) }
    // B0-3（V3）：失败可重试——attempt 进 key 触发重载（A-R2「坏图可重试占位」兑现）
    var attempt by remember(url) { mutableIntStateOf(0) }

    LaunchedEffect(url, attempt) {
        val decoded = withContext(Dispatchers.IO) {
            try {
                val conn = java.net.URL(url).openConnection() as java.net.HttpURLConnection
                conn.connectTimeout = 10000
                conn.readTimeout = 15000
                try {
                    // A24-05：两段有界解码——先读字节+bounds，按采样率降解码内存
                    val bytes = conn.inputStream.use { it.readBytes() }
                    val bounds = android.graphics.BitmapFactory.Options().apply {
                        inJustDecodeBounds = true
                    }
                    android.graphics.BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
                    val sample = sampleSize(bounds.outWidth, bounds.outHeight, 2048)
                    val opts = android.graphics.BitmapFactory.Options().apply { inSampleSize = sample }
                    android.graphics.BitmapFactory.decodeByteArray(bytes, 0, bytes.size, opts)
                } finally {
                    conn.disconnect()
                }
            } catch (e: Exception) {
                android.util.Log.w("PostImage", "fetch fail: $url -> ${e.javaClass.simpleName}: ${e.message}")
                null
            }
        }
        bitmap = decoded
        if (decoded == null) failed = true
    }

    Box(
        Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
        contentAlignment = Alignment.Center,
    ) {
        val bmp = bitmap
        val placeholder = Modifier
            .fillMaxWidth()
            .aspectRatio(3f)
            .clip(RoundedCornerShape(8.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant)
        when {
            bmp != null -> Image(
                bitmap = bmp.asImageBitmap(),
                contentDescription = "帖子图片",
                contentScale = ContentScale.FillWidth,
                modifier = Modifier
                    .fillMaxWidth()
                    // A24-05 修正：Compose aspectRatio=宽/高（此前传高/宽=比例反转）
                    .aspectRatio((bmp.width.toFloat() / bmp.height.toFloat()).coerceIn(1f / 3f, 3f))
                    .clickable(onClick = onClick),
            )
            failed -> Box(placeholder, contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        "图片加载失败",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    TextButton(onClick = {
                        failed = false
                        attempt++
                    }) { Text("重试") }
                }
            }
            else -> Box(placeholder, contentAlignment = Alignment.Center) {
                CircularProgressIndicator(Modifier.size(22.dp), strokeWidth = 2.dp)
            }
        }
    }
}

/** A24-05：按最长边目标值的采样率计算（纯函数，JVM 可测）——始终 ≥1。 */
fun sampleSize(width: Int, height: Int, maxEdge: Int): Int {
    val longest = maxOf(width, height)
    if (longest <= 0 || maxEdge <= 0) return 1
    var sample = 1
    while (longest / (sample * 2) >= maxEdge) sample *= 2
    return sample
}

/** 楼层卡（A70-04 块化）：楼层号+作者+内容块（图文交错）+时间/赞（G4 点赞按钮化）。 */
@Composable
fun FloorCard(
    floor: ThreadRepository.Floor,
    applaudState: WriteUi,
    applauded: Boolean,
    onApplaud: (Long) -> Unit,
    onOpenImage: (List<String>, Int) -> Unit,
    onOpenUrl: (String) -> Unit = {},
    onDownloadAttachment: (String, String) -> Unit = { _, _ -> },
    onLocateQuote: (Long) -> Unit = {},
    onReplyToQuote: (Long) -> Unit = {},
) {
    val blocks = remember(floor.pid, floor.contentHtml) {
        parseContentBlocks(floor.pid, floor.contentHtml)
    }
    val group = remember(blocks) { imageUrls(blocks) }
    Column(Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            AvatarPlaceholder(floor.authorName, 28.dp)
            Spacer(Modifier.width(6.dp))
            Text(
                floor.authorName ?: "匿名",
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Medium,
            )
            if (floor.isPublisher) {
                Spacer(Modifier.width(6.dp))
                Text(
                    "楼主",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
            Spacer(Modifier.weight(1f))
            Text(
                "${floor.floor} 楼",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Spacer(Modifier.height(4.dp))
        // R6+S07：引用块整块点击=定位被引楼层；「引用回复」显式小钮=写表单动作（两语义分离）
        floor.quoteAuthor?.let { qa ->
            Column(
                Modifier.fillMaxWidth()
                    .clip(RoundedCornerShape(6.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant)
                    .padding(8.dp)
                    .let { m ->
                        if (floor.quotedPid != null) m.clickable { onLocateQuote(floor.quotedPid) } else m
                    },
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(qa, style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Medium,
                        modifier = Modifier.weight(1f))
                    if (floor.quotedPid != null) {
                        TextButton(
                            onClick = { onReplyToQuote(floor.quotedPid) },
                            contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 8.dp, vertical = 0.dp),
                            modifier = Modifier.height(24.dp),
                        ) {
                            Text("引用回复", style = MaterialTheme.typography.labelSmall)
                        }
                    }
                }
                floor.quoteText?.let {
                    Text(it, style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 3, overflow = TextOverflow.Ellipsis)
                }
            }
            Spacer(Modifier.height(4.dp))
        }
        blocks.forEach { block ->
            BlockView(block, group, onOpenImage, onOpenUrl, onDownloadAttachment)
        }
        Row(Modifier.padding(top = 2.dp), verticalAlignment = Alignment.CenterVertically) {
            floor.createTime?.let {
                Text(
                    io.github.deserthouse.sdgun.data.repository.BoardListRepository
                        .relativeTime(it),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Spacer(Modifier.weight(1f))
            // G4 点赞：乐观 +1（失败/待确认回退由路由层管理 applaudedIds）
            val count = floor.applaudCount?.let { if (applauded) it + 1 else it }
            // R5（SdEmphasizedSpring 消费）：点赞翻转触发 scale 弹跳（M3E expressive）
            val applaudScale by androidx.compose.animation.core.animateFloatAsState(
                targetValue = if (applauded) 1f else 0.92f,
                animationSpec = io.github.deserthouse.sdgun.core.designsystem.SdEmphasizedSpring,
                label = "applaudScale",
            )
            TextButton(
                onClick = { onApplaud(floor.pid) },
                // Q2 toggle：已赞（Done）再点=取消赞——仅 Pending 在途禁点（S03 禁重复）；
                // 路由层按 applaudedIds 归属分流 Add/Cancel（WriteOps.decideApplaud）
                enabled = applaudState.phase != WriteUiPhase.Pending,
                modifier = Modifier.scale(applaudScale),
            ) {
                Text(
                    when (applaudState.phase) {
                        WriteUiPhase.Pending -> "…"
                        WriteUiPhase.Done -> if (applauded) "已赞 ${count ?: ""} · 取消".trim() else "赞 ${count ?: ""}".trim()
                        WriteUiPhase.Failed -> "赞失败"
                        WriteUiPhase.Verify -> "待核对"
                        WriteUiPhase.Idle -> "赞 ${count ?: ""}".trim()
                    },
                    style = MaterialTheme.typography.labelSmall,
                )
            }
        }
        HorizontalDivider(
            Modifier.padding(top = 8.dp),
            color = MaterialTheme.colorScheme.outlineVariant,
        )
    }
}
