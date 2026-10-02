package io.github.deserthouse.sdgun

import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color
import io.github.deserthouse.sdgun.web.ThemeInjector
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ThemeInjectorTest {

    private val scheme = lightColorScheme(
        primary = Color(0xFFB01F28),
        onPrimary = Color(0xFF000000),
        primaryContainer = Color(0xFF111111),
        onPrimaryContainer = Color(0xFF222222),
        surface = Color(0xFF112233),
        onSurface = Color(0xFF445566),
        surfaceVariant = Color(0xFF778899),
        onSurfaceVariant = Color(0xFF99AABB),
        outlineVariant = Color(0xFFCCDDEE),
    )

    @Test
    fun `variableCss substitutes tokens from scheme`() {
        val css = ThemeInjector.variableCss(scheme)
        assertTrue(css.startsWith(":root {"))
        assertTrue(css.contains("--sdg-primary: #B01F28"))
        assertTrue(css.contains("--sdg-on-primary: #000000"))
        assertTrue(css.contains("--sdg-surface: #112233"))
        assertTrue(css.contains("--sdg-on-surface: #445566"))
        assertTrue(css.contains("--sdg-surface-variant: #778899"))
        assertTrue(css.contains("--sdg-on-surface-variant: #99AABB"))
        assertTrue(css.contains("--sdg-outline-variant: #CCDDEE"))
    }

    @Test
    fun `applyScript embeds rules hide and anchors`() {
        val js = ThemeInjector.applyScript()
        assertTrue(js.contains("window.SDG_RULES="))
        assertTrue(js.contains("window.SDG_HIDE="))
        assertTrue(js.contains("window.SDG_ANCHORS="))
        // 规则层核心锚点在嵌入字面量中出现（jsString 转义后引号形式变化，查规则文本本身）
        assertTrue(js.contains(".plc"))
        assertTrue(js.contains(".landingPrompt"))
        // 幂等保护
        assertTrue(js.contains("if (window.SDG_APPLY) return"))
        // 契约：锚点全灭时跳过
        assertTrue(js.contains("return 'SKIP'"))
    }

    @Test
    fun `applyCall embeds vars and enables`() {
        val call = ThemeInjector.applyCall(scheme)
        assertTrue(call.startsWith("if (window.SDG_APPLY) SDG_APPLY("))
        assertTrue(call.contains("--sdg-primary: #B01F28"))
        assertTrue(call.contains(", true)"))
    }

    @Test
    fun `removeCall disables`() {
        val call = ThemeInjector.removeCall()
        assertTrue(call.contains("SDG_APPLY(null, false)"))
    }

    @Test
    fun `jsString escapes specials`() {
        // 用 Char 码构造输入/期望，避免测试自身的多层反斜杠歧义（92=反斜杠）
        val bs: Char = Char(92)
        val q: Char = Char(34)
        val nl: Char = Char(10)
        val tb: Char = Char(9)
        assertEquals(q.toString() + q.toString(), ThemeInjector.jsString(""))
        assertEquals(
            q.toString() + "a" + bs + bs + "b" + q.toString(),
            ThemeInjector.jsString("a" + bs + "b"),
        )
        assertEquals(
            q.toString() + "a" + bs + "nb" + q.toString(),
            ThemeInjector.jsString("a" + nl + "b"),
        )
        assertEquals(
            q.toString() + "a" + bs + "tb" + q.toString(),
            ThemeInjector.jsString("a" + tb + "b"),
        )
    }
}
