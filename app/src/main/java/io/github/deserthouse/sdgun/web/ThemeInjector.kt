package io.github.deserthouse.sdgun.web

import android.content.Context
import androidx.compose.material3.ColorScheme
import androidx.compose.ui.graphics.toArgb

/**
 * 主题注入器：变量层（按主题生成）+ 应用脚本（一次性定义 SDG_APPLY，幂等）。
 *
 * S2-1 资产化：注入 JS 主体与 CSS 迁 `assets/sdg/`（唯一事实源：inject.js / rules.css /
 * base.css[=BASE+CONTAINER] / hide.css）；本对象只负责组装（CSS 经 jsString 嵌为
 * window.SDG_* 变量后拼 inject.js）与变量层生成。JVM 测试经 testAssets 注入假资产。
 *
 * 运行时契约（SDG_APPLY 内建）：
 *  - 内容锚点（CONTENT_ANCHORS）全不命中 → 跳过注入并返回 SKIP（未知模板保护）；
 *  - disabled=true → 移除全部注入节点（干净回退原版）；
 *  - 三个层级（vars/rules+container/hide）独立节点，主题切换只更新 vars 层。
 */
object ThemeInjector {

    /** 内容锚点：任一命中才认为本页是已知触屏模板 */
    const val CONTENT_ANCHORS: String =
        ".plc, .threadlist li, .news_list, #wp, .bmw, .subforumshow, #byg_header"

    /** JVM 测试覆盖点：非 null 时 asset() 直接查表（生产恒 null，走 context.assets） */
    internal var testAssets: Map<String, String>? = null

    private val assetCache = HashMap<String, String>()

    private fun asset(context: Context?, name: String): String {
        testAssets?.let { return it.getValue(name) }
        synchronized(assetCache) { assetCache[name]?.let { return it } }
        val text = context!!.assets.open("sdg/$name").bufferedReader().readText()
        synchronized(assetCache) { assetCache[name] = text }
        return text
    }

    private fun hex(c: Int): String = String.format("#%06X", c and 0xFFFFFF)

    /** 变量层：仅 :root 色值（规则层不变，主题切换只重发这一层） */
    fun variableCss(scheme: ColorScheme): String {
        val primary = hex(scheme.primary.toArgb())
        val onPrimary = hex(scheme.onPrimary.toArgb())
        val primaryContainer = hex(scheme.primaryContainer.toArgb())
        val onPrimaryContainer = hex(scheme.onPrimaryContainer.toArgb())
        val surface = hex(scheme.surface.toArgb())
        val onSurface = hex(scheme.onSurface.toArgb())
        val surfaceVariant = hex(scheme.surfaceVariant.toArgb())
        val onSurfaceVariant = hex(scheme.onSurfaceVariant.toArgb())
        val outlineVariant = hex(scheme.outlineVariant.toArgb())
        // 亮度判定明暗：让原生控件（select/输入框自动填充/滚动条）跟随主题渲染
        val surfaceArgb = scheme.surface.toArgb()
        val luminance = 0.299 * ((surfaceArgb shr 16) and 0xFF) +
            0.587 * ((surfaceArgb shr 8) and 0xFF) + 0.114 * (surfaceArgb and 0xFF)
        val modeName = if (luminance < 128) "dark" else "light"
        return ":root {" +
            "--sdg-primary: $primary;" +
            "--sdg-on-primary: $onPrimary;" +
            "--sdg-primary-container: $primaryContainer;" +
            "--sdg-on-primary-container: $onPrimaryContainer;" +
            "--sdg-surface: $surface;" +
            "--sdg-on-surface: $onSurface;" +
            "--sdg-surface-variant: $surfaceVariant;" +
            "--sdg-on-surface-variant: $onSurfaceVariant;" +
            "--sdg-outline-variant: $outlineVariant;" +
            "--sdg-radius: 16px;" +
            "--sdg-radius-lg: 20px;" +
            "color-scheme: $modeName;" +
            "}"
    }

    /** JS 字符串字面量转义（纯函数：含引号/反斜杠/控制符的 CSS 安全进 JS） */
    internal fun jsString(s: String): String = buildString {
        append('"')
        for (ch in s) when (ch) {
            '\\' -> append("\\\\")
            '"' -> append("\\\"")
            '\n' -> append("\\n")
            '\r' -> append("\\r")
            '\t' -> append("\\t")
            else -> if (ch < ' ') append("\\u%04x".format(ch.code)) else append(ch)
        }
        append('"')
    }

    /** 应用脚本（S2-1）：CSS 资产经 jsString 嵌为 window.SDG_* 变量 + inject.js 主体。
     *  幂等：inject.js 首行即 `if (window.SDG_APPLY) return;`，重复 evaluate 无副作用。 */
    fun applyScript(context: Context? = null): String =
        "window.SDG_RULES=" + jsString(asset(context, "base.css") + "\n" + asset(context, "rules.css")) + ";" +
            "window.SDG_HIDE=" + jsString(asset(context, "hide.css")) + ";" +
            "window.SDG_ANCHORS=" + jsString(CONTENT_ANCHORS) + ";" +
            asset(context, "inject.js")

    /** onPageStarted 阶段的提前压暗：HTML 流式渲染即用暗底，消除白→黑闪变 */
    fun earlyBgJs(scheme: ColorScheme): String {
        val bg = hex(scheme.surfaceVariant.toArgb())
        return "(function(){var h=document.documentElement;" +
            "h.style.background='" + bg + "';})()"
    }

    /** 应用调用（vars 层随主题生成；每次主题变更只发这一小段） */
    fun applyCall(scheme: ColorScheme): String =
        "if (window.SDG_APPLY) SDG_APPLY(" + jsString(variableCss(scheme)) + ", true);"

    /** 移除调用（注入开关关闭时） */
    fun removeCall(): String =
        "if (window.SDG_APPLY) SDG_APPLY(null, false);"
}
