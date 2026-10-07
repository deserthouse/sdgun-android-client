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

    /** 应用脚本：幂等定义 SDG_APPLY（仅首次生效，重复 evaluate 无副作用） */
    fun applyScript(): String =
        "window.SDG_RULES=" + jsString(ThemeRules.BASE + "\n" + ThemeRules.RULES + "\n" + ThemeRules.CONTAINER) + ";" +
            "window.SDG_HIDE=" + jsString(ThemeRules.HIDE) + ";" +
            "window.SDG_ANCHORS=" + jsString(CONTENT_ANCHORS) + ";" +
            "(function(){ if (window.SDG_APPLY) return;" +
            " window.SDG_ENSURE = function(id, css) { var el = document.getElementById(id);" +
            "  if (!el) { el = document.createElement('style'); el.id = id; document.head.appendChild(el); }" +
            "  el.textContent = css; };" +
            " window.SDG_IMG_RETRY = function(e) {" +
            "  var a = e.target && e.target.closest ? e.target.closest('a') : null; if (!a) return;" +
            "  var imgs = a.querySelectorAll('img'); var broken = false;" +
            "  for (var i = 0; i < imgs.length; i++) if (imgs[i].naturalWidth === 0) broken = true;" +
            "  if (!broken) return;" +
            "  e.preventDefault(); e.stopPropagation();" +
            "  for (var j = 0; j < imgs.length; j++) { if (imgs[j].naturalWidth === 0) {" +
            "   var src = imgs[j].src; imgs[j].src = 'about:blank';" +
            "   imgs[j].src = src + (src.indexOf('?') > -1 ? '&' : '?') + 'r=' + Date.now(); } } };" +
            " if (!window.SDG_IMG_RETRY_BOUND) { document.addEventListener('click', window.SDG_IMG_RETRY, true); window.SDG_IMG_RETRY_BOUND = true; }" +
            " window.SDG_CHIPS = function() {" +
            "  var groups = document.querySelectorAll('div[data-byginto]');" +
            "  if (groups.length === 0 || document.getElementById('sdg-chips')) return;" +
            "  var bar = document.createElement('div'); bar.id = 'sdg-chips';" +
            "  bar.style.cssText = 'position:sticky;top:0;z-index:9998;display:flex;gap:8px;overflow-x:auto;padding:8px 12px;background:var(--sdg-surface);border-bottom:1px solid var(--sdg-outline-variant);scrollbar-width:none;';" +
            "  var mk = function(txt, fn) { var c = document.createElement('span');" +
            "   c.textContent = txt; c.style.cssText = 'flex:0 0 auto;padding:6px 14px;border-radius:999px;background:var(--sdg-surface-variant);color:var(--sdg-on-surface);font-size:14px;white-space:nowrap;cursor:pointer;';" +
            "   c.onclick = fn; bar.appendChild(c); };" +
            "  mk('最新', function(){ location.href = 'forum.php?mod=guide&view=newthread&mobile=2'; });" +
            "  groups.forEach(function(g) { var h = g.querySelector('h2 a'); if (h) mk(h.textContent.trim(), function(){ g.scrollIntoView({behavior:'smooth'}); }); });" +
            "  var hdr = document.getElementById('byg_header');" +
            "  if (hdr && hdr.parentElement) hdr.parentElement.insertBefore(bar, hdr.nextSibling);" +
            "  document.body.classList.add('sdg-home');" +
            "  window.SDG_IMG_PH = 'data:image/svg+xml;utf8,<svg xmlns=%22http://www.w3.org/2000/svg%22 width=%2248%22 height=%2248%22><rect width=%2248%22 height=%2248%22 rx=%2210%22 fill=%22%235a5d68%22 fill-opacity=%220.45%22/></svg>';" +
            "  window.SDG_IMG_FALLBACK = function(e) { var im = e.target;" +
            "   if (!im || im.tagName !== 'IMG' || im.dataset.sdgPh || !im.closest('.bm')) return;" +
            "   im.dataset.sdgPh = '1'; im.src = window.SDG_IMG_PH; };" +
            "  if (!window.SDG_IMG_FALLBACK_BOUND) { document.addEventListener('error', window.SDG_IMG_FALLBACK, true); window.SDG_IMG_FALLBACK_BOUND = true; }" +
            "  var bi = document.querySelectorAll('.bm img');" +
            "  for (var k = 0; k < bi.length; k++) if (bi[k].complete && bi[k].naturalWidth === 0) { bi[k].dataset.sdgPh = '1'; bi[k].src = window.SDG_IMG_PH; }" +
            " };" +
            " window.SDG_APPLY = function(varsCss, enabled) {" +
            "  if (!enabled) { ['sdg-vars','sdg-rules','sdg-hide','sdg-chips'].forEach(function(id){" +
            "   var el = document.getElementById(id); if (el && el.parentNode) el.parentNode.removeChild(el); });" +
            "   document.body.classList.remove('sdg-home');" +
            "   return 'REMOVED'; }" +
            "  if (document.querySelectorAll(window.SDG_ANCHORS).length === 0) return 'SKIP';" +
            "  window.SDG_ENSURE('sdg-vars', varsCss);" +
            "  window.SDG_ENSURE('sdg-rules', window.SDG_RULES);" +
            "  window.SDG_ENSURE('sdg-hide', window.SDG_HIDE);" +
            "  var msg = document.getElementById('fastpostmessage');" +
            "  if (!msg) { var cands = document.querySelectorAll('textarea, input[type=text]');" +
            "   for (var i = 0; i < cands.length; i++) if ((cands[i].placeholder||'').indexOf('说') > -1) { msg = cands[i]; break; } }" +
            "  if (msg) { var bar = msg; for (var k = 0; k < 6 && bar; k++) {" +
            "   if (getComputedStyle(bar).position === 'fixed') break; bar = bar.parentElement; }" +
            "   if (bar) { bar.style.background = 'var(--sdg-surface)';" +
            "    bar.style.borderTop = '1px solid var(--sdg-outline-variant)';" +
            "    bar.style.backdropFilter = 'none'; } }" +
            "  if (document.querySelectorAll('div[data-byginto]').length > 0) window.SDG_CHIPS();" +
            "  if (location.href.indexOf('mod=viewthread') > -1) { var fl = document.querySelectorAll('.postlist .plc:not(.plc_xin)');" +
            "   if (fl.length > 1) fl[0].classList.add('sdg-op'); }" +
            // 原生栏标题取页面自身 DOM（站方 <title> 是"品牌+板块+标题+SEO 串"的拼接体，读起来糊成一团）
            "  var ttl = '';" +
            "  if (location.href.indexOf('mod=viewthread') > -1) {" +
            "   var h2 = document.querySelector('.postlist h2');" +
            "   if (h2) { ttl = h2.textContent.trim();" +
            "    if (ttl.charAt(0) === '[') { var cl = ttl.indexOf(']'); if (cl > 0) ttl = ttl.slice(cl + 1).trim(); } }" +
            "  } else if (location.href.indexOf('mod=forumdisplay') > -1) {" +
            "   var hf = document.querySelector('.header_font'); if (hf) ttl = hf.textContent.trim();" +
            "  }" +
            "  if (ttl) {" +
            // 站方脚本会在加载后回写 <title>，用观察器守住我们的干净标题（相等判断防自激循环）
            "   window.SDG_TITLE_TARGET = ttl;" +
            "   var tEl = document.querySelector('title');" +
            "   if (tEl && !window.SDG_TITLE_OBS) {" +
            "    window.SDG_TITLE_OBS = new MutationObserver(function() {" +
            "     if (window.SDG_TITLE_TARGET && document.title !== window.SDG_TITLE_TARGET)" +
            "      document.title = window.SDG_TITLE_TARGET; });" +
            "    window.SDG_TITLE_OBS.observe(tEl, {childList: true, characterData: true, subtree: true});" +
            "   }" +
            "   if (document.title !== ttl) document.title = ttl;" +
            "  }" +
            "  return 'OK'; };" +
            "})()"

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
