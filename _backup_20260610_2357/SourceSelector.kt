package com.mirlanmamytov.ticker247.network

import com.mirlanmamytov.ticker247.util.UserLocale
import com.mirlanmamytov.ticker247.util.UserLocale.Region

/**
 * Выбирает новостные источники на основе локали пользователя.
 *
 * Принципы:
 * - KG-новости всегда присутствуют (это KG-приложение)
 * - Мировые источники — нейтральные, без перекоса в одну сторону
 * - KG спорт → URGENT при победе атлетов (везде в мире)
 * - 50/50 баланс локального и мирового
 */
object SourceSelector {

    data class ChannelSource(
        val handle: String,      // @handle для Telegram, channel_id для YouTube
        val category: String,    // "KG", "WORLD", "SPORT", "CULTURE", etc.
        val type: SourceType,
        val priority: Int = 0    // 0=обычный, 1=важный, 2=приоритетный
    )

    enum class SourceType { TELEGRAM, YOUTUBE_RSS, RSS }

    // ── Постоянные KG-источники (всегда, везде) ───────────────────────────────

    val KG_ALWAYS = listOf(
        // Новости Кыргызстана
        ChannelSource("akipress",        "KG",    SourceType.TELEGRAM, 2),
        ChannelSource("kabar_news_kg",   "KG",    SourceType.TELEGRAM, 2),
        ChannelSource("kyrgyzinform",    "KG",    SourceType.TELEGRAM, 1),
        ChannelSource("24kgnews",        "KG",    SourceType.TELEGRAM, 1),
        ChannelSource("tazabek",         "KG",    SourceType.TELEGRAM, 1),
        // Срочное
        ChannelSource("breakingmash",    "URGENT", SourceType.TELEGRAM, 3),
        ChannelSource("shot_shot",       "URGENT", SourceType.TELEGRAM, 3),
    )

    val KG_SPORT = listOf(
        // Спорт Кыргызстана — победы атлетов → URGENT
        ChannelSource("akipress",        "SPORT",  SourceType.TELEGRAM, 2),
        ChannelSource("kabar_news_kg",   "SPORT",  SourceType.TELEGRAM, 1),
    )

    // ── YouTube KG-каналы ──────────────────────────────────────────────────────
    val KG_YOUTUBE = listOf(
        // KTR — Кыргызское телевидение
        ChannelSource("UCJQOJGxH87GCxUyHGqOG6Ew", "KG", SourceType.YOUTUBE_RSS, 2),
        // КООРТ ТВ
        ChannelSource("UCxxx_KOORT",               "KG", SourceType.YOUTUBE_RSS, 1),
        // Sputnik Кыргызстан
        ChannelSource("UCiivW6grbRvpMtXKBIGfOdg",  "KG", SourceType.YOUTUBE_RSS, 1),
    )

    // ── Мировые источники по региону ──────────────────────────────────────────

    /** Нейтральные международные — основа для всех регионов */
    val WORLD_NEUTRAL = listOf(
        ChannelSource("bbcrussian",      "WORLD", SourceType.TELEGRAM, 2),
        ChannelSource("aljazeeraee",     "WORLD", SourceType.TELEGRAM, 2),
        ChannelSource("deutscheWelle",   "WORLD", SourceType.TELEGRAM, 1),  // DW Russian
        ChannelSource("inosmi",          "WORLD", SourceType.TELEGRAM, 1),  // переводы мировых СМИ
        ChannelSource("inopressa",       "WORLD", SourceType.TELEGRAM, 1),  // переводы мировых СМИ
    )

    /** Дополнительные для СНГ/ЦА аудитории */
    val WORLD_CIS_EXTRA = listOf(
        ChannelSource("meduzaio",        "WORLD", SourceType.TELEGRAM, 1),
        ChannelSource("tvrain",          "WORLD", SourceType.TELEGRAM, 1),
        ChannelSource("currenttime",     "WORLD", SourceType.TELEGRAM, 1),  // Настоящее Время (RFE/RL)
    )

