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

    private const val MAX_BUFFER = 80           // сколько хранить в памяти
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
        val broadCats = setOf("KG","WORLD","NEWS","SPORT","URGENT")
        if (a.category != b.category &&
            !(a.category in broadCats && b.category in broadCats)) return false
        val ta = normalizeTitle(a.title)
        val tb = normalizeTitle(b.title)
        if (ta.isEmpty() || tb.isEmpty()) return false
        // Метод 1: позиционное совпадение символов (одинаковые заголовки)
        val len = minOf(ta.length, tb.length)
        val charMatch = ta.take(len).zip(tb.take(len)).count { (c1, c2) -> c1 == c2 }
        if (charMatch.toFloat() / len > 0.70f) return true
        // Метод 2: пересечение значимых слов (разные формулировки одного события)
        val wordsA = ta.split(" ").filter { it.length > 4 }.toSet()
        val wordsB = tb.split(" ").filter { it.length > 4 }.toSet()
        if (wordsA.isEmpty() || wordsB.isEmpty()) return false
        val common = wordsA.intersect(wordsB).size
        val minWords = minOf(wordsA.size, wordsB.size)
        return common.toFloat() / minWords > 0.55f
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
     * 1. Непрочитанные — сортировка по качеству (приоритет + свежесть + наличие текста/фото)
     * 2. Прочитанные — в конце, тоже по качеству
     * Итого не более 50 карточек — меньше да лучше.
     */
    // Язык устройства для фильтрации контента
    var deviceLanguage: String = java.util.Locale.getDefault().language  // "ru", "en", "ky" и т.д.

    @Synchronized
    fun getSorted(): List<NewsItem> {
        fun qualityScore(item: NewsItem): Int {
            var score = 0
            score += item.priority * 5
            if (item.summary.length > 80) score += 3
            if (item.imageUrl != null)    score += 2
            val ageH = (System.currentTimeMillis() - item.publishedAt) / 3_600_000
            score += when {
                ageH < 1  -> 6
                ageH < 3  -> 4
                ageH < 8  -> 2
                else      -> 0
            }
            return score
        }
        // Новости старше 48 часов не показываем
        val cutoff = System.currentTimeMillis() - 24 * 3_600_000L
        // Финансовые данные всегда первыми — их нельзя вытеснить из ленты
        // Кириллические языки взаимопонятны — ru/ky/uk читают одно
        val cyrillicLangs = setOf("ru", "ky", "uk", "be", "bg", "sr", "mk")
        val userLang = deviceLanguage
        val finance = buffer.filter { it.category in setOf("CURRENCY", "CRYPTO") }
        val rest    = buffer.filter { item ->
            item.category !in setOf("CURRENCY", "CRYPTO") &&
            item.publishedAt >= cutoff &&
            // Фильтруем контент не на языке пользователя
            // Исключение: если язык неизвестен ("unknown"/"other") — показываем
            run {
                val itemLang = item.language
                if (itemLang == "unknown" || itemLang == "other" || itemLang.isEmpty()) true
                else if (userLang in cyrillicLangs) itemLang in cyrillicLangs
                else itemLang == userLang
            }
        }
        val unseen  = rest.filter { it.url !in seenUrls }.sortedByDescending { qualityScore(it) }
        val seen    = rest.filter { it.url in seenUrls  }.sortedByDescending { qualityScore(it) }
        return finance + (unseen + seen).take(50)
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
