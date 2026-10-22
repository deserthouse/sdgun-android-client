package io.github.deserthouse.sdgun.core.session

import io.github.deserthouse.sdgun.data.repository.AppPrefs

/**
 * 会话单入口（A70-03）：登录/退出/换号只经 [store] 推进 epoch 并写 prefs。
 * UI 层禁止直接调 AppPrefs.setApiSession/clearApiSession——唯一写路，
 * 杜绝"独立单测绿但真实路径绕过 epoch"的旧问题。
 */
object SessionManager {

    @Volatile
    private var storeRef: SessionStore? = null

    /** Activity onCreate 注入 AppPrefs；重复调用返回同一实例 */
    fun init(prefs: AppPrefs): SessionStore =
        storeRef ?: SessionStore(prefs).also { storeRef = it }

    val store: SessionStore
        get() = storeRef ?: error("SessionManager.init(prefs) 未在 onCreate 调用")
}
