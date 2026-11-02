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
 * R5（U3-a）：order/typeId 筛选状态入 VM，筛选变化=新代际重拉（旧包整弃+游标复位）。
 * fetcher/meta 注入点供 JVM 反例。
 */
class BoardViewModel(
    private val scope: CoroutineScope,
    private val fetchPage: suspend (fid: Long, page: Int, order: String, typeId: String) -> BoardListRepository.PageResult? =
        { fid, page, order, typeId -> BoardListRepository.fetchPage(fid, page, order, typeId) },
    private val fetchMeta: suspend (fid: Long) -> BoardListRepository.BoardMeta? =
        { fid -> BoardListRepository.fetchBoardMeta(fid) },
) {

    var listState by androidx.compose.runtime.mutableStateOf<UiState<BoardListRepository.PageResult>>(UiState.InitialLoading)
        private set
    var meta by androidx.compose.runtime.mutableStateOf<BoardListRepository.BoardMeta?>(null)
        private set
    var order by androidx.compose.runtime.mutableStateOf("")
        private set
    var typeId by androidx.compose.runtime.mutableStateOf("")
        private set

    private val rows = androidx.compose.runtime.mutableStateListOf<BoardListRepository.ThreadRow>()
    private var page = 1
    private var totalPages = 1
    private val gen = AtomicInteger(0)
    /** R0-3（A31-S02）：分页在途改 Compose 状态（UI 响应式；原 AtomicBoolean 不触发重组） */
    var paging by androidx.compose.runtime.mutableStateOf(false)
        private set
    private var job: Job? = null

    fun start(fid: Long) {
        order = ""; typeId = ""
        meta = null
        val g = gen.incrementAndGet()
        job?.cancel()
        paging = false
        listState = UiState.InitialLoading
        rows.clear(); page = 1; totalPages = 1
        job = scope.launch {
            val pr = fetchPage(fid, 1, order, typeId)
            if (gen.get() != g) return@launch
            applyFirst(pr)
            if (pr != null) {
                val m = fetchMeta(fid)
                if (gen.get() == g) meta = m
            }
        }
    }

    /** R5：筛选变化=新代际重拉；同值 no-op 防重复请求；板块元数据与筛选无关，不清除。 */
    fun setFilter(fid: Long, newOrder: String, newTypeId: String) {
        if (newOrder == order && newTypeId == typeId) return
        order = newOrder; typeId = newTypeId
        val g = gen.incrementAndGet()
        job?.cancel()
        paging = false
        listState = UiState.InitialLoading
        rows.clear(); page = 1; totalPages = 1
        job = scope.launch {
            val pr = fetchPage(fid, 1, order, typeId)
            if (gen.get() != g) return@launch
            applyFirst(pr)
        }
    }

    /**
     * B1-1：下拉刷新——保留旧行进 Refreshing 态（不闪白）；
     * 成功换新首页（失败回 RetryableError 但保 rows 语义由 UiState.Stale 承载略简：失败保旧行可读）
     */
    fun refresh(fid: Long) {
        val g = gen.incrementAndGet()
        job?.cancel()
        paging = false
        val oldRows = rows.toList()
        val oldPage = page
        val oldTotal = totalPages
        if (oldRows.isNotEmpty()) {
            listState = UiState.Refreshing(BoardListRepository.PageResult(oldRows, page, totalPages))
        } else {
            listState = UiState.InitialLoading
        }
        rows.clear(); page = 1; totalPages = 1
        job = scope.launch {
            val pr = fetchPage(fid, 1, order, typeId)
            if (gen.get() != g) return@launch
            if (pr == null && oldRows.isNotEmpty()) {
                // R0-2（A31-S01）：刷新失败保旧行（Stale 可重试），页元数据一并回滚——
                // 旧实现走 applyFirst(null)=RetryableError 丢掉全部已取得内容（审查实锤）
                rows.clear(); rows.addAll(oldRows)
                page = oldPage; totalPages = oldTotal
                listState = UiState.Stale(
                    BoardListRepository.PageResult(oldRows, oldPage, oldTotal),
                    "刷新失败，内容仍可浏览，可重试",
                )
            } else {
                applyFirst(pr)
            }
        }
    }

    fun loadMore(fid: Long) {
        // R0-2（A31-S01）：Stale（刷新/分页失败保旧）态允许按原意图重试下一页——
        // 旧 !is Data 门把失败后的列表锁死成"只能离页重进"（审查实锤）
        if (paging ||
            (listState !is UiState.Data<*> && listState !is UiState.Stale<*>)
        ) return
        // R0-3：VM 终页自守——UI 门失效（旧预取缺陷）时也不越界多发（审查探针=终页后仍发请求）
        if (page >= totalPages) return
        val g = gen.get()
        paging = true
        job = scope.launch {
            val pr = fetchPage(fid, page + 1, order, typeId)
            paging = false
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
