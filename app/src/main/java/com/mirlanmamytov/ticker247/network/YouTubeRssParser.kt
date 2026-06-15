package com.mirlanmamytov.ticker247.network

import android.util.Log
import com.mirlanmamytov.ticker247.data.model.NewsItem
import org.xmlpull.v1.XmlPullParser
import org.xmlpull.v1.XmlPullParserFactory
import java.net.URL
import java.text.SimpleDateFormat
import java.util.Locale

/**
 * Парсит публичный YouTube RSS без API-ключа.
 * URL: https://www.youtube.com/feeds/videos.xml?channel_id=CHANNEL_ID
 *
 * Возвращает NewsItem с:
 * - url → youtube.com/watch?v=...
 * - imageUrl → превью видео (maxresdefault.jpg)
 * - category → из SourceSelector
 * - source → название канала
 */
object YouTubeRssParser {

    private const val BASE_URL = "https://www.youtube.com/feeds/videos.xml?channel_id="
    private val TAG = "YouTubeRSS"

    fun fetchChannel(source: SourceSelector.ChannelSource): List<NewsItem> {
        val url = BASE_URL + source.handle
        return try {
            val xml = URL(url).readText()
            parseXml(xml, source)
        } catch (e: Exception) {
            Log.w(TAG, "Failed ${source.handle}: ${e.message}")
            emptyList()
        }
    }

    private fun parseXml(xml: String, source: SourceSelector.ChannelSource): List<NewsItem> {
        val items     = mutableListOf<NewsItem>()
        val factory   = XmlPullParserFactory.newInstance().apply { isNamespaceAware = true }
        val parser    = factory.newPullParser()
        parser.setInput(xml.reader())

        var channelName = source.handle
        var videoId     = ""
        var title       = ""
        var published   = ""
        var inEntry     = false

        var event = parser.eventType
        while (event != XmlPullParser.END_DOCUMENT) {
            when (event) {
                XmlPullParser.START_TAG -> when (parser.name) {
                    "title" -> {
                        val t = parser.nextText()
                        if (!inEntry) channelName = t else title = t
                    }
                    "yt:videoId" -> videoId = parser.nextText()
                    "published"  -> published = parser.nextText()
                    "entry"      -> inEntry = true
                }
                XmlPullParser.END_TAG -> {
                    if (parser.name == "entry" && title.isNotEmpty() && videoId.isNotEmpty()) {
                        val watchUrl   = "https://www.youtube.com/watch?v=$videoId"
                        val thumbnailUrl = "https://i.ytimg.com/vi/$videoId/hqdefault.jpg"
                        val publishedAt = parseDate(published)

                        items.add(NewsItem(
                            url        = watchUrl,
                            title      = title.trim(),
                            summary    = "",  // описание не в RSS, загружается при открытии
                            imageUrl   = thumbnailUrl,
                            source     = "@$channelName",
                            category   = source.category,
                            publishedAt = publishedAt,
                            priority   = source.priority,
                            isVideo    = true
                        ))
                        // Сброс
                        videoId = ""; title = ""; published = ""; inEntry = false
                    }
                }
            }
            event = parser.next()
        }
        Log.d(TAG, "YouTube @$channelName: ${items.size} videos")
        return items.take(5)  // максимум 5 последних видео на канал
    }

    private fun parseDate(dateStr: String): Long {
        return try {
            val sdf = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss+SS:SS", Locale.US)
            sdf.parse(dateStr)?.time ?: System.currentTimeMillis()
        } catch (e: Exception) {
            try {
                val sdf2 = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US)
                sdf2.parse(dateStr)?.time ?: System.currentTimeMillis()
            } catch (e2: Exception) {
                System.currentTimeMillis()
            }
        }
    }
}
