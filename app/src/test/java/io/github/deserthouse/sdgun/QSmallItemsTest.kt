package io.github.deserthouse.sdgun

import io.github.deserthouse.sdgun.feature.thread.parseContentBlocks
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Q 小项批回归（2026-11-02 游客真读采样兑现）：
 * B0-2 余项=video/source 真实形态（tests/probe_out_login/guest_probe_q_20261102.json
 * 实采 fid=153 近期帖：`<video class="mag-video" poster=…><source src=…mp4>`）——
 * 验证既有 SRC_ATTR 机制对 source 子元素形态的覆盖（挂账=「已验证机制覆盖」非新代码）。
 */
class QSmallItemsTest {

    @Test
    fun `V1 真实 video source 形态提取 mp4 地址`() {
        // 2026-11-02 游客探针实采（tid=4402023，picapp 域 mp4）
        val html = """
            <p>视频帖正文</p>
            <video controls poster="http://picapp.sdgun.net/pic/20261101/179353081635276310.jpg"
              width="800px" height="600px" webkit-playsinline playsinline
              class="mag-video" preload="none" style="background-color: #222222">
              <source src="http://picapp.sdgun.net/video/20261101/17935308163526995.mp4" type="video/mp4">
            </video>
        """.trimIndent()
        val blocks = parseContentBlocks(4402023L, html)
        val video = blocks.filterIsInstance<io.github.deserthouse.sdgun.feature.thread.ContentBlock.Video>()
        assertEquals(1, video.size)
        assertEquals(
            "http://picapp.sdgun.net/video/20261101/17935308163526995.mp4",
            video.single().url,
        )
    }

    @Test
    fun `V2 poster 不被误当视频地址`() {
        // 无 <source> 的裸 video（poster 在场）——不得把 poster 值当 src
        val html = """<video poster="http://picapp.sdgun.net/pic/x.jpg" preload="none"></video>"""
        val blocks = parseContentBlocks(1L, html)
        assertTrue(blocks.filterIsInstance<io.github.deserthouse.sdgun.feature.thread.ContentBlock.Video>().isEmpty())
    }

    @Test
    fun `V3 data-src 前缀不吞裸 src（交替序回归）`() {
        // SRC_ATTR 交替 (data-original|data-src|src)——裸 src 仍须命中
        val html = """<video src="http://picapp.sdgun.net/v/direct.mp4"></video>"""
        val blocks = parseContentBlocks(2L, html)
        assertEquals(
            "http://picapp.sdgun.net/v/direct.mp4",
            blocks.filterIsInstance<io.github.deserthouse.sdgun.feature.thread.ContentBlock.Video>()
                .single().url,
        )
    }
}
