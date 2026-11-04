package io.github.deserthouse.sdgun

import io.github.deserthouse.sdgun.core.model.UiState
import io.github.deserthouse.sdgun.core.navigation.Target
import io.github.deserthouse.sdgun.core.navigation.TargetParser
import io.github.deserthouse.sdgun.data.repository.ThreadRepository
import io.github.deserthouse.sdgun.feature.thread.ThreadViewModel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Q1 深链 typed Target + 跳楼常驻回归（合成关闭制）。
 * TargetParser：shuidan:// 三形态 + bbs web 三形态 + host 防伪（外站同形参数不得误路由）。
 * ThreadViewModel.jumpTo：已加载即时定位 / 跨页逐页扫描 / 末页未找到 / 楼层号匹配。
 */
class Q1NavigationTest {

    // ── TargetParser：shuidan:// ──

    @Test
    fun `S1 forumThreadList 解析为 Board`() {
        assertEquals(Target.Board(153), TargetParser.parse("shuidan://forumThreadList?fid=153&circleId=x"))
    }

    @Test
    fun `S2 forumThread 解析为 Thread 含可选 pid`() {
        assertEquals(Target.Thread(198, null), TargetParser.parse("shuidan://forumThread?tid=198"))
        assertEquals(Target.Thread(198, 3385L), TargetParser.parse("shuidan://forumThread?tid=198&pid=3385"))
    }

    @Test
    fun `S3 user 形态解析为 User`() {
        assertEquals(Target.User(42), TargetParser.parse("shuidan://user?uid=42"))
        assertEquals(Target.User(42), TargetParser.parse("shuidan://userHome?uid=42"))
    }

    @Test
    fun `S4 未知 shuidan host 返回 null（调用方可恢复回退不吞死链）`() {
        assertNull(TargetParser.parse("shuidan://mysteryZone?x=1"))
    }

    @Test
    fun `S5 非法参数不产生目标`() {
        assertNull(TargetParser.parse("shuidan://forumThread?tid=abc"))
        assertNull(TargetParser.parse("shuidan://forumThreadList?fid=0"))
        assertNull(TargetParser.parse("shuidan://forumThread?tid=-1"))
    }

    // ── TargetParser：https bbs web 形态 ──

    @Test
    fun `W1 viewthread 解析为 Thread 锚点 pid 兼容`() {
        assertEquals(
            Target.Thread(198, null),
            TargetParser.parse("https://bbs.sdgun.com.cn/forum.php?mod=viewthread&tid=198"),
        )
        assertEquals(
            Target.Thread(198, 1001L),
            TargetParser.parse("https://bbs.sdgun.com.cn/forum.php?mod=viewthread&tid=198#pid1001"),
        )
    }

    @Test
    fun `W2 forumdisplay 与 space-uid 解析`() {
        assertEquals(
            Target.Board(153),
            TargetParser.parse("https://bbs.sdgun.com.cn/forum.php?mod=forumdisplay&fid=153"),
        )
        assertEquals(
            Target.User(7),
            TargetParser.parse("https://bbs.sdgun.com.cn/space-uid-7.html"),
        )
    }

    @Test
    fun `W3 外站同形参数保持 Web 不误路由（host 防伪）`() {
        val url = "https://evil.example.com/forum.php?mod=viewthread&tid=198"
        assertEquals(Target.Web(url), TargetParser.parse(url))
    }

    @Test
    fun `W4 其余站内与站外 URL 均为 Web（走 WebView 壳）`() {
        val other = "https://bbs.sdgun.com.cn/search.php?mod=forum&mobile=2"
        assertEquals(Target.Web(other), TargetParser.parse(other))
        val ext = "https://example.org/page?a=1"
        assertEquals(Target.Web(ext), TargetParser.parse(ext))
        assertNull(TargetParser.parse("about:blank"))
        assertNull(TargetParser.parse(null))
    }

    // ── ThreadViewModel.jumpTo ──

    private fun floor(pid: Long, fl: Int) = ThreadRepository.Floor(
        pid = pid, floor = fl, authorName = null, authorId = null, avatarUrl = null,
        contentHtml = "", pics = emptyList(), createTime = null, applaudCount = null,
        isPublisher = false,
    )

    private fun page(p: Int, total: Int, count: Int = 2) = ThreadRepository.FloorsPage(
        (1..count).map { i -> floor(pid = p * 10L + i, fl = (p - 1) * count + i + 1) },
        p, total,
    )

    private suspend fun awaitData(vm: ThreadViewModel) {
        var n = 0
        while (vm.floorsState !is UiState.Data<*> && n++ < 500) delay(2)
    }

    private suspend fun awaitJumpDone(vm: ThreadViewModel) {
        var n = 0
        while (vm.jumpState is ThreadViewModel.JumpState.Searching && n++ < 2000) delay(2)
    }

