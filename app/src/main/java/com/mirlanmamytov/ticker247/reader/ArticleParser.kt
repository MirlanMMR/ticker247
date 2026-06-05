package com.mirlanmamytov.ticker247.reader

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jsoup.Jsoup

data class Article(
    val title: String,
    val imageUrl: String?,
    val text: String,
    val source: String,
    val url: String
)

object ArticleParser {

    suspend fun parse(url: String, fallbackTitle: String, fallbackSource: String): Article? =
        withContext(Dispatchers.IO) {
            try {
                val doc = Jsoup.connect(url)
                    .userAgent("Mozilla/5.0 (Linux; Android 13) AppleWebKit/537.36")
                    .timeout(10000)
                    .get()

                val title = doc.selectFirst("h1")?.text()
                    ?: doc.selectFirst("meta[property=og:title]")?.attr("content")
                    ?: fallbackTitle

                val imageUrl = doc.selectFirst("meta[property=og:image]")?.attr("content")
                    ?: doc.selectFirst("article img")?.attr("abs:src")

                // Удаляем мусор
                doc.select("script, style, nav, header, footer, aside, .ads, .comments, .social, .share, .related").remove()

                // Ищем основной текст
                val articleEl = doc.selectFirst("article")
                    ?: doc.selectFirst("[class*=article]")
                    ?: doc.selectFirst("[class*=content]")
                    ?: doc.selectFirst("main")
                    ?: doc.body()

                val paragraphs = articleEl.select("p")
                    .map { it.text().trim() }
                    .filter { it.length > 50 }
                    .take(20)

                val text = paragraphs.joinToString("\n\n")

                if (text.isEmpty()) return@withContext null

                Article(
                    title = title,
                    imageUrl = imageUrl,
                    text = text,
                    source = fallbackSource,
                    url = url
                )
            } catch (e: Exception) {
                null
            }
        }
}
