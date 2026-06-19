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
            // Светлый статус-бар с тёмными иконками
            window.statusBarColor = TickerBackground.toArgb()
            WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = true
            window.navigationBarColor = TickerBackground.toArgb()
            WindowCompat.getInsetsController(window, view).isAppearanceLightNavigationBars = true
        }
    }

    MaterialTheme(
        colorScheme = LightColorScheme,
        typography  = Typography,
        content     = content
    )
}
