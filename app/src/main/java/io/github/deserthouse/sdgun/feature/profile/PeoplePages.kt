package io.github.deserthouse.sdgun.feature.profile

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
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

/** 收藏项（帖子维度，与 PersonItem 异形）；tid<=0=合成夹具，点击禁用 */
data class CollectItem(val tid: Long, val title: String, val extra: String?)

data class CollectPageData(val items: List<CollectItem>)

/**
 * 装载器工厂：合成三态/游客门/真实读链的唯一决策点（JVM 可测=PeopleLoaderTest）。
 * fakeMode: off/data/empty/error（AppPrefs.profileFake，仅 debug 暴露切换）。
 */
fun peopleLoader(
    kind: PeopleKind,
    token: String?,
    fakeMode: String,
    fetchReal: suspend (String) -> List<ProfileRepository.PersonItem>,
): suspend () -> UiState<PeoplePageData> = {
    when {
        fakeMode == "data" -> UiState.Data(PeoplePageData(syntheticPeople(kind)))
        fakeMode == "empty" -> UiState.Empty("暂无内容")
        fakeMode == "error" -> UiState.RetryableError("（合成）加载失败，可重试")
        token.isNullOrBlank() -> UiState.AuthRequired
        else -> {
            val list = fetchReal(token)
            if (list.isEmpty()) UiState.Empty("暂无内容") else UiState.Data(PeoplePageData(list))
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
fun PeopleListBody(data: PeoplePageData) {
    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(vertical = 6.dp),
    ) {
        items(data.people) { p ->
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
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

/** 收藏列表体：tid>0 才可跳帖（合成夹具 tid=0 禁点击） */
@Composable
fun CollectListBody(
    data: CollectPageData,
    onOpenThread: (Long) -> Unit,
) {
    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        items(data.items, key = { "${it.tid}_${it.title}" }) { c ->
            Card(
                modifier = Modifier.fillMaxWidth(),
                enabled = c.tid > 0,
                onClick = { if (c.tid > 0) onOpenThread(c.tid) },
            ) {
                Column(Modifier.padding(12.dp)) {
                    Text(c.title, style = MaterialTheme.typography.titleSmall)
                    c.extra?.let {
                        Text(
                            it,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }
    }
}
