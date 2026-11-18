package ru.dvbstudio.dvcloud.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

// Тёмная палитра: глубокий тёмно-синий фон, мягкие акценты
private val DarkColors = darkColorScheme(
    primary = Color(0xFF82B1FF),           // мягкий голубой
    onPrimary = Color(0xFF000000),
    primaryContainer = Color(0xFF1E3A5F),
    onPrimaryContainer = Color(0xFFD0E4FF),
    secondary = Color(0xFFB39DDB),         // приглушённый фиолетовый
    onSecondary = Color(0xFF000000),
    background = Color(0xFF0D1117),        // почти чёрный, не чистый
    onBackground = Color(0xFFE6EDF3),      // мягкий светлый
    surface = Color(0xFF161B22),           // тёмно-серый для карточек
    onSurface = Color(0xFFE6EDF3),
    surfaceVariant = Color(0xFF21262D),
    onSurfaceVariant = Color(0xFF8B949E),
    error = Color(0xFFFF8A80),             // мягкий красный
    onError = Color(0xFF000000),
    outline = Color(0xFF30363D),
)

// Светлая палитра (на случай если пользователь выключит тёмную)
private val LightColors = lightColorScheme(
    primary = Color(0xFF1976D2),
    onPrimary = Color(0xFFFFFFFF),
    background = Color(0xFFF5F5F7),
    onBackground = Color(0xFF1A1A1A),
    surface = Color(0xFFFFFFFF),
    onSurface = Color(0xFF1A1A1A),
    error = Color(0xFFD32F2F),
    onError = Color(0xFFFFFFFF),
)

/**
 * Тема приложения.
 *
 * forceDark = true — всегда тёмная тема (как просил пользователь).
 * По умолчанию используется системная настройка (isSystemInDarkTheme).
 */
@Composable
fun DvCloudTheme(
    forceDark: Boolean = true,
    content: @Composable () -> Unit,
) {
    val useDark = forceDark || isSystemInDarkTheme()
    val colors = if (useDark) DarkColors else LightColors

    MaterialTheme(
        colorScheme = colors,
        content = content,
    )
}
