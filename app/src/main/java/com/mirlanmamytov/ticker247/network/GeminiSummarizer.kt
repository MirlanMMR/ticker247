package com.mirlanmamytov.ticker247.network

import android.util.Log
import com.mirlanmamytov.ticker247.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.util.concurrent.TimeUnit

/**
 * Gemini 1.5 Flash — краткое резюме новости по трём вопросам: ЧТО / ГДЕ / КОГДА
 *
 * Бесплатный tier: 15 запросов/минуту, 1M токенов/день
 * Ключ: добавь GEMINI_API_KEY=... в local.properties
 * Получить ключ: https://aistudio.google.com/apikey (бесплатно, без карты)
 */
object GeminiSummarizer {

    private const val TAG = "GeminiAI"
    private const val MODEL = "gemini-1.5-flash"
    private val API_URL get() = "https://generativelanguage.googleapis.com/v1beta/models/$MODEL:generateContent?key=${BuildConfig.GEMINI_API_KEY}"

    // Кэш: url → результат (не вызываем AI дважды для одной новости)
    private val cache = HashMap<String, AiSummary>(64)

    private val client = OkHttpClient.Builder()
        .connectTimeout(8, TimeUnit.SECONDS)
        .readTimeout(12, TimeUnit.SECONDS)
        .build()

    data class AiSummary(
        val what: String,   // ЧТО произошло
        val where: String,  // ГДЕ
        val `when`: String, // КОГДА
        val brief: String   // Одно предложение — суть без воды
    )

    /**
     * Возвращает AI-резюме или null если:
     * - нет ключа API
     * - статья слишком короткая (заголовок сам по себе уже понятен)
     * - API недоступен
     */
    suspend fun summarize(title: String, body: String, url: String): AiSummary? {
        val apiKey = BuildConfig.GEMINI_API_KEY
        if (apiKey.isBlank()) {
            Log.w(TAG, "No API key. Add GEMINI_API_KEY to local.properties")
            return null
        }

        // Кэш-хит
        cache[url]?.let { return it }

        // Если статья пустая — нечего суммаризировать
        val combined = "$title\n$body".trim()
        if (combined.length < 80) return null

        return withContext(Dispatchers.IO) {
            try {
                val prompt = """
                    Ты редактор новостей. Прочитай новость и ответь строго в формате JSON:
                    {
                      "what": "Что произошло — одно предложение, суть события",
                      "where": "Где произошло — страна/город или 'не указано'",
                      "when": "Когда — дата/время или 'не указано'",
                      "brief": "Краткое резюме всей новости в 1-2 предложения без воды"
                    }

                    Новость:
                    $combined

                    Отвечай только JSON, без пояснений. На русском языке.
                """.trimIndent()

                val requestJson = JSONObject().apply {
                    put("contents", org.json.JSONArray().apply {
                        put(JSONObject().apply {
                            put("parts", org.json.JSONArray().apply {
                                put(JSONObject().put("text", prompt))
                            })
                        })
                    })
                    put("generationConfig", JSONObject().apply {
                        put("temperature", 0.2)       // низкая — факты, не фантазии
                        put("maxOutputTokens", 256)    // краткость
                        put("responseMimeType", "application/json")
                    })
                }

                val request = Request.Builder()
                    .url(API_URL)
                    .post(requestJson.toString().toRequestBody("application/json".toMediaType()))
                    .build()

                val responseBody = client.newCall(request).execute().use { resp ->
                    if (!resp.isSuccessful) {
                        Log.w(TAG, "API error ${resp.code}")
                        return@withContext null
                    }
                    resp.body?.string() ?: return@withContext null
                }

                // Парсим ответ Gemini
                val root = JSONObject(responseBody)
                val text = root
                    .getJSONArray("candidates")
                    .getJSONObject(0)
                    .getJSONObject("content")
                    .getJSONArray("parts")
                    .getJSONObject(0)
                    .getString("text")

                val json = JSONObject(text)
                val summary = AiSummary(
                    what  = json.optString("what",  "").ifBlank { title },
                    where = json.optString("where", "не указано"),
                    `when` = json.optString("when", "не указано"),
                    brief = json.optString("brief", "").ifBlank { body.take(200) }
                )

                cache[url] = summary
                Log.d(TAG, "Summarized: ${summary.brief.take(60)}")
                summary

            } catch (e: Exception) {
                Log.w(TAG, "Summarize failed: ${e.message}")
                null
            }
        }
    }

    fun clearCache() = cache.clear()
}
