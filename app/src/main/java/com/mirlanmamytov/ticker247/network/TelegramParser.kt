package com.mirlanmamytov.ticker247.network

import android.util.Log
import com.mirlanmamytov.ticker247.data.model.NewsItem
import com.mirlanmamytov.ticker247.util.UserLocale
import okhttp3.OkHttpClient
import okhttp3.Request
import org.jsoup.Jsoup
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

        // Парсим каждый пост как изолированный блок через Jsoup
        val doc = org.jsoup.Jsoup.parse(html)
        val postBlocks = doc.select("div.tgme_widget_message_wrap")

        for (block in postBlocks.takeLast(10)) {
            try {
                // Текст поста
                val textEl = block.selectFirst("div.tgme_widget_message_text") ?: continue
                val rawHtml = textEl.html()
                    .replace(Regex("<br\\s*/?>", RegexOption.IGNORE_CASE), "\n")
                val cleanText = stripHtml(rawHtml).trim()
                if (cleanText.length < 20) continue

                // Ссылка на пост в Telegram
                val telegramUrl = block.selectFirst("a.tgme_widget_message_date")
                    ?.attr("href") ?: continue

                // Внешняя ссылка — только из link_preview карточки
                val externalUrl = block.selectFirst("a.tgme_widget_message_link_preview")
                    ?.attr("href")?.takeIf { it.startsWith("http") && !it.contains("t.me") }

                val url = externalUrl ?: telegramUrl

                // Дата
                val dateStr = block.selectFirst("time[datetime]")
                    ?.attr("datetime") ?: continue

                // Фото — background-image внутри этого поста
                val imgUrl = block.select("[style*=background-image]")
                    .firstOrNull()
                    ?.attr("style")
                    ?.let { Regex("""url\('([^']+)'\)""").find(it)?.groupValues?.get(1) }

                // Просмотры
                val viewStr = block.selectFirst("span.tgme_widget_message_views")?.text() ?: "0"
                val telegramViews = parseTgViews(viewStr)

                // Чистим текст — убираем URL, хэштеги, призывы к подписке, лишние эмодзи в начале
                val urlRegex = Regex("https?://\\S+")
                val hashtagRegex = Regex("#\\w+")
                val selfPromo = Regex(
                    "(подпис|поделис|подпишись|читайте|читать далее|следите|наш канал|наш telegram|@\\w+\\s*$|>>>|<<<)",
                    RegexOption.IGNORE_CASE
                )
                val textWithoutUrls = cleanText
                    .replace(urlRegex, "")
                    .replace(hashtagRegex, "")
                    .split("\n")
                    .filterNot { line -> selfPromo.containsMatchIn(line) }
                    .joinToString("\n")
                    .trim()
                if (textWithoutUrls.length < 20) continue

                val publishedAt = parseDate(dateStr)

                // Заголовок = первое предложение, убираем ведущие эмодзи и спецсимволы
                val sentenceEnd = Regex("(?<=[.!?])\\s")
                val sentences = textWithoutUrls.split(sentenceEnd)
                val rawTitle = sentences.firstOrNull()?.trim() ?: textWithoutUrls.take(150)
                val title = rawTitle
                    .replace(Regex("^[\\p{So}\\p{Sk}\\s·\\-▶]+"), "")
                    .trim()
                    .take(150)
                    .ifEmpty { rawTitle.take(150) }
                // Тело = всё начиная со второго предложения
                val body = if (sentences.size > 1)
                    sentences.drop(1).joinToString(" ").trim()
                else
                    textWithoutUrls
                val summary = body

                // Пустышка: тело отсутствует или совпадает с заголовком
                val hasMeaningfulBody = summary.isNotBlank() &&
                    summary.trimEnd('.', ' ').lowercase() != title.trimEnd('.', ' ').lowercase() &&
                    summary.length > 30

                if (!hasMeaningfulBody) {
                    // Без тела допускаем только если есть внешняя ссылка —
                    // TikTokReader загрузит полный текст через ArticleExtractor.
                    // Нет тела + нет ссылки = пустышка, выбрасываем всегда.
                    if (externalUrl == null) continue
                }

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
                    "STARS"   to Regex("звезда|знаменит|шоубиз|певец|певица|актёр|актриса|клип|концерт|альбом|ырчы|артист", RegexOption.IGNORE_CASE),
                    "HEALTH"  to Regex("здоровь|медицин|врач|болезн|лечен|диет|питани|витамин|психолог|стресс|вакцин", RegexOption.IGNORE_CASE),
                    "MONEY"   to Regex("зарплат|доход|бюджет|инвестиц|кредит|ипотек|инфляц|курс|налог|пенси|финансов", RegexOption.IGNORE_CASE),
                    "LIFE"    to Regex("лайфхак|совет|рецепт|готовим|уборк|отношени|воспитани|привычк|мотивац|экономи", RegexOption.IGNORE_CASE),
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

                // Совпадение с Google Trends KG
                val trending = TrendingFetcher.isTrending(title)

                items.add(NewsItem(
                    url = url,
                    title = title,
                    summary = summary,
                    imageUrl = imgUrl,
                    source = "@${source.channel}",
                    category = finalCategory,
                    publishedAt = publishedAt,
                    priority = finalPriority,
                    telegramViews = telegramViews,
                    isTrending = trending
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

    /** Конвертирует строку просмотров Telegram "12.5K" / "1.2M" / "895" → Int */
    private fun parseTgViews(s: String): Int {
        return try {
            when {
                s.endsWith("K", ignoreCase = true) -> (s.dropLast(1).toDouble() * 1_000).toInt()
                s.endsWith("M", ignoreCase = true) -> (s.dropLast(1).toDouble() * 1_000_000).toInt()
                else -> s.replace(",", "").trim().toIntOrNull() ?: 0
            }
        } catch (e: Exception) { 0 }
    }

    private fun parseDate(iso: String): Long {
        return try {
            val sdf = java.text.SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", java.util.Locale.ENGLISH)
            sdf.parse(iso)?.time ?: System.currentTimeMillis()
        } catch (e: Exception) {
            System.currentTimeMillis()
        }
    }
}
