package com.mirlanmamytov.ticker247.network

import android.util.Log
import com.mirlanmamytov.ticker247.data.model.NewsItem
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.Locale
import java.util.concurrent.TimeUnit

object GoogleNewsRssParser {

    private val client = OkHttpClient.Builder()
        .connectTimeout(12, TimeUnit.SECONDS)
        .readTimeout(12, TimeUnit.SECONDS)
        .followRedirects(true)
        .addInterceptor { chain ->
            val req = chain.request().newBuilder()
                .header("User-Agent", "Mozilla/5.0 (Android 14; Mobile)")
                .header("Accept", "application/rss+xml, application/xml, text/xml")
                .build()
            chain.proceed(req)
        }
        .build()

    // Страны где Google News недоступен — используем fallback источники
    private val BLOCKED_COUNTRIES = setOf("CN", "KP", "IR", "CU")

    // Fallback RSS для заблокированных стран
    private val COUNTRY_FALLBACK_RSS = mapOf(
        "CN" to "https://feeds.bbci.co.uk/zhongwen/simp/rss.xml",      // BBC Chinese
        "IR" to "https://www.irna.ir/rss/allnews/",                      // IRNA Iran
        "RU" to "https://news.yandex.ru/index.rss",                      // Яндекс.Новости
    )

    /**
     * Строит URL Google News RSS по локали телефона.
     * Пример: "pt_BR" → hl=pt-BR&gl=BR&ceid=BR:pt
     */
    fun buildUrl(locale: Locale): String {
        val lang    = locale.language.lowercase()   // "pt", "en", "ru", "ky"
        val country = locale.country.uppercase()    // "BR", "AU", "KG", "US"

        // Языковой тег для Google: "pt-BR", "en-AU", "ru-RU"
        val hl = if (country.isNotEmpty()) "$lang-$country" else lang
        val gl = country.ifEmpty { "US" }
        val ceid = "$gl:$lang"

        return "https://news.google.com/rss?hl=$hl&gl=$gl&ceid=$ceid"
    }

    /**
     * Загружает и парсит Google News RSS для локали пользователя.
     * Возвращает список NewsItem готовых для отображения в ленте.
     */
    fun fetch(locale: Locale = Locale.getDefault()): List<NewsItem> {
        val country = locale.country.uppercase()

        // Для заблокированных стран используем fallback
        if (country in BLOCKED_COUNTRIES) {
            val fallbackUrl = COUNTRY_FALLBACK_RSS[country] ?: return emptyList()
            return fetchRss(fallbackUrl, locale)
        }

        val url = buildUrl(locale)
        Log.d("GoogleNewsRSS", "Fetching: $url")
        return fetchRss(url, locale)
    }

    /**
     * Загружает и парсит любой RSS-фид.
     * Работает с Google News и fallback-источниками.
     */
    private fun fetchRss(url: String, locale: Locale): List<NewsItem> {
        return try {
            val req = Request.Builder().url(url).build()
            val body = client.newCall(req).execute().use { it.body?.string() ?: return emptyList() }
            parseRss(body, locale)
        } catch (e: Exception) {
            Log.w("GoogleNewsRSS", "Failed to fetch $url: ${e.message}")
            emptyList()
        }
    }

