package io.github.deserthouse.sdgun

import io.github.deserthouse.sdgun.feature.community.communityColumns
import org.junit.Assert.assertEquals
import org.junit.Test

/** Q7 Expanded 多列布局矩阵（纯函数）：1 列 <600 / 2 列 600-839 / 3 列 >=840。 */
class Q7CommunityGridTest {

    @Test
    fun `G1 窄屏单列维持原布局`() {
        assertEquals(1, communityColumns(360))
        assertEquals(1, communityColumns(411))
        assertEquals(1, communityColumns(599))
    }

    @Test
    fun `G2 平板断点两列（与 TabShell rail 600dp 对齐）`() {
        assertEquals(2, communityColumns(600))
        assertEquals(2, communityColumns(720))
        assertEquals(2, communityColumns(839))
    }

    @Test
    fun `G3 桌面窗三列封顶`() {
        assertEquals(3, communityColumns(840))
        assertEquals(3, communityColumns(1919))
    }
}
