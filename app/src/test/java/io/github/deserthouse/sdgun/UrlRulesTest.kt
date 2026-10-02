package io.github.deserthouse.sdgun

import io.github.deserthouse.sdgun.web.UrlRules.Verdict.AppScheme
import io.github.deserthouse.sdgun.web.UrlRules.Verdict.External
import io.github.deserthouse.sdgun.web.UrlRules.Verdict.Internal
import io.github.deserthouse.sdgun.web.UrlRules.classifyPure
import io.github.deserthouse.sdgun.web.UrlRules.forceTouchVersionQuery
import io.github.deserthouse.sdgun.web.UrlRules.normalizeInternalPure
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class UrlRulesTest {

    private val host = "bbs.sdgun.com.cn"

    @Test
    fun `classifyPure internal hosts`() {
        assertEquals(Internal, classifyPure("https://bbs.sdgun.com.cn/forum.php", host))
        assertEquals(Internal, classifyPure("http://bbs.sdgun.com.cn/forum.php", "BBS.SDGUN.COM.CN"))
        assertEquals(Internal, classifyPure("https://www.sdgun.com.cn/", "www.sdgun.com.cn"))
        assertEquals(Internal, classifyPure("about:blank", null))
        assertEquals(Internal, classifyPure("blob:https://x/y", host))
        assertEquals(Internal, classifyPure("javascript:void(0)", host))
        assertEquals(Internal, classifyPure("", host))
    }

    @Test
    fun `classifyPure external http`() {
        val v = classifyPure("https://example.com/page", "example.com")
        assertTrue(v is External)
        assertEquals("https://example.com/page", (v as External).url)
    }

    @Test
    fun `classifyPure schemes`() {
        assertTrue(classifyPure("bzsh://threadPost", host) is AppScheme)
        // file: 不再放行 WebView 加载（B4）
        assertTrue(classifyPure("file:///sdcard/x.html", host) is AppScheme)
    }

    @Test
    fun `forceTouchVersionQuery rewrites only exact mobile=no`() {
        assertEquals("mobile=2", forceTouchVersionQuery("mobile=no"))
        assertEquals("fid=196&mobile=2", forceTouchVersionQuery("fid=196&mobile=no"))
        assertEquals("mobile=2&fid=196", forceTouchVersionQuery("mobile=2&fid=196"))
        // 其他 mobile 值不动
        assertEquals("mobile=1&tid=9", forceTouchVersionQuery("mobile=1&tid=9"))
        assertEquals("fid=196", forceTouchVersionQuery("fid=196"))
        // B3 回归：参数值包含 mobile=no 子串时不得误伤
        assertEquals("note=mobile=no", forceTouchVersionQuery("note=mobile=no"))
        assertEquals("", forceTouchVersionQuery(""))
        assertEquals(null, forceTouchVersionQuery(null))
    }

    @Test
    fun `normalizeInternalPure forces touch on mobile=no`() {
        assertEquals(
            "https://bbs.sdgun.com.cn/forum.php?mod=forumdisplay&fid=196&mobile=2",
            normalizeInternalPure(
                "https://bbs.sdgun.com.cn/forum.php?mod=forumdisplay&fid=196&mobile=no", host),
        )
    }

    @Test
    fun `normalizeInternalPure keeps url unchanged without mobile=no`() {
        val url = "https://bbs.sdgun.com.cn/forum.php?mod=forumdisplay&fid=196&mobile=2"
        assertEquals(url, normalizeInternalPure(url, host))
        val fl = "https://bbs.sdgun.com.cn/forum.php?forumlist=1&mobile=2"
        assertEquals(fl, normalizeInternalPure(fl, host))
    }

    @Test
    fun `normalizeInternalPure ignores non-internal host`() {
        val url = "https://example.com/forum.php?mobile=no"
        assertEquals(url, normalizeInternalPure(url, "example.com"))
    }
}
