package com.mirlanmamytov.ticker247.util

/**
 * Профиль валют по локали устройства: базовая валюта региона и набор
 * популярных валют. Единственный источник правды для сервиса и UI —
 * значения и подписи всегда согласованы.
 */
object CurrencyProfile {

    data class Quote(val code: String, val emoji: String)
    data class Profile(val base: String, val label: String, val quotes: List<Quote>)

    fun current(): Profile {
        val lang = java.util.Locale.getDefault().language
        val country = DeviceCountry.get()
        val cyrillic = setOf("ru", "ky", "kk", "uz", "tg", "be", "uk", "bg", "sr", "mk")

        // КГ (или кириллическая локаль без страны) — домашний профиль
        if (country == "KG" || (country.isEmpty() && lang in cyrillic)) {
            return Profile("KGS", "сом", listOf(
                Quote("USD", "💵"), Quote("EUR", "💶"), Quote("RUB", "🇷🇺"), Quote("CNY", "🇨🇳"),
                Quote("KZT", "🇰🇿"), Quote("UZS", "🇺🇿"), Quote("TRY", "🇹🇷"), Quote("AED", "🇦🇪")))
        }

        // Любая страна мира: национальная валюта из системного справочника
        val base = runCatching {
            java.util.Currency.getInstance(java.util.Locale("", country)).currencyCode
        }.getOrNull() ?: when {
            lang in cyrillic -> "RUB"
            lang == "pt" -> "BRL"
            else -> "USD"
        }

        // Котировки: мировые резервные + региональные, свою базу исключаем
        val quotes = (if (lang in cyrillic)
            listOf(Quote("USD", "💵"), Quote("EUR", "💶"), Quote("RUB", "🇷🇺"),
                   Quote("CNY", "🇨🇳"), Quote("TRY", "🇹🇷"))
        else
            listOf(Quote("USD", "💵"), Quote("EUR", "💶"), Quote("GBP", "🇬🇧"),
                   Quote("CNY", "🇨🇳"), Quote("JPY", "🇯🇵"))
        ).filter { it.code != base }

        return Profile(base, base, quotes)
    }

    private fun fmt(v: Double): String = when {
        v >= 1    -> "%.2f".format(v)
        v >= 0.01 -> "%.4f".format(v)
        else      -> "%.6f".format(v)
    }

    /**
     * "USD 87.46 | EUR 99.77 | ..." — цена 1 единицы валюты в базовой.
     * rates — ответ API с base = current().base (rates[code] = code за 1 базовую).
     */
    fun buildRatesText(rates: Map<String, Double>): String? {
        val parts = current().quotes.mapNotNull { q ->
            rates[q.code]?.takeIf { it > 0 }?.let { "${q.code} ${fmt(1.0 / it)}" }
        }
        return if (parts.isEmpty()) null else parts.joinToString(" | ")
    }

    /** Строки для бегущей строки: "💵 USD 87.46 сом" */
    fun buildTickerEntries(rates: Map<String, Double>): List<String> {
        val p = current()
        return p.quotes.mapNotNull { q ->
            rates[q.code]?.takeIf { it > 0 }?.let { "${q.emoji} ${q.code} ${fmt(1.0 / it)} ${p.label}" }
        }
    }
}
