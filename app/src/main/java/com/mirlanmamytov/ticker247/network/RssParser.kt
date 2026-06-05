package com.mirlanmamytov.ticker247.network

import android.util.Log
import com.mirlanmamytov.ticker247.data.model.NewsItem
import okhttp3.OkHttpClient
import okhttp3.Request
import org.xmlpull.v1.XmlPullParser
import org.xmlpull.v1.XmlPullParserFactory
import java.util.concurrent.TimeUnit

data class RssSource(
    val url: String,
    val sourceName: String,
    val category: String,
    val priority: Int = 0
)

val RSS_SOURCES = listOf(
    // Мировые новости (RU)
    RssSource("https://ria.ru/export/rss2/archive/index.xml", "РИА Новости", "NEWS", 1),
    RssSource("https://tass.ru/rss/v2.xml", "ТАСС", "NEWS", 1),
    RssSource("https://rssexport.rbc.ru/rbcnews/news/30/full.rss", "РБК", "NEWS", 0),
    RssSource("https://lenta.ru/rss/news", "Лента.ру", "NEWS", 0),

    // ЦА новости
    RssSource("https://24.kg/rss/", "24.kg", "NEWS", 1),
    RssSource("https://kabar.kg/rss/", "Kabar.kg", "NEWS", 1),
    RssSource("https://akipress.com/rss/news.rss", "AKIpress", "NEWS", 0),
    RssSource("https://www.zakon.kz/rss/news.rss", "Zakon.kz", "NEWS", 0),

    // Технологии
    RssSource("https://habr.com/ru/rss/flows/develop/all/", "Хабр", "TECH", 0),
    RssSource("https://www.ixbt.com/export/news.rss", "iXBT", "TECH", 0),
    RssSource("https://4pda.to/feed/", "4PDA", "TECH", 0),
    RssSource("https://feeds.feedburner.com/CultOfMacRss", "Cult of Mac", "TECH", 0),

    // Спорт
    RssSource("https://rsport.ria.ru/export/rss2/archive/index.xml", "РИА Спорт", "SPORT", 1),
    RssSource("https://www.sports.ru/rss/main.xml", "Sports.ru", "SPORT", 0),
    RssSource("https://tass.ru/sport/rss/v2.xml", "ТАСС Спорт", "SPORT", 0),

    // Авто
    RssSource("https://www.drive.ru/rss.xml", "Drive.ru", "AUTO", 0),
    RssSource("https://auto.mail.ru/rss/news/", "Auto.Mail", "AUTO", 0),

    // Мода / Стиль
    RssSource("https://www.elle.ru/rss/", "Elle Russia", "FASHION", 0),
    RssSource("https://www.vogue.ru/rss/", "Vogue Russia", "FASHION", 0),

    // Кино / Сериалы
    RssSource("https://www.kinopoisk.ru/rss/premiere.rss", "Кинопоиск", "CULTURE", 0),
    RssSource("https://www.kino-teatr.ru/rss/news.rss", "Кино-Театр", "CULTURE", 0),

    // Недвижимость
    RssSource("https://www.realestate.ru/rss.xml", "RealEstate.ru", "REALTY", 0),

    // Туры
    RssSource("https://www.tourprom.ru/rss/", "Tourprom", "TOURS", 0),
    RssSource("https://www.turpravda.ru/rss.xml", "Турправда", "TOURS", 0),

    // Google Trends
    RssSource("https://trends.google.com/trends/trendingsearches/daily/rss?geo=RU", "Тренды RU", "TRENDS", 1),
    RssSource("https://trends.google.com/trends/trendingsearches/daily/rss?geo=KZ", "Тренды KZ", "TRENDS", 1),
)

// Стоп-слова для фильтрации скучных новостей
private val BORING_KEYWORDS = setOf(
    "заседание", "совещание", "пресс-конференция", "протокол",
    "постановление", "распоряжение", "регламент", "меморандум",
    "брифинг", "пленарное", "ратификация"
)

object RssParser {

    private val client = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(10, TimeUnit.SECONDS)
        .build()

