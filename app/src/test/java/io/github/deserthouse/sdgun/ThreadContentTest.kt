package io.github.deserthouse.sdgun

import io.github.deserthouse.sdgun.feature.thread.ContentBlock
import io.github.deserthouse.sdgun.feature.thread.imageUrls
import io.github.deserthouse.sdgun.feature.thread.parseContentBlocks
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 内容块解析器（A70-04/R-A5）契约测试。
 * 反例来源：装机走查实测——`<img src=…>` 直出上屏、表情图混入正文、实体未解码。
 */
class ThreadContentTest {

    @Test
    fun `图文按文档序交错`() {
        val html = "<p>前文</p><img src=\"https://pic.example/1.jpg\">中段<br>" +
            "<img src=\"https://pic.example/2.jpg\">尾部"
        val blocks = parseContentBlocks(42L, html)
        assertEquals(
            listOf("t", "i", "t", "i", "t"),
            blocks.map {
                when (it) {
                    is ContentBlock.Text -> "t"
                    is ContentBlock.Image -> "i"
                }
            },
        )
        assertEquals("前文", (blocks[0] as ContentBlock.Text).text)
        assertEquals("https://pic.example/1.jpg", (blocks[1] as ContentBlock.Image).url)
        assertEquals("中段", (blocks[2] as ContentBlock.Text).text)
        assertEquals("https://pic.example/2.jpg", (blocks[3] as ContentBlock.Image).url)
        assertEquals("尾部", (blocks[4] as ContentBlock.Text).text)
    }

    @Test
    fun `key 带 pid 前缀且组内唯一`() {
        val html = "a<img src=\"https://x/1.jpg\">b<img src=\"https://x/2.jpg\">c"
        val blocks = parseContentBlocks(7L, html)
        assertEquals(blocks.size, blocks.map { it.key }.toSet().size)
        assertTrue(blocks.all { it.key.startsWith("7_") })
    }

    @Test
    fun `实体解码含数字实体`() {
        val html = "A&amp;B&nbsp;&lt;tag&gt;&#39;q&#20320;"
        val blocks = parseContentBlocks(1L, html)
        assertEquals(1, blocks.size)
        assertEquals("A&B <tag>'q你", (blocks[0] as ContentBlock.Text).text)
    }

    @Test
    fun `表情图跳过不产生块`() {
        // 本站两套表情路径：Discuz smiley + mag1 /public/emotion/（CDN 404 实证）
        val html = "文字<img src=\"https://bbs.sdgun.com.cn/static/image/smiley/doge.gif\">中" +
            "<img src=\"https://shuidan.app1.magcloud.net/public/emotion/face_002.png\">后继"
        val blocks = parseContentBlocks(1L, html)
        assertTrue(blocks.none { it is ContentBlock.Image })
        assertEquals("文字", (blocks[0] as ContentBlock.Text).text)
        assertEquals("中", (blocks[1] as ContentBlock.Text).text)
        assertEquals("后继", (blocks[2] as ContentBlock.Text).text)
    }

    @Test
    fun `非 http 图源跳过`() {
        val html = "<img src=\"data:image/png;base64,AAAA\"><img src=\"/relative/a.jpg\">正文"
        val blocks = parseContentBlocks(1L, html)
        assertTrue(blocks.none { it is ContentBlock.Image })
    }

    @Test
    fun `标签剥离与空段折叠`() {
        // 契约：段落=块内换行（\n），Text 块只在 img 边界切分
        val html = "<span style=\"color:red\">带样式</span><br><br><div></div>普通"
        val blocks = parseContentBlocks(1L, html)
        assertEquals(1, blocks.size)
        assertEquals("带样式\n\n普通", (blocks[0] as ContentBlock.Text).text)
    }

    @Test
    fun `空输入返回空块`() {
        assertTrue(parseContentBlocks(1L, "").isEmpty())
    }

    @Test
    fun `imageUrls 保序且只含图`() {
        val html = "a<img src=\"https://x/1.jpg\">b<img src=\"https://x/2.jpg\">"
        val urls = imageUrls(parseContentBlocks(1L, html))
        assertEquals(listOf("https://x/1.jpg", "https://x/2.jpg"), urls)
    }

    @Test
    fun `懒加载形态取 data-original 真图`() {
        // 2026-10-22 threadView 实测：src="" + data-original=真图（扩展 E70-02 同族问题）
        val html = "<img class=\"img-posted\" src=\"\" " +
            "data-original=\"http://sdgun.ymgames.com.cn/pic/1.jpg\">说明文字"
        val blocks = parseContentBlocks(1L, html)
        assertEquals(2, blocks.size)
        assertEquals("http://sdgun.ymgames.com.cn/pic/1.jpg", (blocks[0] as ContentBlock.Image).url)
        assertEquals("说明文字", (blocks[1] as ContentBlock.Text).text)
    }

    @Test
    fun `IMG 大小写不敏感`() {
        val html = "<IMG SRC=\"https://x/1.jpg\">图"
        val blocks = parseContentBlocks(1L, html)
        assertEquals(1, blocks.count { it is ContentBlock.Image })
    }
}
