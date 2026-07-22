package com.mirlanmamytov.ticker247.network

import android.util.Log
import com.mirlanmamytov.ticker247.data.model.NewsItem
import okhttp3.OkHttpClient
import okhttp3.Request
import org.jsoup.Jsoup
import org.jsoup.parser.Parser
import java.time.format.DateTimeFormatter
import java.util.concurrent.TimeUnit

/**
 * Местные новости строго по СТРАНЕ устройства (Google News RSS).
 * Для пользователей вне Кыргызстана блок «Местные» наполняется новостями
 * их страны: 50% на госязыке + 50% на языке пула.
 * КГ-пользователи получают курируемые источники как раньше.
 */
object CountryNewsFetcher {

    private val client = OkHttpClient.Builder()
        .connectTimeout(8, TimeUnit.SECONDS)
        .readTimeout(10, TimeUnit.SECONDS)
        .addInterceptor { chain ->
            chain.proceed(chain.request().newBuilder()
                .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64)")
                .build())
        }
        .build()

    // Госязык по стране (для 50% местных на родном языке).
    // Для стран вне списка — только язык пула.
    private val STATE_LANG = mapOf(
        "UZ" to "uz", "KZ" to "kk", "BY" to "be", "TJ" to "tg", "AZ" to "az",
        "AM" to "hy", "GE" to "ka", "MD" to "ro", "UA" to "uk", "RU" to "ru",
        "TR" to "tr", "DE" to "de", "FR" to "fr", "IT" to "it", "PL" to "pl",
        "RS" to "sr", "BG" to "bg", "GR" to "el", "RO" to "ro", "HU" to "hu",
        "CZ" to "cs", "NL" to "nl", "SE" to "sv", "ID" to "id", "VN" to "vi",
        "TH" to "th", "JP" to "ja", "KR" to "ko", "IN" to "hi", "HT" to "fr"
    )

    private fun poolLang(): String {
        val lang = java.util.Locale.getDefault().language
        val cyrillic = setOf("ru", "ky", "kk", "uz", "tg", "be", "uk", "bg", "sr", "mk")
        return when {
            lang in cyrillic -> "ru"
            lang == "es" -> "es"
            lang == "pt" -> "pt"
            else -> "en"
        }
    }

    /** Страна устройства (ISO, напр. "UZ"); пустая строка если неизвестна */
    fun deviceCountry(): String = com.mirlanmamytov.ticker247.util.DeviceCountry.get()

    /**
     * Нужна ли замена местных: страна известна и это не Кыргызстан
     * (КГ — курируемое ядро, его источники лучше автоматики)
     */
    fun shouldReplaceLocals(): Boolean {
        val c = deviceCountry()
        return c.isNotEmpty() && c != "KG"
    }

    /**
     * Местные новости страны устройства: 50/50 госязык + язык пула.
     * Если у Google News нет редакции для страны (редирект на другую) —
     * фолбэк: поиск по названию страны на языке пула.
     */
    fun fetchCountryLocals(): List<NewsItem> {
        val country = deviceCountry()
        if (country.isEmpty()) return emptyList()
        val pool = poolLang()
        val state = STATE_LANG[country]

        val poolNews = fetchEdition(country, pool, 6)
        val stateNews = if (state != null && state != pool) fetchEdition(country, state, 6) else emptyList()

        // Чередуем: госязык и язык пула через один
        val mixed = mutableListOf<NewsItem>()
        val a = stateNews.iterator()
        val b = poolNews.iterator()
        while (a.hasNext() || b.hasNext()) {
            if (a.hasNext()) mixed.add(a.next())
            if (b.hasNext()) mixed.add(b.next())
        }
        if (mixed.isNotEmpty()) {
            Log.d("CountryNews", "$country: ${stateNews.size} гос + ${poolNews.size} пул")
            return mixed
        }

        // Редакции нет (KZ, BY, JM...) — поиск по названию страны на языке пула
        val countryName = java.util.Locale("", country)
            .getDisplayCountry(java.util.Locale(pool))
        if (countryName.isBlank()) return emptyList()
        val found = fetchSearch(countryName, pool)
        Log.d("CountryNews", "$country: редакции нет, поиск «$countryName» → ${found.size}")
        return found
    }

    /** Топ-новости редакции страны; пустой список если редакции нет (редирект) */
    private fun fetchEdition(country: String, lang: String, limit: Int): List<NewsItem> {
        return try {
            val url = "https://news.google.com/rss?gl=$country&hl=$lang&ceid=$country:$lang"
            client.newCall(Request.Builder().url(url).build()).execute().use { resp ->
                // Редирект на другую страну = редакции нет
                val finalUrl = resp.request.url.toString()
                if (!finalUrl.contains("gl=$country")) return emptyList()
                parseItems(resp.body?.string() ?: return emptyList(), limit)
            }
        } catch (e: Exception) {
            Log.w("CountryNews", "$country/$lang: ${e.message}")
            emptyList()
        }
    }

    /** Поиск новостей по запросу (для стран без своей редакции) */
    private fun fetchSearch(query: String, pool: String): List<NewsItem> {
        val (hl, gl, ceid) = when (pool) {
            "ru" -> Triple("ru", "RU", "RU:ru")
            "es" -> Triple("es-419", "MX", "MX:es-419")
            "pt" -> Triple("pt-BR", "BR", "BR:pt-419")
            else -> Triple("en-US", "US", "US:en")
        }
        return try {
            val url = "https://news.google.com/rss/search?q=${android.net.Uri.encode(query)}&hl=$hl&gl=$gl&ceid=$ceid"
            val xml = client.newCall(Request.Builder().url(url).build())
                .execute().use { it.body?.string() ?: return emptyList() }
            parseItems(xml, 10)
        } catch (e: Exception) {
            Log.w("CountryNews", "search «$query»: ${e.message}")
            emptyList()
        }
    }

    // Срочность: отключения, аварии, ЧС, спортивные победы — в СРОЧНО/ВАЖНО
    // (русский + английский + испанский/португальский базовые)
    private val urgentRegex = Regex(
        "отключ|авари|землетряс|наводнен|паводк|пожар|взрыв|теракт|стрельб|погиб|жертв|" +
        "эвакуац|чрезвычайн|крушени|обрушени|эпидеми|перекрыт|" +
        "earthquake|flood|wildfire|explosion|terror|shooting|killed|evacuat|emergency|crash|outbreak|" +
        "terremoto|inundaci|incendio|explosi|tiroteo|muert|evacuaci|emergencia|" +
        "чемпион|финал|золото|медаль|champion|final|gold medal|campeón|campeão|" +
        // Рост цен на жизненно важное — актуально всем странам одинаково
        "подорожал|дефицит хлеба|дефицит топлива|нехватка продуктов|цены взлетели|скачок цен|" +
        "price(s)? (rise|rose|surge|soar)|fuel shortage|food shortage|" +
        "suben los precios|escasez de (combustible|alimentos)|" +
        "preços? (sobem|disparam)|escassez de (combustível|alimentos)",
        RegexOption.IGNORE_CASE
    )

    private fun parseItems(xml: String, limit: Int): List<NewsItem> {
        val doc = Jsoup.parse(xml, "", Parser.xmlParser())
        val cutoff = System.currentTimeMillis() - 24 * 3_600_000L
        return doc.select("item").mapNotNull { item ->
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
            val title = rawTitle.substringBeforeLast(" - ").trim()
            if (title.length < 15) return@mapNotNull null

            val isUrgent = urgentRegex.containsMatchIn(title)
            NewsItem(
                url = link,
                title = title,
                summary = "",
                imageUrl = null,
                source = sourceName,
                category = if (isUrgent) "URGENT" else "NEWS",
                publishedAt = pubMillis,
                priority = if (isUrgent) 2 else 1,
                // "unknown" — проходит языковые фильтры любого пула
                // (госязык страны намеренно показывается вместе с языком пула)
                language = "unknown",
                scope = "local"
            )
        }.sortedByDescending { it.publishedAt }.take(limit)
    }
}
