package io.github.deserthouse.sdgun.ui

/** 剥掉 Discuz 标题的模板长尾，只留页面主体（含 "-  手机版" 双空格变体） */
internal fun prettyTitle(raw: String): String =
    raw.replace(Regex("""\s*-\s*手机版\s*-\s*Powered by Discuz!\s*$"""), "")
        .replace(Regex("""\s*-\s*Powered by Discuz!\s*$"""), "")
        .trim()
        .replace("SDGUN,wargame", "SDGun 社区")  // C-4：站点原生标题统一品牌名
        .ifBlank { "SDGun 社区" }