    fun fetchFeed(source: RssSource): List<NewsItem> {
        return try {
            val request = Request.Builder()
                .url(source.url)
                .header("User-Agent", "Mozilla/5.0 Ticker247/1.0")
                .build()
            val response = client.newCall(request).execute()
            if (!response.isSuccessful) return emptyList()
            val body = response.body?.string() ?: return emptyList()
            parseRss(body, source)
        } catch (e: Exception) {
            Log.e("RssParser", "${source.sourceName}: ${e.message}")
            emptyList()
        }
    }

    private fun parseRss(xml: String, source: RssSource): List<NewsItem> {
        val items = mutableListOf<NewsItem>()
        try {
            val factory = XmlPullParserFactory.newInstance().apply { isNamespaceAware = true }
            val parser = factory.newPullParser()
            parser.setInput(xml.reader())

            var inItem = false
            var title = ""
            var link = ""
            var description = ""
            var imageUrl: String? = null
            var pubDate = ""
            var eventType = parser.eventType

            while (eventType != XmlPullParser.END_DOCUMENT) {
                when (eventType) {
                    XmlPullParser.START_TAG -> when (parser.name) {
                        "item" -> {
                            inItem = true
                            title = ""; link = ""; description = ""; imageUrl = null; pubDate = ""
                        }
                        "title" -> if (inItem) title = parser.nextText().trim()
                        "link" -> if (inItem && link.isEmpty()) {
                            try { link = parser.nextText().trim() } catch (e: Exception) {}
                        }
                        "description" -> if (inItem) description = stripHtml(parser.nextText())
                        "pubDate" -> if (inItem) pubDate = parser.nextText().trim()
                        "enclosure" -> {
                            val type = parser.getAttributeValue(null, "type") ?: ""
                            if (type.startsWith("image") && imageUrl == null) {
                                imageUrl = parser.getAttributeValue(null, "url")
                            }
                        }
                        "content", "thumbnail" -> {
                            val url = parser.getAttributeValue(null, "url")
                            val medium = parser.getAttributeValue(null, "medium") ?: ""
                            if (url != null && imageUrl == null &&
                                (medium == "image" || url.contains(".jpg") || url.contains(".jpeg") || url.contains(".png"))) {
                                imageUrl = url
                            }
                        }
                    }
                    XmlPullParser.END_TAG -> {
                        if (parser.name == "item" && inItem && title.isNotEmpty()) {
                            if (!isBoring(title)) {
                                items.add(NewsItem(
                                    url = link,
                                    title = title,
                                    summary = description.take(200),
                                    imageUrl = imageUrl,
                                    source = source.sourceName,
                                    category = source.category,
                                    publishedAt = parseDate(pubDate),
                                    priority = source.priority
                                ))
                            }
                            inItem = false
                        }
                    }
                }
                eventType = parser.next()
            }
        } catch (e: Exception) {
            Log.e("RssParser", "Parse error ${source.sourceName}: ${e.message}")
        }
        return items.take(8)
    }

    private fun isBoring(title: String): Boolean {
        val lower = title.lowercase()
        return BORING_KEYWORDS.any { lower.contains(it) }
    }

    private fun stripHtml(html: String): String =
        html.replace(Regex("<[^>]*>"), "").trim()

    private fun parseDate(dateStr: String): Long {
        if (dateStr.isEmpty()) return System.currentTimeMillis()
        return try {
            listOf(
                java.text.SimpleDateFormat("EEE, dd MMM yyyy HH:mm:ss z", java.util.Locale.ENGLISH),
                java.text.SimpleDateFormat("EEE, dd MMM yyyy HH:mm:ss Z", java.util.Locale.ENGLISH),
                java.text.SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ssZ", java.util.Locale.ENGLISH)
            ).firstNotNullOfOrNull { fmt ->
                try { fmt.parse(dateStr)?.time } catch (e: Exception) { null }
            } ?: System.currentTimeMillis()
        } catch (e: Exception) {
            System.currentTimeMillis()
        }
    }
}