    /** Европейский фокус */
    val WORLD_EUROPE_EXTRA = listOf(
        ChannelSource("euromaidan",      "WORLD", SourceType.TELEGRAM, 1),
        ChannelSource("bbcrussian",      "WORLD", SourceType.TELEGRAM, 2),
        ChannelSource("deutscheWelle",   "WORLD", SourceType.TELEGRAM, 2),
    )

    /** Ближневосточный фокус */
    val WORLD_MIDDLE_EAST_EXTRA = listOf(
        ChannelSource("aljazeeraee",     "WORLD", SourceType.TELEGRAM, 2),
        ChannelSource("bbcrussian",      "WORLD", SourceType.TELEGRAM, 1),
    )

    // ── Нишевые (одинаковы для всех) ─────────────────────────────────────────
    val NICHE_ALWAYS = listOf(
        ChannelSource("avtoradar",       "AUTO",    SourceType.TELEGRAM),
        ChannelSource("driveru",         "AUTO",    SourceType.TELEGRAM),
        ChannelSource("motor_ru",        "AUTO",    SourceType.TELEGRAM),
        ChannelSource("buro247",         "FASHION", SourceType.TELEGRAM),
        ChannelSource("vogue_russia",    "FASHION", SourceType.TELEGRAM),
        ChannelSource("travel_kg",       "TOURS",   SourceType.TELEGRAM),
        ChannelSource("travelplus_ru",   "TOURS",   SourceType.TELEGRAM),
        ChannelSource("kinopoisk",       "CULTURE", SourceType.TELEGRAM),
        ChannelSource("afishakg",        "CULTURE", SourceType.TELEGRAM),
        ChannelSource("sport24russia",   "SPORT",   SourceType.TELEGRAM),
        ChannelSource("matchtv",         "SPORT",   SourceType.TELEGRAM),
    )

    // ── YouTube мировые новостные каналы ──────────────────────────────────────
    val YOUTUBE_WORLD_NEWS = listOf(
        // BBC News Russian
        ChannelSource("UCK9hDpGRfzZuoOkL9Nf-7jA", "WORLD", SourceType.YOUTUBE_RSS, 2),
        // Al Jazeera Arabic News
        ChannelSource("UC0d3LGCJMzB0YQZN5TFe1QA", "WORLD", SourceType.YOUTUBE_RSS, 1),
        // DW News
        ChannelSource("UCknLrEdhRCp1aegoMqRaCZg",  "WORLD", SourceType.YOUTUBE_RSS, 1),
        // Reuters
        ChannelSource("UChqUTb7kYRX8-EiaN3XFrSQ",  "WORLD", SourceType.YOUTUBE_RSS, 1),
    )

    // ── Главная функция — собрать полный список источников ───────────────────

    fun getSources(ctx: UserLocale.UserContext): List<ChannelSource> {
        val sources = mutableListOf<ChannelSource>()

        // 1. KG-источники — всегда
        sources.addAll(KG_ALWAYS)
        sources.addAll(KG_SPORT)
        sources.addAll(KG_YOUTUBE)

        // 2. Мировые — нейтральная база
        sources.addAll(WORLD_NEUTRAL)

        // 3. Дополнительные мировые по региону
        when (ctx.region) {
            Region.KYRGYZSTAN,
            Region.CIS,
            Region.CENTRAL_ASIA -> sources.addAll(WORLD_CIS_EXTRA)
            Region.EUROPE        -> sources.addAll(WORLD_EUROPE_EXTRA)
            Region.MIDDLE_EAST   -> sources.addAll(WORLD_MIDDLE_EAST_EXTRA)
            else                 -> sources.addAll(WORLD_NEUTRAL) // нейтральные повторно = больший вес
        }

        // 4. Нишевые
        sources.addAll(NICHE_ALWAYS)

        // 5. YouTube мировые новости
        sources.addAll(YOUTUBE_WORLD_NEWS)

        return sources.distinctBy { it.handle }
    }

    /** Только Telegram-каналы для TelegramParser */
    fun getTelegramSources(ctx: UserLocale.UserContext) =
        getSources(ctx).filter { it.type == SourceType.TELEGRAM }

    /** Только YouTube-каналы для YouTubeRssParser */
    fun getYoutubeSources(ctx: UserLocale.UserContext) =
        getSources(ctx).filter { it.type == SourceType.YOUTUBE_RSS }
}
