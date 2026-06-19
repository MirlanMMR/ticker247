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

    /** Нормализуем заголовок для сравнения схожести */
    private fun normalizeTitle(title: String) = title
        .lowercase()
        .replace(Regex("[^а-яёa-z0-9\\s]"), " ")
        .replace(Regex("\\s+"), " ")
        .trim()
        .take(60)

    /** Одна и та же новость из разных источников? Берём только первую */
    private fun isSameStory(a: NewsItem, b: NewsItem): Boolean {
        if (a.category != b.category &&
            !(a.category in setOf("KG","WORLD","NEWS") && b.category in setOf("KG","WORLD","NEWS"))) return false
        val ta = normalizeTitle(a.title)
        val tb = normalizeTitle(b.title)
        if (ta.isEmpty() || tb.isEmpty()) return false
        val len = minOf(ta.length, tb.length)
        val common = ta.take(len).zip(tb.take(len)).count { (c1, c2) -> c1 == c2 }
        return common.toFloat() / len > 0.75f
    }

    /** Добавляем свежие новости в буфер (дедупликация по URL и схожему заголовку) */
    @Synchronized
    fun addItems(newItems: List<NewsItem>) {
        fun itemKey(item: NewsItem) = if (item.url.isNotEmpty()) item.url
                                     else "${item.category}:${item.title.take(50)}"

        val existingKeys = buffer.map { itemKey(it) }.toSet()

        // Финансовые данные — всегда обновляем
        buffer.removeAll { it.category == "CURRENCY" || it.category == "CRYPTO" }

        val (finance, news) = newItems.partition { it.category in setOf("CURRENCY", "CRYPTO") }

        // Для новостей: убираем дубли по URL и по схожему заголовку
        val freshNews = news.filter { candidate ->
            if (itemKey(candidate) in existingKeys) return@filter false
            // Проверяем что такой же истории ещё нет в буфере
            buffer.none { existing -> isSameStory(candidate, existing) }
        }

        buffer.addAll(0, freshNews)
        buffer.addAll(0, finance)

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
