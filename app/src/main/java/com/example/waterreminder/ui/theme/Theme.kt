package com.example.waterreminder.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext

private val DarkColorScheme = darkColorScheme(
    primary = Color(0xFF90CAF9),
    onPrimary = Color(0xFF003258),
    primaryContainer = Color(0xFF00497D),
    onPrimaryContainer = Color(0xFFD1E4FF),
    secondary = Color(0xFFBBC7DB),
    onSecondary = Color(0xFF253140),
    secondaryContainer = Color(0xFF3B4858),
    onSecondaryContainer = Color(0xFFD7E3F8),
    tertiary = Color(0xFFB0C5FF),
    onTertiary = Color(0xFF002C71),
    tertiaryContainer = Color(0xFF1B438F),
    onTertiaryContainer = Color(0xFFD9E2FF),
    error = Color(0xFFFFB4AB),
    onError = Color(0xFF690005),
    errorContainer = Color(0xFF93000A),
    onErrorContainer = Color(0xFFFFDAD6),
    background = Color(0xFF1A1C1E),
    onBackground = Color(0xFFE2E2E6),
    surface = Color(0xFF1A1C1E),
    onSurface = Color(0xFFE2E2E6),
    surfaceVariant = Color(0xFF42474E),
    onSurfaceVariant = Color(0xFFC2C7CF),
    outline = Color(0xFF8C9199),
    // 分组卡容器：主页「快速记录」卡的中性底（比 surface 亮一档，比 surfaceVariant 暗一档）
    surfaceContainer = Color(0xFF23262D),
    surfaceContainerHigh = Color(0xFF2B2F36)
)

private val LightColorScheme = lightColorScheme(
    primary = Color(0xFF2196F3),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFE3F2FD),
    onPrimaryContainer = Color(0xFF1565C0),
    secondary = Color(0xFF546E7A),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFECEFF1),
    onSecondaryContainer = Color(0xFF37474F),
    tertiary = Color(0xFF03A9F4),
    onTertiary = Color.White,
    tertiaryContainer = Color(0xFFB3E5FC),
    onTertiaryContainer = Color(0xFF01579B),
    error = Color(0xFFE74C3C),
    onError = Color.White,
    errorContainer = Color(0xFFFEE2E2),
    onErrorContainer = Color(0xFF991B1B),
    background = Color(0xFFF5F7FA),
    onBackground = Color(0xFF2C3E50),
    surface = Color.White,
    onSurface = Color(0xFF2C3E50),
    surfaceVariant = Color(0xFFF8FAFC),
    onSurfaceVariant = Color(0xFF64748B),
    outline = Color(0xFFCBD5E1),
    // 分组卡容器：主页「快速记录」卡的中性底（比 background 深一档，与 surface 白区分）
    surfaceContainer = Color(0xFFEFF3F8),
    surfaceContainerHigh = Color(0xFFE6ECF4)
)

/**
 * Material 3 没有内置 success 语义色，而历史页多处需要「达标绿」。
 * 收敛到这里统一下发，避免界面里散落硬编码绿色；深色下用浅一档保证可读。
 */
data class ExtendedColors(val success: Color)

private val LocalExtendedColors = staticCompositionLocalOf {
    ExtendedColors(success = Color(0xFF4CAF50))
}

val MaterialTheme.successColor: Color
    @Composable get() = LocalExtendedColors.current.success

@Composable
fun WaterReminderTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    // 固定品牌蓝：界面里饮料色/达标绿等身份色是固定的，动态取色会与它们打架，
    // 统一用内置蓝色主题；参数保留便于预览调试
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit
) {
    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }
        darkTheme -> DarkColorScheme
        else -> LightColorScheme
    }
    CompositionLocalProvider(
        LocalExtendedColors provides ExtendedColors(
            success = if (darkTheme) Color(0xFF81C784) else Color(0xFF4CAF50)
        )
    ) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = Typography,
            content = content
        )
    }
}
