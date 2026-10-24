package io.github.deserthouse.sdgun

import io.github.deserthouse.sdgun.core.model.UiState
import io.github.deserthouse.sdgun.data.repository.BoardListRepository
import io.github.deserthouse.sdgun.data.repository.ThreadRepository
import io.github.deserthouse.sdgun.feature.community.BoardViewModel
import io.github.deserthouse.sdgun.feature.thread.ThreadViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * A24-06 关闭门反例（审计原文）：乱序响应、双击分页、空尾页——只显示最新意图。
 */
class QueryGenerationTest {

    private fun op(tid: Long) = ThreadRepository.ThreadOP(
        tid = tid, pid = 1L, fid = 2L, title = "t", contentHtml = "",
        authorName = "a", authorId = 9L, avatarUrl = null, createTime = null,
        viewCount = null, replyCount = null, pics = emptyList(),
    )

    private fun floor(pid: Long) = ThreadRepository.Floor(
        pid = pid, floor = 2, authorName = "a", authorId = 1L, avatarUrl = null,
        contentHtml = "f$pid", pics = emptyList(), createTime = null,
        applaudCount = 0, isPublisher = false,
    )

    @Test
    fun `乱序楼层旧慢新快只显新意图`() = runBlocking {
        // 旧筛选（正序）慢回；新筛选（倒序）快回——旧结果必须整包丢弃
        var call = 0
        val vm = ThreadViewModel(
            this, fetchOp = { _, _ -> op(1) },
            fetchFloors = { _, page, descending, _ ->
                call++
                if (page == 1) {
                    if (descending) {           // 新意图（第二次调用）：快
                        ThreadRepository.FloorsPage(listOf(floor(100)), 1, 1)
                    } else {                    // 旧意图：慢 200ms
                        delay(200)
                        ThreadRepository.FloorsPage(listOf(floor(1)), 1, 1)
                    }
                } else null
            },
        )
        vm.start(1, 2)
        vm.toggleOrder(1)  // 立刻切意图（旧 Job 还在 delay）
        delay(400)         // 让两个都完成
        val st = vm.floorsState
        assertTrue("终态应为 Data", st is UiState.Data<*>)
        val fp = (st as UiState.Data<ThreadRepository.FloorsPage>).value
        assertEquals("只显新意图楼层", listOf(100L), fp.floors.map { it.pid })
    }

    @Test
    fun `双击分页只发一次`() = runBlocking {
        var calls = 0
        val vm = ThreadViewModel(
            this, fetchOp = { _, _ -> op(1) },
            fetchFloors = { _, page, _, _ ->
                calls++
                delay(150)  // 在途窗口
                ThreadRepository.FloorsPage(
                    (1..2).map { floor((page * 10 + it).toLong()) }, page, 5
                )
            },
        )
        vm.start(1, 2)
        delay(250)         // 首屏完成（Data 态才出现翻页入口）
        vm.loadMore(1)     // p2 在途（150ms）
        vm.loadMore(1)     // 双击：必须被互斥吞掉
        delay(400)
        assertEquals("分页请求次数=2（首屏+1 次翻页）", 2, calls)
    }

    @Test
    fun `空尾页原子收尾不再可加载`() = runBlocking {
        val vm = ThreadViewModel(
            this, fetchOp = { _, _ -> op(1) },
            fetchFloors = { _, page, _, _ ->
                if (page == 1) ThreadRepository.FloorsPage(listOf(floor(1)), 1, 3)
                else ThreadRepository.FloorsPage(emptyList(), page, 3)  // p2 空
            },
        )
        vm.start(1, 2)
        delay(150)   // 首屏先完成（真实 UI 里按钮只在数据态出现）
        vm.loadMore(1)
        delay(200)
        val st = vm.floorsState as UiState.Data<ThreadRepository.FloorsPage>
        assertEquals("空尾页收尾：totalPages 收到当前页", 1, st.value.totalPages)
    }

    @Test
    fun `板块乱序换代同样丢弃旧包`() = runBlocking {
        var call = 0
        val vm = BoardViewModel(
            this,
            fetchPage = { _, page ->
                call++
                if (page > 1) return@BoardViewModel null
                if (call == 1) { delay(200); rowPage(1, listOf(1)) }   // 旧 fid 慢
                else rowPage(1, listOf(2))                              // 新 fid 快
            },
            fetchMeta = { null },
        )
        vm.start(1)
        delay(60)    // 让旧任务真实进入在途（fetch 已被调用、正在 delay）
        vm.start(2)  // 换板块
        delay(400)
        val st = vm.listState as UiState.Data<BoardListRepository.PageResult>
        assertEquals("只显新板块行", listOf(2L), st.value.rows.map { it.tid })
    }

    private fun rowPage(page: Int, tids: List<Long>) = BoardListRepository.PageResult(
        tids.map { tid ->
            BoardListRepository.ThreadRow(
                tid = tid, title = "t$tid", authorName = null, authorId = null,
                avatarUrl = null, coverUrl = null, replyCount = null, viewCount = null,
                lastReplyTime = null, isTop = false, isDigest = false, applaudCount = null,
            )
        }, page, 2,
    )
}
