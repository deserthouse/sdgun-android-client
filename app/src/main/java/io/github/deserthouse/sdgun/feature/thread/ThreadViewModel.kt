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
 * 帖子页状态所有者（A24-06→WP5 重构）：查询代际+取消+互斥分页。
 *
 * WP5/A1104-S06：尾行状态 [Footer] 单一所有权——分页失败不再占用 floorsState=Stale
 * （旧行保留为 Data，失败语义独立在 footer=Failed，UI 单一尾行渲染重试）。
 * WP5/A1104-S05：跳楼扫描 [jumpScan] 直取 fetchFloors typed 结果（不再轮询 UI 态
 * /调 loadMore——Data 门把暂败扫死成 NotFound 误报），传输失败同页重试 3 次。
 * fetcher 注入点供 JVM 反例。
 */
class ThreadViewModel(
    private val scope: CoroutineScope,
    private val fetchOp: suspend (tid: Long, fid: Long) -> ThreadRepository.ThreadOP? =
        { tid, fid -> ThreadRepository.fetchOP(tid, fid) },
    private val fetchFloors: suspend (tid: Long, page: Int, descending: Boolean, authorId: Long?) -> ThreadRepository.FloorsPage? =
        { tid, page, desc, aid -> ThreadRepository.fetchFloors(tid, page, desc, aid) },
    /** 跳楼轮询间隔（测试注入 1ms；生产 50ms） */
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

    /** WP5/A1104-S06：尾行状态（单一所有权；floorsItems 唯一消费点） */
    sealed class Footer {
        object More : Footer()
        object Loading : Footer()
        data class Failed(val page: Int) : Footer()
        object End : Footer()
    }

    var footer by androidx.compose.runtime.mutableStateOf<Footer>(Footer.Loading)
        private set

    private var authorId: Long? = null
    private val floors = androidx.compose.runtime.mutableStateListOf<ThreadRepository.Floor>()
    /** 当前页/总页（internal setter private，getter public 供 JVM 反例断言） */
    var floorPage = 1
        private set
    var floorTotal = 1
        private set
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
        footer = Footer.Loading
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

    /** WP5：Footer.Failed 的重试（重取失败页，非全量重启） */
    fun retryFooter(tid: Long) {
        if (footer !is Footer.Failed) return
        loadMore(tid)
    }

    private fun restartFloors(tid: Long) {
        val g = gen.incrementAndGet()
        floorsJob?.cancel()
        paging.set(false)
        floors.clear()
        floorPage = 1
        floorsState = UiState.InitialLoading
        footer = Footer.Loading
        loadFloorsInternal(tid, 1, g)
    }

    /** 加载更多（互斥；终页/空尾页零越界；Failed 重试同页） */
    fun loadMore(tid: Long) {
        if (paging.get() || footer is Footer.Loading) return
        if (floorPage >= floorTotal && footer !is Footer.Failed) return  // 已知终页零越界（S05 关闭门）
        val g = gen.get()
        paging.set(true)
        footer = Footer.Loading
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
                    footer = Footer.End
                } else {
                    // WP5/S05：暂败不再占用 floorsState=Stale——旧行保留 Data，失败语义归 footer
                    floorsState = UiState.Data(
                        ThreadRepository.FloorsPage(floors.toList(), floorPage, floorTotal)
                    )
                    footer = Footer.Failed(floorPage + 1)
                }
            } else if (fp.floors.isEmpty()) {
                if (page == 1) {
                    floorsState = UiState.Empty("暂无楼层")
                    footer = Footer.End
                } else {
                    floorTotal = floorPage  // 空尾页原子收尾：不再可加载
                    floorsState = UiState.Data(
                        ThreadRepository.FloorsPage(floors.toList(), floorPage, floorTotal)
                    )
                    footer = Footer.End
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
                footer = if (floorPage >= floorTotal) Footer.End else Footer.More
            }
            onDone()
        }
    }

    // ── 跳楼（WP5/A1104-S05：直取 typed fetch，不再轮询 UI 态）──

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
     * （直取 fetchFloors，失败同页重试 ≤3 次，护栏 ≤100 页）。用户此前的
     * 倒序/只看楼主选择会被跳楼重置（跳楼需要位置序，KDoc 如实披露）。
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
        jumpJob = scope.launch { jumpScan(tid, targetPid, targetFloor) }
    }

    /** WP5/S05：直取扫描（非 UI 轮询）。传回新页追加进 floors；命中即定位。 */
    private suspend fun jumpScan(tid: Long, targetPid: Long?, targetFloor: Int?) {
        descending = false
        posterOnly = false
        restartFloors(tid)
        // WP5 修正：g 必须在 restartFloors 之后捕获（restart 自增 gen，先捕获=全程代际错配）
        val g = gen.get()
        // 等首屏就绪（restartFloors 是异步的）
        var waits = 0
        while (floorsState is UiState.InitialLoading && gen.get() == g && waits < 200) {
            kotlinx.coroutines.delay(jumpPollMs); waits++
        }
        if (gen.get() != g) return
        if (floorsState !is UiState.Data<*> && floorsState !is UiState.Empty) {
            jumpState = JumpState.NotFound(targetPid, targetFloor); return
        }
        // 逐页直取（首屏已含 p1；从 p2 开始）
        var page = floorPage + 1
        while (page <= minOf(floorTotal, 100) && gen.get() == g) {
            jumpState = JumpState.Searching(targetPid, targetFloor, page, floorTotal)
            // 同页重试 ≤3 次（传输失败≠真正NotFound）
            var fp: ThreadRepository.FloorsPage? = null
            var attempts = 0
            while (attempts < 3 && gen.get() == g) {
                fp = fetchFloors(tid, page, false, null)
                if (fp != null) break
                attempts++
                kotlinx.coroutines.delay(jumpPollMs * 6)
            }
            if (gen.get() != g) return
            if (fp == null) {
                jumpState = JumpState.NotFound(targetPid, targetFloor); return  // 诚实：连败不假称扫完
            }
            if (fp.floors.isEmpty()) {
                jumpState = JumpState.NotFound(targetPid, targetFloor); return  // 真正扫完
            }
            // 追加新页
            val merged = (floors.toList() + fp.floors).distinctBy { it.pid }
            floors.clear()
            floors.addAll(merged)
            floorPage = fp.page
            floorTotal = fp.totalPages
            floorsState = UiState.Data(
                ThreadRepository.FloorsPage(merged, floorPage, floorTotal)
            )
            // 命中检查
            val hit = floors.firstOrNull {
                it.pid == targetPid || (targetFloor != null && it.floor == targetFloor)
            }
            if (hit != null) {
                jumpState = JumpState.Idle
                pendingLocate = hit.pid
                return
            }
            page = floorPage + 1
        }
        jumpState = JumpState.NotFound(targetPid, targetFloor)  // 护栏耗尽/扫完
    }
}
