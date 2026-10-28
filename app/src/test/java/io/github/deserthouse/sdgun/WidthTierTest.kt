package io.github.deserthouse.sdgun

import io.github.deserthouse.sdgun.core.designsystem.NavTier
import io.github.deserthouse.sdgun.core.designsystem.widthTier
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * U4 补真实现反例（v1.11.0「宽屏三档」纯声明发布事故后的回归锁）：
 * 档位边界必须与 A-R3 任务卡一致（<600 底栏 / 600~839 rail / ≥840 rail+内容约束）。
 */
class WidthTierTest {

    @Test
    fun `compact boundaries`() {
        assertEquals(NavTier.COMPACT, widthTier(0))
        assertEquals(NavTier.COMPACT, widthTier(320))
        assertEquals(NavTier.COMPACT, widthTier(599))
    }

    @Test
    fun `medium boundaries`() {
        assertEquals(NavTier.MEDIUM, widthTier(600))
        assertEquals(NavTier.MEDIUM, widthTier(731)) // 1920px@420dpi 实测档
        assertEquals(NavTier.MEDIUM, widthTier(839))
    }

    @Test
    fun `expanded boundaries`() {
        assertEquals(NavTier.EXPANDED, widthTier(840))
        assertEquals(NavTier.EXPANDED, widthTier(1280))
    }
}
