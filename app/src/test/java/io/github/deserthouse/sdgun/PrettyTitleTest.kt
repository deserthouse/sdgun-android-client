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

    @Test
    fun `strips site keyword tail and brand prefix`() {
        // 2026-10-07 二轮审计实取样例（触屏版板块页/帖子页标题）
        assertEquals(
            "弓射总分区",
            prettyTitle("SDGun弓射总分区SDGUN,水弹,水弹枪,水弹论坛 -  手机版 - Powered by Discuz!"),
        )
        assertEquals(
            "弓射原创便携弓",
            prettyTitle("SDGun弓射原创便携弓SDGUN,水弹,水弹枪,水弹论坛 -  手机版 - Powered by Discuz!"),
        )
    }

    @Test
    fun `strips mid-title brand segment`() {
        // 导读页形如 "导读-最新发表 -  SDGun -  手机版 - Powered by Discuz!"
        assertEquals(
            "导读-最新发表",
            prettyTitle("导读-最新发表 -  SDGun -  手机版 - Powered by Discuz!"),
        )
    }

    @Test
    fun `home keyword title maps to app name`() {
        assertEquals("SDGun 社区", prettyTitle("SDGUN,wargame -  手机版 - Powered by Discuz!"))
    }

    @Test
    fun `does not mangle titles that merely start with the brand`() {
        assertEquals("SDGun 社区公告", prettyTitle("SDGun 社区公告"))
    }
}
