package io.github.deserthouse.sdgun

import io.github.deserthouse.sdgun.core.model.UiState
import io.github.deserthouse.sdgun.data.repository.BoardListRepository
import io.github.deserthouse.sdgun.feature.community.BoardViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * R5 板块筛选 chips（U3-a 反例先行）：筛选变化=新代际重拉——旧包整弃、游标复位、同值 no-op。
 * 对照 android_code_review_2026-10-25.md R5：orders/threadTypes 已解析但 VM/UI 均未消费。
 */
class BoardFilterTest {

    private fun rowPage(page: Int, tids: List<Long>) = BoardListRepository.PageResult(
        tids.map { tid ->
            BoardListRepository.ThreadRow(
                tid = tid, title = "t$tid", authorName = null, authorId = null,
                avatarUrl = null, coverUrl = null, replyCount = null, viewCount = null,
                lastReplyTime = null, isTop = false, isDigest = false, applaudCount = null,
            )
        }, page, 2,
    )

    @Test
    fun `切筛选携带新order并丢弃旧包`() = runBlocking {
        val calls = mutableListOf<Pair<Int, String>>() // (page, order)
        val vm = BoardViewModel(
            this,
            fetchPage = { _, page, order, _ ->
                calls.add(page to order)
                if (order.isEmpty()) {          // 旧意图（默认序）：慢
                    delay(200)
                    rowPage(1, listOf(1))
                } else {                        // 新意图（最新序）：快
                    rowPage(1, listOf(2))
                }
            },
            fetchMeta = { null },
        )
        vm.start(1)
        delay(60)          // 让旧请求真实进入在途
        vm.setFilter(1, "lastpost", "")
        delay(400)
        val st = vm.listState as UiState.Data<BoardListRepository.PageResult>
        assertEquals("只显新筛选行", listOf(2L), st.value.rows.map { it.tid })
        assertEquals("新请求携带 order=lastpost", "lastpost", calls.last().second)
    }

    @Test
    fun `切筛选游标复位重拉第一页`() = runBlocking {
        val pages = mutableListOf<Int>()
        val vm = BoardViewModel(
            this,
            fetchPage = { _, page, _, _ ->
                pages.add(page)
                delay(50)
                if (page == 1) rowPage(1, listOf(1, 2)) else rowPage(2, listOf(3))
            },
            fetchMeta = { null },
        )
        vm.start(1)
        delay(150)
        vm.loadMore(1)
        delay(150)
        vm.setFilter(1, "hot", "")
        delay(200)
        assertEquals("筛选后必须重拉 p1", 1, pages.last())
        val st = vm.listState as UiState.Data<BoardListRepository.PageResult>
        assertEquals("行集合=新筛选首屏（无旧页残留）", listOf(1L, 2L), st.value.rows.map { it.tid })
    }

    @Test
    fun `start重置筛选到默认`() = runBlocking {
        val orders = mutableListOf<String>()
        val vm = BoardViewModel(
            this,
            fetchPage = { _, _, order, _ ->
                orders.add(order)
                delay(30)
                rowPage(1, listOf(1))
            },
            fetchMeta = { null },
        )
        vm.start(1)
        delay(100)
        vm.setFilter(1, "views", "")
        delay(100)
        vm.start(2)   // 换板块
        delay(150)
        assertEquals("换板块后筛选复位为默认", "", orders.last())
    }

    @Test
    fun `同筛选重复点击不重拉`() = runBlocking {
        var calls = 0
        val vm = BoardViewModel(
            this,
            fetchPage = { _, _, _, _ -> calls++; delay(30); rowPage(1, listOf(1)) },
            fetchMeta = { null },
        )
        vm.start(1)
        delay(100)
        vm.setFilter(1, "hot", "")
        delay(100)
        vm.setFilter(1, "hot", "")   // 重复点击同一 chip
        delay(100)
        assertEquals("同筛选重复设置=零新请求", 2, calls)  // start 1 次 + setFilter 1 次
    }

    @org.junit.Test
    fun `refresh 保旧行进 Refreshing 且成功换新首页`() = kotlinx.coroutines.runBlocking {
        var call = 0
        val vm = BoardViewModel(
            this,
            fetchPage = { _, page, _, _ ->
                call++
                if (page == 1 && call == 1) {
                    kotlinx.coroutines.delay(30)
                    rowPage(1, listOf(1, 2))
                } else rowPage(1, listOf(9))
            },
            fetchMeta = { null },
        )
        vm.start(1)
        kotlinx.coroutines.delay(150)
        org.junit.Assert.assertTrue(vm.listState is UiState.Data<*>)
        vm.refresh(1)
        org.junit.Assert.assertTrue(
            "refresh 应进 Refreshing 保旧行", vm.listState is UiState.Refreshing<*>)
        kotlinx.coroutines.delay(150)
        val st = vm.listState as UiState.Data<BoardListRepository.PageResult>
        org.junit.Assert.assertEquals(listOf(9L), st.value.rows.map { it.tid })
    }

    // ── R0-2（A31-S01）反例：刷新失败保旧行；Stale 态可重试分页 ──
    @org.junit.Test
    fun `refresh 失败保旧行进 Stale 且页元数据回滚`() = kotlinx.coroutines.runBlocking {
        var fail = false
        val vm = BoardViewModel(
            this,
            fetchPage = { _, page, _, _ ->
                if (fail) null else rowPage(page, listOf((page * 10).toLong(), (page * 10 + 1).toLong()))
            },
            fetchMeta = { null },
        )
        vm.start(1)
        kotlinx.coroutines.delay(150)
        vm.loadMore(1)
        kotlinx.coroutines.delay(150)
        val before = vm.listState as UiState.Data<BoardListRepository.PageResult>
        org.junit.Assert.assertEquals(4, before.value.rows.size)  // p1+p2 各 2 行
        fail = true
        vm.refresh(1)
        kotlinx.coroutines.delay(150)
        val st = vm.listState
        org.junit.Assert.assertTrue("刷新失败应进 Stale 保旧", st is UiState.Stale<*>)
        org.junit.Assert.assertEquals(
            "旧行完整保留", 4,
            (st as UiState.Stale<BoardListRepository.PageResult>).stale.rows.size)
    }

    @org.junit.Test
    fun `Stale 态 loadMore 可按原意图重试下一页`() = kotlinx.coroutines.runBlocking {
        var p2Fails = true
        val vm = BoardViewModel(
            this,
            fetchPage = { _, page, _, _ ->
                if (page == 2 && p2Fails) null else rowPage(page, listOf((page * 10).toLong()))
            },
            fetchMeta = { null },
        )
        vm.start(1)
        kotlinx.coroutines.delay(150)
        vm.loadMore(1)  // p2 失败 → Stale(1 行)
        kotlinx.coroutines.delay(150)
        org.junit.Assert.assertTrue(vm.listState is UiState.Stale<*>)
        p2Fails = false
        vm.loadMore(1)  // Stale 态重试（旧 !is Data 门=此击被吞，审查实锤）
        kotlinx.coroutines.delay(150)
        val st = vm.listState as UiState.Data<BoardListRepository.PageResult>
        org.junit.Assert.assertEquals("p1+p2 合并", listOf(10L, 20L), st.value.rows.map { it.tid })
    }
}