    private fun parseRss(xml: String, locale: Locale): List<NewsItem> {
        val items = mutableListOf<NewsItem>()

        // Разбиваем на <item> блоки
        val itemPattern = Regex("<item>(.*?)</item>", setOf(RegexOption.DOT_MATCHES_ALL))
        val titlePat    = Regex("<title>(?:<!\\[CDATA\\[)?(.*?)(?:\\]\\]>)?</title>")
        val linkPat     = Regex("<link>(.*?)</link>|<link[^>]+href=\"([^\"]+)\"")
        val descPat     = Regex("<description>(?:<!\\[CDATA\\[)?(.*?)(?:\\]\\]>)?</description>", setOf(RegexOption.DOT_MATCHES_ALL))
        val pubDatePat  = Regex("<pubDate>(.*?)</pubDate>")
        val sourcePat   = Regex("<source[^>]+url=\"[^\"]*\"[^>]*>(.*?)</source>|<source>(.*?)</source>")
        val imgPat      = Regex("""<media:(?:content|thumbnail)[^>]+url="([^"]+)""")
        val enclosurePat = Regex("""<enclosure[^>]+url="([^"]+)"[^>]+type="image""")

        val matches = itemPattern.findAll(xml).take(30)  // берём до 30 новостей

        for (match in matches) {
            try {
                val block = match.groupValues[1]

                val rawTitle = titlePat.find(block)?.groupValues?.get(1)?.trim() ?: continue
                val title = cleanText(rawTitle)
                if (title.length < 10) continue

                // Google News ссылки — редиректы через news.google.com
                // ArticleExtractor сам раскроет редирект при открытии статьи
                val link = linkPat.find(block)?.let {
                    it.groupValues[1].ifEmpty { it.groupValues[2] }
                }?.trim() ?: ""

                val rawDesc  = descPat.find(block)?.groupValues?.get(1) ?: ""
                val summary  = cleanText(rawDesc).take(400)

                val pubDate  = pubDatePat.find(block)?.groupValues?.get(1)?.trim() ?: ""
                val publishedAt = parseRssDate(pubDate)

                // Источник: "BBC News", "Reuters", "Akipress" и т.д.
                val source = sourcePat.find(block)?.let {
                    it.groupValues[1].ifEmpty { it.groupValues[2] }
                }?.trim()?.ifEmpty { "Google News" } ?: "Google News"

                // Изображение из media:content или enclosure
                val imageUrl = imgPat.find(block)?.groupValues?.get(1)?.trim()
                    ?: enclosurePat.find(block)?.groupValues?.get(1)?.trim()

                // Определяем категорию по ключевым словам в заголовке
                val trending = TrendingFetcher.isTrending(title)

                items.add(
                    NewsItem(
                        url         = link,
                        title       = title,
                        summary     = summary.ifEmpty { title },
                        imageUrl    = imageUrl,
                        source      = source,
                        category    = "NEWS",   // enrichCategory() уточнит в MainHomeScreen
                        publishedAt = publishedAt,
                        priority    = 0,
                        isTrending  = trending,
                        sourceCount = 1
                    )
                )
            } catch (e: Exception) {
                // Пропускаем битый элемент
            }
        }

        Log.d("GoogleNewsRSS", "Parsed ${items.size} items for ${locale.toLanguageTag()}")
        return items
    }

    private fun cleanText(html: String): String = html
        .replace(Regex("<[^>]+>"), "")
        .replace("&amp;", "&").replace("&lt;", "<").replace("&gt;", ">")
        .replace("&quot;", "\"").replace("&apos;", "'").replace("&nbsp;", " ")
        .replace("&#39;", "'").replace("&mdash;", "—").replace("&ndash;", "–")
        .replace("&hellip;", "…")
        .replace(Regex("&#(\\d+);")) { it.groupValues[1].toIntOrNull()?.toChar()?.toString() ?: "" }
        .replace(Regex("\\s+"), " ")
        .trim()

    // RFC 822: "Mon, 09 Jun 2025 14:30:00 GMT"
    private fun parseRssDate(s: String): Long {
        if (s.isBlank()) return System.currentTimeMillis()
        return try {
            val sdf = java.text.SimpleDateFormat("EEE, dd MMM yyyy HH:mm:ss z", Locale.ENGLISH)
            sdf.parse(s)?.time ?: System.currentTimeMillis()
        } catch (e: Exception) {
            try {
                val sdf2 = java.text.SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.ENGLISH)
                sdf2.parse(s)?.time ?: System.currentTimeMillis()
            } catch (e2: Exception) {
                System.currentTimeMillis()
            }
        }
    }
}
