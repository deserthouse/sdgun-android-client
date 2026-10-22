package io.github.deserthouse.sdgun.feature.thread

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.github.deserthouse.sdgun.core.model.MutationState
import io.github.deserthouse.sdgun.core.model.UiState
import io.github.deserthouse.sdgun.data.repository.ThreadRepository
import io.github.deserthouse.sdgun.feature.media.GalleryScreen
import io.github.deserthouse.sdgun.web.GalleryData

/**
 * 帖子页（G2-A05 / A70-04 重排）：单 LazyColumn——主楼头+主楼内容块+楼层控制+楼层块+分页。
 * G0-C01a 契约：主楼不在 commentList；title 唯一源=threadView。
 * 图片块点击开原生查看器（复用 GalleryScreen，页内自持状态）；
 * 分页入口不随只看楼主隐藏（服务端按 authorid 过滤后 total_page 仍权威）。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ThreadPage(
    opState: UiState<ThreadRepository.ThreadOP>,
    floorsState: UiState<ThreadRepository.FloorsPage>,
    descending: Boolean,
    posterOnly: Boolean,
    onToggleOrder: () -> Unit,
    onTogglePoster: () -> Unit,
    onLoadMore: () -> Unit,
    onBack: () -> Unit,
    // G4 写操作（路由层持有状态与提交；本页只渲染+回调）
    isLoggedIn: Boolean,
    applaudStates: Map<Long, MutationState>,
    applaudedIds: Set<Long>,
    onApplaud: (Long) -> Unit,
    isCollected: Boolean,
    collectState: MutationState?,
    onCollect: () -> Unit,
    onReply: () -> Unit,
    modifier: Modifier = Modifier,
) {
    // 图片查看器自持（A70-04）：任何内容图点击即开，关闭/Back 都回原位
    var viewer by remember { mutableStateOf<GalleryData?>(null) }
    val openViewer: (List<String>, Int) -> Unit = { urls, i -> viewer = GalleryData(urls, i) }
    viewer?.let { g ->
        GalleryScreen(data = g, onClose = { viewer = null })
    }

    Scaffold(
        modifier = modifier,
        bottomBar = {
            // G4 回复入口：游客引导登录（路由层跳转），登录态开编辑器
            Row(
                Modifier.fillMaxWidth()
                    .background(MaterialTheme.colorScheme.surfaceVariant)
                    .padding(horizontal = 16.dp, vertical = 8.dp),
            ) {
                Button(onClick = onReply, modifier = Modifier.fillMaxWidth()) {
                    Text(if (isLoggedIn) "回复本帖" else "登录后回复")
                }
            }
        },
        topBar = {
            TopAppBar(
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                    }
                },
                title = {
                    val t = (opState as? UiState.Data<ThreadRepository.ThreadOP>)?.value?.title
                    Text(t ?: "帖子", maxLines = 1, overflow = TextOverflow.Ellipsis)
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                ),
            )
        },
        containerColor = MaterialTheme.colorScheme.surface,
    ) { padding ->
        when (opState) {
            is UiState.InitialLoading, is UiState.Refreshing<*> -> Column(
                Modifier.fillMaxSize().padding(padding),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) { CircularProgressIndicator() }

            is UiState.RetryableError -> Column(
                Modifier.fillMaxSize().padding(padding),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                Text(opState.error, style = MaterialTheme.typography.bodyLarge)
                TextButton(onClick = onBack) { Text("返回") }
            }

            is UiState.Data<*> -> {
                val op = (opState as UiState.Data<ThreadRepository.ThreadOP>).value
                // 块解析在组合上下文完成（LazyListScope 内不能调 remember）
                val opBlocks = remember(op.pid, op.contentHtml) {
                    parseContentBlocks(op.pid, op.contentHtml)
                }
                val opGroup = remember(opBlocks) { imageUrls(opBlocks) }
                LazyColumn(Modifier.fillMaxSize().padding(padding)) {
                    item(key = "op_head") {
                        OpHeader(
                            op,
                            applauded = op.pid in applaudedIds,
                            applaudState = applaudStates[op.pid],
                            isCollected = isCollected,
                            collectState = collectState,
                            onApplaud = { onApplaud(op.pid) },
                            onCollect = onCollect,
                        )
                    }
                    items(opBlocks, key = { it.key }) { block ->
                        BlockView(block, opGroup, openViewer)
                    }
                    item(key = "controls") {
                        FloorControls(
                            descending = descending, posterOnly = posterOnly,
                            onToggleOrder = onToggleOrder, onTogglePoster = onTogglePoster,
                        )
                    }
                    floorsItems(floorsState, applaudStates, applaudedIds, onApplaud, openViewer)
                    moreItem(floorsState, onLoadMore)
                }
            }

            else -> {}
        }
    }
}

/** 排序/只看楼主控制行（A70-04：常驻，不随筛选消失）。 */
@Composable
private fun FloorControls(
    descending: Boolean,
    posterOnly: Boolean,
    onToggleOrder: () -> Unit,
    onTogglePoster: () -> Unit,
) {
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        TextButton(onClick = onToggleOrder) {
            Text(if (descending) "正序" else "倒序")
        }
        TextButton(onClick = onTogglePoster) {
            Text(if (posterOnly) "全部楼层" else "只看楼主")
        }
    }
}

