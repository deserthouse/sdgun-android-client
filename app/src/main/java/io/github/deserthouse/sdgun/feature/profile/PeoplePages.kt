package io.github.deserthouse.sdgun.feature.profile

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.material3.TextButton
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.width
import androidx.compose.material3.HorizontalDivider
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.github.deserthouse.sdgun.core.model.UiState
import io.github.deserthouse.sdgun.data.repository.ProfileRepository

/**
 * U3-b（F18/19）：访客/浏览历史/关注三列表页 + 收藏页（合成先行）。
 * 读接口在库（myVisitor/myViewUser/myFollowUserList）但真实数据需凭据——
 * 合成三态（设置 debug 区 profileFake）保证路由/权限/导航可走查，真实接入为增量。
 * 收藏读端点不在测绘（userCollectAdd 只证写面）——真实路径给诚实未接入态，不冒充空。
 */

enum class PeopleKind(val title: String) {
    VISITORS("访客"),
    HISTORY("浏览历史"),
    FOLLOWS("关注"),
}

data class PeoplePageData(val people: List<ProfileRepository.PersonItem>)

/** 收藏项（帖子维度，与 PersonItem 异形）；tid<=0=合成夹具，点击禁用；link=真链行外开 */
data class CollectItem(
    val tid: Long,
    val title: String,
    val extra: String?,
    val link: String? = null,
    /** Q2：收藏记录 id（userCollectList 行 id 键）——collectDelete GET id 依赖；空=合成/旧缓存不可删 */
    val recordId: String = "",
)

data class CollectPageData(val items: List<CollectItem>)

/**
 * 装载器工厂：合成三态/游客门/真实读链的唯一决策点（JVM 可测=PeopleLoaderTest）。
 * fakeMode: off/data/empty/error（AppPrefs.profileFake，仅 debug 暴露切换）。
 */
fun peopleLoader(
    kind: PeopleKind,
    token: String?,
    fakeMode: String,
    // Q4（A0-6）：三型结果——AuthExpired→AuthRequired 引导重登（不串私有内容不误导重试）
    fetchReal: suspend (String) -> ProfileRepository.PrivateResult<List<ProfileRepository.PersonItem>>,
): suspend () -> UiState<PeoplePageData> = {
    when {
        fakeMode == "data" -> UiState.Data(PeoplePageData(syntheticPeople(kind)))
        fakeMode == "empty" -> UiState.Empty("暂无内容")
        fakeMode == "error" -> UiState.RetryableError("（合成）加载失败，可重试")
        token.isNullOrBlank() -> UiState.AuthRequired
        else -> when (val r = fetchReal(token)) {
            is ProfileRepository.PrivateResult.Ok -> when {
                r.value.isEmpty() -> UiState.Empty("暂无内容")
                else -> UiState.Data(PeoplePageData(r.value))
            }
            ProfileRepository.PrivateResult.AuthExpired -> UiState.AuthRequired
            ProfileRepository.PrivateResult.Failed -> UiState.RetryableError("加载失败，可重试")
        }
    }
}

/** 收藏装载器：读端点待测绘——真实路径=诚实未接入态（不冒充空/失败） */
fun collectLoader(
    token: String?,
    fakeMode: String,
): suspend () -> UiState<CollectPageData> = {
    when {
        fakeMode == "data" -> UiState.Data(CollectPageData(syntheticCollects()))
        fakeMode == "empty" -> UiState.Empty("暂无收藏")
        fakeMode == "error" -> UiState.RetryableError("（合成）加载失败，可重试")
        token.isNullOrBlank() -> UiState.AuthRequired
        else -> UiState.RetryableError("收藏列表读取待接口接入（真实数据暂不可读）")
    }
}

/** 合成夹具：与真实 PersonItem 同形（tid/userId 为假值，仅 debug 合成模式可见） */
private fun syntheticPeople(kind: PeopleKind): List<ProfileRepository.PersonItem> {
    val extraPrefix = when (kind) {
        PeopleKind.VISITORS -> "访问于 "
        PeopleKind.HISTORY -> "浏览于 "
        PeopleKind.FOLLOWS -> "关注于 "
    }
    return (1..4).map { i ->
        ProfileRepository.PersonItem(
            userId = -(100L + i),  // 负值=合成标记，真实 uid 恒正
            name = "合成用户$i（${kind.title}）",
            head = null,
            extra = extraPrefix + "2026-10-2$i",
        )
    }
}

