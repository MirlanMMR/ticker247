package com.mirlanmamytov.ticker247.util

/**
 * Редакторская повестка: темы, заданные постами «#тема: ...» в TG-канале.
 * Новости, совпадающие с активной темой, получают бонус к рейтингу
 * и поднимаются в ленте. Тема живёт до expiresAt (по умолчанию 3 дня,
 * можно задать #Nд/#Nч в том же посте).
 *
 * Реестр обновляется при каждом fetch-цикле из окна последних постов канала —
 * состояние не хранится, всё выводится из самого канала.
 */
object EditorialTopics {

    data class Topic(val raw: String, val words: List<String>, val expiresAt: Long)

    @Volatile
    private var topics: List<Topic> = emptyList()

    private val stopWords = setOf(
        "в", "на", "и", "с", "по", "из", "за", "от", "к", "о", "об", "не",
        "что", "как", "для", "при", "до", "это", "the", "in", "on", "of", "for"
    )

    /** Извлекает слова темы: значимые, обрезанные до основы (окончания не мешают) */
    private fun topicWords(text: String): List<String> = text.lowercase()
        .split(Regex("[^а-яёa-z0-9]+"))
        .filter { it.length >= 3 && it !in stopWords }
        .map { it.take(6) }

    /** Регистрирует темы, найденные в постах канала (вызывается парсером каждый цикл) */
    fun update(found: List<Pair<String, Long>>) {
        val now = System.currentTimeMillis()
        topics = found.mapNotNull { (raw, expiresAt) ->
            val words = topicWords(raw)
            if (words.isEmpty() || expiresAt <= now) null
            else Topic(raw, words, expiresAt)
        }
        if (topics.isNotEmpty()) {
            android.util.Log.d("EditorialTopics", "Активные темы: ${topics.map { it.raw }}")
        }
    }

    /** Активные темы (для поиска новостей в Google News) */
    fun active(): List<String> {
        val now = System.currentTimeMillis()
        return topics.filter { it.expiresAt > now }.map { it.raw }
    }

    /**
     * Бонус к рейтингу новости, если она совпадает с активной темой.
     * Совпадение: минимум половина слов темы встречается в заголовке+тексте.
     */
    fun boostFor(title: String, summary: String): Int {
        val active = topics
        if (active.isEmpty()) return 0
        val now = System.currentTimeMillis()
        val text = "$title $summary".lowercase()
        val matches = active.any { topic ->
            topic.expiresAt > now &&
            topic.words.count { w -> text.contains(w) } >= (topic.words.size + 1) / 2
        }
        return if (matches) 20 else 0
    }
}
