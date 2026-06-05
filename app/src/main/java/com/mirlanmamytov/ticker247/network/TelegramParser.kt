package com.mirlanmamytov.ticker247.network

import android.util.Log
import com.mirlanmamytov.ticker247.data.model.NewsItem
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.TimeUnit

object TelegramParser {

    private val client = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(10, TimeUnit.SECONDS)
        .addInterceptor { chain ->
            val req = chain.request().newBuilder()
                .header("User-Agent", "Mozilla/5.0 (Android 14)")
                .header("Accept-Language", "ru-RU,ru;q=0.9")
                .build()
            chain.proceed(req)
        }
        .build()

    data class TelegramSource(
        val channel: String,
        val category: String,
        val priority: Int = 0
    )

    val SOURCES = listOf(
        // 🇰🇬 KG Official & News
        TelegramSource("akipress",      "NEWS",    1),
        TelegramSource("kabar_news_kg", "NEWS",    1),
        TelegramSource("kyrgyzinform",  "NEWS",    0),
        TelegramSource("24kgnews",      "NEWS",    0),
        TelegramSource("tazabek",       "NEWS",    0),

        // 🇷🇺 RU & World
        TelegramSource("rian_ru",       "NEWS",    1),
        TelegramSource("tass_agency",   "NEWS",    1),
        TelegramSource("rbc_news",      "NEWS",    0),

        // ⚡ Breaking / Urgent
        TelegramSource("breakingmash",  "URGENT",  2),
        TelegramSource("shot_shot",     "URGENT",  2),

        // 🏦 Official / Finance
        TelegramSource("cbr_official",  "NEWS",    1),
        TelegramSource("kremlin_crypt", "NEWS",    1),

        // 🏆 Sport
        TelegramSource("sport24russia", "SPORT",   0),
        TelegramSource("matchtv",       "SPORT",   0),

        // 🎬 Cinema / Culture
        TelegramSource("kinopoisk",     "CULTURE", 0),
        TelegramSource("afishakg",      "CULTURE", 0),

        // 🚗 Auto
        TelegramSource("autodrive",     "AUTO",    0),
        TelegramSource("carsguru_ru",   "AUTO",    0),

        // 👗 Fashion
        TelegramSource("fashionista_ru","FASHION", 0),

        // ✈️ Tours / Travel
        TelegramSource("travel_kg",     "TOURS",   0),
        TelegramSource("travelplus_ru", "TOURS",   0),
    )

    /** Парсит публичный канал t.me/s/channel → список NewsItem */
    fun fetchChannel(source: TelegramSource): List<NewsItem> {
        return try {
            val url = "https://t.me/s/${source.channel}"
            val req = Request.Builder().url(url).build()
            val body = client.newCall(req).execute().use { it.body?.string() ?: return emptyList() }
            parseHtml(body, source)
        } catch (e: Exception) {
            Log.w("TelegramParser", "${source.channel}: ${e.message}")
            emptyList()
        }
    }

    private fun parseHtml(html: String, source: TelegramSource): List<NewsItem> {
        val items = mutableListOf<NewsItem>()

        // Ищем блоки постов
        val postPattern = Regex(
            """<div class="tgme_widget_message_bubble">.*?</div>\s*</div>""",
            setOf(RegexOption.DOT_MATCHES_ALL)
        )

        // Упрощённый парсинг: берём тексты и даты
        val textPattern = Regex(
            """<div class="tgme_widget_message_text[^"]*"[^>]*>(.*?)</div>""",
            setOf(RegexOption.DOT_MATCHES_ALL)
        )
        val datePattern = Regex("""datetime="([^"]+)"""")
        val imgPattern  = Regex("""background-image:url\('([^']+)'\)""")
        val linkPattern = Regex("""href="(https://t\.me/${source.channel}/(\d+))"""")

        // Парсим ссылки на посты с их номерами
        val posts = linkPattern.findAll(html).toList()
        val texts = textPattern.findAll(html).toList()
        val dates = datePattern.findAll(html).toList()
        val imgs  = imgPattern.findAll(html).toList()

        val count = minOf(posts.size, texts.size, dates.size, 10) // берём до 10 постов
        for (i in 0 until count) {
            try {
                val rawText = texts[i].groupValues[1]
                val cleanText = stripHtml(rawText).trim()
                if (cleanText.length < 20) continue // слишком короткий пост

                val url   = posts[i].groupValues[1]
                val dateStr = dates.getOrNull(i)?.groupValues?.get(1) ?: continue
                val imgUrl = imgs.getOrNull(i)?.groupValues?.get(1)

                val publishedAt = parseDate(dateStr)
                val title = cleanText.take(120)
                val summary = cleanText.take(300)

                // Определяем срочность
                val isUrgent = source.category == "URGENT" ||
                    cleanText.contains(Regex("СРОЧНО|BREAKING|⚡", RegexOption.IGNORE_CASE))
                val finalCategory = if (isUrgent) "URGENT" else source.category
                val finalPriority = if (isUrgent) 2 else source.priority

                items.add(NewsItem(
                    url = url,
                    title = title,
                    summary = summary,
                    imageUrl = imgUrl,
                    source = "@${source.channel}",
                    category = finalCategory,
                    publishedAt = publishedAt,
                    priority = finalPriority
                ))
            } catch (e: Exception) { /* skip bad post */ }
        }

        return items
    }

    private fun stripHtml(html: String): String {
        return html
            .replace(Regex("<br\\s*/?>"), "\n")
            .replace(Regex("<[^>]+>"), "")
            // Именованные HTML-сущности
            .replace("&amp;", "&")
            .replace("&lt;", "<")
            .replace("&gt;", ">")
            .replace("&nbsp;", " ")
            .replace("&#39;", "'")
            .replace("&apos;", "'")
            .replace("&quot;", "\"")
            .replace("&laquo;", "«")
            .replace("&raquo;", "»")
            .replace("&mdash;", "—")
            .replace("&ndash;", "–")
            .replace("&hellip;", "…")
            // Числовые сущности &#33; → ! (и т.д.)
            .replace(Regex("&#(\\d+);")) { mr ->
                mr.groupValues[1].toIntOrNull()?.toChar()?.toString() ?: mr.value
            }
            // Hex сущности &#x21; → !
            .replace(Regex("&#x([0-9a-fA-F]+);")) { mr ->
                mr.groupValues[1].toInt(16).toChar().toString()
            }
            .trim()
    }

    private fun parseDate(iso: String): Long {
        return try {
            java.time.Instant.parse(iso).toEpochMilli()
        } catch (e: Exception) {
            System.currentTimeMillis()
        }
    }
}