/** 楼层流（LazyListScope 扩展）：Data/Stale 两态都产出独立楼层项；Stale 先给横幅。 */
private fun LazyListScope.floorsItems(
    floorsState: UiState<ThreadRepository.FloorsPage>,
    applaudStates: Map<Long, MutationState>,
    applaudedIds: Set<Long>,
    onApplaud: (Long) -> Unit,
    onOpenImage: (List<String>, Int) -> Unit,
) {
    when (val fs = floorsState) {
        is UiState.InitialLoading -> item(key = "floors_loading") {
            Row(Modifier.fillMaxWidth().padding(16.dp),
                horizontalArrangement = Arrangement.Center) { CircularProgressIndicator() }
        }

        is UiState.Empty -> item(key = "floors_empty") {
            Text("暂无楼层", Modifier.fillMaxWidth().padding(16.dp),
                style = MaterialTheme.typography.bodyMedium)
        }

        is UiState.Stale<ThreadRepository.FloorsPage> -> {
            item(key = "floors_stale") {
                Text(
                    fs.error,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onErrorContainer,
                    modifier = Modifier.fillMaxWidth()
                        .background(MaterialTheme.colorScheme.errorContainer)
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                )
            }
            floorEntries(fs.stale, applaudStates, applaudedIds, onApplaud, onOpenImage)
        }

        is UiState.Data<ThreadRepository.FloorsPage> -> floorEntries(fs.value, applaudStates, applaudedIds, onApplaud, onOpenImage)

        else -> {}
    }
}

private fun LazyListScope.floorEntries(
    fp: ThreadRepository.FloorsPage,
    applaudStates: Map<Long, MutationState>,
    applaudedIds: Set<Long>,
    onApplaud: (Long) -> Unit,
    onOpenImage: (List<String>, Int) -> Unit,
) {
    items(fp.floors, key = { "f_${it.pid}" }) { floor ->
        FloorCard(floor, applaudStates[floor.pid], floor.pid in applaudedIds, onApplaud, onOpenImage)
    }
}

/** 下一页入口：仓库 total_page 权威；只看楼主下同样可用（A70-04 反例=筛选隐藏分页）。 */
private fun LazyListScope.moreItem(
    floorsState: UiState<ThreadRepository.FloorsPage>,
    onLoadMore: () -> Unit,
) {
    val fp = (floorsState as? UiState.Data<ThreadRepository.FloorsPage>)?.value ?: return
    if (fp.page < fp.totalPages) {
        item(key = "more") {
            TextButton(onClick = onLoadMore, modifier = Modifier.fillMaxWidth()) {
                Text("加载更多楼层")
            }
        }
    }
}

@Composable
private fun OpHeader(
    op: ThreadRepository.ThreadOP,
    applauded: Boolean,
    applaudState: MutationState?,
    isCollected: Boolean,
    collectState: MutationState?,
    onApplaud: () -> Unit,
    onCollect: () -> Unit,
) {
    Column(Modifier.padding(16.dp)) {
        Text(op.title, style = MaterialTheme.typography.titleLarge)
        Spacer(Modifier.height(8.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            AvatarPlaceholder(op.authorName, 36.dp)
            Spacer(Modifier.width(8.dp))
            Column {
                Text(
                    op.authorName ?: "匿名",
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    listOfNotNull(
                        op.createTime,
                        op.viewCount?.let { "查看 $it" },
                        op.replyCount?.let { "回复 $it" },
                    ).joinToString(" · "),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        Row(Modifier.padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            TextButton(onClick = onApplaud) {
                Text(
                    when (applaudState) {
                        is MutationState.Submitting -> "点赞中…"
                        is MutationState.Rejected -> "点赞失败"
                        is MutationState.OutcomeUnknown -> "点赞待确认"
                        else -> if (applauded) "已点赞" else "点赞"
                    }
                )
            }
            TextButton(onClick = onCollect) {
                Text(
                    when (collectState) {
                        is MutationState.Submitting -> "收藏中…"
                        is MutationState.Rejected -> "收藏失败"
                        is MutationState.OutcomeUnknown -> "收藏待确认"
                        else -> if (isCollected) "已收藏" else "收藏"
                    }
                )
            }
        }
        HorizontalDivider(Modifier.padding(top = 12.dp),
            color = MaterialTheme.colorScheme.outlineVariant)
    }
}
