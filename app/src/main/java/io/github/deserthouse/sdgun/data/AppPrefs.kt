package io.github.deserthouse.sdgun.data

import android.content.Context
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

    fun setTextZoom(v: Int) {
        sp.edit().putInt(KEY_TEXT_ZOOM, v).apply()
        _textZoom.value = v
    }

    private companion object {
        const val KEY_DYNAMIC_COLOR = "dynamic_color"
        const val KEY_WEB_THEME = "web_theme"
        const val KEY_TEXT_ZOOM = "text_zoom"
    }
}
