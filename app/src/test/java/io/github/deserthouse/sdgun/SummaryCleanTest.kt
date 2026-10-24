package io.github.deserthouse.sdgun

import io.github.deserthouse.sdgun.data.repository.NewsRepository
import org.junit.Assert.assertEquals
import org.junit.Test

/** A24-07 摘要清洗回归：编辑痕迹不进摘要。 */
class SummaryCleanTest {

    @Test
    fun `剥本帖最后由编辑前缀`() {
        val raw = "本帖最后由老村长于2023-5-2316:56编辑我真是吐了"
        assertEquals("我真是吐了", NewsRepository.cleanSummary(raw))
    }

    @Test
    fun `正常摘要不动`() {
        assertEquals("plain text", NewsRepository.cleanSummary(" plain text "))  // 首尾 trim=设计语义
    }

    @Test
    fun `方括号行业前缀剥离`() {
        assertEquals("正文内容", NewsRepository.cleanSummary("[测评] 正文内容"))
    }

    @Test
    fun `组合前缀一次性剥离`() {
        assertEquals("正文", NewsRepository.cleanSummary("[测评] 本帖最后由某某于2026-8-221:09编辑正文"))
    }
}
