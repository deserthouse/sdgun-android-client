package io.github.deserthouse.sdgun.feature.account
import androidx.compose.foundation.layout.Arrangement
import io.github.deserthouse.sdgun.core.network.SdApi
import io.github.deserthouse.sdgun.data.repository.AppPrefs
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch

/**
 * 原生登录页（R-A1）：magapp 协议登录（RSA 密码加密+token 会话），独立于 WebView 的
 * Cookie 登录。1009=图形验证码时追加 security_code 输入；限速提示原文透出
 * （服务端 3 次失败暂时受限，客户端绝不重试凭据）。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LoginScreen(
    prefs: AppPrefs,
    onBack: () -> Unit,
    onLoggedIn: () -> Unit,
    /** tab 内嵌（消息/我的游客面）时给设置入口——A70 接线后设置不再在底栏，
     *  游客的唯一设置路径经此齿轮；独立登录路由不传=不显示 */
    onOpenSettings: (() -> Unit)? = null,
) {
    var account by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var securityCode by remember { mutableStateOf("") }
    var codeNeeded by remember { mutableStateOf(false) }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var showPwd by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("登录") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                    }
                },
                actions = {
                    if (onOpenSettings != null) {
                        IconButton(onClick = onOpenSettings) {
                            Icon(Icons.Outlined.Settings, contentDescription = "设置")
                        }
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
                .padding(horizontal = 20.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Spacer(Modifier.height(12.dp))
            Text(
                "登录 SDGun 社区账号",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                "通过官方 App 同源接口登录；密码在本机加密后提交，不存储明文。",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            OutlinedTextField(
                value = account,
                onValueChange = { account = it.trim() },
                label = { Text("用户名") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(
                value = password,
                onValueChange = { password = it },
                label = { Text("密码") },
                singleLine = true,
                visualTransformation = if (showPwd) VisualTransformation.None else PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                trailingIcon = {
                    TextButton(onClick = { showPwd = !showPwd }) {
                        Text(if (showPwd) "隐藏" else "显示")
                    }
                },
                modifier = Modifier.fillMaxWidth(),
            )
            if (codeNeeded) {
                OutlinedTextField(
                    value = securityCode,
                    onValueChange = { securityCode = it.trim() },
                    label = { Text("图形验证码") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            error?.let {
                Text(
                    it,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                )
            }
            Button(
                onClick = {
                    if (account.isBlank() || password.isBlank()) {
                        error = "请输入用户名和密码"
                        return@Button
                    }
                    busy = true
                    error = null
                    scope.launch {
                        when (val r = SdApi.login(account, password, prefs.utdid, securityCode)) {
                            is SdApi.LoginResult.Ok -> {
                                // A70-03：登录唯一写路=SessionManager（epoch 推进+prefs 持久化一体）
                                io.github.deserthouse.sdgun.core.session.SessionManager.store
                                    .onLogin(r.token, account, r.userId)
                                onLoggedIn()
                            }
                            is SdApi.LoginResult.CaptchaRequired -> {
                                codeNeeded = true
                                error = r.msg ?: "需要图形验证码"
                            }
                            is SdApi.LoginResult.Failed -> error = r.msg
                            is SdApi.LoginResult.NetworkError ->
                                error = "网络异常，请稍后重试（${r.throwable.javaClass.simpleName}）"
                        }
                        busy = false
                    }
                },
                enabled = !busy,
                modifier = Modifier.fillMaxWidth().height(48.dp),
            ) {
                if (busy) {
                    CircularProgressIndicator(modifier = Modifier.height(22.dp), strokeWidth = 2.dp)
                } else {
                    Text("登录")
                }
            }
            Spacer(Modifier.height(20.dp))
        }
    }
}

/**
 * 账号页（R-A1 简版）：已登录态展示+退出。完整原生个人中心（myInfo）属 R-A2。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AccountScreen(
    prefs: AppPrefs,
    onBack: () -> Unit,
) {
    val account by prefs.apiAccount.collectAsState()
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("我的账号") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                    }
                },
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier.fillMaxSize().padding(padding).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("已登录", style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary)
                    Text(account, style = MaterialTheme.typography.titleMedium)
                    Text(
                        "API 会话（独立于网页登录）。原生个人中心、消息与收藏将在后续版本接入。",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            TextButton(
                onClick = { io.github.deserthouse.sdgun.core.session.SessionManager.store.onLogout() },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text("退出登录（清除本机会话）")
            }
        }
    }
}