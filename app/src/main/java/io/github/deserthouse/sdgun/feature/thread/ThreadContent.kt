package io.github.deserthouse.sdgun.feature.thread

/**
 * 帖子内容块（A70-04/R-A5 + AF29-04 媒体模型）：正文按文档序拆成
 * 文本/图/视频/附件/链接交错块，稳定 key=`<pid>_t<n>/<i<n>>/<v/a/l<n>>`。
 */
sealed interface ContentBlock {
    val key: String

    data class Text(override val key: String, val text: String) : ContentBlock
    data class Image(override val key: String, val url: String) : ContentBlock

    /** AF29-04（A1-1）：视频块——真实可点击外开（原生播放器=另立任务） */
    data class Video(override val key: String, val url: String) : ContentBlock

    /** AF29-04：附件块（<a href> 媒体文件/非论坛内链）——下载/外开意图 */
    data class Attachment(override val key: String, val url: String, val name: String) : ContentBlock

    /** AF29-04：正文内链接——保留 URL 语义（净化不再丢任务） */
    data class Link(override val key: String, val url: String, val text: String) : ContentBlock
}

private val IMG_TAG = Regex("<img\\b[^>]*>", RegexOption.IGNORE_CASE)
private val ANY_TAG = Regex("<[^>]+>")
private val A_TAG = Regex(
    "<a\\b[^>]*href\\s*=\\s*[\"']([^\"']+)[\"'][^>]*>(.*?)</a>",
    setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL),
)
private val VIDEO_TAG = Regex("<video\\b[^>]*>(.*?)</video>|<video\\b[^>]*/>", setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL))
private val SRC_ATTR = Regex("(?:data-original|data-src|src)\\s*=\\s*[\"']([^\"']+)[\"']", RegexOption.IGNORE_CASE)
private val MEDIA_EXT = Regex("\\.(zip|rar|7z|pdf|mp3|mp4|apk|docx?|xlsx?)(([?#]).*)?$", RegexOption.IGNORE_CASE)

/** 图源属性优先序：懒加载真图 data-original > Discuz file > 普通 src（2026-10-22 实测定案） */
private val IMG_ATTRS = listOf("data-original", "file", "src")

/** 实体解码（&nbsp;&lt;&gt;&amp;&quot;&#39; + 数字实体） */
internal fun decodeEntities(s: String): String = s
    .replace(Regex("&#(\\d+);")) { m -> m.groupValues[1].toIntOrNull()?.toChar()?.toString() ?: m.value }
    .replace("&nbsp;", " ")
    .replace("&lt;", "<").replace("&gt;", ">")
    .replace("&quot;", "\"").replace("&#39;", "'")
    .replace("&amp;", "&")

/** 统一文档序标记：img/video/a 三类一并按 start 排序推进（AF29-04 混排语义） */
private data class Mark(val start: Int, val end: Int, val kind: String, val payload: String?, val label: String = "")

/**
 * content HTML → 块流（A70-04 + AF29-04）：
 * - `<img>` → Image（data-original > file > src；表情图跳过）
 * - `<video>`（src/data-src）→ Video（真实可点击外开，原生播放=另立任务）
 * - `<a href>`：媒体扩展名/非论坛内链 → Attachment（文件名取链接文本）；论坛内链 → Link（URL 语义保留）
 * - 其余文本按 <br>/<p> 断段、剥标签、实体解码，空白段丢弃
 */
