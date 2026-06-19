package com.mirlanmamytov.ticker247.ui.theme

import androidx.compose.ui.graphics.Color

// ── Ticker 24/7 — Light theme palette ────────────────────────────────────────
// Inspired by Bloomberg / Apple News: crisp white, bold blue accent, dark text

val TickerBlue        = Color(0xFF0070F3)   // primary — кнопки, ссылки, badges
val TickerBlueDark    = Color(0xFF0052CC)   // primary variant
val TickerBlueLight   = Color(0xFFE8F1FF)   // primary container

val TickerRed         = Color(0xFFE53935)   // URGENT badge, ошибки
val TickerGreen       = Color(0xFF2E7D32)   // рост крипты/валюты ▲
val TickerAmber       = Color(0xFFF57C00)   // предупреждения

val TickerBackground  = Color(0xFFF5F7FA)   // основной фон — слегка серый, не слепит
val TickerSurface     = Color(0xFFFFFFFF)   // карточки
val TickerSurfaceVar  = Color(0xFFEEF2F7)   // разделители, плейсхолдеры

val TickerOnBg        = Color(0xFF0A0A0A)   // основной текст
val TickerOnSurface   = Color(0xFF1A1A1A)   // текст на карточках
val TickerSecondary   = Color(0xFF6B7280)   // подписи, метки времени

val TickerBarBg       = Color(0xFF0A0A1A)   // бегущая строка — остаётся тёмной (контраст)
val TickerBarText     = Color(0xFFFFFFFF)
val TickerBarAccent   = Color(0xFF00D4FF)   // cyan для $, BTC и т.д.
