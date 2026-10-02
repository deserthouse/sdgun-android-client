package io.github.deserthouse.sdgun

import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color
import io.github.deserthouse.sdgun.web.ThemeInjector
import org.junit.Assert.assertTrue
import org.junit.Test

class BuildCssTest {

    @Test
    fun `variables substituted from scheme`() {
        val scheme = lightColorScheme(
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
        val css = ThemeInjector.buildCss(scheme)
        assertTrue(css.contains("--sdg-primary: #B01F28"))
        assertTrue(css.contains("--sdg-on-primary: #000000"))
        assertTrue(css.contains("--sdg-surface: #112233"))
        assertTrue(css.contains("--sdg-on-surface: #445566"))
        assertTrue(css.contains("--sdg-surface-variant: #778899"))
        assertTrue(css.contains("--sdg-on-surface-variant: #99AABB"))
        assertTrue(css.contains("--sdg-outline-variant: #CCDDEE"))
    }

    @Test
    fun `injection script is idempotent`() {
        val scheme = lightColorScheme()
        val css = ThemeInjector.buildCss(scheme)
        assertTrue(css.contains("getElementById('sdg-theme')"))
        assertTrue(css.contains("removeChild"))
        assertTrue(css.contains("appendChild"))
    }

    @Test
    fun `hides footer and prompt selectors`() {
        val css = ThemeInjector.buildCss(lightColorScheme())
        assertTrue(css.contains(".footer"))
        assertTrue(css.contains(".landingPrompt"))
        assertTrue(css.contains("#wp"))
    }
}