private fun syntheticCollects(): List<CollectItem> = listOf(
    CollectItem(0, "（合成）原载便携弓制作分享", "收藏于 2026-10-21"),
    CollectItem(0, "（合成）新手入门：皮筋选购指南", "收藏于 2026-10-22"),
    CollectItem(0, "（合成）比赛规则解读帖", "收藏于 2026-10-24"),
)

/** 通用八态渲染（F18/19 四页共用）：AuthRequired 给登录引导文案 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun <T> PeopleScaffold(
    title: String,
    state: UiState<T>,
    onBack: () -> Unit,
    onRetry: () -> Unit,
    listContent: @Composable (T) -> Unit,
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(title) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface),
            )
        },
        containerColor = MaterialTheme.colorScheme.surface,
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            when (state) {
                is UiState.InitialLoading -> Column(
                    Modifier.fillMaxSize(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                ) { Text("加载中…", style = MaterialTheme.typography.bodyLarge) }

                is UiState.Empty -> Column(
                    Modifier.fillMaxSize(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                ) { Text(state.message ?: "暂无内容", style = MaterialTheme.typography.bodyLarge) }

                is UiState.RetryableError -> Column(
                    Modifier.fillMaxSize().padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                ) {
                    Text(state.error, style = MaterialTheme.typography.bodyLarge)
                    Button(onClick = onRetry, modifier = Modifier.padding(top = 12.dp)) {
                        Text("重试")
                    }
                }

                is UiState.AuthRequired -> Column(
                    Modifier.fillMaxSize().padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                ) {
                    Text("登录后可查看${title}", style = MaterialTheme.typography.bodyLarge)
                    Button(onClick = onBack, modifier = Modifier.padding(top = 12.dp)) {
                        Text("返回去登录")
                    }
                }

                is UiState.Data<*> -> {
                    @Suppress("UNCHECKED_CAST")
                    listContent(state.value as T)
                }

                else -> {}
            }
        }
    }
}

/** 访客/历史/关注列表体 */
@Composable
fun PeopleListBody(
    data: PeoplePageData,
    onOpenUser: ((Long) -> Unit)? = null,
) {
    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(vertical = 6.dp),
    ) {
        items(data.people) { p ->
            // A2-3 前半：真实行（userId>0）可下钻他人主页；合成行（负 id）禁点
            val clickable = onOpenUser != null && p.userId > 0
            ListItem(
                leadingContent = {
                    // 头像占位：名字首字符圆形底（真实 head 字段接入后换网络图）
                    Column(
                        Modifier.size(40.dp).clip(CircleShape),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center,
                    ) {
                        Text(
                            p.name.take(1),
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                        )
                    }
                },
                headlineContent = { Text(p.name, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                supportingContent = { p.extra?.let { Text(it) } },
                modifier = Modifier.fillMaxWidth()
                    .then(
                        if (clickable) Modifier.clickable { onOpenUser?.invoke(p.userId) }
                        else Modifier,
                    ),
            )
        }
    }
}

/** 收藏列表体：tid>0 才可跳帖（合成夹具 tid=0 禁点击）；Q2：recordId 非空行可取消收藏 */
@Composable
fun CollectListBody(
    data: CollectPageData,
    onOpenThread: (Long) -> Unit,
    onOpenUrl: ((String) -> Unit)? = null,
    onRemove: ((CollectItem) -> Unit)? = null,
    removingIds: Set<String> = emptySet(),
) {
    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        items(data.items, key = { "${it.tid}_${it.title}" }) { c ->
            Card(
                modifier = Modifier.fillMaxWidth(),
                enabled = c.tid > 0 || c.link != null,
                onClick = {
                    when {
                        c.tid > 0 -> onOpenThread(c.tid)
                        c.link != null && onOpenUrl != null -> onOpenUrl(c.link)
                    }
                },
            ) {
                Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(c.title, style = MaterialTheme.typography.titleSmall)
                        c.extra?.let {
                            Text(
                                it,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                    // Q2：取消收藏（jadx OperationHelper.collectDelete GET id）——
                    // 删除是破坏性动作，走确认对话框（调用方管理），行内只在途/可用两态
                    if (c.recordId.isNotBlank() && onRemove != null) {
                        val removing = c.recordId in removingIds
                        TextButton(
                            onClick = { onRemove(c) },
                            enabled = !removing,
                        ) { Text(if (removing) "…" else "取消收藏", style = MaterialTheme.typography.labelSmall) }
                    }
                }
            }
        }
    }
}


/** N1/A2-3：他人主页（getUserInfo POST id= 官方契约复用；错误态诚实，不冒充空）；
 *  Q2：关注/取消关注（fansAdd/removeRelation GET user_id，jadx UserHomeFragment 同构） */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun UserProfilePage(
    uid: Long,
    state: UiState<ProfileRepository.UserProfile>,
    onBack: () -> Unit,
    onRetry: () -> Unit,
    /** relation_status（null=响应未携带→不渲染关注钮，诚实未知） */
    following: Boolean? = null,
    followState: io.github.deserthouse.sdgun.core.model.WriteUi =
        io.github.deserthouse.sdgun.core.model.WriteUi(),
    onFollowToggle: (() -> Unit)? = null,
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("用户主页", maxLines = 1) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface),
            )
        },
        containerColor = MaterialTheme.colorScheme.surface,
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            when (val st = state) {
                is UiState.InitialLoading -> Column(
                    Modifier.fillMaxSize(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                ) { Text("加载中…", style = MaterialTheme.typography.bodyLarge) }

                is UiState.RetryableError -> Column(
                    Modifier.fillMaxSize(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                ) {
                    Text(st.error, style = MaterialTheme.typography.bodyLarge)
                    TextButton(onClick = onRetry, modifier = Modifier.padding(top = 12.dp)) {
                        Text("重试")
                    }
                }

                is UiState.Data<*> -> {
                    val u = st.value as ProfileRepository.UserProfile
                    Column(Modifier.fillMaxWidth().padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Column(
                                Modifier.size(48.dp).clip(androidx.compose.foundation.shape.CircleShape),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.Center,
                            ) {
                                Text(u.name.take(1), style = MaterialTheme.typography.titleLarge,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer)
                            }
                            Spacer(Modifier.width(12.dp))
                            Text(u.name, style = MaterialTheme.typography.headlineSmall)
                        }
                        // Q2：关注 toggle（S03 同款相位纪律：Pending 禁点；Failed 显错不翻状态；
                        // Unknown→Verify 由调用方核对对话框把关后重发）
                        if (following != null && onFollowToggle != null) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Button(
                                    onClick = onFollowToggle,
                                    enabled = followState.phase !=
                                        io.github.deserthouse.sdgun.core.model.WriteUiPhase.Pending,
                                ) {
                                    Text(
                                        when (followState.phase) {
                                            io.github.deserthouse.sdgun.core.model.WriteUiPhase.Pending -> "…"
                                            else -> if (following) "取消关注" else "关注"
                                        },
                                    )
                                }
                                followState.reason?.let {
                                    Spacer(Modifier.width(8.dp))
                                    Text(
                                        it,
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.error,
                                    )
                                }
                            }
                        }
                        u.groupName?.let { Text("用户组：$it", style = MaterialTheme.typography.bodyMedium) }
                        u.score?.let { Text("积分：$it", style = MaterialTheme.typography.bodyMedium) }
                        u.sign?.let { Text("签名：$it", style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant) }
                        HorizontalDivider(Modifier.padding(vertical = 4.dp))
                        Text("他的帖子/动态：待后续版本",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }

                else -> {}
            }
        }
    }
}


