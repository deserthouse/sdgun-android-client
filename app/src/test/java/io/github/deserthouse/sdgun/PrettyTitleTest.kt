package io.github.deserthouse.sdgun

import io.github.deserthouse.sdgun.ui.prettyTitle
import org.junit.Assert.assertEquals
import org.junit.Test

class PrettyTitleTest {

    @Test
    fun `strips single-space suffix`() {
        assertEquals("锦明9弹匣问题", prettyTitle("锦明9弹匣问题 - 手机版 - Powered by Discuz!"))
    }

    @Test
    fun `strips double-space suffix`() {
        // 2026-10-02 回归：触屏版实际标题存在 "-  手机版" 双空格变体
        assertEquals("锦明9弹匣问题", prettyTitle("锦明9弹匣问题 -  手机版 - Powered by Discuz!"))
    }

    @Test
    fun `strips bare discuz suffix`() {
        assertEquals("随便什么", prettyTitle("随便什么 - Powered by Discuz!"))
    }

    @Test
    fun `leaves plain title untouched`() {
        assertEquals("随便什么", prettyTitle("随便什么"))
    }

    @Test
    fun `blank falls back to app name`() {
        assertEquals("SDGun 社区", prettyTitle(""))
        assertEquals("SDGun 社区", prettyTitle("   "))
    }
}
