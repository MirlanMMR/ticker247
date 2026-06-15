package com.mirlanmamytov.ticker247.reader

import android.util.Log
import okhttp3.OkHttpClient
import okhttp3.Request
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import java.util.concurrent.TimeUnit

data class ArticleContent(
    val title: String,
    val body: String,           // чистый текст
    val bodyHtml: String,       // форматированный HTML для отображения
    val coverImage: String?,
    val author: String?,
    val publishedDate: String?,
    val siteName: String?,
    val readTimeMin: Int
)

object ArticleExtractor {

    private val client = OkHttpClient.Builder()
        .connectTimeout(12, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .followRedirects(true)
        .addInterceptor { chain ->
            val req = chain.request().newBuilder()
                .header("User-Agent", "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 Chrome/120")
                .header("Accept", "text/html,application/xhtml+xml")
                .header("Accept-Language", "ru-RU,ru;q=0.9,en;q=0.8")
                .build()
            chain.proceed(req)
        }
        .build()

    suspend fun extract(url: String): Result<ArticleContent> = runCatching {
        // Телеграм-посты — особый путь
        if (url.contains("t.me/")) {
            return@runCatching extractTelegram(url)
        }

        val html = fetchHtml(url)
        val doc = Jsoup.parse(html, url)

        val title = extractTitle(doc)
        val cover = extractCoverImage(doc)
        val author = extractAuthor(doc)
        val date = extractDate(doc)
        val siteName = extractSiteName(doc, url)
        val (body, bodyHtml) = extractBody(doc)
        val readTime = maxOf(1, body.split(" ").size / 200) // ~200 слов/мин

        ArticleContent(
            title = title,
            body = body,
            bodyHtml = bodyHtml,
            coverImage = cover,
            author = author,
            publishedDate = date,
            siteName = siteName,
            readTimeMin = readTime
        )
    }

    /** Для Телеграм-постов контент уже есть — просто форматируем */
    fun fromNewsItem(
        title: String,
        summary: String,
        imageUrl: String?,
        source: String,
        publishedAt: Long
    ): ArticleContent {
        val date = java.text.SimpleDateFormat("d MMMM yyyy, HH:mm",
            java.util.Locale("ru")).format(java.util.Date(publishedAt))
        val readTime = maxOf(1, summary.split(" ").size / 200)
        val bodyHtml = summary
            .split("\n\n").joinToString("") { "<p>${it.trim()}</p>" }
            .replace("\n", "<br>")
        return ArticleContent(
            title = title,
            body = summary,
            bodyHtml = bodyHtml,
            coverImage = imageUrl,
            author = null,
            publishedDate = date,
            siteName = source,
            readTimeMin = readTime
        )
    }

    private fun fetchHtml(url: String): String {
        val req = Request.Builder().url(url).build()
        return client.newCall(req).execute().use { it.body?.string() ?: "" }
    }

    private fun extractTelegram(url: String): ArticleContent {
        val html = fetchHtml(url.replace("t.me/", "t.me/s/").also { Log.d("ArticleExtractor", "TG url: $it") })
        val doc = Jsoup.parse(html, url)
        val postText = doc.select(".tgme_widget_message_text").firstOrNull()?.wholeText() ?: ""
        val img = doc.select(".tgme_widget_message_photo_wrap").firstOrNull()
            ?.attr("style")?.let { Regex("url\\('([^']+)'\\)").find(it)?.groupValues?.get(1) }
        val date = doc.select("time").firstOrNull()?.attr("datetime") ?: ""
        val channel = url.split("/").getOrNull(url.split("/").size - 2) ?: "Telegram"
        val bodyHtml = postText.replace("\n\n", "<br><br>").replace("\n", "<br>")
        return ArticleContent(
            title = postText.take(100),
            body = postText,
            bodyHtml = "<p>$bodyHtml</p>",
            coverImage = img,
            author = null,
            publishedDate = date.take(10),
            siteName = "@$channel",
            readTimeMin = maxOf(1, postText.split(" ").size / 200)
        )
    }

    private fun extractTitle(doc: Document): String {
        return doc.select("meta[property=og:title]").firstOrNull()?.attr("content")
            ?: doc.select("meta[name=twitter:title]").firstOrNull()?.attr("content")
            ?: doc.select("h1").firstOrNull()?.text()
            ?: doc.title()
    }

    private fun extractCoverImage(doc: Document): String? {
        return doc.select("meta[property=og:image]").firstOrNull()?.attr("content")
            ?: doc.select("meta[name=twitter:image]").firstOrNull()?.attr("content")
    }

    private fun extractAuthor(doc: Document): String? {
        return doc.select("meta[name=author]").firstOrNull()?.attr("content")
            ?: doc.select("[rel=author]").firstOrNull()?.text()
            ?: doc.select(".author,.byline").firstOrNull()?.text()
    }

    private fun extractDate(doc: Document): String? {
        return doc.select("meta[property=article:published_time]").firstOrNull()?.attr("content")?.take(10)
            ?: doc.select("time").firstOrNull()?.attr("datetime")?.take(10)
    }

    private fun extractSiteName(doc: Document, url: String): String? {
        return doc.select("meta[property=og:site_name]").firstOrNull()?.attr("content")
            ?: runCatching { java.net.URL(url).host.removePrefix("www.") }.getOrNull()
    }

    private fun extractBody(doc: Document): Pair<String, String> {
        // Убираем мусор
        doc.select("script, style, nav, header, footer, aside, .ads, .ad, .advertisement, .social, .comments, .sidebar").remove()

        // Ищем основной контент по классам
        val contentEl = doc.select(
            "article, [class*=article-body], [class*=article__body], " +
            "[class*=content-body], [class*=post-body], [class*=entry-content], " +
            "[itemprop=articleBody], .article, .post, main"
        ).maxByOrNull { it.text().length } ?: doc.body()

        // Параграфы
        val spamPhrases = listOf(
            "забыл пароль", "регистрация", "войти", "чужой компьютер",
            "подписаться", "subscribe", "sign in", "log in", "cookie",
            "javascript", "включите js", "нажмите здесь", "читайте также",
            "поделиться", "добавить комментарий", "оставить комментарий"
        )
        val paragraphs = contentEl.select("p, h2, h3, h4, blockquote, li")
            .filter { el ->
                val t = el.text().trim()
                t.length > 20 && spamPhrases.none { t.lowercase().contains(it) }
            }

        val bodyHtml = paragraphs.joinToString("\n") { el ->
            when (el.tagName()) {
                "h2" -> "<h2>${el.text()}</h2>"
                "h3", "h4" -> "<h3>${el.text()}</h3>"
                "blockquote" -> "<blockquote>${el.text()}</blockquote>"
                "li" -> "<li>${el.text()}</li>"
                else -> "<p>${el.text()}</p>"
            }
        }
        val plainText = paragraphs.joinToString("\n\n") { it.text() }

        return plainText to bodyHtml
    }
}
