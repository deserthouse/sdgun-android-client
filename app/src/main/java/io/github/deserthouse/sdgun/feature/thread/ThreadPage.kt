package io.github.deserthouse.sdgun.feature.thread

import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import io.github.deserthouse.sdgun.core.model.UiState
import io.github.deserthouse.sdgun.data.repository.ThreadRepository

/**
 * 帖子页（G2-A05）：主楼（threadView 独立端点）+楼层流（commentList 正/倒序+只看楼主）。
 * G0-C01a 契约：主楼不在 commentList；title 唯一源=threadView；倒序不丢主楼。
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
    modifier: Modifier = Modifier,
) {
    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = {
                    val t = (opState as? UiState.Data<ThreadRepository.ThreadOP>)?.value?.title
                    Text(
                        t ?: "帖子",
                        maxLines = 1,
                        overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                    )
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
                Column(Modifier.fillMaxSize().padding(padding)) {
                    ThreadOPCard(op)
                    HorizontalDivider()
                    FloorControls(
                        descending = descending, posterOnly = posterOnly,
                        onToggleOrder = onToggleOrder, onTogglePoster = onTogglePoster,
                    )
                    FloorsSection(
                        floorsState = floorsState, posterOnly = posterOnly,
                        onLoadMore = onLoadMore,
                    )
                }
            }

            else -> {}
        }
    }
}


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
            Text(if (descending) "倒序" else "正序")
        }
        TextButton(onClick = onTogglePoster) {
            Text(if (posterOnly) "全部楼层" else "只看楼主")
        }
    }
}

@Composable
private fun FloorsSection(
    floorsState: UiState<ThreadRepository.FloorsPage>,
    posterOnly: Boolean,
    onLoadMore: () -> Unit,
) {
    when (val fs = floorsState) {
        is UiState.InitialLoading -> Column(
            Modifier.fillMaxWidth().padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) { CircularProgressIndicator() }

        is UiState.Empty -> Text(
            "暂无楼层",
            modifier = Modifier.fillMaxWidth().padding(16.dp),
        )

        is UiState.Data<*> -> {
            val fp = fs.value as? ThreadRepository.FloorsPage
            if (fp != null) {
                LazyColumn(Modifier.fillMaxSize()) {
                    fp.floors.forEach { floor ->
                        item(key = "f_${floor.pid}") {
                            FloorCard(floor)
                        }
                    }
                    if (fp.page < fp.totalPages && !posterOnly) {
                        item(key = "_more") {
                            TextButton(
                                onClick = onLoadMore,
                                modifier = Modifier.fillMaxWidth(),
                            ) { Text("加载更多楼层") }
                        }
                    }
                }
            }
        }

        else -> {}
    }
}