fun parseContentBlocks(pid: Long, html: String): List<ContentBlock> {
    val out = mutableListOf<ContentBlock>()
    var n = 0

    fun pushText(raw: String) {
        val body = decodeEntities(raw.replace(Regex("<br\\s*/?>", RegexOption.IGNORE_CASE), "\n")
            .replace(Regex("</p\\s*>", RegexOption.IGNORE_CASE), "\n\n")
            .let { ANY_TAG.replace(it, "") })
            .replace(Regex("[ \\t\\x0C\\r]+\\n"), "\n")
            .replace(Regex("\\n{3,}"), "\n\n")
            .trim()
        // B0-2（V2）：站方按 UA 探测输出的视频回退文案是零信息噪声，滤除
        // （B0-2 余项 2026-11-02 兑现：游客真读实采 <video class=mag-video><source src>
        // 形态——SRC_ATTR 匹配区含 source 子元素，机制已覆盖，QSmallItemsTest.V1 锁定）
        if (body.contains("您的设备不支持视频标签")) {
            val cleaned = body.replace("您的设备不支持视频标签。", "")
                .replace("您的设备不支持视频标签", "")
                .trim()
            if (cleaned.isNotEmpty()) {
                out += ContentBlock.Text("${pid}_t${n++}", cleaned)
            }
            return
        }
        if (body.isNotEmpty()) {
            out += ContentBlock.Text("${pid}_t${n++}", body)
        }
    }

    val marks = mutableListOf<Mark>()
    for (m in IMG_TAG.findAll(html)) {
        marks += Mark(
            m.range.first, m.range.last + 1, "img",
            IMG_ATTRS.firstNotNullOfOrNull { attr ->
                Regex("$attr\\s*=\\s*[\"']([^\"']+)[\"']", RegexOption.IGNORE_CASE)
                    .find(m.value)?.groupValues?.get(1)
            },
        )
    }
    for (vm in VIDEO_TAG.findAll(html)) {
        val vsrc = SRC_ATTR.find(vm.value)?.groupValues?.get(1)
        if (vsrc != null && vsrc.startsWith("http")) {
            marks += Mark(vm.range.first, vm.range.last + 1, "video", vsrc)
        }
    }
    for (am in A_TAG.findAll(html)) {
        val href = am.groupValues[1]
        if (!href.startsWith("http")) continue
        val label = decodeEntities(ANY_TAG.replace(am.groupValues[2], "")).trim()
        val isMedia = MEDIA_EXT.containsMatchIn(href)
        // R1/S04（A31）：论坛内链不再剥成纯文本——保留 Link 块（openLink 按 UrlRules 分流：
        // 域内进壳内浏览/原生路由），点击不丢目标；相似域名误判防护=精确 host 后缀判断
        val isForumInternal = Regex("^(?:https://)?(?:[a-z0-9.-]*\\.)?sdgun\\.com\\.cn(?:/|$)",
            RegexOption.IGNORE_CASE).containsMatchIn(href)
        marks += when {
            isMedia -> Mark(am.range.first, am.range.last + 1, "attach", href,
                label.ifBlank { href.substringAfterLast('/') })
            else -> Mark(am.range.first, am.range.last + 1, "link", href, label.ifBlank { href })
        }
    }
    marks.sortBy { it.start }
    // 嵌套优先级：<a> 包 <img>/<video> 时弃 a 保媒体（图片本体是内容，链接语义交给查看器）
    val mediaMarks = marks.filter { it.kind == "img" || it.kind == "video" }
    if (mediaMarks.isNotEmpty()) {
        marks.removeAll { mk ->
            (mk.kind == "link" || mk.kind == "attach") &&
                mediaMarks.any { it.start >= mk.start && it.end <= mk.end }
        }
    }

    var last = 0
    for (mk in marks) {
        if (mk.start < last) continue // 嵌套（a 包 img 等）：外层已消费区间内跳过
        pushText(html.substring(last, mk.start))
        last = mk.end
        when (mk.kind) {
            "img" -> {
                val src = mk.payload
                val isEmotion = src != null && (
                    src.contains("/smiley", ignoreCase = true) ||
                        src.contains("/emotion/", ignoreCase = true)
                    )
                if (src != null && src.startsWith("http") && !isEmotion) {
                    out += ContentBlock.Image("${pid}_i${n++}", src)
                }
            }
            "video" -> out += ContentBlock.Video("${pid}_v${n++}", mk.payload ?: continue)
            "attach" -> out += ContentBlock.Attachment("${pid}_a${n++}", mk.payload ?: continue, mk.label)
            "link" -> out += ContentBlock.Link("${pid}_l${n++}", mk.payload ?: continue, mk.label)
        }
    }
    pushText(html.substring(last))
    return out
}

/** 该内容块组里的全部图片 URL（查看器图组用，保持文档序） */
fun imageUrls(blocks: List<ContentBlock>): List<String> =
    blocks.filterIsInstance<ContentBlock.Image>().map { it.url }
