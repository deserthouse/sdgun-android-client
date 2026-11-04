package io.github.deserthouse.sdgun

import io.github.deserthouse.sdgun.core.model.UiState
import io.github.deserthouse.sdgun.data.repository.ThreadRepository
import io.github.deserthouse.sdgun.feature.thread.ThreadViewModel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * WP5 反例（A1104-S05/S06）：
 * R1 分页暂败→Footer.Failed→重试原页命中（旧实现 loadMore 被 Data 门挡死+跳楼误报 NotFound）；
 * R2 跳楼目标在暂败页（首败后成）→恢复命中（旧代码 2.5s NotFound 误报）；
 * R3 已知终页零越界请求；R4 尾行状态机 More→Loading→More/End/Failed 单一所有权。
 */
class WP5PagingJumpTest {

    private fun floor(pid: Long, fl: Int) = ThreadRepository.Floor(
        pid = pid, floor = fl, authorName = null, authorId = null, avatarUrl = null,
        contentHtml = "", pics = emptyList(), createTime = null, applaudCount = null,
        isPublisher = false,
    )

    private fun page(p: Int, total: Int) = ThreadRepository.FloorsPage(
        listOf(floor(p * 10L + 1, p), floor(p * 10L + 2, p + 1)), p, total,
    )

    private suspend fun await(vm: ThreadViewModel, ticks: Int = 400, cond: () -> Boolean) {
        var n = 0
        while (!cond() && n++ < ticks) delay(5)
    }

    @Test
    fun `R1 分页暂败 Footer-Failed 重试命中`() = runBlocking {
        val calls = mutableListOf<Int>()
        val vm = ThreadViewModel(
            CoroutineScope(Dispatchers.Unconfined),
            fetchFloors = { _, p, _, _ ->
                calls.add(p)
                if (p == 2 && calls.count { it == 2 } == 1) null  // p2 首败
                else page(p, 3)
            },
            jumpPollMs = 1,
        )
        vm.start(1, 0)
        await(vm) { vm.floorsState is UiState.Data<*> }
        // loadMore p2：首败 → Footer.Failed（保旧行，UI 有重试）
        vm.loadMore(1)
        await(vm) { vm.footer is ThreadViewModel.Footer.Failed }
        assertTrue("暂败须 Failed（实际=${vm.floorsState}", vm.floorsState is UiState.Data<*>)
        assertEquals(1, vm.floorPage)
        // 重试 → 命中 Data
        vm.retryFooter(1)
        await(vm) { vm.floorPage == 2 }
        assertEquals(2, vm.floorPage)
        assertTrue(vm.floorsState is UiState.Data<*>)
    }

    @Test
    fun `R2 跳楼目标在暂败页 首败后成 恢复命中不误报`() = runBlocking {
        var p2Fails = 0
        val vm = ThreadViewModel(
            CoroutineScope(Dispatchers.Unconfined),
            fetchFloors = { _, p, _, _ ->
                if (p == 2 && p2Fails == 0) { p2Fails++; null }  // 首败
                else page(p, 4)
            },
            jumpPollMs = 1,
        )
        vm.start(1, 0)
        await(vm) { vm.floorsState is UiState.Data<*> }
        vm.jumpTo(1, targetPid = 21L)  // p2 首行
        await(vm) { vm.jumpState !is ThreadViewModel.JumpState.Searching }
        assertTrue("暂败恢复后须命中（实际=${vm.jumpState}", vm.pendingLocate == 21L)
    }

    @Test
    fun `R3 已知终页零越界请求`() = runBlocking {
        var requestCount = 0
        val vm = ThreadViewModel(
            CoroutineScope(Dispatchers.Unconfined),
            fetchFloors = { _, p, _, _ -> requestCount++; page(p, 1) },
            jumpPollMs = 1,
        )
        vm.start(1, 0)
        await(vm) { vm.floorsState is UiState.Data<*> }
        val before = requestCount
        vm.loadMore(1)  // 终页：应零请求
        kotlinx.coroutines.delay(30)
        assertEquals(before, requestCount)
    }

    @Test
    fun `R4 尾行状态机单一所有权`() = runBlocking {
        val vm = ThreadViewModel(
            CoroutineScope(Dispatchers.Unconfined),
            fetchFloors = { _, p, _, _ -> page(p, 3) },
            jumpPollMs = 1,
        )
        vm.start(1, 0)
        await(vm) { vm.floorsState is UiState.Data<*> }
        assertTrue("p1<3 → More", vm.footer is ThreadViewModel.Footer.More)
        vm.loadMore(1)
        await(vm) { vm.floorPage == 2 }
        assertTrue("p2<3 → More", vm.footer is ThreadViewModel.Footer.More)
        vm.loadMore(1)
        await(vm) { vm.floorPage == 3 }
        assertTrue("p3==3 → End", vm.footer is ThreadViewModel.Footer.End)
    }
}
