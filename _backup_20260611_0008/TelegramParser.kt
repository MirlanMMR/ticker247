package com.mirlanmamytov.ticker247.network

import android.util.Log
import com.mirlanmamytov.ticker247.data.model.NewsItem
import com.mirlanmamytov.ticker247.util.UserLocale
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

    /**
     * Динамический список источников на основе локали телефона.
     * Вызывается каждый раз при старте fetch-цикла — автоматически
     * подбирает правильный микс для пользователя в любой точке мира.
     */
    fun getSources(): List<TelegramSource> {
        val userCtx = UserLocale.get()
        return SourceSelector.getTelegramSources(userCtx).map { src ->
            TelegramSource(src.handle, src.category, src.priority)
        }
    }

    // Статический список для обратной совместимости (используется если getSources() упадёт)
    val SOURCES_FALLBACK = listOf(
        TelegramSource("akipress",      "KG",      2),
        TelegramSource("kabar_news_kg", "KG",      2),
        TelegramSource("kyrgyzinform",  "KG",      1),
        TelegramSource("24kgnews",      "KG",      1),
        TelegramSource("tazabek",       "KG",      1),
        TelegramSource("breakingmash",  "URGENT",  2),
        TelegramSource("shot_shot",     "URGENT",  2),
        TelegramSource("bbcrussian",    "WORLD",   1),
        TelegramSource("meduzaio",      "WORLD",   1),
        TelegramSource("tvrain",        "WORLD",   1),
        TelegramSource("aljazeeraee",   "WORLD",   1),
        TelegramSource("inosmi",        "WORLD",   1),
        TelegramSource("inopressa",     "WORLD",   1),
        TelegramSource("sport24russia", "SPORT",   0),
        TelegramSource("kinopoisk",     "CULTURE", 0),
        TelegramSource("avtoradar",     "AUTO",    0),
        TelegramSource("buro247",       "FASHION", 0),
        TelegramSource("travel_kg",     "TOURS",   0),
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
                if (cleanText.length < 20) continue

                val url     = posts[i].groupValues[1]
                val dateStr = dates.getOrNull(i)?.groupValues?.get(1) ?: continue
                val imgUrl  = imgs.getOrNull(i)?.groupValues?.get(1)

                // Убираем URL-ссылки из текста поста (akipress и другие вставляют их в конец)
                val urlRegex = Regex("https?://\\S+")
                // Убираем URL и призывы к подписке
                val selfPromo = Regex(
                    "(подпис|поделис|подпишись|читайте|читать далее|следите|наш канал|наш telegram|@\\w+\\s*$|>>>|<<<)",
                    RegexOption.IGNORE_CASE
                )
                val textWithoutUrls = cleanText
                    .replace(urlRegex, "")
                    .split("\n")
                    .filterNot { line -> selfPromo.containsMatchIn(line) }
                    .joinToString("\n")
                    .trim()
                if (textWithoutUrls.length < 20) continue

                val publishedAt = parseDate(dateStr)

                // Заголовок = только первое предложение (до . ! ?)
                val sentenceEnd = Regex("(?<=[.!?])\\s")
                val sentences = textWithoutUrls.split(sentenceEnd)
                val title = sentences.firstOrNull()?.trim()?.take(150) ?: textWithoutUrls.take(120)
                // Тело = всё начиная со второго предложения
                val body = if (sentences.size > 1)
                    sentences.drop(1).joinToString(" ").trim()
                else
                    textWithoutUrls
                val summary = body

                // Срочность — только если в тексте есть ключевые слова (независимо от канала)
                val urgentKeywords = Regex(
                    "СРОЧНО|BREAKING|ЭКСТРЕННО|⚡|убит|убита|убили|взрыв|теракт|катастроф|крушение|землетрясение|захват|эвакуац",
                    RegexOption.IGNORE_CASE
                )
                val isUrgent = cleanText.contains(urgentKeywords)

                // 🏆 KG спорт → URGENT: наши атлеты победили — первыми в ленте везде в мире
                // Ищем сочетание "кыргыз*" + победа ИЛИ победа + "кыргыз*" в радиусе 80 символов
                val kgVictoryKeywords = Regex(
                    "(?:кыргызстан|кыргыз|кыргызча|кыргызского|отечествен|наш(?:а|и|их)? (?:атлет|спортсмен|команд|борец|бокс|дзюдо|курашист))" +
                    ".{0,80}" +
                    "(?:золото|серебро|бронза|победил|победила|чемпион|рекорд|медаль|выиграл|выиграла|призёр|финал|кубок)|" +
                    "(?:золото|серебро|бронза|победил|победила|чемпион|медаль|выиграл|выиграла|призёр)" +
                    ".{0,80}" +
                    "(?:кыргызстан|кыргыз|за Кыргызстан|сборн)",
                    RegexOption.IGNORE_CASE
                )
                val isKgVictory = source.category in setOf("KG", "SPORT") &&
                        cleanText.contains(kgVictoryKeywords)

                // Для нишевых категорий проверяем что контент соответствует теме
                val categoryKeywords = mapOf(
                    "TOURS"   to Regex("тур|отдых|отель|курорт|виза|перелёт|авиа|путешеств|билет|рейс|пляж|море|горы|Иссык|Бали|Турци|Дубай|Египет|отпуск", RegexOption.IGNORE_CASE),
                    "AUTO"    to Regex("авто|машин|автомобил|мотоцикл|двигател|колес|шин|руль|дтп|гаи|пробк|трафик|электрокар|tesla|BMW|Mercedes|Toyota", RegexOption.IGNORE_CASE),
                    "FASHION" to Regex("мод|стиль|одежд|бренд|коллекц|дизайнер|показ|тренд|outfit|fashion|beauty|красот|макияж", RegexOption.IGNORE_CASE),
                    "SPORT"   to Regex("матч|гол|победа|турнир|чемпионат|спорт|команда|игрок|тренер|лига|кубок|финал|олимпи", RegexOption.IGNORE_CASE),
                    "CULTURE" to Regex("фильм|кино|сериал|актёр|режиссёр|премьера|афиша|концерт|театр|выставк|музык|спектакль", RegexOption.IGNORE_CASE),
                )
                val baseCategory = if (source.category == "URGENT") "NEWS" else source.category
                val matchesCategory = categoryKeywords[baseCategory]?.containsMatchIn(textWithoutUrls) ?: true
                val resolvedCategory = if (!matchesCategory) "NEWS" else baseCategory
                // Приоритет: KG-победа > обычная срочность > остальное
                val finalCategory = when {
                    isKgVictory -> "URGENT"
                    isUrgent    -> "URGENT"
                    else        -> resolvedCategory
                }
                val finalPriority = when {
                    isKgVictory -> 3   // максимальный — победа наших!
                    isUrgent    -> 2
                    else        -> source.priority
                }

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
