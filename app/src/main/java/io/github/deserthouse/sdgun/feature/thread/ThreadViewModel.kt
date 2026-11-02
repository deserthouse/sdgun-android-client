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
    /** Q1 跳楼轮询间隔（测试注入 1ms；生产 50ms） */
    private val jumpPollMs: Long = 50,
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

    // ── Q1 跳楼（B2-2 与 S07 pid 定位合流；A1-5 跨页二分挂账兑现）──

    /** 跳楼状态：Idle / 扫描中（pid 或楼层号 + 当前进度） / 未找到 */
    sealed class JumpState {
        object Idle : JumpState()
        data class Searching(val pid: Long?, val floor: Int?, val page: Int, val total: Int) : JumpState()
        data class NotFound(val pid: Long?, val floor: Int?) : JumpState()
    }

    var jumpState by androidx.compose.runtime.mutableStateOf<JumpState>(JumpState.Idle)
        private set

    /** 已命中待滚动目标（pid 在当前 floors 列表内）；UI 滚动完成后调 [consumeLocate] */
    var pendingLocate by androidx.compose.runtime.mutableStateOf<Long?>(null)
        private set

    private var jumpJob: Job? = null

    fun clearLocate() { pendingLocate = null }

    /**
     * 跳转到目标楼层：已加载 → 即时定位；未加载 → 切正序+全部楼层后逐页扫描
     * （护栏 ≤100 页），命中即定位。用户此前的倒序/只看楼主选择会被跳楼重置
     * （跳楼定义上需要位置序，KDoc 如实披露）。
     */
    fun jumpTo(tid: Long, targetPid: Long? = null, targetFloor: Int? = null) {
        if (targetPid == null && targetFloor == null) return
        // 已加载层内命中（含倒序/只看楼主过滤下的当前列表）——即时定位，不动筛选
        floors.firstOrNull { it.pid == targetPid || (targetFloor != null && it.floor == targetFloor) }
            ?.let { hit ->
                jumpState = JumpState.Idle
                pendingLocate = hit.pid
                return
            }
        jumpJob?.cancel()
        jumpState = JumpState.Searching(targetPid, targetFloor, 1, 0)
        jumpJob = scope.launch {
            descending = false
            posterOnly = false
            restartFloors(tid)
            var staleTicks = 0
            var scanned = 0
            while (scanned < 100) {
                kotlinx.coroutines.delay(jumpPollMs)
                val st = floorsState
                when {
                    st is UiState.InitialLoading -> continue  // 首页在途
                    st is UiState.RetryableError || st is UiState.Empty -> {
                        jumpState = JumpState.NotFound(targetPid, targetFloor); return@launch
                    }
                    st is UiState.Stale<*> -> {
                        // 分页传输失败：稍候重试 loadMore（被 Data 门挡住则持续等）；
                        // 持续 Stale（网络中断）→ 诚实未找到，不假称扫完
                        if (++staleTicks > 40) {
                            jumpState = JumpState.NotFound(targetPid, targetFloor); return@launch
                        }
                        loadMore(tid)
                        continue
                    }
                }
                staleTicks = 0
                val hit = floors.firstOrNull {
                    it.pid == targetPid || (targetFloor != null && it.floor == targetFloor)
                }
                if (hit != null) {
                    jumpState = JumpState.Idle
                    pendingLocate = hit.pid
                    return@launch
                }
                if (floorPage >= floorTotal) {
                    jumpState = JumpState.NotFound(targetPid, targetFloor); return@launch
                }
                loadMore(tid)
                scanned++
                jumpState = JumpState.Searching(targetPid, targetFloor, floorPage, floorTotal)
            }
            jumpState = JumpState.NotFound(targetPid, targetFloor)  // 护栏耗尽
        }
    }
}
