package io.github.deserthouse.sdgun

import io.github.deserthouse.sdgun.data.repository.BoardListRepository
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * A1/A2 反例（2026-11-20）：threadtypes 三真实形态回归。
 * 夹具来源=tests/review_20261114_round3/meta/fd_175.json（fid=175 字典板块实采）
 * + fd_153 字符串形态（P0 实采）+ 旧假设兼容形态。
 * 旧代码只走 ③——字典板块 4/5 分类恒空（fd_175 模拟 parse 得 [("types","")]）。
 */
class A1ThreadtypesTest {

    // ── 形态① data.types.types 数组（fid=175 实采，10 分类）──

    private val fd175Data = """
    {
      "fid": 175, "name": "NERF讨论区",
      "icon": "", "post_today": "137", "thread": "47726", "post": "",
      "orders": [{"value": "", "name": "默认"}, {"value": "hot", "name": "热门"}],
      "threadtypes": {
        "required": "1", "listable": "1",
        "types": {"204": "集优质内容", "98": "讨论", "93": "测评"}
      },
      "types": {
        "required": true, "listable": true,
        "types": [
          {"id": 204, "name": "集优质内容，赢峰海上旋"},
          {"id": 98, "name": "讨论"},
          {"id": 93, "name": "测评"},
          {"id": 95, "name": "改造"},
          {"id": 140, "name": "参与活动！"},
          {"id": 96, "name": "涂装"}
        ]
      }
    }
    """.trimIndent()

    private fun envelope(data: String) = """{"success":true,"code":100,"msg":null,"data":$data}"""

    @Test
    fun `T1 dict form fid175 yields 6 categories from data types types array`() {
        val meta = BoardListRepository.parseBoardMeta(envelope(fd175Data))
        assertTrue("meta 不得为 null", meta != null)
        val types = meta!!.threadTypes
        assertEquals("data.types.types 数组应产出 6 分类", 6, types.size)
        assertEquals("204" to "集优质内容，赢峰海上旋", types[0])
        assertEquals("98" to "讨论", types[1])
        assertEquals("93" to "测评", types[2])
    }

    // ── 形态① fallback：无 data.types 时走 threadtypes.types 内嵌字典 ──

    @Test
    fun `T2 threadtypes types inner dict fallback`() {
        val json = envelope("""
        {
          "fid": 199, "name": "白山光学",
          "threadtypes": {
            "required": "0", "listable": "1",
            "types": {"201": "光学测评", "202": "白山科普"}
          }
        }
        """.trimIndent())
        val meta = BoardListRepository.parseBoardMeta(json)
        assertEquals(2, meta?.threadTypes?.size)
        assertEquals("201" to "光学测评", meta?.threadTypes?.get(0))
        assertEquals("202" to "白山科普", meta?.threadTypes?.get(1))
    }

    // ── 形态② threadtypes="" 字符串（fid=153 P0 实采）──

    @Test
    fun `T3 string form no crash and empty categories`() {
        val json = envelope("""
        {"fid": 153, "name": "卫星区", "threadtypes": ""}
        """.trimIndent())
        val meta = BoardListRepository.parseBoardMeta(json)
        assertTrue("字符串形态 meta 不为 null", meta != null)
        assertTrue("字符串形态分类为空", meta?.threadTypes?.isEmpty() == true)
    }

    // ── 形态③ 旧假设兼容（{key: {name}} 直排）──

    @Test
    fun `T4 legacy form compatible no regression`() {
        val json = envelope("""
        {
          "fid": 100, "name": "测试板块",
          "threadtypes": {"55": {"name": "旧分类A"}, "66": {"name": "旧分类B"}}
        }
        """.trimIndent())
        val meta = BoardListRepository.parseBoardMeta(json)
        assertEquals(2, meta?.threadTypes?.size)
        assertEquals("55" to "旧分类A", meta?.threadTypes?.get(0))
    }

    // ── 无 threadtypes 字段 ──

    @Test
    fun `T5 no threadtypes field empty categories no crash`() {
        val json = envelope("""{"fid": 1, "name": "无分类板块"}""")
        val meta = BoardListRepository.parseBoardMeta(json)
        assertTrue(meta?.threadTypes?.isEmpty() == true)
    }

    // ── 统计字段（fid=175 实采 post_today=137 thread=47726）──

    @Test
    fun `T6 stats fields real-shape check`() {
        val meta = BoardListRepository.parseBoardMeta(envelope(fd175Data))
        assertEquals("137", meta?.postToday)
        assertEquals("47726", meta?.threadCount)
    }
}
