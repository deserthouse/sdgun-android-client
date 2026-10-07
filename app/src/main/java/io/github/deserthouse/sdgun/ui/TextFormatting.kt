package io.github.deserthouse.sdgun.ui

/** 剥掉 Discuz 标题的模板长尾与站方 SEO 关键词串，只留页面主体（含 "-  手机版" 双空格变体） */
internal fun prettyTitle(raw: String): String {
    // 前缀品牌名只在"站方关键词串"确实存在时才剥——避免误伤真正的帖子标题（如 "SDGun水弹枪测评"）
    val seoConcatenated = raw.contains("SDGUN,水弹,水弹枪,水弹论坛")
    val stripped = raw
        .replace(Regex("""\s*-\s*手机版\s*-\s*Powered by Discuz!\s*$"""), "")
        .replace(Regex("""\s*-\s*Powered by Discuz!\s*$"""), "")
        .replace(Regex("""SDGUN,水弹,水弹枪,水弹论坛\s*$"""), "")
        .replace(Regex("""SDGUN,wargame\s*$"""), "")
        // 导读等同构页把 " - SDGun" 夹在中段
        .replace(Regex("""\s+-\s+SDGun\s*$"""), "")
        .trim()
    val deBranded = if (seoConcatenated) {
        // 无分隔符的前缀品牌名（板块/帖子页形如 "SDGun弓射原创便携弓"）
        stripped.replace(Regex("""^SDGun(?=\S)"""), "")
    } else stripped
    return deBranded
        .replace("SDGUN,wargame", "SDGun 社区")  // C-4：站点原生标题统一品牌名
        .trim()
        .ifBlank { "SDGun 社区" }
}
