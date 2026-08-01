package com.mirlanmamytov.ticker247.network

import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray

/**
 * Клиентская подстраховка для кнопки «Перевести» в читалке — на случай если
 * бэкенд не смог перевести статью (сбой эндпоинта, квота, редкий язык).
 * Тот же бесплатный gtx-эндпоинт, что использует бэкенд.
 */
object OnDeviceTranslator {
    private val client = OkHttpClient.Builder().build()

    suspend fun translate(text: String, targetLang: String): String? {
        if (text.isBlank()) return null
        return try {
            val url = "https://translate.googleapis.com/translate_a/single".toHttpUrl()
                .newBuilder()
                .addQueryParameter("client", "gtx")
                .addQueryParameter("sl", "auto")
                .addQueryParameter("tl", targetLang)
                .addQueryParameter("dt", "t")
                .addQueryParameter("q", text.take(1500))
                .build()
            val request = Request.Builder().url(url).build()
            client.newCall(request).execute().use { resp ->
                if (!resp.isSuccessful) return null
                val body = resp.body?.string() ?: return null
                val segments = JSONArray(body).getJSONArray(0)
                buildString {
                    for (i in 0 until segments.length()) {
                        val seg = segments.optJSONArray(i) ?: continue
                        append(seg.optString(0, ""))
                    }
                }.trim().takeIf { it.isNotEmpty() }
            }
        } catch (_: Exception) {
            null
        }
    }
}
