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
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import io.github.deserthouse.sdgun.BuildConfig
import io.github.deserthouse.sdgun.data.AppPrefs
import kotlinx.coroutines.flow.StateFlow

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    prefs: AppPrefs,
    onBack: () -> Unit,
    onClearLoginState: () -> Unit,
    onTab: (Int) -> Unit,
) {
    val dynamicColor by prefs.dynamicColor.collectAsStateCompat()
    val webTheme by prefs.webTheme.collectAsStateCompat()
    val textZoom by prefs.textZoom.collectAsStateCompat()

    Scaffold(
        bottomBar = {
            // C-5：设置页底栏常驻，与其他 tab 行为一致
            AppBottomBar(
                activeIdx = 3,
                onTab = onTab,
                onOpenSettings = { /* 已在设置页 */ },
            )
        },
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
                    // 拖动只更新本地态，松手才落盘（避免一次拖动写几十次 SP + WebView 设置）
                    var zoomLocal by remember(textZoom) { mutableFloatStateOf(textZoom.toFloat()) }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text("网页文字大小", style = MaterialTheme.typography.bodyLarge)
                        Text(
                            "${zoomLocal.toInt()}%",
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.primary,
                        )
                    }
                    Slider(
                        value = zoomLocal,
                        onValueChange = { zoomLocal = it },
                        onValueChangeFinished = { prefs.setTextZoom(zoomLocal.toInt()) },
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

            SettingsCard(title = "账户") {
                var cleared by remember { mutableStateOf(false) }
                Row(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(Modifier.weight(1f)) {
                        Text("清除论坛登录态", style = MaterialTheme.typography.bodyLarge)
                        Text(
                            if (cleared) "已清除，下次打开即为游客状态"
                            else "等同网页退出登录；只影响本应用内的 Cookie",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    TextButton(onClick = { onClearLoginState(); cleared = true }) { Text("清除") }
                }
            }

            SettingsCard(title = "关于") {
                AboutRow("版本", BuildConfig.VERSION_NAME + "（第三方客户端）")
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
