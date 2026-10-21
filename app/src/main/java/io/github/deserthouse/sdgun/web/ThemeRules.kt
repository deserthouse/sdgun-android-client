package io.github.deserthouse.sdgun.web

/**
 * S2-1 资产化后本文件不再持有注入 CSS——唯一事实源=`android/app/src/main/assets/sdg/`：
 *  - rules.css（原 RULES：楼层卡片化/表单/容器压暗等静态规则层）
 *  - base.css（原 BASE+CONTAINER：全局底色/文字基线/容器透明化）
 *  - hide.css（原 HIDE：冗余隐藏层）
 *  - inject.js（原 applyScript 的 JS 主体：监听器 IIFE + SDG_ENRICH_APPLY + SDG_APPLY）
 * 历史版本见 git（本文件在 2026-10-21 v1.5.0 前承载全部 CSS 字符串）。
 * 变量层（--sdg-* 实际值）仍由 ThemeInjector.variableCss 按主题生成。
 */
object ThemeRules
