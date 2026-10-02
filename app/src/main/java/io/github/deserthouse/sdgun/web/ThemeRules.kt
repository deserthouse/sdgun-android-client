package io.github.deserthouse.sdgun.web

/**
 * 注入用 CSS 资产（Kotlin raw string——本 CSS 无 ${ 与反引号，raw string 转义安全，
 * 且可直接 JVM 单测，故不落 assets 文件）。
 *
 * 分层：
 *  - RULES：静态规则层，引用 var(--sdg-*)，页面生命周期内一次注入终身有效；
 *  - HIDE：冗余隐藏层，独立节点（语义上与规则层职责不同，便于契约自检独立开关）。
 * 变量层（--sdg-* 实际值）由 ThemeInjector.variableCss 按主题生成，主题切换只重发变量层。
 *
 * 选择器基于 bbs.sdgun.com.cn 触屏模板（bygsjw_3sj，2026-09 取样）。
 * 运行时契约：内容锚点（见 ThemeInjector.CONTENT_ANCHORS）全不命中的页面不注入——
 * 站方改版导致规则整体错位时，干净回退原版而非花脸。
 */
object ThemeRules {

    /** 静态规则层 */
    val RULES: String = """
        /* 楼层/列表卡片化 */
        .plc, .plcl, .threadlist li, .news_list {
          background: var(--sdg-surface) !important;
          border-radius: var(--sdg-radius) !important;
          margin: 6px 8px !important;
          padding: 10px 12px !important;
          box-shadow: 0 1px 3px rgba(0,0,0,0.10) !important;
        }
        /* 头像圆形 + 正文图片圆角（收窄到正文区，避免误伤版头横幅等装饰图） */
        .avatar, .avatar img { border-radius: 50% !important; }
        .pct img, .message img { border-radius: 12px !important; }
        /* 链接与强调色 */
        a { color: var(--sdg-primary) !important; }
        .blue, .xi2 { color: var(--sdg-primary) !important; }
        /* 按钮：提交类（含 .pn 发帖/回复）主色实心胶囊；.bn 仅形状不着色（保留站方语义） */
        input[type=submit], button, .pn {
          border-radius: 999px !important;
          background: var(--sdg-primary) !important;
          color: var(--sdg-on-primary) !important;
          border: none !important;
          padding: 8px 20px !important;
        }
        .bn { border-radius: 999px !important; }
        /* 输入框圆角 */
        input[type=text], textarea, select {
          border-radius: 12px !important;
          border: 1px solid var(--sdg-outline-variant) !important;
        }
        /* 快速回复区 */
        .fastpost { border-radius: var(--sdg-radius-lg) !important; }
    """.trimIndent()

    /** 冗余隐藏层（页脚链接条/登录浮层提示/语言栏/遮罩） */
    val HIDE: String = """
        .footer, .post_footer, .landingPrompt, .byg_bl_lang1, .byg_bl_lang2,
        .div_foot, #mask { display: none !important; }
    """.trimIndent()

    /** 白底容器透明化（并入规则层语义：继承统一底色，暗色消灭残留亮块） */
    val CONTAINER: String = """
        #wp, .bmw, .subforumshow, .header_nav_cover { background: transparent !important; }
        body { color: var(--sdg-on-surface) !important; }
        .xg1, .xg2, .grey { color: var(--sdg-on-surface-variant) !important; }
    """.trimIndent()
}
