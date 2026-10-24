package io.github.deserthouse.sdgun

import io.github.deserthouse.sdgun.data.repository.BoardListRepository
import io.github.deserthouse.sdgun.feature.thread.sampleSize
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** A-R2 字段契约与媒体采样回归（A24-05/07 字段错配族）。 */
class BoardRowAndMediaTest {

    @Test
    fun `板块行解析 reply_count 与 unix 时间`() {
        val json = """{"list":[{
            "tid":"2296878","title":"T","user_name":"u","user_head":"https://x/h.jpg",
            "reply_count":"531","click":"371万阅读","last_reply_time":"1792810712",
            "is_top":0,"digest":0,"applaud_count":"9"
        }]}"""
        val rows = BoardListRepository.parseRows(json)
        assertEquals(1, rows.size)
        assertEquals("531", rows[0].replyCount)
        assertTrue("unix 时间应被格式化(非原始数字)", rows[0].lastReplyTime?.matches(Regex("\\d{4}-\\d{1,2}-\\d{1,2} \\d{2}:\\d{2}")) == true)
    }

    @Test
    fun `非数字时间原样保留`() {
        val json = """{"list":[{"tid":"1","title":"T","last_reply_time":"2小时前"}]}"""
        val rows = BoardListRepository.parseRows(json)
        assertEquals("2小时前", rows[0].lastReplyTime)
    }

    @Test
    fun `采样率计算`() {
        // 语义：longest/(sample*2) >= maxEdge 才倍增（即采样后最长边 < maxEdge 或略超）
        assertEquals(1, sampleSize(1080, 810, 2048))    // 小图不采样
        assertEquals(1, sampleSize(3000, 2000, 2048))   // 3000/2=1500 < 2048：一倍采样已够
        assertEquals(2, sampleSize(6000, 4000, 2048))   // 6000/2=3000 >= 2048 → 2
        assertEquals(2, sampleSize(8000, 6000, 2048))   // 8000/4=2000 < 2048 停于 2
        assertEquals(1, sampleSize(0, 0, 2048))         // 坏 bounds 恒 1
    }
}
