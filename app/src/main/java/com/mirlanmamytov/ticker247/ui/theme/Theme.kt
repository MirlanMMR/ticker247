package com.mirlanmamytov.ticker247.ui.theme

import android.app.Activity
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

// Всегда светлая тема — контент читается лучше на белом фоне
private val LightColorScheme = lightColorScheme(
    primary            = TickerBlue,
    onPrimary          = TickerSurface,
    primaryContainer   = TickerBlueLight,
    onPrimaryContainer = TickerBlueDark,

    secondary          = TickerSecondary,
    onSecondary        = TickerSurface,

    background         = TickerBackground,
    onBackground       = TickerOnBg,

    surface            = TickerSurface,
    onSurface          = TickerOnSurface,
    surfaceVariant     = TickerSurfaceVar,
    onSurfaceVariant   = TickerSecondary,

    error              = TickerRed,
    onError            = TickerSurface,
)

@Composable
fun Ticker247Theme(
    content: @Composable () -> Unit
) {
    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            // Цвета панелей больше не выставляем: statusBarColor и
            // navigationBarColor — no-op начиная с targetSdk 36, панели
            // всегда прозрачные (edge-to-edge). Управляем только цветом иконок.
            //
            // Статус-бар лежит поверх тёмной бегущей строки (0xFF050508) —
            // ему нужны СВЕТЛЫЕ иконки (isAppearanceLightStatusBars = false).
            // Если поставить true, как раньше, система решает, что тёмные
            // иконки на тёмном фоне нечитаемы, и сама подставляет полупрозрачную
            // светлую подложку для контраста — та самая белёсая полоска.
            // Навигация внизу — над светлой лентой, там всё как было.
            val controller = WindowCompat.getInsetsController(window, view)
            controller.isAppearanceLightStatusBars = false
            controller.isAppearanceLightNavigationBars = true
        }
    }

    MaterialTheme(
        colorScheme = LightColorScheme,
        typography  = Typography,
        content     = content
    )
}