/** N1/A2-4：本地帖阅读历史页（条目可点回帖、可单条移除；纯本机存储） */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LocalHistoryPage(
    entries: List<io.github.deserthouse.sdgun.data.repository.ThreadHistoryStore.Entry>,
    onBack: () -> Unit,
    onOpenThread: (Long) -> Unit,
    onRemove: (Long) -> Unit,
) {
    Scaffold(
        containerColor = MaterialTheme.colorScheme.surface,
        topBar = {
            TopAppBar(
                title = { Text("帖子历史") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface),
            )
        },
    ) { padding ->
        if (entries.isEmpty()) {
            Column(
                Modifier.fillMaxSize().padding(padding),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                io.github.deserthouse.sdgun.core.designsystem.EmptyState(
                    title = "暂无帖子历史",
                    hint = "打开过的帖子会记录在这里（仅本机保存）",
                )
            }
        } else {
            LazyColumn(
                Modifier.fillMaxSize().padding(padding),
                contentPadding = PaddingValues(vertical = 6.dp),
            ) {
                items(entries, key = { it.tid }) { e ->
                    ListItem(
                        headlineContent = { Text(e.title, maxLines = 2) },
                        supportingContent = {
                            Text("阅读于 " + io.github.deserthouse.sdgun.data.repository
                                .BoardListRepository.relativeTime(e.at.toString()))
                        },
                        modifier = Modifier.fillMaxWidth()
                            .clickable { onOpenThread(e.tid) },
                        trailingContent = {
                            TextButton(onClick = { onRemove(e.tid) }) { Text("移除") }
                        },
                    )
                }
            }
        }
    }
}
