package io.github.deserthouse.sdgun

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * S2-2：注入 JS 资产语法门。
 * 首选 node --check（PATH 有 node 时强校验）；本机无 node（已知环境）→ 跳过强校验
 * 并打印警告，退化为括号/引号平衡弱检查——真语法门=装机探针（av01~av08 全链路
 * 注入即语法验证，坏 JS 在探针上表现为零生效）。
 */
class InjectJsSyntaxTest {

    private fun asset(name: String): String =
        File("src/main/assets/sdg/$name").readText()

    private fun tryNodeCheck(file: File): Boolean? {
        val node = listOf("node", "node.exe").firstOrNull {
            runCatching { ProcessBuilder(it, "--version").start().waitFor() == 0 }.getOrDefault(false)
        } ?: return null
        val p = ProcessBuilder(node, "--check", file.absolutePath)
            .redirectErrorStream(true).start()
        val out = p.inputStream.bufferedReader().readText()
        val ok = p.waitFor() == 0
        assertTrue("node --check failed:\n$out", ok)
        return true
    }

    @Test
    fun `inject js passes syntax gate`() {
        val f = File("src/main/assets/sdg/inject.js")
        assertTrue(f.exists())
        when (tryNodeCheck(f)) {
            true -> println("inject.js: node --check PASS")
            false -> println("inject.js: node --check FAILED (see assertion above)")
            null -> println(
                "WARN: node not on PATH - strong syntax check SKIPPED " +
                    "(weak balance check only; real gate = on-device probes av01~av08)",
            )
        }
        weakBalance(f.readText())
    }

    @Test
    fun `asset css files are non-empty and balanced`() {
        for (name in listOf("rules.css", "base.css", "hide.css")) {
            val css = asset(name)
            assertTrue("$name empty", css.isNotBlank())
            assertEquals("$name brace mismatch", css.count { it == '{' }, css.count { it == '}' })
        }
    }

    /** 弱检查：忽略字符串/正则内的括号做平衡性统计——能拦住"截断/少一块"级事故 */
    private fun weakBalance(js: String) {
        var inS = false // '
        var inD = false // "
        var inRe = false // /.../ 粗略：正则面不做完整词法，只统计字符串外括号
        var depthCurly = 0
        var depthParen = 0
        var i = 0
        while (i < js.length) {
            val c = js[i]
            val prev = if (i > 0) js[i - 1] else ' '
            when {
                inS -> if (c == '\'' && prev != '\\') inS = false
                inD -> if (c == '"' && prev != '\\') inD = false
                else -> when {
                    c == '\'' -> inS = true
                    c == '"' -> inD = true
                    c == '{' -> depthCurly++
                    c == '}' -> depthCurly--
                    c == '(' -> depthParen++
                    c == ')' -> depthParen--
                }
            }
            i++
        }
        assertTrue("curly braces unbalanced: $depthCurly", depthCurly == 0)
        assertTrue("parens unbalanced: $depthParen", depthParen == 0)
        assertTrue("unterminated string literal", !inS && !inD)
    }
}
