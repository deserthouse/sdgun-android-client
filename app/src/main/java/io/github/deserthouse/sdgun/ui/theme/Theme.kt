package io.github.deserthouse.sdgun.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp

/**
 * SDGun 社区客户端 — Material 3 Expressive 设计系统
 *
 * 色彩：Android 12+ 默认跟随 Material You 动态取色；
 *      关闭时回退品牌色板——从官方资产实证取样（2026-09-30）：触屏版头栏红 #E24444 系、
 *      官方 App 启动页深红（遮罩校正后 ≈#B00000 系）、深色基因 #111111。
 * 形状：M3E 风格的大圆角体系（卡片 20dp / 中组件 16dp / 控件全胶囊）。
 * 网页层：同一组色值经 [io.github.deserthouse.sdgun.web.buildWebThemeCss] 注入触屏版，
 *        原生与内容共用一套设计令牌。
 */

private val SdRed = Color(0xFFB01F28)
private val SdRedDim = Color(0xFF93000A)
private val SdRedBright = Color(0xFFFFB4AB)
private val SdRedContainer = Color(0xFFFFDAD6)

private val LightColors = lightColorScheme(
    primary = SdRed,
    onPrimary = Color.White,
    primaryContainer = SdRedContainer,
    onPrimaryContainer = Color(0xFF410002),
    secondary = Color(0xFF775652),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFFFDAD5),
    onSecondaryContainer = Color(0xFF2C1512),
    tertiary = Color(0xFF705C2E),
    onTertiary = Color.White,
    tertiaryContainer = Color(0xFFFFDFA9),
    onTertiaryContainer = Color(0xFF271A00),
    background = Color(0xFFFFF8F7),
    onBackground = Color(0xFF201A19),
    surface = Color(0xFFFFF8F7),
    onSurface = Color(0xFF201A19),
    surfaceVariant = Color(0xFFF5DDDA),
    onSurfaceVariant = Color(0xFF534341),
    outline = Color(0xFF857370),
    outlineVariant = Color(0xFFD8C2BF),
)

private val DarkColors = darkColorScheme(
    primary = SdRedBright,
    onPrimary = Color(0xFF690005),
    primaryContainer = SdRedDim,
    onPrimaryContainer = SdRedContainer,
    secondary = Color(0xFFE7BDB7),
    onSecondary = Color(0xFF442926),
    secondaryContainer = Color(0xFF5D3F3B),
    onSecondaryContainer = Color(0xFFFFDAD5),
    tertiary = Color(0xFFE0C38D),
    onTertiary = Color(0xFF3F2E04),
    tertiaryContainer = Color(0xFF574419),
    onTertiaryContainer = Color(0xFFFFDFA9),
    // 官方 App 的 #111111 深色基因（对齐 + 微量暖调）
    background = Color(0xFF141112),
    onBackground = Color(0xFFEDE0DE),
    surface = Color(0xFF141112),
    onSurface = Color(0xFFEDE0DE),
    surfaceVariant = Color(0xFF534341),
    onSurfaceVariant = Color(0xFFD8C2BF),
    outline = Color(0xFFA08C8A),
    outlineVariant = Color(0xFF534341),
)

/** M3E 大圆角形状令牌 */
val SdShapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(20.dp),
    extraLarge = RoundedCornerShape(28.dp),
)

@Composable
fun SDGunTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = true,
    content: @Composable () -> Unit,
) {
    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }
        darkTheme -> DarkColors
        else -> LightColors
    }

    MaterialTheme(
        colorScheme = colorScheme,
        shapes = SdShapes,
        content = content,
    )
}
