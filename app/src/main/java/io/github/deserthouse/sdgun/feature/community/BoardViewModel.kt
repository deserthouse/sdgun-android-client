package io.github.deserthouse.sdgun.feature.community

import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import io.github.deserthouse.sdgun.core.model.UiState
import io.github.deserthouse.sdgun.data.repository.BoardListRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger

/**
 * 板块页状态所有者（A24-06）：与 ThreadViewModel 同构——代际+取消+互斥分页+空尾页原子收尾。
 * fetcher/meta 注入点供 JVM 反例。
 */
class BoardViewModel(
    private val scope: CoroutineScope,
    private val fetchPage: suspend (fid: Long, page: Int) -> BoardListRepository.PageResult? =
        { fid, page -> BoardListRepository.fetchPage(fid, page) },
    private val fetchMeta: suspend (fid: Long) -> BoardListRepository.BoardMeta? =
        { fid -> BoardListRepository.fetchBoardMeta(fid) },
) {

    var listState by androidx.compose.runtime.mutableStateOf<UiState<BoardListRepository.PageResult>>(UiState.InitialLoading)
        private set
    var meta by androidx.compose.runtime.mutableStateOf<BoardListRepository.BoardMeta?>(null)
        private set

    private val rows = androidx.compose.runtime.mutableStateListOf<BoardListRepository.ThreadRow>()
    private var page = 1
    private var totalPages = 1
    private val gen = AtomicInteger(0)
    private val paging = AtomicBoolean(false)
    private var job: Job? = null

    fun start(fid: Long) {
        val g = gen.incrementAndGet()
        job?.cancel()
        paging.set(false)
        listState = UiState.InitialLoading
        rows.clear(); page = 1; totalPages = 1
        meta = null
        job = scope.launch {
            val pr = fetchPage(fid, 1)
            if (gen.get() != g) return@launch
            applyFirst(pr)
            if (pr != null) {
                val m = fetchMeta(fid)
                if (gen.get() == g) meta = m
            }
        }
    }

    fun loadMore(fid: Long) {
        if (paging.get() || listState !is UiState.Data<*>) return  // A24-06：首屏在途禁翻页（反例：空尾页竞态）
        val g = gen.get()
        paging.set(true)
        job = scope.launch {
            val pr = fetchPage(fid, page + 1)
            paging.set(false)
            if (gen.get() != g) return@launch
            when {
                pr == null -> listState = UiState.Stale(
                    BoardListRepository.PageResult(rows.toList(), page, totalPages),
                    "下一页加载失败，可重试",
                )
                pr.rows.isEmpty() -> {  // 空尾页原子收尾
                    totalPages = page
                    listState = UiState.Data(BoardListRepository.PageResult(rows.toList(), page, totalPages))
                }
                else -> {
                    page = pr.page
                    totalPages = pr.totalPages
                    val merged = (rows.toList() + pr.rows).distinctBy { it.tid }
                    rows.clear(); rows.addAll(merged)
                    listState = UiState.Data(BoardListRepository.PageResult(merged, page, totalPages))
                }
            }
        }
    }

    private fun applyFirst(pr: BoardListRepository.PageResult?) {
        listState = when {
            pr == null -> UiState.RetryableError("板块加载失败")
            pr.authRequired -> UiState.AuthRequired
            pr.errorMsg != null -> UiState.RetryableError(pr.errorMsg)
            pr.rows.isEmpty() -> UiState.Empty("该板块暂无帖子")
            else -> {
                page = pr.page; totalPages = pr.totalPages
                rows.clear(); rows.addAll(pr.rows)
                UiState.Data(pr)
            }
        }
    }
}
