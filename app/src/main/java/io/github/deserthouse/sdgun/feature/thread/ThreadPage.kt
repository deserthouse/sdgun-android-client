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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.outlined.Flag
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
import io.github.deserthouse.sdgun.core.model.WriteUi
import io.github.deserthouse.sdgun.core.model.WriteUiPhase
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
    onOpenUrl: (String) -> Unit = {},
    /** A1-3：附件块下载（DownloadManager+WebView Cookie） */
    onDownloadAttachment: (String, String) -> Unit = { _, _ -> },
    onLocateQuote: (Long) -> Unit = {},
    onReplyToQuote: (Long) -> Unit = {},
    onShare: (String) -> Unit = {},
    /** R2/F17：举报（jadx ReportComp 同构 H5 通道：userReportView?type=14&type_value=base64(contentId)） */
    onReport: (contentId: String) -> Unit = {},
    opState: UiState<ThreadRepository.ThreadOP>,
    floorsState: UiState<ThreadRepository.FloorsPage>,
    descending: Boolean,
    posterOnly: Boolean,
    onToggleOrder: () -> Unit,
    onTogglePoster: () -> Unit,
    onLoadMore: () -> Unit,
    onRetryFloors: () -> Unit,
    /** Q1 跳楼：输入楼层号直达（VM 统一裁决：已加载即时定位/未加载跨页扫描） */
    jumpState: ThreadViewModel.JumpState = ThreadViewModel.JumpState.Idle,
    onJumpToFloor: (Int) -> Unit = {},
    /** Q1：VM 已命中待滚动的 pid（引用点击/深链 pid/跳楼命中共用一条通路） */
    locateRequest: Long? = null,
    onLocateHandled: () -> Unit = {},
    onBack: () -> Unit,
    // G4 写操作（路由层持有状态与提交；本页只渲染+回调）
    isLoggedIn: Boolean,
    applaudStates: Map<Long, WriteUi>,
    applaudedIds: Set<Long>,
    onApplaud: (Long) -> Unit,
    verifyDialogFor: Long?,
    onVerifyAck: (Long) -> Unit,
    onVerifyDismiss: () -> Unit,
    isCollected: Boolean,
    collectState: WriteUi,
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

    // A24-02：结果未知核对对话框——显式确认后才允许重发
    verifyDialogFor?.let { pid ->
        androidx.compose.material3.AlertDialog(
            onDismissRequest = onVerifyDismiss,
            confirmButton = {
                TextButton(onClick = { onVerifyAck(pid) }) { Text("仍要重发") }
            },
            dismissButton = {
                TextButton(onClick = onVerifyDismiss) { Text("取消") }
            },
            title = { Text("点赞结果待确认") },
            text = {
                Text("此前一次点赞未收到服务端确认（超时或断网）。请回到列表核对该楼层是否已显示点赞，再决定是否重发。")
            },
        )
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
                // U4 补真实现（A-R3）：宽屏内容宽度约束（U2 声明兑现）——720dp 居中防单列拉伸
                Row(
                    Modifier.fillMaxSize().padding(padding),
                    horizontalArrangement = Arrangement.Center,
                ) {
                val floorsListState = androidx.compose.foundation.lazy.rememberLazyListState()
                // Q1 跳楼对话框 + 定位命中滚动（引用点击/深链 pid/楼层号输入共用一条通路）
                var showJumpDialog by androidx.compose.runtime.remember {
                    androidx.compose.runtime.mutableStateOf(false)
                }
                var jumpInput by androidx.compose.runtime.remember {
                    androidx.compose.runtime.mutableStateOf("")
                }
                androidx.compose.runtime.LaunchedEffect(locateRequest, floorsState, jumpState) {
                    val target = locateRequest ?: return@LaunchedEffect
                    val fs = floorsState
                    val list = when (fs) {
                        is UiState.Data<*> -> (fs.value as? ThreadRepository.FloorsPage)?.floors
                        is UiState.Stale<*> -> (fs.stale as? ThreadRepository.FloorsPage)?.floors
                        else -> null
                    } ?: return@LaunchedEffect
                    val idx = list.indexOfFirst { it.pid == target }
                    if (idx >= 0) {
                        val banner = if (fs is UiState.Stale<*>) 1 else 0
                        // 项序：op_head(1) + 主楼内容块 + controls(1) + 楼层（Stale 加横幅 1）
                        floorsListState.animateScrollToItem(1 + opBlocks.size + 1 + banner + idx)
                        onLocateHandled()
                    }
                }
                if (showJumpDialog) {
                    androidx.compose.material3.AlertDialog(
                        onDismissRequest = { showJumpDialog = false; jumpInput = "" },
                        title = { Text("跳转到楼层") },
                        text = {
                            androidx.compose.material3.OutlinedTextField(
                                value = jumpInput,
                                onValueChange = { v -> jumpInput = v.filter { it.isDigit() }.take(6) },
                                label = { Text("楼层号（如 3385）") },
                                singleLine = true,
                            )
                        },
                        confirmButton = {
                            val n = jumpInput.toIntOrNull()
                            androidx.compose.material3.TextButton(
                                onClick = {
                                    showJumpDialog = false
                                    if (n != null && n > 0) onJumpToFloor(n)
                                    jumpInput = ""
                                },
                                enabled = n != null && n > 0,
                            ) { Text("跳转") }
                        },
                        dismissButton = {
                            androidx.compose.material3.TextButton(
                                onClick = { showJumpDialog = false; jumpInput = "" },
                            ) { Text("取消") }
                        },
                    )
                }
    LazyColumn(
        state = floorsListState,
        modifier = Modifier
            .fillMaxSize()
            .widthIn(max = 720.dp),
    ) {
                    item(key = "op_head") {
                        // A1-9：原生分享（标准文本：标题+H5 链接）；tid 经 op 不可得时降级仅标题
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                            TextButton(onClick = {
                                val opTitle = (opState as? UiState.Data<*>)?.value
                                    as? ThreadRepository.ThreadOP
                                val link = opTitle?.let {
                                    " https://bbs.sdgun.com.cn/forum.php?mod=viewthread&tid=${it.tid}&mobile=2"
                                } ?: ""
                                onShare((opTitle?.title ?: "") + link)
                            }) {
                                Icon(
                                    androidx.compose.material.icons.Icons.Filled.Share,
                                    contentDescription = "分享",
                                    modifier = Modifier.size(16.dp),
                                )
                                Spacer(Modifier.width(4.dp))
                                Text("分享", style = MaterialTheme.typography.labelMedium)
                            }
                            if (onReport != {}) {
                                TextButton(onClick = {
                                    val opv = (opState as? UiState.Data<*>)?.value
                                        as? ThreadRepository.ThreadOP
                                    onReport(opv?.pid?.toString() ?: "")
                                }) {
                                    Text("举报", style = MaterialTheme.typography.labelMedium)
                                }
                            }
                        }
                        OpHeader(
                            op,
                            applauded = op.pid in applaudedIds,
                            applaudState = applaudStates[op.pid] ?: WriteUi(),
                            isCollected = isCollected,
                            collectState = collectState,
                            onApplaud = { onApplaud(op.pid) },
                            onCollect = onCollect,
                            onShare = {
                                val link = " https://bbs.sdgun.com.cn/forum.php?mod=viewthread&tid=${op.tid}&mobile=2"
                                onShare(op.title + link)
                            },
                            onReport = { onReport(op.pid.toString()) },
                        )
                    }
                    items(opBlocks, key = { it.key }) { block ->
                        BlockView(block, opGroup, openViewer, onOpenUrl, onDownloadAttachment)
                    }
                    item(key = "controls") {
                        FloorControls(
                            descending = descending, posterOnly = posterOnly,
                            onToggleOrder = onToggleOrder, onTogglePoster = onTogglePoster,
                            onJump = { showJumpDialog = true },
                            jumpStatus = jumpState,
                        )
                    }
                    floorsItems(floorsState, applaudStates, applaudedIds, onApplaud, onRetryFloors, onLoadMore, openViewer, onOpenUrl, onDownloadAttachment, onLocateQuote, onReplyToQuote)
                    moreItem(floorsState, onLoadMore)
                }
                }
            }

            else -> {}
        }
    }
}

