package com.mirlanmamytov.ticker247.ui.theme

import androidx.compose.ui.graphics.Color

// ── Ticker 24/7 — Neutral gray palette ───────────────────────────────────────
// Не чисто белый (слепит) и не чисто чёрный (резкий контраст, ореолы текста
// при астигматизме) — средний тёплый графитовый серый, мягче для долгого чтения

val TickerBlue        = Color(0xFF00D4FF)   // primary — кнопки, ссылки, badges
val TickerBlueDark    = Color(0xFF0091B0)   // primary variant
val TickerBlueLight   = Color(0xFF163540)   // primary container (тёмный, не светлый)

val TickerRed         = Color(0xFFE24B4A)   // URGENT badge, ошибки
val TickerGreen       = Color(0xFF63C583)   // рост крипты/валюты ▲
val TickerAmber       = Color(0xFFEF9F27)   // предупреждения

val TickerBackground  = Color(0xFF2A2D34)   // основной фон
val TickerSurface     = Color(0xFF363940)   // карточки, диалоги, bottom sheet
val TickerSurfaceVar  = Color(0xFF3D4048)   // разделители, плейсхолдеры

val TickerOnBg        = Color(0xFFE4E6EB)   // основной текст
val TickerOnSurface   = Color(0xFFE4E6EB)   // текст на карточках
val TickerSecondary   = Color(0xFFB4B2A9)   // подписи, метки времени

val TickerBarBg       = Color(0xFF050508)   // бегущая строка — темнее фона, контраст
val TickerBarText     = Color(0xFFFFFFFF)
val TickerBarAccent   = Color(0xFF00D4FF)   // cyan для $, BTC и т.д.
