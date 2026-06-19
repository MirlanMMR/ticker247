package com.mirlanmamytov.ticker247.util

import android.content.Context
import java.util.Locale

/**
 * Определяет язык и страну из системных настроек телефона.
 * Без GPS, без разрешений — просто Locale телефона.
 *
 * Используется для:
 * - Выбора новостных источников (SourceSelector)
 * - Баланса локальных vs мировых новостей
 * - Языка интерфейса (будущее)
 */
object UserLocale {

    data class UserContext(
        val language: String,   // "ru", "en", "ky", "de", "ar", "tr", "zh", ...
        val country: String,    // "KG", "RU", "DE", "US", "AE", "TR", ...
        val region: Region
    )

    enum class Region {
        KYRGYZSTAN,       // дома
        CIS,              // РФ, Казахстан, Узбекистан, Таджикистан...
        CENTRAL_ASIA,     // ЦА без СНГ
        EUROPE,           // Европа
        MIDDLE_EAST,      // ОАЭ, Саудовская Аравия, Турция
        NORTH_AMERICA,    // США, Канада
        EAST_ASIA,        // Китай, Япония, Корея
        OTHER             // всё остальное
    }

    fun get(): UserContext {
        val locale  = Locale.getDefault()
        val lang    = locale.language.lowercase().take(2)   // "ru", "en", "ky"...
        val country = locale.country.uppercase().take(2)    // "KG", "RU", "DE"...
        val region  = detectRegion(country, lang)
        return UserContext(language = lang, country = country, region = region)
    }

    private fun detectRegion(country: String, lang: String): Region = when {
        country == "KG" -> Region.KYRGYZSTAN
        country in setOf("RU", "BY", "UA", "MD", "AM", "AZ", "GE") -> Region.CIS
        country in setOf("KZ", "UZ", "TJ", "TM") -> Region.CENTRAL_ASIA
        country in setOf("AE", "SA", "QA", "BH", "KW", "OM", "TR", "EG", "JO", "LB") -> Region.MIDDLE_EAST
        country in setOf("DE", "FR", "GB", "IT", "ES", "NL", "SE", "NO", "FI", "CH",
            "AT", "BE", "PL", "CZ", "HU", "RO", "PT", "GR", "DK") -> Region.EUROPE
        country in setOf("US", "CA", "MX") -> Region.NORTH_AMERICA
        country in setOf("CN", "JP", "KR", "TW", "HK", "SG") -> Region.EAST_ASIA
        // Если страна неизвестна — определяем по языку
        lang == "ru" || lang == "ky" -> Region.CIS
        lang in setOf("en") -> Region.EUROPE
        lang in setOf("ar") -> Region.MIDDLE_EAST
        else -> Region.OTHER
    }

    /** Нужно ли показывать контент преимущественно на русском */
    fun UserContext.preferRussian(): Boolean =
        language in setOf("ru", "ky", "kk", "uz", "tg") || region in setOf(
            Region.KYRGYZSTAN, Region.CIS, Region.CENTRAL_ASIA
        )

    /** Доля локальных (KG) новостей в ленте */
    fun UserContext.localNewsRatio(): Float = when (region) {
        Region.KYRGYZSTAN  -> 0.55f  // дома: чуть больше локального
        Region.CIS         -> 0.50f  // диаспора в СНГ: 50/50
        Region.CENTRAL_ASIA -> 0.50f
        Region.MIDDLE_EAST -> 0.45f  // диаспора далеко: немного меньше
        else               -> 0.40f  // далёкие страны: меньше локального, больше мирового
    }
}
