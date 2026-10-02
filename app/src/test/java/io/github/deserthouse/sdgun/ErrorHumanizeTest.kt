package io.github.deserthouse.sdgun

import io.github.deserthouse.sdgun.web.humanizeError
import org.junit.Assert.assertEquals
import org.junit.Test

class ErrorHumanizeTest {

    @Test
    fun `connection failures map to server unreachable`() {
        for (raw in listOf(
            "net::ERR_CONNECTION_REFUSED",
            "net::ERR_CONNECTION_RESET",
            "net::ERR_CONNECTION_TIMED_OUT",
            "net::ERR_TIMED_OUT",
        )) assertEquals("无法连接到论坛服务器", humanizeError(raw))
    }

    @Test
    fun `offline maps to no network`() {
        assertEquals("当前无网络连接", humanizeError("net::ERR_INTERNET_DISCONNECTED"))
        assertEquals("当前无网络连接", humanizeError("net::ERR_ADDRESS_UNREACHABLE"))
    }

    @Test
    fun `dns failure maps to resolution`() {
        assertEquals("域名解析失败", humanizeError("net::ERR_NAME_NOT_RESOLVED"))
    }

    @Test
    fun `unknown maps to generic`() {
        assertEquals("页面加载失败", humanizeError("net::ERR_SOMETHING_NEW"))
    }

    @Test
    fun `http statuses map`() {
        assertEquals("页面不存在或已被删除", humanizeError("", 404))
        assertEquals("服务器暂时不可用", humanizeError("", 502))
        assertEquals("没有访问权限", humanizeError("", 403))
        assertEquals("服务器返回错误（418）", humanizeError("", 418))
    }
}
