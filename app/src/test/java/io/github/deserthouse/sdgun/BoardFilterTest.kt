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
}
