package io.github.deserthouse.sdgun.feature.thread

import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import io.github.deserthouse.sdgun.core.model.UiState
import io.github.deserthouse.sdgun.data.repository.ThreadRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicBoolean

/**
 * 帖子页状态所有者（A24-06）：查询代际+取消+互斥分页。
 *
 * - 每次意图变化（切序/只看楼主/重开）推进 gen 并取消旧 Job——迟到旧结果整包丢弃；
 * - 分页互斥：同帖加载更多在途时忽略重复触发；
 * - 空尾页：原子收尾（totalPages=page，不再留在"可加载"态）；
 * - 失败/鉴权态带可执行恢复动作（retry/reload 由 UI 绑定）。
 * fetcher 注入点供 JVM 反例（乱序/双击/空尾页）。
 */
class ThreadViewModel(
    private val scope: CoroutineScope,
    private val fetchOp: suspend (tid: Long, fid: Long) -> ThreadRepository.ThreadOP? =
        { tid, fid -> ThreadRepository.fetchOP(tid, fid) },
    private val fetchFloors: suspend (tid: Long, page: Int, descending: Boolean, authorId: Long?) -> ThreadRepository.FloorsPage? =
        { tid, page, desc, aid -> ThreadRepository.fetchFloors(tid, page, desc, aid) },
) {

    var opState by androidx.compose.runtime.mutableStateOf<UiState<ThreadRepository.ThreadOP>>(UiState.InitialLoading)
        private set
    var floorsState by androidx.compose.runtime.mutableStateOf<UiState<ThreadRepository.FloorsPage>>(UiState.InitialLoading)
        private set
    var descending by androidx.compose.runtime.mutableStateOf(false)
        private set
    var posterOnly by androidx.compose.runtime.mutableStateOf(false)
        private set

    private var authorId: Long? = null
    private val floors = androidx.compose.runtime.mutableStateListOf<ThreadRepository.Floor>()
    private var floorPage = 1
    private var floorTotal = 1
    private val gen = AtomicInteger(0)
    private val paging = AtomicBoolean(false)
    private var floorsJob: Job? = null
    private var opJob: Job? = null

    /** 进入帖子（或换帖）：全量复位+新代际 */
    fun start(tid: Long, fid: Long) {
        val g = gen.incrementAndGet()
        opJob?.cancel(); floorsJob?.cancel()
        paging.set(false)
        opState = UiState.InitialLoading
        floorsState = UiState.InitialLoading
        floors.clear()
        floorPage = 1; floorTotal = 1
        authorId = null
        opJob = scope.launch {
            val op = fetchOp(tid, fid)
            if (gen.get() != g) return@launch
            opState = if (op == null) {
                UiState.RetryableError("主楼加载失败")
            } else {
                UiState.Data(op)
            }
            if (op != null) {
                authorId = op.authorId
                loadFloorsInternal(tid, 1, g)
            }
        }
    }

    fun toggleOrder(tid: Long) {
        descending = !descending
        restartFloors(tid)
    }

    fun togglePoster(tid: Long) {
        posterOnly = !posterOnly
        restartFloors(tid)
    }

    /** 失败态恢复：按当前筛选重拉楼层（A24-06：失败可操作） */
    fun retryFloors(tid: Long) = restartFloors(tid)

    private fun restartFloors(tid: Long) {
        val g = gen.incrementAndGet()
        floorsJob?.cancel()
        paging.set(false)
        floors.clear()
        floorPage = 1
        floorsState = UiState.InitialLoading
        loadFloorsInternal(tid, 1, g)
    }

    /** 加载更多（互斥；空尾页原子收尾） */
    fun loadMore(tid: Long) {
        if (paging.get() || floorsState !is UiState.Data<*>) return  // A24-06：首屏在途禁翻页（反例：空尾页竞态）
        val g = gen.get()
        paging.set(true)
        loadFloorsInternal(tid, floorPage + 1, g, onDone = { paging.set(false) })
    }

    private fun loadFloorsInternal(
        tid: Long,
        page: Int,
        g: Int,
        onDone: () -> Unit = {},
    ) {
        floorsJob = scope.launch {
            val fp = fetchFloors(tid, page, descending, if (posterOnly) authorId else null)
            if (gen.get() != g) { onDone(); return@launch }  // 迟到旧结果整包丢弃
            if (fp == null) {
                if (page == 1) {
                    floorsState = UiState.RetryableError("楼层加载失败")
                } else {
                    floorsState = UiState.Stale(
                        ThreadRepository.FloorsPage(floors.toList(), floorPage, floorTotal),
                        "下一页加载失败，可重试",
                    )
                }
            } else if (fp.floors.isEmpty()) {
                if (page == 1) {
                    floorsState = UiState.Empty("暂无楼层")
                } else {
                    floorTotal = floorPage  // 空尾页原子收尾：不再可加载
                    floorsState = UiState.Data(
                        ThreadRepository.FloorsPage(floors.toList(), floorPage, floorTotal)
                    )
                }
            } else {
                floorPage = fp.page
                floorTotal = fp.totalPages
                val merged = if (page == 1) fp.floors
                else (floors.toList() + fp.floors).distinctBy { it.pid }
                floors.clear()
                floors.addAll(merged)
                floorsState = UiState.Data(
                    ThreadRepository.FloorsPage(merged, floorPage, floorTotal)
                )
            }
            onDone()
        }
    }
}
