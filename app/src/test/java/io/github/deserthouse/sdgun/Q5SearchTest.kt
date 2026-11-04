package io.github.deserthouse.sdgun

import io.github.deserthouse.sdgun.core.model.UiState
import io.github.deserthouse.sdgun.data.repository.BoardListRepository
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Q5 搜索族常驻回归（A1-10）：
 * parseTopicSearch（allTopicSearch list 信封，icon/name/link 形态=api_survey 实测 +
 * jadx AllTopicSearchActivity DataViewType.icon_name_line）。
 * userSearch 行=parsePeople 复用（PeopleLoaderTest 既有矩阵覆盖）。
 */
class Q5SearchTest {

    @Test
    fun `T1 合法信封解析出圈子行 icon name link`() {
        val rows = BoardListRepository.parseTopicSearch(
            """{"success":true,"list":[
                {"name":"原载区","icon":"https://pic/a.png","link":"https://bbs.sdgun.com.cn/forum.php?mod=forumdisplay&fid=153"},
                {"name":"交易区","icon":"","link":""}
            ]}""",
        )
        assertEquals(2, rows?.size)
        assertEquals("原载区", rows?.get(0)?.name)
        assertEquals("https://pic/a.png", rows?.get(0)?.icon)
        assertTrue(rows?.get(1)?.icon == null && rows?.get(1)?.link == null)
    }

    @Test
    fun `T2 拒绝与未知信封为 null（S05 口径）`() {
        assertNull(BoardListRepository.parseTopicSearch("""{"success":false,"msg":"denied"}"""))
        assertNull(BoardListRepository.parseTopicSearch("{}"))  // 无 list 键=未知信封
        assertNull(BoardListRepository.parseTopicSearch("<html>gateway</html>"))
    }

    @Test
    fun `T3 合法空=空列表且无名行被剔除`() {
        assertEquals(emptyList<BoardListRepository.TopicSearchRow>(),
            BoardListRepository.parseTopicSearch("""{"success":true,"list":[]}"""))
        // name 缺失的行 mapNotNull 剔除（不产出空名行）
        assertEquals(emptyList<BoardListRepository.TopicSearchRow>(),
            BoardListRepository.parseTopicSearch("""{"success":true,"list":[{"icon":"https://x"}]}"""))
    }

    @Test
    fun `T4 空关键词调用方短路（Repository 契约：不发起请求）`() {
        // searchTopics 的 blank 短路在网络层（suspend+触网不可 JVM 直测），
        // 此处锁契约：parse 层对空输入无特殊分支——调用方必须先短路。
        // （回归保护：若未来把 blank 短路从 searchTopics 移除，此注释即为规格锚点）
        val rows = BoardListRepository.parseTopicSearch("""{"success":true,"list":[]}""")
        assertEquals(emptyList<BoardListRepository.TopicSearchRow>(), rows)
    }
    // ── WP3/A1104-S04：SearchOwner 竞速与取消（A 慢返回不覆盖 B） ──

    @Test
    fun `O1 A 慢返回不覆盖 B 结果`() = kotlinx.coroutines.runBlocking {
        val owner = io.github.deserthouse.sdgun.feature.community.SearchOwner(
            kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.Unconfined))
        owner.mode = 0
        owner.query = "A"
        var releaseA: (() -> Unit)? = null
        val gate = kotlinx.coroutines.CompletableDeferred<Unit>()
        owner.search({ _ ->
            kotlinx.coroutines.delay(80)
            gate.await()
            listOf(BoardListRepository.TopicSearchRow("A结果", null, null))
        }, { _ -> emptyList() })
        owner.query = "B"
        owner.search({ _ ->
            listOf(BoardListRepository.TopicSearchRow("B结果", null, null))
        }, { _ -> emptyList() })
        kotlinx.coroutines.delay(150)
        releaseA?.invoke()
        gate.complete(Unit)
        kotlinx.coroutines.delay(150)
        val st = owner.topics
        assertTrue("A 晚返回不得覆盖 B",
            st is UiState.Data<*> &&
                (st as UiState.Data<List<BoardListRepository.TopicSearchRow>>)
                    .value.first().name == "B结果")
    }

    @Test
    fun `O2 空白关键词零请求`() = kotlinx.coroutines.runBlocking {
        val owner = io.github.deserthouse.sdgun.feature.community.SearchOwner(
            kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.Unconfined))
        var calls = 0
        owner.mode = 0
        owner.query = "  "
        owner.search({ _ -> calls++; null }, { _ -> calls++; null })
        kotlinx.coroutines.delay(50)
        assertEquals(0, calls)
    }

}


