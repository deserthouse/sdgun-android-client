package io.github.deserthouse.sdgun.feature.account

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.TextButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import io.github.deserthouse.sdgun.core.model.UiState
import io.github.deserthouse.sdgun.data.repository.ProfileRepository

/** 个人中心（G3 + U3-b 入口行）：getUserInfo 明文 dict 渲染；齿轮=设置、退出登录经 SessionManager（A70-03 闭环）。 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProfilePage(
    state: UiState<ProfileRepository.UserProfile>,
    onBack: () -> Unit,
    onOpenSettings: () -> Unit = {},
    onLogout: () -> Unit = {},
    onOpenPeople: ((io.github.deserthouse.sdgun.feature.profile.PeopleKind) -> Unit)? = null,
    onOpenCollect: () -> Unit = {},
    onOpenServices: () -> Unit = {},
) {
    Scaffold(
        topBar = {
            TopAppBar(title = { Text("个人中心") },
                actions = {
                    IconButton(onClick = onOpenSettings) {
                        Icon(Icons.Outlined.Settings, contentDescription = "设置")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface))
        },
        containerColor = MaterialTheme.colorScheme.surface,
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            when (state) {
                is UiState.InitialLoading -> Column(
                    Modifier.weight(1f).fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                ) { Text("加载中…") }

                is UiState.RetryableError -> Column(
                    Modifier.weight(1f).fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                ) { Text(state.error) }

                is UiState.Data<*> -> {
                    val p = state.value as ProfileRepository.UserProfile
                    Column(Modifier.fillMaxWidth().padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Text(p.name, style = MaterialTheme.typography.headlineSmall)
                        p.groupName?.let { Text("用户组：$it", style = MaterialTheme.typography.bodyMedium) }
                        p.score?.let { Text("积分：$it", style = MaterialTheme.typography.bodyMedium) }
                        p.sign?.let { Text("签名：$it", style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant) }
                    }
                }

                else -> {}
            }
            // U3-b 功能入口行（F18/19）：导航入口不依赖资料加载态（资料失败时仍可达）
            if (onOpenPeople != null) {
                androidx.compose.material3.HorizontalDivider(Modifier.padding(horizontal = 16.dp, vertical = 4.dp))
                io.github.deserthouse.sdgun.feature.profile.PeopleKind.entries.forEach { kind ->
                    ListItem(
                        headlineContent = { Text(kind.title) },
                        modifier = Modifier.fillMaxWidth()
                            .clickable { onOpenPeople(kind) },
                    )
                }
                ListItem(
                    headlineContent = { Text("收藏") },
                    modifier = Modifier.fillMaxWidth()
                        .clickable(onClick = onOpenCollect),
                )
                // U3-c（F23~27）：H5 服务入口（配置驱动；商城外开/发现附近占位）
                ListItem(
                    headlineContent = { Text("社区服务") },
                    modifier = Modifier.fillMaxWidth()
                        .clickable(onClick = onOpenServices),
                )
                androidx.compose.material3.HorizontalDivider(Modifier.padding(horizontal = 16.dp, vertical = 4.dp))
            }
            Spacer(Modifier.weight(1f))
            TextButton(
                onClick = onLogout,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
            ) {
                Text("退出登录（清除本机会话）")
            }
        }
    }
}
