package io.github.deserthouse.sdgun.web

import androidx.compose.material3.ColorScheme
import androidx.compose.ui.graphics.toArgb

/**
 * 网页主题注入：把 Compose 侧的设计令牌（色彩/圆角）翻译成 CSS，
 * onPageFinished 时 evaluateJavascript 注入，让 Discuz 触屏版贴合 M3E 视觉。
 *
 * 选择器基于 bbs.sdgun.com.cn 当前触屏模板（bygsjw_3sj，2026-09 取样）：
 * 楼层 .plc / 顶栏 #byg_header / 页脚 .footer / 登录浮层 .landingPrompt。
 */
object ThemeInjector {

    /** ARGB int -> #RRGGBB */
    private fun hex(c: Int): String = String.format("#%06X", c and 0xFFFFFF)

    fun buildCss(scheme: ColorScheme): String {
        val primary = hex(scheme.primary.toArgb())
        val onPrimary = hex(scheme.onPrimary.toArgb())
        val primaryContainer = hex(scheme.primaryContainer.toArgb())
        val onPrimaryContainer = hex(scheme.onPrimaryContainer.toArgb())
        val surface = hex(scheme.surface.toArgb())
        val onSurface = hex(scheme.onSurface.toArgb())
        val surfaceVariant = hex(scheme.surfaceVariant.toArgb())
        val onSurfaceVariant = hex(scheme.onSurfaceVariant.toArgb())
        val outlineVariant = hex(scheme.outlineVariant.toArgb())

        return """
        (function(){
          var old = document.getElementById('sdg-theme');
          if (old && old.parentNode) old.parentNode.removeChild(old);
          var s = document.createElement('style'); s.id = 'sdg-theme';
          s.textContent = `
            :root {
              --sdg-primary: $primary;
              --sdg-on-primary: $onPrimary;
              --sdg-primary-container: $primaryContainer;
              --sdg-on-primary-container: $onPrimaryContainer;
              --sdg-surface: $surface;
              --sdg-on-surface: $onSurface;
              --sdg-surface-variant: $surfaceVariant;
              --sdg-on-surface-variant: $onSurfaceVariant;
              --sdg-outline-variant: $outlineVariant;
              --sdg-radius: 16px;
              --sdg-radius-lg: 20px;
            }
            /* 隐藏冗余：页脚链接条 / 登录浮层提示 / 语言栏 */
            .footer, .post_footer, .landingPrompt, .byg_bl_lang1, .byg_bl_lang2,
            .div_foot, #mask { display: none !important; }
            /* 全局底色：html 一并压住外围留白；Discuz 白底容器（#wp/.bmw/.subforumshow）
               透明化以继承统一底色——暗色下消灭残留亮块，亮色下无感 */
            html, body, .bg_xin {
              background: var(--sdg-surface-variant) !important;
            }
            #wp, .bmw, .subforumshow, .header_nav_cover {
              background: transparent !important;
            }
            body { color: var(--sdg-on-surface) !important; }
            .xg1, .xg2, .grey { color: var(--sdg-on-surface-variant) !important; }
            /* 顶栏：保留站方原生红头栏（即品牌色本身，不覆盖） */
            /* 楼层/列表卡片化 */
            .plc, .plcl, .threadlist li, .news_list {
              background: var(--sdg-surface) !important;
              border-radius: var(--sdg-radius) !important;
              margin: 6px 8px !important;
              padding: 10px 12px !important;
              box-shadow: 0 1px 3px rgba(0,0,0,0.10) !important;
            }
            /* 头像圆形 + 图片圆角 */
            .avatar, .avatar img { border-radius: 50% !important; }
            .plc img, .pct img, .message img { border-radius: 12px !important; }
            /* 链接与强调色 */
            a { color: var(--sdg-primary) !important; }
            .blue, .xi2 { color: var(--sdg-primary) !important; }
            /* 按钮胶囊化 */
            button, .bn, .pn, input[type=submit] {
              border-radius: 999px !important;
              background: var(--sdg-primary) !important;
              color: var(--sdg-on-primary) !important;
              border: none !important;
              padding: 8px 20px !important;
            }
            /* 输入框圆角 */
            input[type=text], textarea, select {
              border-radius: 12px !important;
              border: 1px solid var(--sdg-outline-variant) !important;
            }
            /* 快速回复区 */
            .fastpost { border-radius: var(--sdg-radius-lg) !important; }
          `;
          document.head.appendChild(s);
        })();
        """.trimIndent()
    }
}
