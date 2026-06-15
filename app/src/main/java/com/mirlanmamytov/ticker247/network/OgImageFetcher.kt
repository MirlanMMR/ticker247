package com.mirlanmamytov.ticker247.network

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import org.jsoup.Jsoup
import java.util.concurrent.TimeUnit

/**
 * Поиск релевантного фото для новости в три шага:
 *
 * 1. og:image со страницы статьи (фото именно этой новости)
 * 2. Wikipedia — ищем фото человека / места / события по заголовку
 * 3. Категорийный fallback — красивое тематическое фото по теме
 *
 * Всё бесплатно, без API-ключей.
 */
object OgImageFetcher {

    private const val TAG = "OgImageFetcher"
    private const val TIMEOUT_MS = 6_000

    // "" = уже проверяли, ничего нет — не запрашиваем снова
    private val cache = HashMap<String, String>(256)

    private val http = OkHttpClient.Builder()
        .connectTimeout(6, TimeUnit.SECONDS)
        .readTimeout(8, TimeUnit.SECONDS)
        .build()

    // ── Категорийные fallback-фото (Unsplash, стабильные прямые ссылки) ────────
    val CATEGORY_IMAGES = mapOf(
        "KG"       to "https://images.unsplash.com/photo-1586861635167-e5223aadc9fe?w=800&q=80",
        "URGENT"   to "https://images.unsplash.com/photo-1504711434969-e33886168f5c?w=800&q=80",
        "WORLD"    to "https://images.unsplash.com/photo-1529107386315-e1a2ed48a620?w=800&q=80",
        "SPORT"    to "https://images.unsplash.com/photo-1461896836934-ffe607ba8211?w=800&q=80",
        "CULTURE"  to "https://images.unsplash.com/photo-1514525253161-7a46d19cd819?w=800&q=80",
        "AUTO"     to "https://images.unsplash.com/photo-1492144534655-ae79c964c9d7?w=800&q=80",
        "FASHION"  to "https://images.unsplash.com/photo-1558618666-fcd25c85cd64?w=800&q=80",
        "TOURS"    to "https://images.unsplash.com/photo-1488085061387-422e29b40080?w=800&q=80",
        "NEWS"     to "https://images.unsplash.com/photo-1504711434969-e33886168f5c?w=800&q=80",
        "CRYPTO"   to "https://images.unsplash.com/photo-1518546305927-5a555bb7020d?w=800&q=80",
        "TECH"     to "https://images.unsplash.com/photo-1518770660439-4636190af475?w=800&q=80",
        "GOOD"     to "https://images.unsplash.com/photo-1521737711867-e3b97375f902?w=800&q=80",
        "STARS"    to "https://images.unsplash.com/photo-1511671782779-c97d3d27a1d4?w=800&q=80",
        "HEALTH"   to "https://images.unsplash.com/photo-1505751172876-fa1923c5c528?w=800&q=80",
        "MONEY"    to "https://images.unsplash.com/photo-1579621970563-ebec7560ff3e?w=800&q=80",
        "LIFE"     to "https://images.unsplash.com/photo-1484627147104-f5197bcd6651?w=800&q=80",
    )

    suspend fun fetch(articleUrl: String, title: String = "", category: String = ""): String? {
        if (articleUrl.isBlank() && title.isBlank()) return null

        val cacheKey = articleUrl.ifEmpty { title }
        cache[cacheKey]?.let { return it.ifEmpty { null } }

        return withContext(Dispatchers.IO) {
            val isTelegram = articleUrl.contains("t.me/")

            // ── Шаг 1: og:image — только для внешних URL, не Telegram ──────
            if (!isTelegram && articleUrl.isNotBlank()) {
                val ogImage = fetchOgImage(articleUrl)
                if (ogImage != null) {
                    cache[cacheKey] = ogImage
                    return@withContext ogImage
                }
            }

            // Нет og:image — возвращаем null, UI покажет цветной placeholder с эмодзи категории
            cache[cacheKey] = ""
            null
        }
    }

    // Домены которые возвращают видео-кадры или некачественные превью вместо фото
    private val VIDEO_PREVIEW_DOMAINS = setOf(
        "mash.ru", "shot.ru", "360tv.ru", "ren.tv", "vk.com",
        "rutube.ru", "youtube.com", "youtu.be", "tiktok.com"
    )

    // ── og:image / twitter:image / первая картинка в теле статьи ────────────
    private fun fetchOgImage(url: String): String? {
        // Для видео-ориентированных источников og:image — это скриншот видео, не фото
        val host = try { java.net.URI(url).host?.removePrefix("www.") ?: "" } catch (_: Exception) { "" }
        if (VIDEO_PREVIEW_DOMAINS.any { host.endsWith(it) }) return null

        return try {
            val doc = Jsoup.connect(url)
                .userAgent("Mozilla/5.0 (compatible; Ticker247Bot/1.0)")
                .timeout(TIMEOUT_MS)
                .get()

            doc.select("meta[property=og:image]").attr("content").ifEmpty { null }
                ?: doc.select("meta[name=twitter:image]").attr("content").ifEmpty { null }
                ?: doc.select("article img, .article img, .news-body img, .entry-content img")
                    .firstOrNull()?.attr("abs:src")?.ifEmpty { null }
        } catch (e: Exception) {
            null
        }?.trim()?.takeIf { it.startsWith("http") }
    }

    // ── Wikipedia REST API — ищет фото главного субъекта заголовка ──────────
    // Сначала рунет (ru.wikipedia), потом английская версия
    private fun fetchWikipediaImage(title: String): String? {
        // Берём первые 4 слова — обычно имя человека / название события
        val query = title
            .replace(Regex("""[«»„“”—–"]"""), "")
            .split(" ")
            .take(4)
            .joinToString("+")

        for (lang in listOf("ru", "en", "ky")) {
            try {
                val url = "https://$lang.wikipedia.org/w/api.php" +
                    "?action=query&generator=search&gsrsearch=${query}" +
                    "&gsrlimit=1&prop=pageimages&piprop=thumbnail&pithumbsize=600" +
                    "&format=json&origin=*"

                val req = Request.Builder().url(url)
                    .header("User-Agent", "Ticker247Bot/1.0")
                    .build()

                val body = http.newCall(req).execute().use { it.body?.string() } ?: continue
                val pages = JSONObject(body)
                    .optJSONObject("query")
                    ?.optJSONObject("pages") ?: continue

                val pageId = pages.keys().asSequence().firstOrNull() ?: continue
                val thumb = pages.optJSONObject(pageId)
                    ?.optJSONObject("thumbnail")
                    ?.optString("source")

                if (!thumb.isNullOrBlank()) {
                    Log.d(TAG, "Wikipedia image found ($lang): ${thumb.take(60)}")
                    // Просим бо́льший размер — заменяем /NNpx- на /600px-
                    return thumb.replace(Regex("/\\d+px-"), "/600px-")
                }
            } catch (e: Exception) {
                Log.w(TAG, "Wiki fetch failed ($lang): ${e.message}")
            }
        }
        return null
    }

    fun clearCache() = cache.clear()
}
