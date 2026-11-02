package io.github.deserthouse.sdgun.core.designsystem

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp

/**
 * B1-3（V12）：列表首载骨架屏——alpha 脉动占位（无第三方 shimmer 依赖）。
 * 替换「spinner+加载中…」：聚焦/板块/人列表首载形似最终卡片，布局零跳变。
 */

@Composable
private fun Modifier.pulse(): Modifier {
    val t = rememberInfiniteTransition(label = "skeleton")
    val a by t.animateFloat(
        initialValue = 0.35f,
        targetValue = 0.75f,
        animationSpec = infiniteRepeatable(tween(700), RepeatMode.Reverse),
        label = "alpha",
    )
    return alpha(a)
}

@Composable
private fun Bone(modifier: Modifier) {
    Box(
        modifier
            .pulse()
            .clip(RoundedCornerShape(6.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant),
    )
}

/** 资讯/帖子行骨架：头像骨+两行文字骨+媒体骨（对齐 FeedCard/ThreadRow 形态） */
@Composable
fun SkeletonList(rows: Int, withMedia: Boolean = false, modifier: Modifier = Modifier) {
    Column(modifier = modifier.padding(vertical = 6.dp)) {
        repeat(rows.coerceIn(1, 12)) {
            Column(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 6.dp)
                    .clip(MaterialTheme.shapes.medium)
                    .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f))
                    .padding(12.dp),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Bone(Modifier.size(36.dp).clip(CircleShape))
                    Spacer(Modifier.width(8.dp))
                    Column(Modifier.weight(1f)) {
                        Bone(Modifier.fillMaxWidth(0.45f).height(12.dp))
                        Spacer(Modifier.height(4.dp))
                        Bone(Modifier.fillMaxWidth(0.3f).height(10.dp))
                    }
                }
                Spacer(Modifier.height(8.dp))
                Bone(Modifier.fillMaxWidth().height(12.dp))
                Spacer(Modifier.height(4.dp))
                Bone(Modifier.fillMaxWidth(0.8f).height(12.dp))
                if (withMedia) {
                    Spacer(Modifier.height(8.dp))
                    Bone(Modifier.fillMaxWidth().aspectRatio(16f / 9f))
                }
            }
        }
    }
}
