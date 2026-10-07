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
        /* 登录/注册表单（L5 排版对齐官方：下划线输入 + 全宽胶囊提交；安全提问整行隐藏） */
        #loginform .px {
          border: none !important;
          border-bottom: 1px solid var(--sdg-outline-variant) !important;
          border-radius: 0 !important;
          background: transparent !important;
          padding: 12px 4px !important;
          font-size: 16px !important;
        }
        #loginform select.sel_list {
          border: none !important;
          border-bottom: 1px solid var(--sdg-outline-variant) !important;
          border-radius: 0 !important;
          background: transparent !important;
        }
        #loginform button.pnc { width: 100% !important; margin-top: 16px !important; }
        #loginform input[name=answer] { display: none !important; }
        #loginform li { background: transparent !important; }
        /* C-2 字号自适应：over_two/over_one 固定高度在 140% 字号档裁切，改为随内容生长 */
        .over_two, .over_one, .over_2 {
          height: auto !important;
          max-height: none !important;
          line-height: 1.45 !important;
        }
        /* C-3 原生 checkbox：默认样式在暗背景不可辨，补描边与选中态 */
        input[type=checkbox] {
          appearance: none !important;
          -webkit-appearance: none !important;
          width: 18px !important;
          height: 18px !important;
          border: 2px solid var(--sdg-outline-variant) !important;
          border-radius: 4px !important;
          background: var(--sdg-surface) !important;
          vertical-align: middle !important;
          margin: 0 6px 0 2px !important;
        }
        input[type=checkbox]:checked {
          background: var(--sdg-primary) !important;
          border-color: var(--sdg-primary) !important;
        }
        /* C-6 深色头部搜索框配色断裂修复（亮色模式无感） */
        .header_search_z, #scform_srchtxt {
          background: var(--sdg-surface) !important;
          color: var(--sdg-on-surface) !important;
        }
        /* 快速回复区（底色+圆角；站点以 background-image 铺浅色渐变，须清除） */
        .fastpost {
          background: var(--sdg-surface) !important;
          background-image: none !important;
          border-radius: var(--sdg-radius-lg) !important;
        }
        /* 门户统计条（今日/帖子/会员） */
        .byg_tongji {
          background: var(--sdg-surface) !important;
        }
        .byg_tongji, .byg_tongji * { color: var(--sdg-on-surface) !important; }
        /* 版块列表行（bygsjw 皮肤用 background-image 铺白底，backgroundColor 探不到） */
        .sub_forum, .byg_forum1, .byg_forum2, .subforumshow li, .subforumshow ul {
          background: var(--sdg-surface) !important;
          background-image: none !important;
        }
        /* 帖子页容器（2026-10-02 实测白底元素：postlist/标题条/侧栏）
           注：.post_fixed 必须排除——它是 .postlist 的直接子元素，会命中 >div:not(.plc) 而拿到 (0,2,1) 特异性，
           压掉下方按需给它的 !important 底色（防御性规则过期反转的教训）。 */
        .postlist, .postlist > div:not(.plc):not(.post_fixed), .postlist_title, .byg_sidenav {
          background: transparent !important;
        }
        /* 固定快复条容器（plc_xin，fixed）：不透明暗底 + 顶分隔线（透底会漏出楼层文字） */
        div.plc.plc_xin {
          background: var(--sdg-surface) !important;
          border-top: 1px solid var(--sdg-outline-variant) !important;
        }
        .postlist_title, .post_fixed { color: var(--sdg-on-surface) !important; }
        /* 作者行强化（L4 对比借鉴：头像/用户名视觉权重提升） */
        .plc .authi a { font-weight: 600 !important; font-size: 15px !important; }
        /* 引用块/代码块/表格：暗色适配（变量驱动，亮色模式无感） */
        blockquote, .quote, .blockcode {
          background: var(--sdg-surface) !important;
          color: var(--sdg-on-surface) !important;
          border: 1px solid var(--sdg-outline-variant) !important;
          border-radius: 12px !important;
        }
        table, th, td {
          background: transparent !important;
          color: var(--sdg-on-surface) !important;
          border-color: var(--sdg-outline-variant) !important;
        }
        /* 正文容器 */
        .t_f, .pct { color: var(--sdg-on-surface) !important; }
        /* 首楼（楼主）强化：左侧品牌色标记 + 头像/用户名放大 */
        .plc.sdg-op {
          border-left: 3px solid var(--sdg-primary) !important;
        }
        .plc.sdg-op .avatar, .plc.sdg-op img.avatar {
          width: 56px !important;
          height: 56px !important;
        }
        .plc.sdg-op .authi a {
          font-size: 16px !important;
          font-weight: 700 !important;
        }
        /* 版头横幅（sidenav-brand，站方 home_bg.jpg 亮色花图）：遮罩压图保留质感，文字浮上层（白字黑影站方自带） */
        .sidenav-brand { position: relative !important; }
        .sidenav-brand::before {
          content: '' !important;
          position: absolute !important;
          left: 0 !important; top: 0 !important; right: 0 !important; bottom: 0 !important;
          background: rgba(13, 15, 20, 0.74) !important;
          z-index: 0 !important;
          pointer-events: none !important;
        }
        .sidenav-brand > * { position: relative !important; z-index: 1 !important; }
        .sidenav-brand .forumdisplay_top_y a,
        .sidenav-brand a[class*='favorite'] {
          background: rgba(40, 44, 56, 0.85) !important;
          color: #d6ddf2 !important;
          border: 1px solid rgba(255, 255, 255, 0.14) !important;
          text-shadow: none !important;
        }
        /* 帖子页底部固定条：可见条是 .post_fixed（头像+胶囊入口+三图标），弹出表单层是 .plc_xin（默认隐藏）——两层都硬压不透明 */
        .post_fixed {
          background: var(--sdg-surface) !important;
          border-top-color: var(--sdg-outline-variant) !important;
        }
        .post_fixed .post_edit {
          background: var(--sdg-surface-variant) !important;
          color: var(--sdg-on-surface-variant) !important;
          border-color: var(--sdg-outline-variant) !important;
        }
        .plc_xin { background: var(--sdg-surface) !important; }
        .plc_xin textarea {
          background: var(--sdg-surface-variant) !important;
          color: var(--sdg-on-surface) !important;
          border: 1px solid var(--sdg-outline-variant) !important;
          border-radius: 22px !important;
        }
        .plc_xin textarea::placeholder { color: var(--sdg-on-surface-variant) !important; }
        /* 白底位图图标（☰★<）反色转亮；头像（post_avatar）必须排除 */
        .post_fixed .post_sidenav img,
        .post_fixed .post_favorite img,
        .post_fixed .post_return img {
          filter: invert(0.92) hue-rotate(180deg) !important;
        }
        /* 登录页：注册提示白底条压暗；安全提问行白块=icon-arrow 白底（非 select 本体），透明化 + select 原生控件兜底 */
        .reg_link a {
          background: var(--sdg-surface-variant) !important;
          color: var(--sdg-on-surface-variant) !important;
          border-color: var(--sdg-outline-variant) !important;
        }
        .login_select .icon-arrow { background: transparent !important; }
        li.questionli select.sel_list {
          opacity: 0 !important;
          background: transparent !important;
        }
        /* 板块页帖子分类筛选行（bygsjw byg_thread_types 白底）：整行压暗，选中态由站方 .a 类红框保留 */
        .byg_thread_types {
          background: var(--sdg-surface) !important;
          border-top-color: var(--sdg-outline-variant) !important;
        }
        .byg_thread_types li a {
          background: var(--sdg-surface-variant) !important;
          color: var(--sdg-on-surface-variant) !important;
          border-color: var(--sdg-outline-variant) !important;
        }
        /* 帖子页"赞"按钮（recommend_thread 白底块）压暗 */
        .recommend_thread a {
          background: var(--sdg-surface-variant) !important;
          color: var(--sdg-on-surface) !important;
          border: 1px solid var(--sdg-outline-variant) !important;
          border-radius: 10px !important;
        }
        /* 底部"加载更多/已经到底了"全宽链接（站方白底）压暗 */
        .load_more_button a {
          background: var(--sdg-surface-variant) !important;
          color: var(--sdg-on-surface-variant) !important;
        }
        /* 首页：原生顶栏 + 分组 chips 已完全覆盖站方固定头（#byg_header 内 .hdc_xin 53px 红条）——
           隐藏之并收掉 .header_xin 为其预留的 padding-bottom（.93rem），消除空灰带与红色残边 */
        body.sdg-home .header_xin { padding-bottom: 0 !important; }
        body.sdg-home #byg_header, body.sdg-home .hdc_xin { display: none !important; }
    """.trimIndent()

    /** 冗余隐藏层（页脚链接条/登录浮层提示/语言栏/遮罩） */
    val HIDE: String = """
        .footer, .post_footer, .landingPrompt, .byg_bl_lang1, .byg_bl_lang2,
        .div_foot, #mask { display: none !important; }
    """.trimIndent()

    /** 基础层：全局底色（body.bg_xin 特异性打平站方规则，注入节点在 head 尾部取胜）+ 容器透明化 + 文字基线 */
    val BASE: String = """
        html, body, .bg_xin, body.bg_xin { background: var(--sdg-surface-variant) !important; }
        #wp, .bmw, .subforumshow, .header_nav_cover { background: transparent !important; }
        body { color: var(--sdg-on-surface) !important; }
        .xg1, .xg2, .grey { color: var(--sdg-on-surface-variant) !important; }
    """.trimIndent()

    /** 白底容器透明化（并入规则层语义：继承统一底色，暗色消灭残留亮块） */
    val CONTAINER: String = """
        #wp, .bmw, .subforumshow, .header_nav_cover { background: transparent !important; }
        body { color: var(--sdg-on-surface) !important; }
        .xg1, .xg2, .grey { color: var(--sdg-on-surface-variant) !important; }
    """.trimIndent()
}
