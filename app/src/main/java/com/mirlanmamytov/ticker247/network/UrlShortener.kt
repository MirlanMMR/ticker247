package com.mirlanmamytov.ticker247.network

import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request

/**
 * Сокращает длинную ссылку шаринга (docs/share/?title=...&body=...&img=...)
 * через бесплатный TinyURL API — без него мессенджеры (WhatsApp/Telegram)
 * показывают простыню из процент-закодированных символов вместо ссылки.
 */
object UrlShortener {
    private val client = OkHttpClient.Builder().build()

    suspend fun shorten(longUrl: String): String? {
        return try {
            val url = "https://tinyurl.com/api-create.php".toHttpUrl()
                .newBuilder()
                .addQueryParameter("url", longUrl)
                .build()
            val request = Request.Builder().url(url).build()
            client.newCall(request).execute().use { resp ->
                if (!resp.isSuccessful) return null
                resp.body?.string()?.trim()?.takeIf { it.startsWith("http") }
            }
        } catch (_: Exception) {
            null
        }
    }
}
