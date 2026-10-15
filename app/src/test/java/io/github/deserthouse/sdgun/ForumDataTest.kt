package io.github.deserthouse.sdgun

import io.github.deserthouse.sdgun.data.ForumRepository
import io.github.deserthouse.sdgun.web.EnrichBridge
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ForumDataTest {

    // ── ForumRepository.parseForumView ──

    @Test
    fun `forumView response parses to normalized summaries`() {
        val json = """
            {"success":true,"code":100,"list":[
              {"tid":4346363,"title":" 【晒照】【一天三支fsq】25-SIG17","user_name":"彩虹的彼端",
               "user_head":"http://bbs.sdgun.com.cn/uc_server/avatar.php?uid=1","pics":["http://picapp/a.jpg","http://picapp/b.jpg"],
               "applaud_count":2,"lastpost":"3分钟前","reply_count":3},
              {"tid":0,"title":"bad row dropped"}
            ],"total_page":7}
        """.trimIndent()
        val (map, totalPages) = ForumRepository.parseForumView(json)
        assertEquals(7, totalPages)
        assertEquals(1, map.size)
        val s = map[4346363L]!!
        assertEquals("彩虹的彼端", s.author)
        assertEquals(listOf("http://picapp/a.jpg", "http://picapp/b.jpg"), s.coverUrls)
        assertEquals(2, s.applauseCount)
        assertEquals("3分钟前", s.lastPostText)
    }

    @Test
    fun `forumView parse fails closed`() {
        // 空映射 + total_page=0
        assertEquals(0, ForumRepository.parseForumView("").second)
        assertEquals(0, ForumRepository.parseForumView("not json").second)
        assertTrue(ForumRepository.parseForumView("not json").first.isEmpty())
        assertTrue(ForumRepository.parseForumView("{\"success\":false,\"list\":[]}").first.isEmpty())
        assertTrue(ForumRepository.parseForumView("{\"success\":true}").first.isEmpty())
    }

    @Test
    fun `page urls use p param with budget cap`() {
        val urls = ForumRepository.pageUrls(190, 1, 9)
        assertEquals(ForumRepository.MAX_PAGES_PER_REQUEST, urls.size)
        assertTrue(urls[0].contains("fid=190&p=1&"))
        assertTrue(urls.all { it.contains("app.sdgun.com.cn") })
    }

    // ── EnrichBridge 请求解析与回填 JSON ──

    @Test
    fun `enrich request parses fid and tids`() {
        val req = EnrichBridge.parseRequest("{\"fid\":193,\"tids\":[4346363,4346000]}")
        assertEquals(193L, req?.fid)
        assertEquals(listOf(4346363L, 4346000L), req?.tids)
        assertNull(EnrichBridge.parseRequest(null))
        assertNull(EnrichBridge.parseRequest("{\"fid\":0,\"tids\":[1]}"))
        assertNull(EnrichBridge.parseRequest("{\"fid\":193,\"tids\":[]}"))
    }

    @Test
    fun `enrich json round-trips fields`() {
        val s = io.github.deserthouse.sdgun.data.ThreadSummary(
            tid = 1, title = "t", author = "a", avatarUrl = "http://x/av.jpg",
            coverUrls = listOf("http://x/c.jpg"), applauseCount = 7, lastPostText = null,
        )
        val json = EnrichBridge.buildEnrichJson(mapOf(1L to s))
        assertTrue(json.contains("\"1\":{\"a\":\"http://x/av.jpg\""))
        assertTrue(json.contains("\"c\":\"http://x/c.jpg\""))
        assertTrue(json.contains("\"z\":7"))
        // 回填 JSON 可被 org.json 解析（往返有效）
        org.json.JSONObject(json).getJSONObject("threads").getJSONObject("1")
    }
}
