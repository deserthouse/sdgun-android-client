package io.github.deserthouse.sdgun.feature.account

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import kotlinx.coroutines.launch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import io.github.deserthouse.sdgun.core.model.MutationResult
import io.github.deserthouse.sdgun.core.model.WriteUi
import io.github.deserthouse.sdgun.core.model.WriteUiPhase

/**
 * X1-3/A1104-F28：安全与隐私（jadx PrivacySettingActivity 同构）。
 * 合成关闭制：切换→MutationRunner 五态；Confirmed 才翻转开关（失败回弹+显错）。
 * 改密码（RSA+短信链）不在本页——端点在册但多步链未勘定，如实不含。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SecurityPage(
    onBack: () -> Unit,
    token: String,
    setPrivacy: suspend (Int, Int) -> MutationResult,
    setHomeVisible: suspend (Int) -> MutationResult,
) {
    // 本地开关态（Confirmed 才翻转；初始未知=false 不假称已开）
    var hideActive by remember { mutableStateOf(false) }
    var hideDistance by remember { mutableStateOf(false) }
    var homeVisible by remember { mutableStateOf(true) }
    var pendingType by remember { mutableStateOf<Int?>(null) }
    var error by remember { mutableStateOf<String?>(null) }

    suspend fun toggle(type: Int, turnOn: Boolean, apply: (Boolean) -> Unit) {
        if (pendingType != null) return
        pendingType = type
        error = null
        val result = when (type) {
            1 -> setPrivacy(1, if (turnOn) 1 else -1)
            2 -> setPrivacy(2, if (turnOn) 1 else -1)
            else -> setHomeVisible(if (turnOn) 1 else -1)
        }
        pendingType = null
        when (result) {
            is MutationResult.Confirmed -> apply(turnOn)
            is MutationResult.Rejected -> error = result.msg
            MutationResult.OutcomeUnknown -> error = "设置结果未知，请稍后重进页面核对"
            else -> {}
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("安全与隐私") },
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
            Modifier.fillMaxSize().padding(padding).padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text(
                "以下设置与官方 App 同步（同源端点）",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(vertical = 8.dp),
            )
            SecurityRow(
                title = "隐藏在线状态",
                subtitle = "开启后其他用户看不到你的在线时间",
                checked = hideActive,
                enabled = pendingType == null,
                onToggle = { on -> hideActive = on },
                onCommit = { on -> kotlinx.coroutines.runBlocking { toggle(1, on) { hideActive = it } } },
            )
            SecurityRow(
                title = "隐藏距离",
                subtitle = "开启后不向他人展示你的位置距离",
                checked = hideDistance,
                enabled = pendingType == null,
                onToggle = { on -> hideDistance = on },
                onCommit = { on -> kotlinx.coroutines.runBlocking { toggle(2, on) { hideDistance = it } } },
            )
            SecurityRow(
                title = "主页对外可见",
                subtitle = "关闭后其他用户无法访问你的主页",
                checked = homeVisible,
                enabled = pendingType == null,
                onToggle = { on -> homeVisible = on },
                onCommit = { on -> kotlinx.coroutines.runBlocking { toggle(3, on) { homeVisible = it } } },
            )
            error?.let {
                Text(
                    it,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                )
            }
            Text(
                "修改账号密码需通过官方渠道（本客户端暂不支持）",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 16.dp),
            )
        }
    }
}

@Composable
private fun SecurityRow(
    title: String,
    subtitle: String,
    checked: Boolean,
    enabled: Boolean,
    onToggle: (Boolean) -> Unit,
    onCommit: suspend (Boolean) -> Unit,
) {
    Row(
        Modifier.fillMaxWidth().padding(vertical = 8.dp),
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
        var optimistic by remember { mutableStateOf(checked) }
        val rowScope = androidx.compose.runtime.rememberCoroutineScope()
        Switch(
            checked = optimistic,
            enabled = enabled,
            onCheckedChange = { target ->
                optimistic = target  // 乐观翻转（Rejected 回弹由外层 apply 处理）
                onToggle(target)
                rowScope.launch { onCommit(target) }
            },
        )
    }
}