    @Test
    fun `J1 已加载命中即时定位不动筛选`() = runBlocking {
        val vm = ThreadViewModel(
            CoroutineScope(Dispatchers.Unconfined),
            fetchFloors = { _, p, _, _ -> page(p, 1) },
            jumpPollMs = 1,
        )
        vm.start(1, 0)
        awaitData(vm)
        vm.jumpTo(1, targetPid = 11L)
        assertEquals(11L, vm.pendingLocate)
        assertEquals(ThreadViewModel.JumpState.Idle, vm.jumpState)
    }

    @Test
    fun `J2 跨页扫描命中第 3 页`() = runBlocking {
        val vm = ThreadViewModel(
            CoroutineScope(Dispatchers.Unconfined),
            fetchFloors = { _, p, _, _ -> page(p, 5) },
            jumpPollMs = 1,
        )
        vm.start(1, 0)
        awaitData(vm)
        assertTrue(vm.pendingLocate == null)
        vm.jumpTo(1, targetPid = 31L)  // p1 含 11/12；目标 31 在 p3（pid=p*10+i）
        awaitJumpDone(vm)
        assertEquals(31L, vm.pendingLocate)
        assertEquals(ThreadViewModel.JumpState.Idle, vm.jumpState)
    }

    @Test
    fun `J3 扫到末页未找到=NotFound 不假称成功`() = runBlocking {
        val vm = ThreadViewModel(
            CoroutineScope(Dispatchers.Unconfined),
            fetchFloors = { _, p, _, _ -> page(p, 2) },
            jumpPollMs = 1,
        )
        vm.start(1, 0)
        awaitData(vm)
        vm.jumpTo(1, targetPid = 999L)
        awaitJumpDone(vm)
        assertTrue(vm.jumpState is ThreadViewModel.JumpState.NotFound)
        assertNull(vm.pendingLocate)
    }

    @Test
    fun `J4 楼层号匹配（跳过第 1 页的楼层号）`() = runBlocking {
        // 2 页×2 层：楼层号 3 在第 2 页（floor=(p-1)*2+i+1 → p2 i1 → floor=4? 显式验证跨页）
        val vm = ThreadViewModel(
            CoroutineScope(Dispatchers.Unconfined),
            fetchFloors = { _, p, _, _ -> page(p, 3) },
            jumpPollMs = 1,
        )
        vm.start(1, 0)
        awaitData(vm)
        vm.jumpTo(1, targetFloor = 4)  // p1: floors 2,3；p2: floors 4,5
        awaitJumpDone(vm)
        assertEquals(21L, vm.pendingLocate)  // p2 的首个 pid=21（pid=p*10+i），floor=4
    }

    // ── WP1-A（A1104-U01）：官方 app/mag1 域帖页 host+path 双门 ──

    @Test
    fun `P1 app 域 threadViewPage 解析为 Thread`() {
        assertEquals(
            Target.Thread(392675L, null),
            TargetParser.parse("https://app.sdgun.com.cn/mag/circle/v1/forum/threadViewPage?tid=392675&circle_id="),
        )
        assertEquals(
            Target.Thread(42L, null),
            TargetParser.parse("https://mag1.sdgun.net/mag/circle/v1/forum/threadWapPage?tid=42"),
        )
    }

    @Test
    fun `P2 官方域非帖路径保持 Web（host+path 双门）`() {
        val u1 = "https://app.sdgun.com.cn/mag/user/v1/User/userSearch?name=x"
        assertEquals(Target.Web(u1), TargetParser.parse(u1))
        val u2 = "https://mag1.sdgun.net/mag/circle/v1/show/allTopicSearch?keyword=x"
        assertEquals(Target.Web(u2), TargetParser.parse(u2))
    }

    @Test
    fun `P3 bbs 伪静态 thread-N 与 t-N 短链解析`() {
        assertEquals(Target.Thread(42L, null),
            TargetParser.parse("https://bbs.sdgun.com.cn/thread-42-1-1.html"))
        assertEquals(Target.Thread(42L, null),
            TargetParser.parse("https://bbs.sdgun.com.cn/t-42.html"))
    }

    @Test
    fun `P4 外站同形伪静态不误路由`() {
        val u = "https://evil.example.com/t-42.html"
        assertEquals(Target.Web(u), TargetParser.parse(u))
        assertEquals(Target.Web("https://x.org/thread-9-1-1.html"),
            TargetParser.parse("https://x.org/thread-9-1-1.html"))
    }

    @Test
    fun `P5 伪静态第二段页码不采为 pid`() {
        assertEquals(Target.Thread(42L, null),
            TargetParser.parse("https://bbs.sdgun.com.cn/thread-42-3-2.html"))
    }
}