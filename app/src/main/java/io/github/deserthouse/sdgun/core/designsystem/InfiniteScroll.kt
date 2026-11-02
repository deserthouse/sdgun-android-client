package io.github.deserthouse.sdgun.core.designsystem

import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp

/**
 * B1-4：无限滚动尾部预取——倒数 [buffer] 项进入视口即触发 [onLoadMore]。
 * loadingMore=true 期间不重复触发；调用方保证 hasMore=false 时不再发请求。
 */
@Composable
fun InfiniteScrollHandler(
    listState: LazyListState,
    isLoadingMore: Boolean,
    hasMore: Boolean,
    buffer: Int = 3,
    onLoadMore: () -> Unit,
) {
    val density = LocalDensity.current
    // R0-3（A31-S02）：参数/回调经 rememberUpdatedState 持最新——旧 remember(listState) 闭包
    // 捕获首次组合的 isLoadingMore/hasMore/onLoadMore，后续值变化不生效（审查探针实锤）
    val currentLoading by androidx.compose.runtime.rememberUpdatedState(isLoadingMore)
    val currentHasMore by androidx.compose.runtime.rememberUpdatedState(hasMore)
    val currentLoadMore by androidx.compose.runtime.rememberUpdatedState(onLoadMore)
    val shouldLoad = remember(listState) {
        derivedStateOf {
            if (currentLoading || !currentHasMore) return@derivedStateOf false
            val total = listState.layoutInfo.totalItemsCount
            if (total == 0) return@derivedStateOf false
            val lastVisible = listState.layoutInfo.visibleItemsInfo.maxOfOrNull { it.index + 1 } ?: 0
            if (lastVisible < total - buffer) return@derivedStateOf false
            // 视口底部已接近列表尾（含不足 buffer 屏余量的短列表）
            val lastItemEnd = listState.layoutInfo.visibleItemsInfo
                .maxOfOrNull { it.offset + it.size } ?: 0
            val viewport = listState.layoutInfo.viewportEndOffset
            val nearEnd = lastItemEnd >= viewport - with(density) { 200.dp.toPx() }
            lastVisible >= total - buffer || nearEnd
        }
    }
    LaunchedEffect(listState, shouldLoad) {
        snapshotFlow { shouldLoad.value }
            .collect { if (it) currentLoadMore() }
    }
}
