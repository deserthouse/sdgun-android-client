package io.github.deserthouse.sdgun.feature.account

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
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

/** 个人中心（G3）：getUserInfo 明文 dict 渲染；工具栏齿轮=设置入口（A70-02：设置属于"我的"）。 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProfilePage(
    state: UiState<ProfileRepository.UserProfile>,
    onBack: () -> Unit,
    onOpenSettings: () -> Unit = {},
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
        when (state) {
            is UiState.InitialLoading -> Column(
                Modifier.fillMaxSize().padding(padding),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) { Text("加载中…") }

            is UiState.RetryableError -> Column(
                Modifier.fillMaxSize().padding(padding),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) { Text(state.error) }

            is UiState.Data<*> -> {
                val p = state.value as ProfileRepository.UserProfile
                Column(Modifier.fillMaxSize().padding(padding).padding(16.dp),
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
    }
}
