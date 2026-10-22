package io.github.deserthouse.sdgun.data.repository
import android.content.Context
import io.github.deserthouse.sdgun.core.network.SdApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * 应用设置：SharedPreferences 持久化 + StateFlow 供 Compose 收集。
 */
class AppPrefs(context: Context) {

    private val sp = context.getSharedPreferences("sdgun_prefs", Context.MODE_PRIVATE)

    private val _dynamicColor = MutableStateFlow(sp.getBoolean(KEY_DYNAMIC_COLOR, true))
    val dynamicColor: StateFlow<Boolean> = _dynamicColor

    private val _webTheme = MutableStateFlow(sp.getBoolean(KEY_WEB_THEME, true))
    val webTheme: StateFlow<Boolean> = _webTheme

    private val _debugBridge = MutableStateFlow(sp.getBoolean(KEY_DEBUG_BRIDGE, true))
    val debugBridge: StateFlow<Boolean> = _debugBridge

    /** G4 假服务分支（debug 装机验证用）：off/confirm/reject/timeout——off=真端点 */
    private val _mutationFake = MutableStateFlow(sp.getString(KEY_MUTATION_FAKE, "off") ?: "off")
    val mutationFake: StateFlow<String> = _mutationFake

    private val _textZoom = MutableStateFlow(sp.getInt(KEY_TEXT_ZOOM, 100))
    val textZoom: StateFlow<Int> = _textZoom

    fun setDynamicColor(v: Boolean) {
        sp.edit().putBoolean(KEY_DYNAMIC_COLOR, v).apply()
        _dynamicColor.value = v
    }

    fun setWebTheme(v: Boolean) {
        sp.edit().putBoolean(KEY_WEB_THEME, v).apply()
        _webTheme.value = v
    }

    fun setDebugBridge(v: Boolean) {
        sp.edit().putBoolean(KEY_DEBUG_BRIDGE, v).apply()
        _debugBridge.value = v
    }

    fun setMutationFake(v: String) {
        sp.edit().putString(KEY_MUTATION_FAKE, v).apply()
        _mutationFake.value = v
    }

    fun setTextZoom(v: Int) {
        sp.edit().putInt(KEY_TEXT_ZOOM, v).apply()
        _textZoom.value = v
    }

    // ---- API 会话（R-A1）：token/账号/utdid，独立于 WebView Cookie 会话 ----

    private val _apiToken = MutableStateFlow(sp.getString(KEY_API_TOKEN, "") ?: "")
    val apiToken: StateFlow<String> = _apiToken

    private val _apiAccount = MutableStateFlow(sp.getString(KEY_API_ACCOUNT, "") ?: "")
    val apiAccount: StateFlow<String> = _apiAccount

    /** 登录用户 id（getUserInfo POST {id} 契约必需，A70-05） */
    private val _apiUserId = MutableStateFlow(sp.getLong(KEY_API_USER_ID, 0L))
    val apiUserId: StateFlow<Long> = _apiUserId

    /** 客户端本地设备身份（utdid），首取现生成，终身不变 */
    val utdid: String = sp.getString(KEY_UTDID, "") ?: SdApi.newUtdid(System.nanoTime()).also {
        sp.edit().putString(KEY_UTDID, it).apply()
    }

    fun setApiSession(token: String, account: String, userId: Long = 0L) {
        sp.edit().putString(KEY_API_TOKEN, token).putString(KEY_API_ACCOUNT, account)
            .putLong(KEY_API_USER_ID, userId).apply()
        _apiToken.value = token
        _apiAccount.value = account
        _apiUserId.value = userId
    }

    fun clearApiSession() {
        sp.edit().remove(KEY_API_TOKEN).remove(KEY_API_ACCOUNT).remove(KEY_API_USER_ID).apply()
        _apiToken.value = ""
        _apiAccount.value = ""
        _apiUserId.value = 0L
    }

    private companion object {
        const val KEY_DYNAMIC_COLOR = "dynamic_color"
        const val KEY_WEB_THEME = "web_theme"
        const val KEY_TEXT_ZOOM = "text_zoom"

        const val KEY_DEBUG_BRIDGE = "debug_bridge"
        const val KEY_MUTATION_FAKE = "mutation_fake"

        const val KEY_API_TOKEN = "api_token"
        const val KEY_API_ACCOUNT = "api_account"
        const val KEY_API_USER_ID = "api_user_id"
        const val KEY_UTDID = "api_utdid"
    }
}
