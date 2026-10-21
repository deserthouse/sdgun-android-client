package io.github.deserthouse.sdgun

import io.github.deserthouse.sdgun.data.repository.NewsRepository
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test

/** C1 反例/回归：mag1 pics_arr 实际字段是 url/tburl（无 pic）——firstCover 读错字段，
 *  封面自 v1.4.0 起从未显示。样本结构取自 2026-10-21 API 实测。 */
class NewsCoverTest {

    private fun rowJson(): JSONObject = JSONObject(
        """
        {"id":4009924,"title":"t","des":"d","forum_name":"f","publish_time_str":"x",
         "comment_count":5,"link":"https://bbs.sdgun.com.cn/t-4009924.html",
         "pics_arr":[
           {"url":"http://picapp.sdgun.net/pic/a.jpg","tburl":"http://picapp.sdgun.net/pic/a.jpg","width":0,"height":0,"video_url":""},
           {"url":"http://picapp.sdgun.net/pic/b.jpg","tburl":"http://picapp.sdgun.net/pic/b.jpg","width":0,"height":0,"video_url":""}
         ]}
        """.trimIndent(),
    )

    @Test
    fun `cover resolves from real api field names`() {
        val item = NewsRepository.parseItem(rowJson())
        assertNotNull("coverUrl is null - covers never render (C1)", item)
        assertEquals("http://picapp.sdgun.net/pic/a.jpg", item!!.coverUrl)
    }

    @Test
    fun `cover falls back past non-http entries`() {
        val o = JSONObject(
            """
            {"id":1,"link":"https://x/1","pics_arr":[
              {"width":0},
              {"url":"https://pic.sdgun.net/ok.jpg"}
            ]}
            """.trimIndent(),
        )
        val item = NewsRepository.parseItem(o)
        assertEquals("https://pic.sdgun.net/ok.jpg", item?.coverUrl)
    }

    @Test
    fun `no pics means null cover`() {
        val o = JSONObject("""{"id":1,"link":"https://x/1","pics_arr":[]}""")
        assertEquals(null, NewsRepository.parseItem(o)?.coverUrl)
    }
}
