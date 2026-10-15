package io.github.deserthouse.sdgun

import io.github.deserthouse.sdgun.ui.activeTab
import io.github.deserthouse.sdgun.web.GalleryBridge
import io.github.deserthouse.sdgun.web.UrlRules
import io.github.deserthouse.sdgun.web.isForumlistUrl
import io.github.deserthouse.sdgun.web.primaryAcceptType
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

    // ── isForumlistUrl：空壳探测的页型门（A-07） ──

    @Test
    fun `forumlist urls pass the gate`() {
        assertTrue(isForumlistUrl("https://bbs.sdgun.com.cn/forum.php?forumlist=1&mobile=2"))
        assertTrue(isForumlistUrl("https://bbs.sdgun.com.cn/forum.php?mod=forumlist&mobile=2"))
        assertTrue(isForumlistUrl("https://bbs.sdgun.com.cn/forum.php"))
    }

    @Test
    fun `board thread and member pages fail the gate`() {
        // 关键回归：板块页即使带空子版块区（.sub_forum 无链接）也不得判空壳
        assertFalse(isForumlistUrl("https://bbs.sdgun.com.cn/forum.php?mod=forumdisplay&fid=193&mobile=2"))
        assertFalse(isForumlistUrl("https://bbs.sdgun.com.cn/forum.php?mod=viewthread&tid=4120861&mobile=2"))
        assertFalse(isForumlistUrl("https://bbs.sdgun.com.cn/member.php?mod=logging&action=login&mobile=2"))
        assertFalse(isForumlistUrl("https://bbs.sdgun.com.cn/forum.php?mod=guide&view=newthread&mobile=2"))
    }

    // ── primaryAcceptType：文件选择器 acceptTypes 归一（A-06） ──

    @Test
    fun `first non-blank accept type wins`() {
        assertEquals("image/png", primaryAcceptType(arrayOf(null, "image/png", "image/jpeg")))
        assertEquals("application/pdf", primaryAcceptType(arrayOf("application/pdf")))
    }

    @Test
    fun `blank or wildcard accept falls back to any`() {
        assertEquals("*/*", primaryAcceptType(null))
        assertEquals("*/*", primaryAcceptType(arrayOf<String?>(null, "")))
        assertEquals("*/*", primaryAcceptType(arrayOf("*/*")))
        assertEquals("*/*", primaryAcceptType(arrayOf("  ")))
    }

    // ── AF-07：H5 分享链接构造 ──

    @Test
    fun `h5 url built from thread url`() {
        assertEquals(
            "https://app.sdgun.com.cn/mag/circle/v1/forum/threadWapPage?tid=4120861",
            UrlRules.threadH5Url("https://bbs.sdgun.com.cn/forum.php?mod=viewthread&tid=4120861&mobile=2"),
        )
        // 带分页参数的帖子页同样提取 tid
        assertEquals(
            "https://app.sdgun.com.cn/mag/circle/v1/forum/threadWapPage?tid=3736041",
            UrlRules.threadH5Url("https://bbs.sdgun.com.cn/forum.php?mod=viewthread&tid=3736041&page=2&mobile=2"),
        )
    }

    @Test
    fun `h5 url null on non-thread pages`() {
        assertEquals(null, UrlRules.threadH5Url("https://bbs.sdgun.com.cn/forum.php?forumlist=1&mobile=2"))
        assertEquals(null, UrlRules.threadH5Url("https://bbs.sdgun.com.cn/forum.php?mod=forumdisplay&fid=193&mobile=2"))
        assertEquals(null, UrlRules.threadH5Url("https://bbs.sdgun.com.cn/member.php?mod=logging&action=login&mobile=2"))
    }

    // ── AF-03：图组 JSON 解析（fail-closed） ──

    @Test
    fun `gallery payload parses urls and index`() {
        val d = GalleryBridge.parseGalleryPayload("{\"urls\":[\"http://a/1.jpg\",\"http://a/2.jpg\"],\"index\":1}")
        assertEquals(listOf("http://a/1.jpg", "http://a/2.jpg"), d?.urls)
        assertEquals(1, d?.startIndex)
    }

    @Test
    fun `gallery payload fails closed on garbage`() {
        assertEquals(null, GalleryBridge.parseGalleryPayload(null))
        assertEquals(null, GalleryBridge.parseGalleryPayload(""))
        assertEquals(null, GalleryBridge.parseGalleryPayload("not json"))
        assertEquals(null, GalleryBridge.parseGalleryPayload("{\"urls\":[]\"}"))
    }

    @Test
    fun `gallery payload rejects non-http and clamps index`() {
        val d = GalleryBridge.parseGalleryPayload("{\"urls\":[\"javascript:x\",\"http://a/ok.jpg\"],\"index\":9}")
        assertEquals(listOf("http://a/ok.jpg"), d?.urls)
        assertEquals(0, d?.startIndex)
    }

    // ── 门户死端守卫（对齐扩展 navguard） ──

    @Test
    fun `portal and bare forum php are dead ends`() {
        assertTrue(UrlRules.isPortalDeadEnd("https://bbs.sdgun.com.cn/portal.php"))
        assertTrue(UrlRules.isPortalDeadEnd("https://bbs.sdgun.com.cn/forum.php"))
        assertTrue(UrlRules.isPortalDeadEnd("https://bbs.sdgun.com.cn/forum.php?mobile=2"))
    }

    @Test
    fun `real forum pages are not dead ends`() {
        assertFalse(UrlRules.isPortalDeadEnd("https://bbs.sdgun.com.cn/forum.php?forumlist=1&mobile=2"))
        assertFalse(UrlRules.isPortalDeadEnd("https://bbs.sdgun.com.cn/forum.php?mod=forumdisplay&fid=193&mobile=2"))
        assertFalse(UrlRules.isPortalDeadEnd("https://bbs.sdgun.com.cn/forum.php?mod=viewthread&tid=1&mobile=2"))
        assertFalse(UrlRules.isPortalDeadEnd("https://bbs.sdgun.com.cn/member.php?mod=logging&action=login&mobile=2"))
        assertFalse(UrlRules.isPortalDeadEnd("https://app.sdgun.com.cn/mag/circle/v1/forum/threadWapPage?tid=1"))
    }
}
