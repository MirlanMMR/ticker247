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

    // t.me блокируют операторы в ряде стран (КГ в т.ч.) — пробуем зеркала
    private val TG_DOMAINS = listOf("t.me", "telegram.me", "telegram.dog")

    /** Парсит публичный канал через t.me или зеркала → список NewsItem */
    fun fetchChannel(source: TelegramSource): List<NewsItem> {
        for (domain in TG_DOMAINS) {
            try {
                val url = "https://$domain/s/${source.channel}"
                val req = Request.Builder().url(url).build()
                val body = client.newCall(req).execute().use { it.body?.string() } ?: continue
                val items = parseHtml(body, source)
                // Пустой список при живом домене = в канале нет постов; но если
                // страница без разметки постов (блок/редирект) — пробуем зеркало
                if (body.contains("tgme_widget_message_wrap") || domain == TG_DOMAINS.last()) {
                    return items
                }
            } catch (e: Exception) {
                Log.w("TelegramParser", "${source.channel} via $domain: ${e.message}")
            }
        }
        return emptyList()
    }

    private fun parseHtml(html: String, source: TelegramSource): List<NewsItem> {
        val items = mutableListOf<NewsItem>()
        // Директивы «#тема: ...» и «#удалить: ...» — собираем и регистрируем в конце
        val foundTopics = mutableListOf<Pair<String, Long>>()
        val foundRemovals = mutableListOf<Pair<String, Long>>()

        // Парсим каждый пост как изолированный блок через Jsoup
        val doc = org.jsoup.Jsoup.parse(html)
        val postBlocks = doc.select("div.tgme_widget_message_wrap")

        // 30 постов: длинные посты (#7д) не должны вытесняться свежими из окна
        for (block in postBlocks.takeLast(30)) {
            try {
                // Текст поста
                val textEl = block.selectFirst("div.tgme_widget_message_text") ?: continue
                val rawHtml = textEl.html()
                    .replace(Regex("<br\\s*/?>", RegexOption.IGNORE_CASE), "\n")
                val cleanText = stripHtml(rawHtml).trim()
                if (cleanText.length < 20) continue

                // #удалить БЕЗ двоеточия, дописанный к самому посту (редактированием) —
                // скрыть из ленты именно ЭТУ новость. Работает и на архивных постах бота
                if (source.priority >= 10 &&
                    Regex("#удалить\\b(?!\\s*:)|#remove\\b(?!\\s*:)", RegexOption.IGNORE_CASE).containsMatchIn(cleanText)
                ) {
                    // Цель блокировки — первая строка поста (заголовок), без ссылок и тегов
                    val target = cleanText
                        .replace(Regex("https?://\\S+"), "")
                        .replace(Regex("#[\\wа-яё]+", RegexOption.IGNORE_CASE), "")
                        .lines().firstOrNull { it.trim().length > 10 }?.trim()?.take(120)
                    if (target != null) {
                        foundRemovals.add(target to (System.currentTimeMillis() + 3 * 24 * 3_600_000L))
                    }
                    continue
                }

                // Архивные посты бота (новости из тикера, запощенные бэкендом в канал)
                // имеют подпись «📲 @t247feed…» — их обратно в приложение не берём.
                // Ручные редакторские посты подписи не имеют и получают высший приоритет.
                if (cleanText.contains("📲 @t247feed")) continue

                // #скрыто / #hide — служебный пост только для Telegram
                // (закрепы, объявления канала) — в ленту приложения не берём
                if (Regex("#скрыто|#hide", RegexOption.IGNORE_CASE).containsMatchIn(cleanText)) continue

                // ── Директива «#тема: ...» — редакторская повестка ──────────
                // Пост-директива НЕ новость: темы регистрируются, пост в ленту не идёт.
                // Время жизни темы: #Nд/#Nч в том же посте, по умолчанию 3 дня.
                val topicMatches = Regex("#тема\\s*:\\s*([^#\\n]+)|#topic\\s*:\\s*([^#\\n]+)", RegexOption.IGNORE_CASE)
                    .findAll(cleanText).toList()
                val removalMatches = Regex("#удалить\\s*:\\s*([^#\\n]+)|#remove\\s*:\\s*([^#\\n]+)", RegexOption.IGNORE_CASE)
                    .findAll(cleanText).toList()
                if (topicMatches.isNotEmpty() || removalMatches.isNotEmpty()) {
                    val postDate = block.selectFirst("time[datetime]")?.attr("datetime")
                        ?.let { runCatching { java.time.OffsetDateTime.parse(it).toInstant().toEpochMilli() }.getOrNull() }
                        ?: System.currentTimeMillis()
                    val life = Regex("#(\\d{1,2})\\s?(д|d|ч|h)\\b", RegexOption.IGNORE_CASE)
                        .find(cleanText)?.let { m ->
                            val n = m.groupValues[1].toLongOrNull() ?: return@let null
                            val unit = if (m.groupValues[2].lowercase() in setOf("д", "d")) 24 * 3_600_000L else 3_600_000L
                            n * unit
                        } ?: (3 * 24 * 3_600_000L)
                    topicMatches.forEach { m ->
                        val topic = (m.groupValues[1].ifEmpty { m.groupValues[2] }).trim(' ', ',', '.', '-')
                        if (topic.length >= 3) foundTopics.add(topic to (postDate + life))
                    }
                    removalMatches.forEach { m ->
                        val target = (m.groupValues[1].ifEmpty { m.groupValues[2] }).trim(' ', ',', '.', '-')
                        if (target.length >= 3) foundRemovals.add(target to (postDate + life))
                    }
                    continue  // директива — не новость
                }

                // Ссылка на пост в Telegram
                val telegramUrl = block.selectFirst("a.tgme_widget_message_date")
                    ?.attr("href") ?: continue

                // Внешняя ссылка — из link_preview карточки, а если Telegram
                // не создал превью — прямо из текста поста
                val externalUrl = block.selectFirst("a.tgme_widget_message_link_preview")
                    ?.attr("href")?.takeIf { it.startsWith("http") && !it.contains("t.me") }
                    ?: Regex("https?://\\S+").findAll(cleanText)
                        .map { it.value.trimEnd(')', ']', '.', ',') }
                        .firstOrNull { !it.contains("t.me") }

                val url = externalUrl ?: telegramUrl

                // Ссылка на видео (YouTube/TikTok/Instagram/VK) — редактор делится
                // интересным видео: YouTube откроется во встроенном плеере,
                // остальные — в родном приложении платформы
                val videoHosts = listOf("youtube.com/watch", "youtu.be/", "youtube.com/shorts",
                    "youtube.com/live", "tiktok.com/", "instagram.com/reel", "instagram.com/p/", "vk.com/video")
                val isVideoLink = externalUrl != null && videoHosts.any { externalUrl.contains(it) }

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
                val hashtagRegex = Regex("#[\\wа-яё]+", RegexOption.IGNORE_CASE)
                val selfPromo = Regex(
                    "(подпис|поделис|подпишись|читайте|читать далее|следите|наш канал|наш telegram|@\\w+\\s*$|>>>|<<<)",
                    RegexOption.IGNORE_CASE
                )
                val textWithoutUrls = cleanText
                    .replace(urlRegex, "")
                    // Директивы с текстом («#метка: видео дня», пробел у двоеточия допустим)
                    .replace(Regex("#(метка|label|тема|topic)\\s*:\\s*[^#\\n]+", RegexOption.IGNORE_CASE), "")
                    .replace(hashtagRegex, "")
                    .split("\n")
                    .filterNot { line -> selfPromo.containsMatchIn(line) }
                    .joinToString("\n")
                    .trim()
                // Видео-пост может быть совсем без описания — одни хэштеги и ссылка
                if (textWithoutUrls.length < 20 && !isVideoLink) continue

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
                    .ifEmpty { if (isVideoLink) "🎬 Видео" else "" }
                if (title.isEmpty()) continue
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

                // ── Редакторские маркеры-хэштеги ─────────────────────────────
                // #срочно → уведомление + тикер первым; #важно → тикер ⚡;
                // #карусель → hero-карусель; #3д/#12ч → время жизни поста
                val tagUrgent    = Regex("#срочно|#urgent", RegexOption.IGNORE_CASE).containsMatchIn(cleanText)
                val tagImportant = Regex("#важно|#important", RegexOption.IGNORE_CASE).containsMatchIn(cleanText)
                val tagCarousel  = Regex("#карусель|#carousel", RegexOption.IGNORE_CASE).containsMatchIn(cleanText)
                // #метка: <текст> — произвольный бейдж редактора (пробел у двоеточия допустим)
                val editorLabel = Regex("#метка\\s*:\\s*([^#\\n]+)|#label\\s*:\\s*([^#\\n]+)", RegexOption.IGNORE_CASE)
                    .find(cleanText)?.let { m ->
                        (m.groupValues[1].ifEmpty { m.groupValues[2] })
                            .trim(' ', ',', '.', '-').take(24).uppercase()
                            .ifEmpty { null }
                    }
                val lifetimeMs = Regex("#(\\d{1,2})\\s?(д|d|ч|h)\\b", RegexOption.IGNORE_CASE)
                    .find(cleanText)?.let { m ->
                        val n = m.groupValues[1].toLongOrNull() ?: return@let null
                        val unit = if (m.groupValues[2].lowercase() in setOf("д", "d")) 24 * 3_600_000L else 3_600_000L
                        n * unit
                    }

                // Срочность — маркер редактора или ключевые слова (для всех каналов)
                val urgentKeywords = Regex(
                    "СРОЧНО|BREAKING|ЭКСТРЕННО|⚡|убит|убита|убили|взрыв|теракт|катастроф|крушение|землетрясение|захват|эвакуац",
                    RegexOption.IGNORE_CASE
                )
                val isUrgent = tagUrgent || cleanText.contains(urgentKeywords)

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
                    isTrending = trending,
                    isVideo = isVideoLink,
                    isEditorImportant = tagImportant,
                    isEditorCarousel = tagCarousel,
                    editorLabel = editorLabel,
                    expiresAt = lifetimeMs?.let { publishedAt + it }
                ))
            } catch (e: Exception) { /* skip bad post */ }
        }

        // Регистрируем темы и блокировки (пустой список очищает устаревшие)
        if (source.priority >= 10) {  // только редакторские каналы управляют лентой
            com.mirlanmamytov.ticker247.util.EditorialTopics.update(source.channel, foundTopics)
            com.mirlanmamytov.ticker247.util.EditorialTopics.updateRemovals(source.channel, foundRemovals)
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
