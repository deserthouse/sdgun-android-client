package io.github.deserthouse.sdgun.feature.settings
import androidx.compose.foundation.layout.Arrangement
import io.github.deserthouse.sdgun.core.designsystem.AppBottomBar
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
import io.github.deserthouse.sdgun.data.repository.AppPrefs
import kotlinx.coroutines.flow.StateFlow

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    prefs: AppPrefs,
    onBack: () -> Unit,
    onClearLoginState: (onDone: () -> Unit) -> Unit,
    onTab: (Int) -> Unit,
) {
    val dynamicColor by prefs.dynamicColor.collectAsStateCompat()
    val webTheme by prefs.webTheme.collectAsStateCompat()
    val textZoom by prefs.textZoom.collectAsStateCompat()

    Scaffold(
        bottomBar = {
            // C-5：设置页底栏常驻；A70-02：设置属于"我的"，高亮 3（恰好一个选中项）
            AppBottomBar(
                activeIdx = 3,
                onTab = onTab,
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
                    subtitle = "Android 12+ 跟随系统壁纸配色（Material You）；开启后品牌红让位系统色板",
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
                    // C10：连续滑条（steps 的末端 tick 在满值时与拇指并成"杂线"），松手取整 10
                    Slider(
                        value = zoomLocal,
                        onValueChange = { zoomLocal = it },
                        onValueChangeFinished = {
                            val snapped = ((zoomLocal / 10f).let { it.toInt() + if (it % 1 >= 0.5f) 1 else 0 }) * 10
                            zoomLocal = snapped.toFloat()
                            prefs.setTextZoom(snapped)
                        },
                        valueRange = 100f..140f,
                    )
                    Text(
                        "仅影响论坛页面文字；100% 为网页默认",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            // C6：调试桥只在 debug 构建存在（release 永不注册接收器，开关是无效摆设）
            if (BuildConfig.DEBUG) {
                SettingsCard(title = "开发者") {
                    val debugBridge by prefs.debugBridge.collectAsStateCompat()
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text("调试桥", style = MaterialTheme.typography.bodyLarge)
                            Text(
                                "允许本机自动化工具注入 JS 到页面（仅调试构建有效）",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        androidx.compose.material3.Switch(
                            checked = debugBridge,
                            onCheckedChange = { prefs.setDebugBridge(it) },
                        )
                    }

                    // G4 假服务分支（零触网）：装机验证写操作 UI 状态机用；
                    // 真实写入验收=用户本人 MANUAL，红线不变
                    val fakeModes = listOf("off", "confirm", "reject", "timeout")
                    val mutationFake by prefs.mutationFake.collectAsStateCompat()
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text("写操作假服务", style = MaterialTheme.typography.bodyLarge)
                            Text(
                                "回复/点赞/收藏走离线假端点（零触网），验证提交状态机；off=真端点",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        TextButton(onClick = {
                            val next = fakeModes[((fakeModes.indexOf(mutationFake) + 1) % fakeModes.size)]
                            prefs.setMutationFake(next)
                        }) {
                            Text(mutationFake)
                        }
                    }

                    // U3-b 合成个人数据（零触网）：访客/历史/关注/收藏/消息/通知页
                    // 无凭据装机的三态走查用；off=真端点（真实读链需登录）
                    val profileFakeModes = listOf("off", "data", "empty", "error")
                    val profileFake by prefs.profileFake.collectAsStateCompat()
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text("合成个人数据", style = MaterialTheme.typography.bodyLarge)
                            Text(
                                "访客/历史/关注/收藏/消息/通知走合成夹具（零触网）；off=真端点",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        TextButton(onClick = {
                            val next = profileFakeModes[
                                ((profileFakeModes.indexOf(profileFake) + 1) % profileFakeModes.size)]
                            prefs.setProfileFake(next)
                        }) {
                            Text(profileFake)
                        }
                    }
                }
            }

            SettingsCard(title = "账户") {
                // A-04：清除中禁重复点击；完成回调（Cookie 实际删完）才显示成功
                var clearing by remember { mutableStateOf(false) }
                var done by remember { mutableStateOf(false) }
                Row(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(Modifier.weight(1f)) {
                        Text("清除论坛登录态", style = MaterialTheme.typography.bodyLarge)
                        Text(
                            when {
                                clearing -> "清除中…"
                                done -> "已清除，本应用已退出登录状态"
                                else -> "仅清理本应用内的 Cookie；不影响其他应用与服务器端账号"
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    TextButton(
                        enabled = !clearing,
                        onClick = { clearing = true; onClearLoginState { done = true; clearing = false } },
                    ) { Text(if (clearing) "清除中" else "清除") }
                }
            }

            SettingsCard(title = "关于") {
                AboutRow("版本", BuildConfig.VERSION_NAME + "（第三方客户端）")
                AboutRow("数据来源", "bbs.sdgun.com.cn 触屏版")
                AboutRow("说明",
                    "本应用是论坛网页的客户端外壳：阅读与互动均在官方页面完成，" +
                            "等同于在浏览器中直接访问。非官方，与 SDGun 及其运营方无关联；" +
                            "按现状提供（AS IS），开发者不承担任何责任。代码由 AI 辅助编写，经人类审阅与验收。" +
                            "数据说明：不收集、不上传任何用户数据；浏览论坛时仅向 bbs.sdgun.com.cn 发起只读请求；" +
                            "资讯页会向 mag1.sdgun.net 发起无凭据的只读请求；" +
                            "最近访问的板块名仅保存在本机（清除应用数据即可移除）；" +
                            "页面内容仅存于内存，退出即释放。")
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
