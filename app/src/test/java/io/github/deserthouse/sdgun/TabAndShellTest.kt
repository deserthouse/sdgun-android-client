package io.github.deserthouse.sdgun

import io.github.deserthouse.sdgun.ui.activeTab
import io.github.deserthouse.sdgun.web.shellVerdict
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TabAndShellTest {

    // ── activeTab：URL 反推 + 登录页意图回退（F6） ──

    @Test
    fun `pm url maps to messages tab`() {
        assertEquals(1, activeTab("https://bbs.sdgun.com.cn/home.php?mod=space&do=pm&mobile=2"))
    }

    @Test
    fun `mycenter url maps to profile tab`() {
        assertEquals(2, activeTab("https://bbs.sdgun.com.cn/home.php?mod=space&do=profile&mycenter=1&mobile=2"))
    }

    @Test
    fun `home url maps to first tab`() {
        assertEquals(0, activeTab("https://bbs.sdgun.com.cn/forum.php?forumlist=1&mobile=2"))
        assertEquals(0, activeTab("https://bbs.sdgun.com.cn/forum.php?mod=forumdisplay&fid=193&mobile=2"))
    }

    @Test
    fun `login page falls back to intended tab`() {
        val login = "https://bbs.sdgun.com.cn/member.php?mod=logging&action=login&mobile=2"
        assertEquals(1, activeTab(login, intendedTab = 1))
        assertEquals(2, activeTab(login, intendedTab = 2))
        assertEquals(0, activeTab(login, intendedTab = 0))
    }

    @Test
    fun `login page intent is clamped to valid range`() {
        val login = "https://bbs.sdgun.com.cn/member.php?mod=logging&action=login&mobile=2"
        assertEquals(0, activeTab(login, intendedTab = 3))
        assertEquals(0, activeTab(login, intendedTab = -5))
    }

    @Test
    fun `explicit urls win over stale intent`() {
        // 意图是消息(1)但页面已回到首页特征 URL → 首页
        assertEquals(0, activeTab("https://bbs.sdgun.com.cn/forum.php?forumlist=1&mobile=2", intendedTab = 1))
    }

    // ── shellVerdict：空壳探测回值解析（批次 A） ──

    @Test
    fun `empty verdict only on explicit empty`() {
        assertTrue(shellVerdict("\"empty\""))
    }

    @Test
    fun `ok and na and null are not shell`() {
        assertFalse(shellVerdict("\"ok\""))
        assertFalse(shellVerdict("\"n/a\""))
        assertFalse(shellVerdict("null"))
        assertFalse(shellVerdict(null))
        assertFalse(shellVerdict(""))
    }

    @Test
    fun `malformed verdict fails closed`() {
        // 漏报好过误报：任何非预期形态都按"非空壳"处理
        assertFalse(shellVerdict("empty"))
        assertFalse(shellVerdict("\"EMPTY\""))
        assertFalse(shellVerdict("garbage"))
    }
}
