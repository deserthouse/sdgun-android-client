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
            " window.SDG_ACTIVE = false;" +
            " window.SDG_ENSURE = function(id, css) { var el = document.getElementById(id);" +
            "  if (!el) { el = document.createElement('style'); el.id = id; document.head.appendChild(el); }" +
            "  el.textContent = css; };" +
            // AR-05：三个监听器在 IIFE 内一次性定义+绑定（引用永久稳定，永不摘除），
            // 生效性由 SDG_ACTIVE 令牌门控——disable 置 false 即全体惰性，重开置 true 即恢复。
            " window.SDG_IMG_RETRY = function(e) {" +
            "  if (!window.SDG_ACTIVE) return;" +
            "  var a = e.target && e.target.closest ? e.target.closest('a') : null; if (!a) return;" +
            "  var imgs = a.querySelectorAll('img'); var broken = false;" +
            "  for (var i = 0; i < imgs.length; i++) if (imgs[i].naturalWidth === 0) broken = true;" +
            "  if (!broken) return;" +
            "  e.preventDefault(); e.stopPropagation();" +
            "  for (var j = 0; j < imgs.length; j++) { if (imgs[j].naturalWidth === 0) {" +
            "   var src = imgs[j].src; imgs[j].src = 'about:blank';" +
            "   imgs[j].src = src + (src.indexOf('?') > -1 ? '&' : '?') + 'r=' + Date.now(); } } };" +
            " document.addEventListener('click', window.SDG_IMG_RETRY, true);" +
            " window.SDG_IMG_PH = 'data:image/svg+xml;utf8,<svg xmlns=%22http://www.w3.org/2000/svg%22 width=%2248%22 height=%2248%22><rect width=%2248%22 height=%2248%22 rx=%2210%22 fill=%22%235a5d68%22 fill-opacity=%220.45%22/></svg>';" +
            " window.SDG_IMG_FALLBACK = function(e) {" +
            "   if (!window.SDG_ACTIVE) return;" +
            "   var im = e.target;" +
            "   if (!im || im.tagName !== 'IMG' || im.dataset.sdgPh || !im.closest('.bm')) return;" +
            "   im.dataset.sdgPh = '1'; im.dataset.sdgOrig = im.src; im.src = window.SDG_IMG_PH; };" +
            " document.addEventListener('error', window.SDG_IMG_FALLBACK, true);" +
            " window.SDG_GALLERY_CLICK = function(e) {" +
            "  if (!window.SDG_ACTIVE) return;" +
            "  var im = e.target;" +
            "  if (!im || im.tagName !== 'IMG') return;" +
            "  var box = im.closest('.message, .pct, .t_f');" +
            "  if (!box) return;" +
            "  var imgs = Array.prototype.filter.call(box.querySelectorAll('img'), function(x){" +
            "   var s = x.currentSrc || x.src; return s && s.indexOf('http') === 0 && x.naturalWidth > 1 && !x.closest('a'); });" +
            "  if (imgs.length === 0) return;" +
            "  var urls = []; var idx = 0;" +
            "  for (var i = 0; i < imgs.length; i++) {" +
            "   var s = imgs[i].currentSrc || imgs[i].src; urls.push(s);" +
            "   if (imgs[i] === im) idx = i; }" +
            "  if (window.sdgGallery && window.sdgGallery.open) {" +
            "   e.preventDefault(); e.stopPropagation();" +
            "   window.sdgGallery.open(JSON.stringify({urls: urls, index: idx})); } };" +
            " document.addEventListener('click', window.SDG_GALLERY_CLICK, true);" +
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
            // SV-1：此处不得重定义 SDG_IMG_FALLBACK——监听器已在 IIFE 一次性定义（含
            // SDG_ACTIVE 令牌门控），重定义会以未门控版本覆盖之（首页关闭主题后占位仍生效）。
            // 破图占位 eager 扫描直接复用 IIFE 的函数与占位符。
            "  var bi = document.querySelectorAll('.bm img');" +
            "  for (var k = 0; k < bi.length; k++) if (bi[k].complete && bi[k].naturalWidth === 0) { bi[k].dataset.sdgPh = '1'; bi[k].dataset.sdgOrig = bi[k].src; bi[k].src = window.SDG_IMG_PH; }" +
            " };" +
            " if (!window.SDG_ENRICH_APPLY) {" +
            // AF-02 方案 A 回填：tid → 行容器插头像/封面缩略/点赞徽标（行打标防重复）
            " window.SDG_ENRICH_APPLY = function(data) {" +
            "  var th = (data && data.threads) || {};" +
            "  Array.prototype.forEach.call(document.querySelectorAll('a[href*=\"mod=viewthread\"]'), function(a){" +
            "   var m = a.href.match(/[?&]tid=(\\d+)/); if (!m) return;" +
            "   var row = a.closest('.threadlist li, li, .plc_xin, .plc') || a.parentElement;" +
            "   var d = th[m[1]]; if (!row || !d) return;" +
            "   if (!row.dataset.sdgEn) { row.dataset.sdgEn = '1';" +
            "    row.dataset.sdgPos0 = row.style.position || ''; row.dataset.sdgPad0 = row.style.paddingLeft || '';" +
            "    row.style.position = 'relative'; }" +
            "   if (d.a && !row.querySelector('img.sdg-avatar')) {" +
            "    var av = document.createElement('img'); av.src = d.a; av.className = 'sdg-avatar';" +
            "    av.style.cssText = 'position:absolute;left:6px;top:8px;width:40px;height:40px;border-radius:10px;object-fit:cover;background:var(--sdg-surface-variant);';" +
            "    row.appendChild(av);" +
            "    row.style.paddingLeft = '52px';" +
            "    row.style.boxSizing = 'border-box';" +
            "   }" +
            "   if (d.c && !row.querySelector('.list_img1, img.sdg-cover')) {" +
            "    var cv = document.createElement('img'); cv.src = d.c; cv.className = 'sdg-cover';" +
            "    cv.style.cssText = 'float:right;width:76px;height:56px;border-radius:8px;object-fit:cover;margin:4px 6px;background:var(--sdg-surface-variant);';" +
            "    row.insertBefore(cv, row.firstChild.nextSibling || row.firstChild);" +
            "   }" +
            "   if (d.z >= 0 && !row.querySelector('.sdg-zan')) {" +
            "    var z = document.createElement('span'); z.className = 'sdg-zan';" +
            "    z.style.cssText = 'position:absolute;right:6px;bottom:4px;font-size:11px;color:var(--sdg-on-surface-variant);';" +
            "    z.textContent = '赞 ' + d.z; row.appendChild(z);" +
            "   }" +
            "  });" +
            " };" +
            " }" +
            " window.SDG_APPLY = function(varsCss, enabled) {" +
            // A-05：关闭=对称撤销本注入做过的全部修改——style 节点、chips、观察器、
            // 监听器、sdg 标记类、fastpost 内联覆盖、破图占位、earlyBg 底色。
            // 只移除本实例持有的引用；站方后续自行改过的值不动。
            "  if (!enabled) {" +
            "   window.SDG_ACTIVE = false;" +
            "   ['sdg-vars','sdg-rules','sdg-hide','sdg-chips','sdg-tools','sdg-recent'].forEach(function(id){" +
            "    var el = document.getElementById(id); if (el && el.parentNode) el.parentNode.removeChild(el); });" +
            "   document.body.classList.remove('sdg-home');" +
            "   if (window.SDG_TITLE_OBS) { try { window.SDG_TITLE_OBS.disconnect(); } catch(e){}" +
            "    window.SDG_TITLE_OBS = null; window.SDG_TITLE_TARGET = ''; }" +
            "   var ph = document.querySelectorAll('img[data-sdg-ph]');" +
            "   for (var i = 0; i < ph.length; i++) { if (ph[i].dataset.sdgOrig) ph[i].src = ph[i].dataset.sdgOrig;" +
            "    delete ph[i].dataset.sdgPh; delete ph[i].dataset.sdgOrig; }" +
            "   var ops = document.querySelectorAll('.plc.sdg-op');" +
            "   for (var j = 0; j < ops.length; j++) ops[j].classList.remove('sdg-op');" +
            "   var enr = document.querySelectorAll('[data-sdgEn]');" +
            "   for (var e2 = 0; e2 < enr.length; e2++) { var rw = enr[e2];" +
            "    var avs = rw.querySelectorAll('img.sdg-avatar, img.sdg-cover, .sdg-zan');" +
            "    for (var e3 = 0; e3 < avs.length; e3++) avs[e3].parentNode.removeChild(avs[e3]);" +
            "    if (rw.dataset.sdgPos0 !== undefined) rw.style.position = rw.dataset.sdgPos0;" +
            "    if (rw.dataset.sdgPad0 !== undefined) rw.style.paddingLeft = rw.dataset.sdgPad0;" +
            "    delete rw.dataset.sdgEn; delete rw.dataset.sdgPos0; delete rw.dataset.sdgPad0; }" +
            "   var msg = document.getElementById('fastpostmessage');" +
            "   if (!msg) { var cands = document.querySelectorAll('textarea, input[type=text]');" +
            "    for (var m = 0; m < cands.length; m++) if ((cands[m].placeholder||'').indexOf('说') > -1) { msg = cands[m]; break; } }" +
            "   if (msg) { var bar = msg; for (var k = 0; k < 6 && bar; k++) {" +
            "    if (getComputedStyle(bar).position === 'fixed') break; bar = bar.parentElement; }" +
            "    if (bar && bar.dataset.sdgFp !== undefined) { var fp = bar.dataset.sdgFp.split('||');" +
            "     var pr = ['background','border-top','backdrop-filter'];" +
            "     for (var k2 = 0; k2 < 3; k2++) { if (fp[k2]) bar.style[pr[k2]] = fp[k2]; else bar.style.removeProperty(pr[k2]); }" +
            "     delete bar.dataset.sdgFp; } }" +
            "   var h = document.documentElement;" +
            "   if (h.dataset.sdgHbg !== undefined) { if (h.dataset.sdgHbg) h.style.background = h.dataset.sdgHbg;" +
            "    else h.style.removeProperty('background'); delete h.dataset.sdgHbg; }" +
            "   return 'REMOVED'; }" +
            "  if (document.querySelectorAll(window.SDG_ANCHORS).length === 0) return 'SKIP';" +
            "  window.SDG_ACTIVE = true;" +
            "  window.SDG_ENSURE('sdg-vars', varsCss);" +
            "  window.SDG_ENSURE('sdg-rules', window.SDG_RULES);" +
            "  window.SDG_ENSURE('sdg-hide', window.SDG_HIDE);" +
            // AR-05：监听器在 IIFE 一次性绑定并受 SDG_ACTIVE 门控——enable 只置令牌，
            // disable 只清令牌；引用永不重建（复审复现的摘新留旧问题从结构上消除）
            "  if (!document.documentElement.dataset.sdgHbg) {" +
            "   document.documentElement.dataset.sdgHbg = document.documentElement.style.background || '';" +
            "   document.documentElement.style.background = 'var(--sdg-surface-variant)'; }" +
            "  var msg = document.getElementById('fastpostmessage');" +
            // AF-02 方案 A：板块页列表增强——收集行 tid 上报原生（拉 forumView 游客数据），回填头像/封面/点赞
            "  if (location.href.indexOf('mod=forumdisplay') > -1 && window.sdgEnrich) {" +
            "   var seen = {}; var tids = [];" +
            "   Array.prototype.forEach.call(document.querySelectorAll('a[href*=\"mod=viewthread\"]'), function(a){" +
            "    var m = a.href.match(/[?&]tid=(\\d+)/); if (!m || seen[m[1]]) return; seen[m[1]] = 1; tids.push(m[1]); });" +
            "   if (tids.length > 0) {" +
            "    var fmatch = location.href.match(/[?&]fid=(\\d+)/);" +
            "    if (fmatch) window.sdgEnrich.enrich(JSON.stringify({fid: parseInt(fmatch[1]), tids: tids.slice(0, 60)}), true, location.href);" +
            "   }" +
            "  }" +
            // AF-05：板块页工具行——搜本板块 / 发帖（真实页面链接）
            "  if (location.href.indexOf('mod=forumdisplay') > -1 && !document.getElementById('sdg-tools')) {" +
            "   var f2 = location.href.match(/[?&]fid=(\\d+)/);" +
            "   var anchor = document.querySelector('.byg_thread_types');" +
            "   if (f2 && anchor && anchor.parentNode) {" +
            "    var bar = document.createElement('div'); bar.id = 'sdg-tools';" +
            "    bar.style.cssText = 'display:flex;gap:8px;padding:8px 12px;';" +
            "    var mkTool = function(label, href) { var a = document.createElement('a'); a.href = href;" +
            "     a.style.cssText = 'flex:0 0 auto;padding:6px 14px;border-radius:999px;background:var(--sdg-surface-variant);color:var(--sdg-on-surface);font-size:13px;text-decoration:none;';" +
            "     a.textContent = label; bar.appendChild(a); };" +
            "    mkTool('搜本板块', 'forum.php?mod=forum&srchfid=' + f2[1] + '&srchtxt=&searchsort=&mobile=2');" +
            "    mkTool('发帖', 'forum.php?mod=post&action=newthread&fid=' + f2[1] + '&mobile=2');" +
            "    anchor.parentNode.insertBefore(bar, anchor.nextSibling);" +
            "   }" +
            "  }" +
            "  var msg = document.getElementById('fastpostmessage');" +
            "  if (!msg) { var cands = document.querySelectorAll('textarea, input[type=text]');" +
            "   for (var i = 0; i < cands.length; i++) if ((cands[i].placeholder||'').indexOf('说') > -1) { msg = cands[i]; break; } }" +
            "  if (msg) { var bar = msg; for (var k = 0; k < 6 && bar; k++) {" +
            "   if (getComputedStyle(bar).position === 'fixed') break; bar = bar.parentElement; }" +
            "   if (bar && !bar.dataset.sdgFp) {" +
            "    bar.dataset.sdgFp = [bar.style.background||'', bar.style.borderTop||'', bar.style.backdropFilter||''].join('||'); }" +
            "   if (bar) { bar.style.background = 'var(--sdg-surface)';" +
            "    bar.style.borderTop = '1px solid var(--sdg-outline-variant)';" +
            "    bar.style.backdropFilter = 'none'; } }" +
            "  if (document.querySelectorAll('div[data-byginto]').length > 0) window.SDG_CHIPS();" +
            // C4：常用板块（localStorage 记录最近访问的板块，首页渲染直达 chips；纯客户端，无隐私面）
            "  if (location.href.indexOf('mod=forumdisplay') > -1) {" +
            "   var fm3 = location.href.match(/[?&]fid=(\\d+)/);" +
            "   var hf3 = document.querySelector('.header_font');" +
            "   if (fm3 && hf3 && hf3.textContent.trim()) {" +
            "    try { var rb = JSON.parse(localStorage.getItem('sdgRecentBoards') || '[]');" +
            "     rb = rb.filter(function(x) { return x.fid !== fm3[1]; });" +
            "     rb.unshift({fid: fm3[1], name: hf3.textContent.trim().slice(0, 12)});" +
            "     localStorage.setItem('sdgRecentBoards', JSON.stringify(rb.slice(0, 5))); } catch(e3) {}" +
            "   }" +
            "  }" +
            "  if (location.href.indexOf('forumlist=1') > -1 && !document.getElementById('sdg-recent')) {" +
            "   try { var rb2 = JSON.parse(localStorage.getItem('sdgRecentBoards') || '[]');" +
            "    if (rb2.length > 0) {" +
            "     var bar2 = document.createElement('div'); bar2.id = 'sdg-recent';" +
            "     bar2.style.cssText = 'display:flex;gap:8px;overflow-x:auto;padding:8px 12px;scrollbar-width:none;';" +
            "     var mk2 = function(label, fid) { var c = document.createElement('span');" +
            "      c.style.cssText = 'flex:0 0 auto;padding:5px 12px;border-radius:999px;background:var(--sdg-surface-variant);color:var(--sdg-on-surface);font-size:13px;white-space:nowrap;cursor:pointer;';" +
            "      c.textContent = label;" +
            "      c.onclick = function() { location.href = 'forum.php?mod=forumdisplay&fid=' + fid + '&mobile=2'; }; bar2.appendChild(c); };" +
            "     for (var ri = 0; ri < rb2.length; ri++) mk2(rb2[ri].name, rb2[ri].fid);" +
            "     var chips = document.getElementById('sdg-chips');" +
            "     if (chips && chips.parentNode) chips.parentNode.insertBefore(bar2, chips.nextSibling);" +
            "     else document.body.insertBefore(bar2, document.body.firstChild);" +
            "    } } catch(e4) {}" +
            "  }" +
            // AF-04/AR-04：楼主身份按“首楼证据”判定——仅当前页含 post_number=1 的楼层
            // （或 URL 无 page 参数即第一页）时取其作者为 owner（UID 优先，显示名回退）；
            // owner 按 tid 存 sessionStorage，翻页/追加复用不重定义；证据不足一律不标。
            "  if (location.href.indexOf('mod=viewthread') > -1) {" +
            "   var pm = location.href.match(/[?&]page=(\\d+)/);" +
            "   var firstPage = !(pm && parseInt(pm[1]) > 1);" +
            "   var tidm = location.href.match(/[?&]tid=(\\d+)/);" +
            "   var tidKey = tidm ? tidm[1] : location.href;" +
            "   var own = null;" +
            "   try { own = JSON.parse(sessionStorage.getItem('sdgOwner') || 'null'); } catch(e5) {}" +
            "   if (own && own.tid !== tidKey) own = null;" +
            "   if (!own && firstPage) {" +
            "    var fls = document.querySelectorAll('.postlist .plc:not(.plc_xin)');" +
            "    for (var fi = 0; fi < fls.length; fi++) {" +
            "     var pn = fls[fi].querySelector('.post_number');" +
            "     if (!pn || pn.textContent.replace(/[^0-9]/g, '') !== '1') continue;" +
            "     var al = fls[fi].querySelector('.authi a[href*=\"uid=\"]') || fls[fi].querySelector('.authi a');" +
            "     if (al) { var um = (al.getAttribute('href') || '').match(/uid=(\\d+)/);" +
            "      own = {tid: tidKey, name: (al.textContent || '').trim(), uid: um ? um[1] : ''};" +
            "      try { sessionStorage.setItem('sdgOwner', JSON.stringify(own)); } catch(e6) {}" +
            "      break; } } }" +
            "   if (own && own.name) {" +
            "    var fl2 = document.querySelectorAll('.postlist .plc:not(.plc_xin)');" +
            "    for (var oi = 0; oi < fl2.length; oi++) {" +
            "     var ai = fl2[oi].querySelector('.authi a[href*=\"uid=\"]') || fl2[oi].querySelector('.authi a');" +
            "     if (!ai) continue;" +
            "     var um2 = (ai.getAttribute('href') || '').match(/uid=(\\d+)/);" +
            "     var isOp = own.uid && um2 ? um2[1] === own.uid : (ai.textContent || '').trim() === own.name;" +
            "     if (isOp) fl2[oi].classList.add('sdg-op'); } } }" +
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
