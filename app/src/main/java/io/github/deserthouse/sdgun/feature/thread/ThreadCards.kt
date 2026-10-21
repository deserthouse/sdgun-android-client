package io.github.deserthouse.sdgun.feature.thread

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import io.github.deserthouse.sdgun.data.repository.ThreadRepository

/** 头像占位：暂用首字母圆标（Coil 引入后替换为 AsyncImage）。 */
@Composable
fun AvatarPlaceholder(name: String?, sizeDp: androidx.compose.ui.unit.Dp) {
    Box(
        Modifier
            .size(sizeDp)
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.surfaceVariant),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            name?.firstOrNull()?.toString() ?: "?",
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/** 主楼卡（G2-A05）：标题+作者+正文（threadView 40 字段契约）。 */
@Composable
fun ThreadOPCard(op: ThreadRepository.ThreadOP) {
    Column(Modifier.padding(16.dp)) {
        Text(op.title, style = MaterialTheme.typography.titleLarge)
        Spacer(Modifier.height(8.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            AvatarPlaceholder(op.authorName, 36.dp)
            Spacer(Modifier.width(8.dp))
            Column {
                Text(
                    op.authorName ?: "匿名",
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    listOfNotNull(
                        op.createTime,
                        op.viewCount?.let { "查看 $it" },
                        op.replyCount?.let { "回复 $it" },
                    ).joinToString(" · "),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        Spacer(Modifier.height(12.dp))
        Text(
            htmlToText(op.contentHtml),
            style = MaterialTheme.typography.bodyMedium,
        )
    }
}

/** 楼层卡：楼层号+作者+正文+时间/赞。 */
@Composable
fun FloorCard(floor: ThreadRepository.Floor) {
    Column(Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            AvatarPlaceholder(floor.authorName, 28.dp)
            Spacer(Modifier.width(6.dp))
            Text(
                floor.authorName ?: "匿名",
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Medium,
            )
            Spacer(Modifier.weight(1f))
            Text(
                "${floor.floor} 楼",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Spacer(Modifier.height(4.dp))
        Text(
            htmlToText(floor.contentHtml),
            style = MaterialTheme.typography.bodyMedium,
        )
        floor.createTime?.let {
            Text(it, style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        floor.applaudCount?.let {
            Text("赞 $it", style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        HorizontalDivider(
            Modifier.padding(top = 8.dp),
            color = MaterialTheme.colorScheme.outlineVariant,
        )
    }
}

/** 简易 HTML→纯文本（G2-A05 降级渲染；R-A5 块化解析替代）。 */
fun htmlToText(html: String): String = html
    .replace(Regex("<br\\s*/?>"), "\n")
    .replace("</p>", "\n")
    .replace(Regex("<img[^>]*>"), "[图片]")
    .replace(Regex("<[^>]+>"), "")
    .replace("&nbsp;", " ")
    .replace("&lt;", "<").replace("&gt;", ">").replace("&amp;", "&")
    .replace("&quot;", "\"").replace("&#39;", "'")
    .trim()
