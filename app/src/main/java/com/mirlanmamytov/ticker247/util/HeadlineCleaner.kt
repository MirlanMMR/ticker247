package com.mirlanmamytov.ticker247.util

/**
 * Литературный редактор для заголовков бегущей строки.
 * Убирает воду, бюрократизмы и пассивные конструкции из русских новостей.
 */
object HeadlineCleaner {

    // ── Фразы-пустышки в начале — просто удаляем ─────────────────────────────
    private val LEAD_FILLERS = listOf(
        Regex("^стало известно,?\\s*(что)?\\s*", RegexOption.IGNORE_CASE),
        Regex("^сообщается,?\\s*(что)?\\s*", RegexOption.IGNORE_CASE),
        Regex("^как сообщается,?\\s*", RegexOption.IGNORE_CASE),
        Regex("^по данным\\s+[^,]+,\\s*", RegexOption.IGNORE_CASE),
        Regex("^по информации\\s+[^,]+,\\s*", RegexOption.IGNORE_CASE),
        Regex("^источники сообщают,?\\s*(что)?\\s*", RegexOption.IGNORE_CASE),
        Regex("^как стало известно,?\\s*", RegexOption.IGNORE_CASE),
        Regex("^по словам\\s+[^,]+,\\s*", RegexOption.IGNORE_CASE),
        Regex("^в \\w+ сообщили,?\\s*(что)?\\s*", RegexOption.IGNORE_CASE),
        Regex("^в администрации [^,]+,?\\s*(сообщили|заявили|рассказали),?\\s*(что)?\\s*", RegexOption.IGNORE_CASE),
        Regex("^представители [^,]+,?\\s*(сообщили|заявили|рассказали),?\\s*(что)?\\s*", RegexOption.IGNORE_CASE),
        Regex("^сегодня стало известно,?\\s*(что)?\\s*", RegexOption.IGNORE_CASE),
        Regex("^напомним,?\\s*(что)?\\s*", RegexOption.IGNORE_CASE),
        Regex("^отметим,?\\s*(что)?\\s*", RegexOption.IGNORE_CASE),
        Regex("^подчеркнём,?\\s*(что)?\\s*", RegexOption.IGNORE_CASE),
    )

    // ── Заголовки которые не несут новости — пропускаем ─────────────────────
    private val SKIP_PATTERNS = listOf(
        Regex("эксперты (рассказали|объяснили|назвали|раскрыли)", RegexOption.IGNORE_CASE),
        Regex("(диетолог|психолог|нутрициолог|астролог)\\s+(рассказал|назвал|объяснил)", RegexOption.IGNORE_CASE),
        Regex("стало известно почему", RegexOption.IGNORE_CASE),
        Regex("^(топ|рейтинг|список)\\s+\\d+", RegexOption.IGNORE_CASE),
        Regex("как (правильно|лучше|нужно)\\s+", RegexOption.IGNORE_CASE),
    )

    // ── Замены бюрократизмов ─────────────────────────────────────────────────
    private val REPLACEMENTS = listOf(
        "в ходе"            to "во время",
        "в рамках"          to "в ходе",
        "осуществить"       to "сделать",
        "осуществляет"      to "делает",
        "произвести"        to "сделать",
        "оказать помощь"    to "помочь",
        "принять участие"   to "участвовать",
        "провести встречу"  to "встретиться",
        "имеет место"       to "есть",
        "на сегодняшний день" to "сегодня",
        "в настоящее время" to "сейчас",
        "по итогам"         to "после",
    )

    /**
     * Очищает текст для бегущей строки.
     * Возвращает null если новость "пустая" — без конкретного факта.
     */
    fun clean(raw: String): String? {
        var text = raw.trim()

        // Пропускаем мусорные заголовки
        if (SKIP_PATTERNS.any { it.containsMatchIn(text) }) return null

        // Убираем вводные фразы
        for (filler in LEAD_FILLERS) {
            val cleaned = filler.replace(text, "")
            if (cleaned.length < text.length) {
                text = cleaned.trim().replaceFirstChar { it.uppercaseChar() }
                break // убираем только первую найденную
            }
        }

        // Заменяем бюрократизмы
        for ((from, to) in REPLACEMENTS) {
            text = text.replace(from, to, ignoreCase = true)
        }

        // Берём до первой точки (заголовок)
        val dotIdx = text.indexOf('.')
        val headline = if (dotIdx > 20) text.substring(0, dotIdx + 1) else text

        // Слишком короткий или не информативный — пропускаем
        if (headline.length < 25) return null

        return headline.trim()
    }
}
