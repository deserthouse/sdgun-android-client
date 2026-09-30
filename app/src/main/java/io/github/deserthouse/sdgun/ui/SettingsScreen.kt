package io.github.deserthouse.sdgun.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import io.github.deserthouse.sdgun.data.AppPrefs
import kotlinx.coroutines.flow.StateFlow

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    prefs: AppPrefs,
    onBack: () -> Unit,
) {
    val dynamicColor by prefs.dynamicColor.collectAsStateCompat()
    val webTheme by prefs.webTheme.collectAsStateCompat()
    val textZoom by prefs.textZoom.collectAsStateCompat()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("设置") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                    }
                },
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            SettingsCard(title = "外观") {
                SwitchRow(
                    title = "动态取色",
                    subtitle = "Android 12+ 跟随系统壁纸配色（Material You）",
                    checked = dynamicColor,
                    onCheckedChange = prefs::setDynamicColor,
                )
                SwitchRow(
                    title = "网页主题注入",
                    subtitle = "将论坛页面配色/圆角与客户端设计对齐",
                    checked = webTheme,
                    onCheckedChange = prefs::setWebTheme,
                )
            }

            SettingsCard(title = "阅读") {
                Column(modifier = Modifier.padding(vertical = 8.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text("网页文字大小", style = MaterialTheme.typography.bodyLarge)
                        Text(
                            "$textZoom%",
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.primary,
                        )
                    }
                    Slider(
                        value = textZoom.toFloat(),
                        onValueChange = { prefs.setTextZoom(it.toInt()) },
                        valueRange = 100f..140f,
                        steps = 3,  // 100/110/120/130/140
                    )
                    Text(
                        "仅影响论坛页面文字；100% 为网页默认",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            SettingsCard(title = "关于") {
                AboutRow("版本", "1.0.1（第三方客户端）")
                AboutRow("数据来源", "bbs.sdgun.com.cn 触屏版")
                AboutRow("说明",
                    "本应用是论坛网页的客户端外壳：所有内容与操作均来自官方页面，" +
                            "等同于在浏览器中直接访问；本应用不产生、不修改、不存储、不中转任何论坛内容。" +
                            "非官方，与 SDGun 及其运营方无关联；按现状提供（AS IS），开发者不承担任何责任。" +
                            "不收集任何数据。代码由 AI 辅助编写，经人类审阅与验收。")
            }
        }
    }
}

@Composable
private fun SettingsCard(title: String, content: @Composable () -> Unit) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                title,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.primary,
            )
            content()
        }
    }
}

@Composable
private fun SwitchRow(
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            Text(
                subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}

@Composable
private fun AboutRow(key: String, value: String) {
    Column(modifier = Modifier.padding(vertical = 6.dp)) {
        Text(key, style = MaterialTheme.typography.bodyLarge)
        Text(
            value,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/** StateFlow -> Compose 状态（避免为三个布尔再引 lifecycle-compose） */
@Composable
private fun <T> StateFlow<T>.collectAsStateCompat() = this.collectAsState()