/** 排序/只看楼主控制行（A70-04：常驻，不随筛选消失）；Q1：跳楼入口+扫描/未找到状态行。 */
@Composable
private fun FloorControls(
    descending: Boolean,
    posterOnly: Boolean,
    onToggleOrder: () -> Unit,
    onTogglePoster: () -> Unit,
    onJump: () -> Unit = {},
    jumpStatus: ThreadViewModel.JumpState = ThreadViewModel.JumpState.Idle,
) {
    Column(Modifier.fillMaxWidth()) {
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
            TextButton(onClick = onJump) {
                Text("跳楼")
            }
        }
        when (val js = jumpStatus) {
            is ThreadViewModel.JumpState.Searching -> Text(
                "正在定位… 第 ${js.page}/${js.total} 页",
                Modifier.padding(horizontal = 16.dp),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            is ThreadViewModel.JumpState.NotFound -> Text(
                "未找到目标楼层（可能已删除或超出扫描范围）",
                Modifier.padding(horizontal = 16.dp),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.error,
            )
            ThreadViewModel.JumpState.Idle -> {}
        }
    }
}

/** 楼层流（LazyListScope 扩展）：Data/Stale 两态都产出独立楼层项；Stale 先给横幅。 */
private fun LazyListScope.floorsItems(
    floorsState: UiState<ThreadRepository.FloorsPage>,
    applaudStates: Map<Long, WriteUi>,
    applaudedIds: Set<Long>,
    onApplaud: (Long) -> Unit,
    onRetryFloors: () -> Unit,
    onLoadMore: () -> Unit,
    onOpenImage: (List<String>, Int) -> Unit,
    onOpenUrl: (String) -> Unit = {},
    onDownloadAttachment: (String, String) -> Unit = { _, _ -> },
    onLocateQuote: (Long) -> Unit = {},
    onReplyToQuote: (Long) -> Unit = {},
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

        is UiState.RetryableError -> item(key = "floors_retry") {
            // R1 主修：失败不再静默空白——文案+重试钮（审计 R1）
            Column(Modifier.fillMaxWidth().padding(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally) {
                Text("楼层加载失败", style = MaterialTheme.typography.bodyLarge)
                TextButton(onClick = onRetryFloors) { Text("重试") }
            }
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
            floorEntries(fs.stale, applaudStates, applaudedIds, onApplaud, onOpenImage, onOpenUrl, onDownloadAttachment, onLocateQuote, onReplyToQuote)
        }

        is UiState.Data<ThreadRepository.FloorsPage> -> {
            floorEntries(fs.value, applaudStates, applaudedIds, onApplaud, onOpenImage, onOpenUrl, onDownloadAttachment, onLocateQuote, onReplyToQuote)
            // R1：more 行不随筛选消失（挂在分支尾，page<totalPages 才显示）
            if (fs.value.page < fs.value.totalPages) {
                item(key = "more") {
                    TextButton(onClick = onLoadMore, modifier = Modifier.fillMaxWidth()) {
                        Text("加载更多楼层")
                    }
                }
            }
        }

        else -> {}
    }
}

