package io.github.deserthouse.sdgun.feature.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

/**
 * A3-5/X5-2：关于页（版本+数据源+AI 披露+许可）。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AboutPage(
    onBack: () -> Unit,
    versionName: String,
    versionCode: Int,
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("关于") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface),
            )
        },
        containerColor = MaterialTheme.colorScheme.surface,
    ) { padding ->
        Column(
            Modifier.fillMaxSize().padding(padding).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text("SDGun 客户端", style = MaterialTheme.typography.headlineSmall)
            Text(
                "版本 $versionName (code $versionCode)",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            HorizontalDivider(Modifier.padding(vertical = 8.dp))

            Card {
                Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("数据源", style = MaterialTheme.typography.titleSmall)
                    Text(
                        "本客户端通过 SDGun 社区官方 API 获取内容，不修改服务器数据。\n" +
                            "登录、阅读、发帖等操作与官方 App 使用相同端点。",
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            }

            Card {
                Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("AI 使用声明", style = MaterialTheme.typography.titleSmall)
                    Text(
                        "本客户端的代码部分全部由 AI 编写，人类负责需求提出、方案审批与验收。\n" +
                            "本项目不含任何人类编写的代码成分。",
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            }

            Card {
                Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("开源许可", style = MaterialTheme.typography.titleSmall)
                    Text(
                        "• Kotlin / Jetpack Compose / Material 3 (Apache 2.0)\n" +
                            "• Media3 ExoPlayer (Apache 2.0)",
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            }

            Text(
                "本项目仅为个人学习用途，与 SDGun 社区官方无关。\n" +
                    "所有内容版权归原网站及作者所有。",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
