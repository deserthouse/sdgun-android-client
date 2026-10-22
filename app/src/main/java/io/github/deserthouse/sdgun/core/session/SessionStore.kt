package io.github.deserthouse.sdgun.core.session

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * 会话 epoch（G1/A01-3）：每次登录/登出/换号递增。
 *
 * 用途：所有异步回调（网络响应、图片加载、Frida 桥回写等）在发起时捕获 epoch，
 * 交付时比对——不一致=旧会话的迟到结果，一律丢弃。这是"退出后零迟到写回"硬门的
 * 结构化实现（此前各处散落的 token 比对推广为单一事实源）。
 *
 * 线程安全：volatile int；写只发生在登录/登出的主线程路径。
 */
class SessionEpoch {

    @Volatile
    private var current: Int = 0

    /** 当前纪元值；异步任务发起时捕获 */
    val value: Int get() = current

    /** 登录/登出/换号时推进（旧 epoch 的所有在途任务随即失效） */
    fun advance(): Int = ++current

    /** 快捷判断：捕获的 epoch 是否仍是当前 */
    fun isCurrent(captured: Int): Boolean = captured == current
}

/**
 * API 会话状态（R-A1 SessionStore 升级版）：token+账号+userId+epoch 单一持有。
 * AppPrefs 保留持久化；本类持运行时单例流。
 * 写路唯一性（A70-03）：UI 只能经 onLogin/onLogout 触达，不得直写 AppPrefs。
 */
class SessionStore(private val prefs: io.github.deserthouse.sdgun.data.repository.AppPrefs) {

    data class Session(val token: String, val account: String, val userId: Long)

    private val _session = MutableStateFlow(
        prefs.apiToken.value.takeIf { it.isNotBlank() }?.let {
            Session(it, prefs.apiAccount.value, prefs.apiUserId.value)
        }
    )
    val session: StateFlow<Session?> = _session

    val epoch = SessionEpoch()

    /** 登录成功：写入会话并推进 epoch（旧会话在途任务全部作废） */
    fun onLogin(token: String, account: String, userId: Long) {
        prefs.setApiSession(token, account, userId)
        epoch.advance()
        _session.value = Session(token, account, userId)
    }

    /** 退出：先撤销 token 再清状态（总设计 §4：退出先撤权再换页面）；幂等 */
    fun onLogout() {
        if (_session.value == null && prefs.apiToken.value.isBlank()) return
        prefs.clearApiSession()
        epoch.advance()
        _session.value = null
    }

    /** 当前 token（无会话=null）；请求层按此决定是否带第 8 段 */
    fun currentToken(): String? = _session.value?.token?.takeIf { it.isNotBlank() }
}
