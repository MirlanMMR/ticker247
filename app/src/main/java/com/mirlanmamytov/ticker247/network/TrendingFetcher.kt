package com.mirlanmamytov.ticker247.network

import android.util.Log
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.TimeUnit

/**
 * Получает трендовые темы для Кыргызстана из Google Trends RSS.
 * Бесплатно, без API-ключа.
 *
 * Логика:
 * 1. Загружаем топ-20 поисковых трендов КГ прямо сейчас
 * 2. Для каждой новости проверяем — совпадает ли тема с трендом
 * 3. Совпавшие помечаются isTrending = true и поднимаются в ленте
 */
object TrendingFetcher {

    private val client = OkHttpClient.Builder()
        .connectTimeout(8, TimeUnit.SECONDS)
        .readTimeout(8, TimeUnit.SECONDS)
        .build()

    // Кэш: список нормализованных трендовых слов, обновляется раз в 30 мин
    @Volatile private var trendKeywords: List<String> = emptyList()
    @Volatile private var lastFetch: Long = 0L
    private const val CACHE_TTL = 30 * 60 * 1000L // 30 минут

    /**
     * Загружает актуальные тренды Кыргызстана.
     * Пробует KG, при неудаче — RU (много пересечений с КГ аудиторией).
     */
    suspend fun refresh() {
        val now = System.currentTimeMillis()
        if (now - lastFetch < CACHE_TTL) return   // ещё свежие

        val keywords = fetchTrends("KG").ifEmpty { fetchTrends("RU") }
        if (keywords.isNotEmpty()) {
            trendKeywords = keywords
            lastFetch = now
            Log.d("TrendingFetcher", "Loaded ${keywords.size} trends: ${keywords.take(5)}")
        }
    }

    /**
     * Проверяет — соответствует ли заголовок текущим трендам.
     */
    fun isTrending(title: String): Boolean {
        if (trendKeywords.isEmpty()) return false
        val t = title.lowercase()
        return trendKeywords.any { keyword -> t.contains(keyword) }
    }

    /**
     * Получает тренды из Google Trends RSS для указанной страны.
     * Возвращает список нормализованных ключевых слов (lowercase, отдельные слова).
     */
    private fun fetchTrends(geo: String): List<String> {
        return try {
            val url = "https://trends.google.com/trends/trendingsearches/daily/rss?geo=$geo"
            val request = Request.Builder().url(url)
                .header("User-Agent", "Mozilla/5.0 (Android; Mobile)")
                .build()

            val body = client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return emptyList()
                response.body?.string() ?: return emptyList()
            }

            // Парсим XML: извлекаем теги <title> внутри <item>
            val titleRegex = Regex("<item>.*?<title><!\\[CDATA\\[(.+?)]]></title>", RegexOption.DOT_MATCHES_ALL)
            val simpleTitle = Regex("<item>.*?<title>([^<]+)</title>", RegexOption.DOT_MATCHES_ALL)

            val trends = mutableListOf<String>()
            (titleRegex.findAll(body) + simpleTitle.findAll(body)).forEach { match ->
                val topic = match.groupValues[1].trim()
                if (topic.length > 2) {
                    // Разбиваем на слова — каждое слово длиннее 3 букв становится ключом
                    topic.lowercase()
                        .split(Regex("[\\s,/]+"))
                        .filter { it.length > 3 }
                        .forEach { trends.add(it) }
                    // Также добавляем фразу целиком (для точного совпадения)
                    if (topic.length > 5) trends.add(topic.lowercase())
                }
            }
            trends.distinct().take(100)  // не более 100 ключей
        } catch (e: Exception) {
            Log.w("TrendingFetcher", "Failed to fetch trends for $geo: ${e.message}")
            emptyList()
        }
    }
}
