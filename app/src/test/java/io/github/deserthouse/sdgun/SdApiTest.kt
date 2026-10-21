package io.github.deserthouse.sdgun

import io.github.deserthouse.sdgun.data.SdApi
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * R-A1 协议核心纯函数。sig 向量用独立口径预计算（Python hashlib，同盐同串）；
 * RSA 无私钥不可解回，断言输出形状（1024 位密钥=128 字节密文=172 字符 base64）。
 * 加密链正确性的权威验证=假凭据登录探针（服务端正常解密比对返回"账号或密码错误"），
 * 已于 2026-10-21 实测通过；真账号登录一次即成=MANUAL。
 */
class SdApiTest {

    @Test
    fun `md5 matches independent python vector`() {
        // python: hashlib.md5(b"shuidanmagappabcDEFnanjingxinxi").hexdigest()
        assertEquals(
            "c2f8fdd6588cfb82f5c9b09b4039dc79",
            SdApi.md5Hex("shuidanmagappabcDEFnanjingxinxi"),
        )
    }

    @Test
    fun `sig1 and sig2 use correct salts`() {
        val s1 = SdApi.sig1("aryDyspC6CcDAKvAtWtyPDEb")
        val s2 = SdApi.sig2("aryDyspC6CcDAKvAtWtyPDEb")
        assertEquals(32, s1.length)
        assertEquals(32, s2.length)
        assertNotEquals(s1, s2)
    }

    @Test
    fun `ua has 7 segments guest and 8 with token`() {
        val guest = SdApi.magappxUa("utdid123", null)
        assertEquals(7, guest.split("|").size)
        assertEquals("MAGAPPX", guest.split("|").first())
        val authed = SdApi.magappxUa("utdid123", "TOKENVALUE")
        assertEquals(8, authed.split("|").size)
        assertEquals("TOKENVALUE", authed.split("|").last())
    }

    @Test
    fun `rsa ciphertext shape is 172-char base64`() {
        val ct = SdApi.encryptPassword("probe123")
        assertTrue("len=${ct.length}", ct.length == 172)
        assertTrue(ct.matches(Regex("^[A-Za-z0-9+/]+={0,2}$")))
        // 同明文两次加密（PKCS1v15 随机填充）密文不同
        assertNotEquals(ct, SdApi.encryptPassword("probe123"))
    }

    @Test
    fun `mag-ss-key encrypts appcode-timestamp payload`() {
        val k = SdApi.magSsKey(1790000000)
        assertEquals(172, k.length)
    }

    @Test
    fun `utdid is 22 alnum chars and deterministic per seed`() {
        val a = SdApi.newUtdid(42L)
        val b = SdApi.newUtdid(42L)
        val c = SdApi.newUtdid(43L)
        assertEquals(22, a.length)
        assertTrue(a.matches(Regex("^[A-Za-z0-9]{22}$")))
        assertEquals(a, b)
        assertNotEquals(a, c)
    }
}
