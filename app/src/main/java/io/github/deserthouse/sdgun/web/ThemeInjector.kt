package io.github.deserthouse.sdgun.web

import androidx.compose.material3.ColorScheme
import androidx.compose.ui.graphics.toArgb

/**
 * 主题注入器：变量层（按主题生成）+ 应用脚本（一次性定义 SDG_APPLY，幂等）。
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

    /** 应用脚本：幂等定义 SDG_APPLY（仅首次生效，重复 evaluate 无副作用） */
    fun applyScript(): String =
        "window.SDG_RULES=" + jsString(ThemeRules.BASE + "\n" + ThemeRules.RULES + "\n" + ThemeRules.CONTAINER) + ";" +
            "window.SDG_HIDE=" + jsString(ThemeRules.HIDE) + ";" +
            "window.SDG_ANCHORS=" + jsString(CONTENT_ANCHORS) + ";" +
            "(function(){ if (window.SDG_APPLY) return;" +
            " window.SDG_ENSURE = function(id, css) { var el = document.getElementById(id);" +
            "  if (!el) { el = document.createElement('style'); el.id = id; document.head.appendChild(el); }" +
            "  el.textContent = css; };" +
            " window.SDG_APPLY = function(varsCss, enabled) {" +
            "  if (!enabled) { ['sdg-vars','sdg-rules','sdg-hide'].forEach(function(id){" +
            "   var el = document.getElementById(id); if (el && el.parentNode) el.parentNode.removeChild(el); });" +
            "   return 'REMOVED'; }" +
            "  if (document.querySelectorAll(window.SDG_ANCHORS).length === 0) return 'SKIP';" +
            "  window.SDG_ENSURE('sdg-vars', varsCss);" +
            "  window.SDG_ENSURE('sdg-rules', window.SDG_RULES);" +
            "  window.SDG_ENSURE('sdg-hide', window.SDG_HIDE);" +
            "  return 'OK'; };" +
            "})()"

    /** 应用调用（vars 层随主题生成；每次主题变更只发这一小段） */
    fun applyCall(scheme: ColorScheme): String =
        "if (window.SDG_APPLY) SDG_APPLY(" + jsString(variableCss(scheme)) + ", true);"

    /** 移除调用（注入开关关闭时） */
    fun removeCall(): String =
        "if (window.SDG_APPLY) SDG_APPLY(null, false);"
}
