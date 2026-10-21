package io.github.deserthouse.sdgun

import io.github.deserthouse.sdgun.data.NewsItem
import io.github.deserthouse.sdgun.data.NewsRepository
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** AV-02：资讯分页纯逻辑（去重合并/新增判定/终点语义的数据侧） */
class NewsPagingTest {

    private fun item(id: Long) = NewsItem(
        id = id, title = "t$id", summary = "", coverUrl = null,
        source = "", timeText = null, comments = null, link = "https://x/$id",
    )

    @Test
    fun `merge into empty adds everything`() {
        val (merged, added) = NewsRepository.mergeById(emptyList(), listOf(item(1), item(2)))
        assertTrue(added)
        assertEquals(listOf(1L, 2L), merged.map { it.id })
    }

    @Test
    fun `all-duplicate page means no more data`() {
        val old = listOf(item(1), item(2))
        val (merged, added) = NewsRepository.mergeById(old, listOf(item(1), item(2)))
        // 成功返回但零新增 → UI 置 endReached（成功空页/全重复才是真终点，网络失败不是）
        assertFalse(added)
        assertEquals(listOf(1L, 2L), merged.map { it.id })
    }

    @Test
    fun `partial new items appended keeping old order`() {
        val old = listOf(item(1), item(2))
        val (merged, added) = NewsRepository.mergeById(old, listOf(item(2), item(3)))
        assertTrue(added)
        assertEquals(listOf(1L, 2L, 3L), merged.map { it.id })
    }

    @Test
    fun `merge keeps first occurrence position on duplicate`() {
        val (merged, _) = NewsRepository.mergeById(listOf(item(5), item(6)), listOf(item(5)))
        assertEquals(listOf(5L, 6L), merged.map { it.id })
    }

    @Test
    fun `pageUrls build p-parameter ascending`() {
        val urls = NewsRepository.pageUrls(1, 2, 2)
        assertEquals(2, urls.size)
        assertTrue(urls[0].contains("p=2"))
        assertTrue(urls[1].contains("p=3"))
        assertTrue(urls.all { it.contains("cat_id=1") })
    }

    // ---- fetchPage 失败/空页语义（经 fetchOverride 注入；真机链路无法可靠注入失败，
    // ---- HttpURLConnection 对代理失败会 failover 直连——见 Repository 注释）----

    @Test
    fun `all pages failed means null not empty`() {
        NewsRepository.fetchOverride = { null }
        try {
            kotlinx.coroutines.runBlocking {
                assertEquals(null, NewsRepository.fetchPage(1, 1))
            }
        } finally {
            NewsRepository.fetchOverride = null
        }
    }

    @Test
    fun `success with zero rows is empty list - the only true end`() {
        NewsRepository.fetchOverride = { """{"success":true,"list":[]}""" }
        try {
            kotlinx.coroutines.runBlocking {
                val r = NewsRepository.fetchPage(1, 1)
                assertEquals(emptyList<NewsItem>(), r)
            }
        } finally {
            NewsRepository.fetchOverride = null
        }
    }

    @Test
    fun `partial failure keeps successful page`() {
        var first = true
        NewsRepository.fetchOverride = { if (first) { first = false; null } else
            """{"success":true,"list":[{"id":9,"title":"n","link":"https://x/9"}]}""" }
        try {
            kotlinx.coroutines.runBlocking {
                val r = NewsRepository.fetchPage(1, 1, 2)
                assertEquals(1, r?.size)
            }
        } finally {
            NewsRepository.fetchOverride = null
        }
    }
}
