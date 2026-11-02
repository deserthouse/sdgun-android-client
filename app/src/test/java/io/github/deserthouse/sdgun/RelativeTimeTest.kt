package io.github.deserthouse.sdgun

import io.github.deserthouse.sdgun.data.repository.BoardListRepository
import org.junit.Assert.assertEquals
import org.junit.Test

/** B1-7：相对时间纯函数（now 注入；>=7 天回落绝对时间；未来时间=刚刚） */
class RelativeTimeTest {

    private val now = 1_793_400_000_000L // 固定「当前」
    private fun secAgo(minutes: Long) = (now / 1000 - minutes * 60).toString()

    @Test
    fun `刚刚`() = assertEquals("刚刚", BoardListRepository.relativeTime(secAgo(0), now))

    @Test
    fun `分钟前`() = assertEquals("5 分钟前", BoardListRepository.relativeTime(secAgo(5), now))

    @Test
    fun `小时前`() = assertEquals("3 小时前", BoardListRepository.relativeTime(secAgo(180), now))

    @Test
    fun `天前`() = assertEquals("2 天前", BoardListRepository.relativeTime(secAgo(60 * 48), now))

    @Test
    fun `七天内边界`() =
        assertEquals("6 天前", BoardListRepository.relativeTime(secAgo(60 * 24 * 6), now))

    @Test
    fun `七天外回落绝对时间`() {
        val raw = secAgo(60 * 24 * 8)
        val out = BoardListRepository.relativeTime(raw, now)
        // 绝对时间格式 yyyy-M-d HH:mm（年月日随当前时区，只断言形态）
        assert(Regex("""^\d{4}-\d{1,2}-\d{1,2} \d{2}:\d{2}$""").matches(out))
    }

    @Test
    fun `未来时间按刚刚处理不出负数`() =
        assertEquals("刚刚", BoardListRepository.relativeTime((now / 1000 + 3600).toString(), now))

    @Test
    fun `非数字原样保留`() =
        assertEquals("刚刚编辑", BoardListRepository.relativeTime("刚刚编辑", now))
}