private fun LazyListScope.floorEntries(
    fp: ThreadRepository.FloorsPage,
    applaudStates: Map<Long, WriteUi>,
    applaudedIds: Set<Long>,
    onApplaud: (Long) -> Unit,
    onOpenImage: (List<String>, Int) -> Unit,
    onOpenUrl: (String) -> Unit = {},
    onDownloadAttachment: (String, String) -> Unit = { _, _ -> },
    onLocateQuote: (Long) -> Unit = {},
    onReplyToQuote: (Long) -> Unit = {},
) {
    items(fp.floors, key = { "f_${it.pid}" }) { floor ->
        FloorCard(floor, applaudStates[floor.pid] ?: WriteUi(), floor.pid in applaudedIds, onApplaud, onOpenImage, onOpenUrl, onDownloadAttachment, onLocateQuote, onReplyToQuote)
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
    applaudState: WriteUi,
    isCollected: Boolean,
    collectState: WriteUi,
    onApplaud: () -> Unit,
    onCollect: () -> Unit,
    onShare: () -> Unit = {},
    onReport: () -> Unit = {},
) {
    Column(Modifier.padding(16.dp)) {
        Text(op.title, style = MaterialTheme.typography.titleLarge)
        Spacer(Modifier.height(8.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            AvatarPlaceholder(op.authorName, 36.dp)
            Spacer(Modifier.width(8.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    op.authorName ?: "匿名",
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    listOfNotNull(
                        // B0-4（V4 根因更正）：createTime 为裸 unix 秒（截图 1793284436 实证），
                        // 格式化后上屏——非 uid 泄漏
                        op.createTime?.let {
                            io.github.deserthouse.sdgun.data.repository.BoardListRepository
                                .relativeTime(it)
                        },
                        op.viewCount?.let { "查看 $it" },
                        op.replyCount?.let { "回复 $it" },
                    ).joinToString(" · "),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Spacer(Modifier.weight(1f))
            // N5/V02：分享/举报小图标钮并入作者行（不再独占一行）
            IconButton(onClick = onShare, modifier = Modifier.size(32.dp)) {
                Icon(
                    androidx.compose.material.icons.Icons.Filled.Share,
                    contentDescription = "分享",
                    modifier = Modifier.size(16.dp),
                )
            }
            IconButton(onClick = onReport, modifier = Modifier.size(32.dp)) {
                Icon(
                    Icons.Outlined.Flag,  // V02/Q2 修正：Flag 是扩展属性，全限定名不可解析（该行 V02 引入后从未真正编译——幻绿窗口实证）
                    contentDescription = "举报",
                    modifier = Modifier.size(16.dp),
                )
            }
        }
        Row(Modifier.padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            // S03 禁重复 + Q2 toggle：Done 不再是终态（已赞再点=取消，路由层
            // WriteOps.decideApplaud 分流）——仅 Pending 在途禁点
            TextButton(
                onClick = onApplaud,
                enabled = applaudState.phase != WriteUiPhase.Pending,
            ) {
                Text(
                    when (applaudState.phase) {
                        WriteUiPhase.Pending -> "点赞中…"
                        WriteUiPhase.Done -> if (applauded) "已点赞 · 取消" else "点赞"
                        WriteUiPhase.Failed -> "点赞失败"
                        WriteUiPhase.Verify -> "点赞待核对"
                        WriteUiPhase.Idle -> if (applauded) "已点赞" else "点赞"
                    }
                )
            }
            TextButton(
                onClick = onCollect,
                enabled = collectState.phase !in setOf(WriteUiPhase.Pending, WriteUiPhase.Done) &&
                    !(collectState.phase == WriteUiPhase.Idle && isCollected),
            ) {
                Text(
                    when (collectState.phase) {
                        WriteUiPhase.Pending -> "收藏中…"
                        WriteUiPhase.Done -> "已收藏"
                        WriteUiPhase.Failed -> "收藏失败"
                        WriteUiPhase.Verify -> "收藏待核对"
                        WriteUiPhase.Idle -> if (isCollected) "已收藏" else "收藏"
                    }
                )
            }
        }
        HorizontalDivider(Modifier.padding(top = 12.dp),
            color = MaterialTheme.colorScheme.outlineVariant)
    }
}
