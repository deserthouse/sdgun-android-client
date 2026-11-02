package io.github.deserthouse.sdgun.core.designsystem

import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.foundation.layout.BoxScope

/** B1-1：M3 下拉刷新统一封装（isRefreshing=true 时显示指示器不可再拉） */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun B1PullToRefresh(
    refreshing: Boolean,
    onRefresh: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable BoxScope.() -> Unit,
) {
    PullToRefreshBox(
        isRefreshing = refreshing,
        onRefresh = onRefresh,
        modifier = modifier,
        content = content,
    )
}
