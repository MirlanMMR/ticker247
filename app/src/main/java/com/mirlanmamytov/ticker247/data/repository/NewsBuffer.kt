package com.mirlanmamytov.ticker247.data.repository

import android.content.Context
import android.content.SharedPreferences
import com.mirlanmamytov.ticker247.data.model.NewsItem

/**
 * Instagram-like буфер новостей:
 * - Хранит до MAX_BUFFER свежих новостей
 * - Отслеживает "прочитанные" (scrolled past top)
 * - Прочитанные опускаются вниз, никогда не повторяются первыми
 * - Fetch каждые 5 минут добавляет новые, не дублируя старые
 */
object NewsBuffer {

    private const val MAX_BUFFER = 300          // сколько хранить в памяти
    private const val MAX_SEEN   = 2000         // сколько помнить "прочитанных"
    private const val PREFS_NAME = "news_buffer"
    private const val KEY_SEEN   = "seen_urls"

    private var prefs: SharedPreferences? = null
    private val seenUrls = mutableSetOf<String>()   // прочитанные URL
    private val buffer   = mutableListOf<NewsItem>() // весь буфер

    fun init(context: Context) {
        prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        // Загружаем историю прочитанных из SharedPrefs
        prefs?.getStringSet(KEY_SEEN, emptySet())?.let { seenUrls.addAll(it) }
    }

    /** Добавляем свежие новости в буфер (дедупликация по URL) */
    @Synchronized
    fun addItems(newItems: List<NewsItem>) {
        val existingUrls = buffer.map { it.url }.toSet()
        val fresh = newItems.filter { it.url.isNotEmpty() && it.url !in existingUrls }
        buffer.addAll(0, fresh) // добавляем в начало (самые свежие)
        // Ограничиваем размер буфера
        if (buffer.size > MAX_BUFFER) {
            buffer.subList(MAX_BUFFER, buffer.size).clear()
        }
    }

    /**
     * Возвращает список для отображения:
     * 1. Непрочитанные (новые) — сначала, по дате
     * 2. Прочитанные — в конце, по дате
     */
    @Synchronized
    fun getSorted(): List<NewsItem> {
        val unseen = buffer.filter { it.url !in seenUrls }
        val seen   = buffer.filter { it.url in seenUrls }
        return unseen + seen
    }

    /** Помечаем новость прочитанной (когда она ушла за верхний край) */
    @Synchronized
    fun markSeen(url: String) {
        if (url.isEmpty() || url in seenUrls) return
        seenUrls.add(url)
        // Ограничиваем историю
        if (seenUrls.size > MAX_SEEN) {
            val oldest = seenUrls.first()
            seenUrls.remove(oldest)
        }
        // Сохраняем в SharedPrefs (async)
        prefs?.edit()?.putStringSet(KEY_SEEN, seenUrls.toSet())?.apply()
    }

    /** Сколько непрочитанных */
    fun unseenCount(): Int = buffer.count { it.url !in seenUrls }

    /** Полный размер буфера */
    fun size(): Int = buffer.size

    /** Очистить всё (для отладки) */
    fun clearSeen() {
        seenUrls.clear()
        prefs?.edit()?.remove(KEY_SEEN)?.apply()
    }
}
