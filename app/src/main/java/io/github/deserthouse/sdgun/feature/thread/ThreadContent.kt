package io.github.deserthouse.sdgun.feature.thread

/**
 * 帖子内容块（A70-04/R-A5）：正文按文档序拆成文本/图交错块，
 * 稳定 key=`<pid>_t<n>/<i<n>>`（LazyColumn key 用，重复不崩）。
 */
sealed interface ContentBlock {
    val key: String

    data class Text(override val key: String, val text: String) : ContentBlock
    data class Image(override val key: String, val url: String) : ContentBlock
}

private val IMG_TAG = Regex("<img\\b[^>]*>", RegexOption.IGNORE_CASE)
private val ANY_TAG = Regex("<[^>]+>")

/** 图源属性优先序：懒加载真图 data-original > Discuz file > 普通 src（2026-10-22 实测定案） */
private val IMG_ATTRS = listOf("data-original", "file", "src")

/** 实体解码（&nbsp;&lt;&gt;&amp;&quot;&#39; + 数字实体） */
internal fun decodeEntities(s: String): String = s
    .replace(Regex("&#(\\d+);")) { m -> m.groupValues[1].toIntOrNull()?.toChar()?.toString() ?: m.value }
    .replace("&nbsp;", " ")
    .replace("&lt;", "<").replace("&gt;", ">")
    .replace("&quot;", "\"").replace("&#39;", "'")
    .replace("&amp;", "&")

/**
 * content HTML → 块流（A70-04）：
 * - `<img>` 按文档序成 Image 块（src 必须 http 开头；表情图跳过——
 *   本站两套路径 Discuz static/image/smiley 与 mag1 /public/emotion/（后者 CDN 404，2026-10-22 logcat 实证））
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
        if (body.isNotEmpty()) {
            out += ContentBlock.Text("${pid}_t${n++}", body)
        }
    }

    var last = 0
    for (m in IMG_TAG.findAll(html)) {
        pushText(html.substring(last, m.range.first))
        last = m.range.last + 1
        // 懒加载形态（2026-10-22 threadView 实测）：src="" + 真图在 data-original；
        // 按优先序取第一个非空属性值
        val src = IMG_ATTRS.firstNotNullOfOrNull { attr ->
            Regex("$attr\\s*=\\s*[\"']([^\"']+)[\"']", RegexOption.IGNORE_CASE)
                .find(m.value)?.groupValues?.get(1)
        }
        val isEmotion = src != null && (
            src.contains("/smiley", ignoreCase = true) ||
                src.contains("/emotion/", ignoreCase = true)
            )
        if (src != null && src.startsWith("http") && !isEmotion) {
            out += ContentBlock.Image("${pid}_i${n++}", src)
        }
    }
    pushText(html.substring(last))
    return out
}

/** 该内容块组里的全部图片 URL（查看器图组用，保持文档序） */
fun imageUrls(blocks: List<ContentBlock>): List<String> =
    blocks.filterIsInstance<ContentBlock.Image>().map { it.url }
