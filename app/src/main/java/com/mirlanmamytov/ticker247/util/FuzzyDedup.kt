package com.mirlanmamytov.ticker247.util

import com.mirlanmamytov.ticker247.data.model.NewsItem

/**
 * Умный дедуп новостей из разных источников (Telegram + Google News + YouTube).
 *
 * Проблема: одно событие может прийти из нескольких источников с разными заголовками:
 *   Telegram:     "⚡ Землетрясение в Бишкеке — 4.2 балла"
 *   Google News:  "В Кыргызстане зафиксировано землетрясение магнитудой 4.2"
 *
 * Точный match (первые 55 символов) не сработает.
 * Нужно fuzzy-сравнение: если 70%+ ключевых слов совпадают → это одна новость.
 *
 * Стратегия выбора лучшей версии:
 * 1. Telegram-пост с фото > Google News без фото (фото важнее)
 * 2. Больше просмотров > меньше просмотров
 * 3. Свежее > старше
 * 4. Длиннее summary > короче (больше информации)
 */
object FuzzyDedup {

    private const val SIMILARITY_THRESHOLD = 0.65  // 65% совпадение ключевых слов = дубликат

    /**
     * Основная функция: принимает сырой список из всех источников,
     * возвращает дедуплицированный список с агрегированными метриками.
     */
    fun deduplicate(items: List<NewsItem>): List<NewsItem> {
        if (items.size <= 1) return items
        // Финансовые данные не дедуплицируем — пусть всегда проходят целиком
        val finance = items.filter { it.category in setOf("CURRENCY", "CRYPTO") }
        val news    = items.filter { it.category !in setOf("CURRENCY", "CRYPTO") }
        return finance + deduplicateNews(news)
    }

    private fun deduplicateNews(items: List<NewsItem>): List<NewsItem> {
        if (items.size <= 1) return items

        val groups = mutableListOf<MutableList<NewsItem>>()

        for (item in items) {
            val matchedGroup = groups.firstOrNull { group ->
                group.any { existing -> isDuplicate(existing.title, item.title) }
            }
            if (matchedGroup != null) {
                matchedGroup.add(item)
            } else {
                groups.add(mutableListOf(item))
            }
        }

        return groups.map { group -> mergeGroup(group) }
    }

    /**
     * Проверяет два заголовка на дублирование.
     * Использует пересечение ключевых слов — не зависит от порядка слов.
     */
    fun isDuplicate(a: String, b: String): Boolean {
        val wordsA = extractKeywords(a)
        val wordsB = extractKeywords(b)
        if (wordsA.isEmpty() || wordsB.isEmpty()) return false

        val intersection = wordsA.intersect(wordsB).size.toDouble()
        val union = wordsA.union(wordsB).size.toDouble()
        val jaccard = intersection / union

        return jaccard >= SIMILARITY_THRESHOLD
    }

    /**
     * Из группы дубликатов выбирает лучшую версию
     * и обогащает её агрегированными метриками.
     */
    private fun mergeGroup(group: List<NewsItem>): NewsItem {
        // Лучшая версия: сначала по медиа, потом по просмотрам, потом по свежести
        val best = group.maxWithOrNull(
            compareBy<NewsItem> {
                when {
                    it.isVideo          -> 4
                    it.imageUrl != null -> 3
                    it.summary.length > 100 -> 2  // длинный текст лучше — больше информации
                    else                -> 0
                }
            }
            .thenByDescending { it.telegramViews }
            .thenByDescending { it.publishedAt }
        ) ?: group.first()

        if (group.size == 1) return best

        // Агрегируем метрики по всей группе
        val totalViews  = group.sumOf { it.telegramViews }
        val isTrending  = group.any { it.isTrending }
        val sourceCount = group.size
        val maxPriority = group.maxOf { it.priority }

        return best.copy(
            telegramViews = totalViews,
            isTrending    = isTrending,
            sourceCount   = sourceCount,
            priority      = maxPriority
        )
    }

    /**
     * Извлекает ключевые слова из заголовка:
     * - lowercase
     * - убираем стоп-слова (предлоги, союзы, частицы)
     * - убираем пунктуацию
     * - берём слова длиннее 3 символов (короткие слова шумят)
     */
    private fun extractKeywords(title: String): Set<String> {
        val stopWords = setOf(
            // Русские
            "и", "в", "на", "с", "по", "из", "за", "от", "до", "при", "под", "над",
            "для", "или", "но", "что", "как", "это", "был", "была", "были", "будет",
            "не", "уже", "ещё", "также", "только", "очень", "всё", "все", "они",
            "его", "её", "их", "он", "она", "мы", "вы", "тот", "та", "те", "ту",
            "который", "которая", "которые", "после", "через", "между", "о", "об",
            // Английские
            "the", "a", "an", "and", "or", "but", "in", "on", "at", "to", "for",
            "of", "with", "by", "from", "is", "are", "was", "were", "has", "have",
            "be", "been", "will", "would", "could", "should", "may", "might",
            "this", "that", "these", "those", "it", "its", "as", "up", "out",
            // Кыргызские частицы
            "жана", "же", "да", "эмес", "деп", "үчүн", "боюнча"
        )

        return title
            .lowercase()
            .replace(Regex("[^a-zа-яёүңөқ0-9\\s]"), " ")
            .split(Regex("\\s+"))
            .filter { word -> word.length > 3 && word !in stopWords }
            .toSet()
    }
}
