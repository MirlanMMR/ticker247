package com.mirlanmamytov.ticker247.network

import android.util.Log
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.TimeUnit

/**
 * Цены на топливо в Кыргызстане.
 * Источник: ежедневная сводка Kaktus.media «Сколько стоит бензин и дизтопливо»
 * (строка Роснефть/КНП — ценовой ориентир). Динамика ▲▼ по локальной истории.
 */
object FuelPriceFetcher {

    private const val TAG = "FuelPriceFetcher"
    private const val CACHE_TTL_MS = 6 * 60 * 60_000L  // обновляем раз в 6 часов

    data class FuelPrices(
        val a92: Double?,
        val a95: Double?,
        val diesel: Double?,
        val fetchedAt: Long = System.currentTimeMillis(),
        val isReal: Boolean = false  // true только если данные реально получены с сайта
    )

    private var cache: FuelPrices? = null

    // История цен: храним последнюю цену в SharedPreferences,
    // при изменении запоминаем предыдущую — для динамики ▲▼ в тикере
    private var prefs: android.content.SharedPreferences? = null

    fun init(context: android.content.Context) {
        prefs = context.getSharedPreferences("fuel_prices", android.content.Context.MODE_PRIVATE)
    }

    /** Обновляет историю и возвращает изменение цены (текущая - предыдущая) */
    private fun trackDelta(key: String, current: Double?): Double? {
        val p = prefs ?: return null
        if (current == null) return null
        val last = p.getFloat("last_$key", 0f).toDouble()
        if (last == 0.0) {
            p.edit().putFloat("last_$key", current.toFloat()).apply()
            return null
        }
        if (kotlin.math.abs(last - current) >= 0.5) {
            // Цена изменилась — запоминаем прошлую и новую
            p.edit()
                .putFloat("prev_$key", last.toFloat())
                .putFloat("last_$key", current.toFloat())
                .putLong("changed_$key", System.currentTimeMillis())
                .apply()
            return current - last
        }
        // Цена не менялась — показываем последнее изменение если оно свежее 7 дней
        val prev = p.getFloat("prev_$key", 0f).toDouble()
        val changedAt = p.getLong("changed_$key", 0L)
        val weekAgo = System.currentTimeMillis() - 7 * 24 * 3_600_000L
        return if (prev > 0 && changedAt > weekAgo) current - prev else null
    }

    // Реальные цены Бишкек (обновлять вручную при изменении)
    private val FALLBACK = FuelPrices(a92 = null, a95 = null, diesel = null, isReal = false)

    private val client = OkHttpClient.Builder()
        .connectTimeout(8, TimeUnit.SECONDS)
        .readTimeout(10, TimeUnit.SECONDS)
        .addInterceptor { chain ->
            chain.proceed(
                chain.request().newBuilder()
                    .header("User-Agent", "Mozilla/5.0 (Android 14; Mobile)")
                    .build()
            )
        }
        .build()

    fun fetch(): FuelPrices {
        val cached = cache
        if (cached != null && System.currentTimeMillis() - cached.fetchedAt < CACHE_TTL_MS) {
            return cached
        }

        return try {
            // Kaktus.media ежедневно публикует сводку цен «Сколько стоит бензин...»
            // Находим свежую статью с главной и парсим таблицу цен
            val home = fetchHtml("https://kaktus.media") ?: return useFallback()
            val articleUrl = Regex("href=\"(https?://kaktus\\.media/doc/\\d+_skolko_stoi[a-z]*_benzin[^\"]*)\"")
                .find(home)?.groupValues?.get(1) ?: return useFallback()
            val article = fetchHtml(articleUrl) ?: return useFallback()
            val prices = parseKaktusTable(article)
            if (prices != null) {
                cache = prices.copy(isReal = true)
                Log.d(TAG, "Fuel prices (kaktus): А-92=${prices.a92}, А-95=${prices.a95}, ДТ=${prices.diesel}")
                cache!!
            } else {
                useFallback()
            }
        } catch (e: Exception) {
            Log.w(TAG, "Fetch failed: ${e.message}")
            useFallback()
        }
    }

    /**
     * Парсит таблицу цен из статьи Kaktus: строка «Роснефть» содержит
     * три цены подряд — АИ-92, АИ-95, ДТ (Роснефть/КНП — ценовой ориентир рынка)
     */
    private fun parseKaktusTable(html: String): FuelPrices? {
        val text = html.replace(Regex("<[^>]+>"), " ")
        val idx = text.indexOf("Роснефть")
        if (idx < 0) return null
        val window = text.substring(idx, (idx + 200).coerceAtMost(text.length))
        val nums = Regex("""\d{2,3}[.,]\d{1,2}""").findAll(window)
            .mapNotNull { it.value.replace(',', '.').toDoubleOrNull() }
            .filter { it in 40.0..200.0 }
            .take(3).toList()
        return if (nums.size >= 3) FuelPrices(a92 = nums[0], a95 = nums[1], diesel = nums[2]) else null
    }

    private fun useFallback(): FuelPrices {
        return cache ?: FALLBACK
    }

    private fun fetchHtml(url: String): String? {
        val req = Request.Builder().url(url).build()
        return client.newCall(req).execute().use { it.body?.string() }
    }

    /** Форматирует цены в строки для тикера — с динамикой ▲▼ если цена менялась */
    fun toTickerItems(prices: FuelPrices): List<String> {
        fun fmt(label: String, key: String, price: Double?): String? {
            if (price == null) return null
            val delta = trackDelta(key, price)
            val arrow = when {
                delta == null || kotlin.math.abs(delta) < 0.5 -> ""
                delta > 0 -> " ▲+${"%.0f".format(delta)}"
                else      -> " ▼${"%.0f".format(delta)}"
            }
            return "⛽ $label: ${"%.0f".format(price)} сом$arrow"
        }
        return listOfNotNull(
            fmt("А-92", "a92", prices.a92),
            fmt("А-95", "a95", prices.a95),
            fmt("ДТ", "diesel", prices.diesel)
        )
    }
}
