package io.github.deserthouse.sdgun

import io.github.deserthouse.sdgun.data.repository.ThreadHistoryStore
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** N1/A2-4：本地帖历史纯函数——去重/置顶/封顶/坏数据容忍 */
class ThreadHistoryStoreTest {

    private fun e(tid: Long, title: String = "t$tid", at: Long = tid) =
        ThreadHistoryStore.Entry(tid, title, at)

    @Test
    fun `追加置顶且同 tid 去重`() {
        val out = ThreadHistoryStore.addEntry(
            listOf(e(1), e(2), e(3)), tid = 2, title = "t2-new", at = 999)
        assertEquals(listOf(2L, 1L, 3L), out.map { it.tid })
        assertEquals("t2-new", out[0].title)
        assertEquals(999L, out[0].at)
    }

    @Test
    fun `封顶 50 截尾`() {
        var list = (1L..60L).map { e(it) }
        list = ThreadHistoryStore.addEntry(list, tid = 61, title = "t61", at = 61)
        assertEquals(ThreadHistoryStore.MAX, list.size)
        assertEquals(61L, list.first().tid)
        assertEquals(49L, list.last().tid)  // [61,1..49]——最旧的 50~60 被截
    }

    @Test
    fun `非法输入零变更`() {
        val base = listOf(e(1))
        assertEquals(base, ThreadHistoryStore.addEntry(base, tid = 0, title = "x", at = 1))
        assertEquals(base, ThreadHistoryStore.addEntry(base, tid = 5, title = "  ", at = 1))
    }

    @Test
    fun `json 往返保序保字段`() {
        val list = listOf(e(7, "标题七", 123L), e(8, "标题八", 456L))
        val back = ThreadHistoryStore.fromJson(ThreadHistoryStore.toJson(list))
        assertEquals(list, back)
    }

    @Test
    fun `坏 json 与坏行容忍`() {
        assertTrue(ThreadHistoryStore.fromJson("not json").isEmpty())
        val back = ThreadHistoryStore.fromJson("""[{"tid":0,"title":"x"},{"tid":5,"title":"ok"}]""")
        assertEquals(listOf(5L), back.map { it.tid })
    }
}
