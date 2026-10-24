package io.github.deserthouse.sdgun.feature.community

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.github.deserthouse.sdgun.core.model.UiState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import io.github.deserthouse.sdgun.data.repository.BoardRepository

/**
 * 社区页（G2-A03）：板块树全渲染（分组卡+板块行：图标/名称/统计）+我的板块横滑。
 * circleTree + userCircleList 数据（G2-A03 实测：5 组/板块 fid=type_value）。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CommunityPage(
    state: UiState<List<BoardRepository.Group>>,
    myBoards: List<BoardRepository.Board>,
    onOpenBoard: (Long, String) -> Unit,
    modifier: Modifier = Modifier,
) {
    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = { Text("社区") },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                ),
            )
        },
        containerColor = MaterialTheme.colorScheme.surface,
    ) { padding ->
        when (state) {
            is UiState.InitialLoading -> Column(
                Modifier.fillMaxSize().padding(padding),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) { Text("加载中…", style = MaterialTheme.typography.bodyLarge) }

            is UiState.RetryableError -> Column(
                Modifier.fillMaxSize().padding(padding),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                Text(state.error, style = MaterialTheme.typography.bodyLarge)
            }

            else -> {
                val groups = (state as? UiState.Data<*>)?.let {
                    @Suppress("UNCHECKED_CAST")
                    (it as UiState.Data<List<BoardRepository.Group>>).value
                } ?: emptyList()
                LazyColumn(
                    Modifier.fillMaxSize().padding(padding),
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(vertical = 6.dp),
                ) {
                    groups.forEach { group ->
                        item(key = "gh_${group.id}") {
                            Text(
                                group.name,
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                            )
                        }
                        items(group.boards, key = { "b_${it.circleId}" }) { board ->
                            BoardRow(board) { onOpenBoard(board.fid, board.name) }
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun BoardRow(board: BoardRepository.Board, onClick: () -> Unit) {
    Card(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 3.dp),
    ) {
        Row(
            Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // A24-07 板块行真实化：icon_url 有则加载真图，否则保留语义占位
            BoardIcon(url = board.iconUrl)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(board.name, style = MaterialTheme.typography.titleSmall,
                    maxLines = 1, overflow = TextOverflow.Ellipsis)
                board.description?.let {
                    Spacer(Modifier.height(2.dp))
                    Text(it, style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
            board.postToday?.let {
                Text("今日 $it", style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

/** 板块图标：icon_url 小图有界解码；无图/失败=语义占位（A24-07）。 */
@Composable
fun BoardIcon(url: String?) {
    var bitmap by remember { mutableStateOf<android.graphics.Bitmap?>(null) }
    var failed by remember { mutableStateOf(false) }
    androidx.compose.runtime.LaunchedEffect(url) {
        if (url == null) {
            failed = true
            return@LaunchedEffect
        }
        bitmap = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
            try {
                val conn = java.net.URL(url).openConnection() as java.net.HttpURLConnection
                conn.connectTimeout = 8000
                conn.readTimeout = 12000
                try {
                    val bytes = conn.inputStream.use { it.readBytes() }
                    val opts = android.graphics.BitmapFactory.Options().apply { inSampleSize = 2 }
                    android.graphics.BitmapFactory.decodeByteArray(bytes, 0, bytes.size, opts)
                } finally {
                    conn.disconnect()
                }
            } catch (e: Exception) {
                null
            }
        }
        if (bitmap == null) failed = true
    }
    val shape = RoundedCornerShape(10.dp)
    androidx.compose.foundation.layout.Box(
        Modifier.size(48.dp).background(
            MaterialTheme.colorScheme.surfaceVariant,
            shape,
        ),
        contentAlignment = Alignment.Center,
    ) {
        val bmp = bitmap
        when {
            bmp != null -> androidx.compose.foundation.Image(
                bitmap = bmp.asImageBitmap(),
                contentDescription = "板块图标",
                modifier = Modifier.size(44.dp).clip(shape),
            )
            failed -> Text("板块", style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
            else -> androidx.compose.material3.CircularProgressIndicator(
                Modifier.size(20.dp), strokeWidth = 2.dp)
        }
    }
}