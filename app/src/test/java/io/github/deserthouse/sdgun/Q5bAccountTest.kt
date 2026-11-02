package io.github.deserthouse.sdgun

import io.github.deserthouse.sdgun.data.repository.ProfileRepository
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * A5 批常驻回归：A5-2 setuserlogin 桥 URL 提取（宽容正则+缺失诚实 null）+
 * parseUserInfo 融合字段。A5-1 注册/找回=纯导航链接（UI 锚点由装机轮验证）。
 */
class Q5bAccountTest {

    @Test
    fun `B1 getUserInfo 内嵌桥 URL 被提取`() {
        val p = ProfileRepository.parseUserInfo(
            """{"success":true,"data":{"user_id":42,"name":" tester ",
                "bridge_url":"https://bbs.sdgun.com.cn/magmobileapi.php?mag_fuc=setuserlogin&uid=42&t=abc&k=xyz"}}""",
        )
        assertEquals(
            "https://bbs.sdgun.com.cn/magmobileapi.php?mag_fuc=setuserlogin&uid=42&t=abc&k=xyz",
            p?.webSessionUrl,
        )
    }

    @Test
    fun `B2 无桥字段=诚实 null 不构造`() {
        val p = ProfileRepository.parseUserInfo(
            """{"success":true,"data":{"user_id":42,"name":"t"}}""",
        )
        assertNull(p?.webSessionUrl)
    }

    @Test
    fun `B3 桥提取不受字段名约束（形状宽容）`() {
        // 服务端动态下发、jadx 无常量——对任意字段名的 URL 值都能命中
        val p = ProfileRepository.parseUserInfo(
            """{"success":true,"data":{"user_id":1,"name":"t","anything":"https://bbs.sdgun.com.cn/magmobileapi.php?mag_fuc=setuserlogin&uid=1&t=x&k=y"}}""",
        )
        assertTrue(p?.webSessionUrl?.contains("mag_fuc=setuserlogin") == true)
    }

    @Test
    fun `B4 既有字段解析不受桥提取影响（回归）`() {
        val p = ProfileRepository.parseUserInfo(
            """{"success":true,"data":{"user_id":42,"name":"t","group_name":"组","is_vip":1}}""",
        )
        assertEquals(42L, p?.userId)
        assertEquals("t", p?.name)
        assertEquals("组", p?.groupName)
        assertNull(p?.webSessionUrl)
    }
}
