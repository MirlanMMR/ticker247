package com.mirlanmamytov.ticker247.network

import android.net.Uri
import android.util.Log
import com.mirlanmamytov.ticker247.data.model.NewsItem
import okhttp3.OkHttpClient
import okhttp3.Request
import org.jsoup.Jsoup
import org.jsoup.parser.Parser
import java.time.format.DateTimeFormatter
import java.util.concurrent.TimeUnit

/**
 * Поиск свежих новостей по редакторским темам (#тема: в TG-канале)
 * через Google News RSS — бесплатно, без ключей, любой запрос.
 * Если в ленте нет новостей по заданной теме, они подтянутся отсюда.
 */
object TopicNewsFetcher {

    private val client = OkHttpClient.Builder()
        .connectTimeout(8, TimeUnit.SECONDS)
        .readTimeout(10, TimeUnit.SECONDS)
        .addInterceptor { chain ->
            chain.proceed(chain.request().newBuilder()
                .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64)")
                .build())
        }
        .build()

    // Параметры Google News по локали пула
    private fun localeParams(): Triple<String, String, String> {
        val lang = java.util.Locale.getDefault().language
        val cyrillic = setOf("ru", "ky", "kk", "uz", "tg", "be", "uk", "bg", "sr", "mk")
        return when {
            lang in cyrillic -> Triple("ru", "RU", "RU:ru")
            lang == "es" -> Triple("es-419", "MX", "MX:es-419")
            lang == "pt" -> Triple("pt-BR", "BR", "BR:pt-419")
            else -> Triple("en-US", "US", "US:en")
        }
    }

    private fun poolLanguage(): String {
        val lang = java.util.Locale.getDefault().language
        val cyrillic = setOf("ru", "ky", "kk", "uz", "tg", "be", "uk", "bg", "sr", "mk")
        return when {
            lang in cyrillic -> "ru"
            lang == "es" -> "es"
            lang == "pt" -> "pt"
            else -> "en"
        }
    }

    /** Свежие статьи по теме: не старше 3 дней, максимум 5 */
    fun fetch(topic: String): List<NewsItem> {
        return try {
            val (hl, gl, ceid) = localeParams()
            val url = "https://news.google.com/rss/search?q=${Uri.encode(topic)}&hl=$hl&gl=$gl&ceid=$ceid"
            val xml = client.newCall(Request.Builder().url(url).build())
                .execute().use { it.body?.string() ?: return emptyList() }
            val doc = Jsoup.parse(xml, "", Parser.xmlParser())
            val cutoff = System.currentTimeMillis() - 3 * 24 * 3_600_000L

            doc.select("item").mapNotNull { item ->
                val rawTitle = item.selectFirst("title")?.text() ?: return@mapNotNull null
                val link = item.selectFirst("link")?.text() ?: return@mapNotNull null
                val sourceName = item.selectFirst("source")?.text() ?: "Google News"
                val pubMillis = item.selectFirst("pubDate")?.text()?.let { d ->
                    runCatching {
                        java.time.ZonedDateTime.parse(d, DateTimeFormatter.RFC_1123_DATE_TIME)
                            .toInstant().toEpochMilli()
                    }.getOrNull()
                } ?: return@mapNotNull null
                if (pubMillis < cutoff) return@mapNotNull null
                // Google News добавляет « - Источник» в конец заголовка
                val title = rawTitle.substringBeforeLast(" - ").trim()
                if (title.length < 15) return@mapNotNull null

                NewsItem(
                    url = link,
                    title = title,
                    summary = "",
                    imageUrl = null,
                    source = sourceName,
                    category = "NEWS",
                    publishedAt = pubMillis,
                    priority = 2,
                    language = poolLanguage(),
                    scope = "local"
                )
            }.sortedByDescending { it.publishedAt }.take(5)
        } catch (e: Exception) {
            Log.w("TopicNewsFetcher", "«$topic»: ${e.message}")
            emptyList()
        }
    }
}
