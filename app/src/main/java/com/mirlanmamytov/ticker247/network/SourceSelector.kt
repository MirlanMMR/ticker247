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
        // Новости Кыргызстана — только проверенные редакционные источники
        ChannelSource("t247feed",        "KG",    SourceType.TELEGRAM, 10), // редакторский канал
        ChannelSource("akipress",        "KG",    SourceType.TELEGRAM, 2),
        ChannelSource("kabar_news_kg",   "KG",    SourceType.TELEGRAM, 2),
        ChannelSource("kyrgyzinform",    "KG",    SourceType.TELEGRAM, 1),
        ChannelSource("tazabek",         "KG",    SourceType.TELEGRAM, 1),
        // Срочное — только BBC-уровня агрегатор (shot_shot убран: слишком много мусора)
        ChannelSource("breakingmash",    "URGENT", SourceType.TELEGRAM, 2),
        ChannelSource("ticketon_kg",     "CULTURE", SourceType.TELEGRAM, 1), // афиша: концерты, спектакли, премьеры
    )

    val KG_SPORT = listOf(
        // Общий спорт Кыргызстана
        ChannelSource("akipress",        "SPORT",  SourceType.TELEGRAM, 2),
        ChannelSource("kabar_news_kg",   "SPORT",  SourceType.TELEGRAM, 1),
        // Боевые виды спорта — MMA, бокс, борьба, кикбоксинг
        // Кыргызстан силён: Эльдар Джумагулов (UFC), Тажибай Досмагамбетов, Заурбек Сидаков
        ChannelSource("kgboxing",        "SPORT",  SourceType.TELEGRAM, 2), // бокс КГ
        ChannelSource("mma_kg",          "SPORT",  SourceType.TELEGRAM, 2), // MMA Кыргызстан
        ChannelSource("kyrgyz_sport",    "SPORT",  SourceType.TELEGRAM, 2), // спорт КГ общий
        ChannelSource("ufc_ru",          "SPORT",  SourceType.TELEGRAM, 1), // UFC на русском
        ChannelSource("mmafightclub",    "SPORT",  SourceType.TELEGRAM, 1), // MMA новости
        ChannelSource("wrestlingkg",     "SPORT",  SourceType.TELEGRAM, 2), // борьба КГ
        ChannelSource("sport24kg",       "SPORT",  SourceType.TELEGRAM, 2), // Sport 24 КГ
    )

    // ── YouTube KG-каналы ──────────────────────────────────────────────────────
    val KG_YOUTUBE = listOf(
        // KTR — Кыргызское телевидение
        ChannelSource("UCJQOJGxH87GCxUyHGqOG6Ew", "KG", SourceType.YOUTUBE_RSS, 2),
        // Sputnik Кыргызстан
        ChannelSource("UCiivW6grbRvpMtXKBIGfOdg",  "KG", SourceType.YOUTUBE_RSS, 1),
        // Азаттык — Радио Свобода Кыргызстан
        ChannelSource("UCBFxQUBinMPqQHLHmpMZaJw",  "KG", SourceType.YOUTUBE_RSS, 2),
    )

    // ── YouTube: боевые виды спорта ───────────────────────────────────────────
    // MMA, бокс, борьба — контент на русском и кыргызском
    val COMBAT_SPORTS_YOUTUBE = listOf(
        // UFC Russia — официальный канал UFC на русском
        ChannelSource("UCNFDnh7bvAMCgKzDFNO2fhg",  "SPORT", SourceType.YOUTUBE_RSS, 2),
        // MMA Fighting (английский, но топ-контент)
        ChannelSource("UCPIAn-SWhJzBilt1MekO4Vg",  "SPORT", SourceType.YOUTUBE_RSS, 1),
        // Bellator MMA
        ChannelSource("UCwIiHyFLKZBBzzpLEPsGkEA",  "SPORT", SourceType.YOUTUBE_RSS, 1),
        // Бокс: World Boxing — топ бои
        ChannelSource("UCou-8TbxWsXQnBd4hkB9EBg",  "SPORT", SourceType.YOUTUBE_RSS, 1),
        // ONE Championship (Азия — там много бойцов из ЦА)
        ChannelSource("UCfX2-S9FBD1MZiDqxMCNmLg",  "SPORT", SourceType.YOUTUBE_RSS, 2),
    )

    // ── Мировые источники по региону ──────────────────────────────────────────

    /** Нейтральные международные — основа для всех регионов */
    val WORLD_NEUTRAL = listOf(
        ChannelSource("bbcrussian",      "WORLD", SourceType.TELEGRAM, 2),
        ChannelSource("aljazeeraee",     "WORLD", SourceType.TELEGRAM, 2),
        ChannelSource("deutscheWelle",   "WORLD", SourceType.TELEGRAM, 1),  // DW Russian
        ChannelSource("inosmi",          "WORLD", SourceType.TELEGRAM, 1),  // переводы мировых СМИ
        // inopressa убрана: дублирует inosmi
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

    // ── Технологии и гаджеты ─────────────────────────────────────────────────
    // Новые телефоны, обзоры, лайфхаки, Android-новости
    val TECH_ALWAYS = listOf(
        ChannelSource("androidauthority", "TECH", SourceType.TELEGRAM, 2), // Android Authority
        ChannelSource("mobilereview",     "TECH", SourceType.TELEGRAM, 2), // Mobile Review — Эльдар Муртазин
        ChannelSource("ru_9to5google",    "TECH", SourceType.TELEGRAM, 1), // 9to5Google на русском
        ChannelSource("phonegeeks",       "TECH", SourceType.TELEGRAM, 1), // обзоры телефонов
        ChannelSource("techinsider_ru",   "TECH", SourceType.TELEGRAM, 1), // Tech Insider Россия
        ChannelSource("ixbt_live",        "TECH", SourceType.TELEGRAM, 2), // iXBT — лучший русский техноресурс
        ChannelSource("gsminfo_ru",       "TECH", SourceType.TELEGRAM, 1), // GSMinfo — смартфоны
        ChannelSource("androidinsider_ru","TECH", SourceType.TELEGRAM, 1), // AndroidInsider.ru
        ChannelSource("wylsacom",         "TECH", SourceType.TELEGRAM, 2), // Wylsacom — топ обзоры
        ChannelSource("fandroid_ru",      "TECH", SourceType.TELEGRAM, 1), // Fandroid
    )

    // ── Туризм в Кыргызстане — иностранцы о нашей природе ───────────────────
    // Лёгкий, добрый, вдохновляющий контент: горы, озёра, люди, культура
    val TOURS_KG = listOf(
        ChannelSource("travel_kg",          "TOURS", SourceType.TELEGRAM, 2),
        ChannelSource("kyrgyzstan_travel",  "TOURS", SourceType.TELEGRAM, 2), // путешествия по КГ
        ChannelSource("visitkyrgyzstan",    "TOURS", SourceType.TELEGRAM, 2), // официальный туризм КГ
        ChannelSource("ilovekgtravel",      "TOURS", SourceType.TELEGRAM, 1), // я люблю КГ
        ChannelSource("centralasiatravel",  "TOURS", SourceType.TELEGRAM, 1), // ЦА туризм
        ChannelSource("travelplus_ru",      "TOURS", SourceType.TELEGRAM, 1),
    )

    // YouTube: иностранцы исследуют Кыргызстан — самый вдохновляющий контент
    val TOURS_YOUTUBE = listOf(
        // Lost With Purpose — Алекс Берджер, культовый канал о путешествиях в ЦА
        ChannelSource("UCt_NLJ4McJlCnSbLn5LxjJg", "TOURS", SourceType.YOUTUBE_RSS, 2),
        // Kara and Nate — американская пара объехала весь мир включая КГ
        ChannelSource("UCojElg7pBFxqgFRfKLUwMoA",  "TOURS", SourceType.YOUTUBE_RSS, 2),
        // Kristen Sarah — канадка, много контента про нетуристическую Азию
        ChannelSource("UCKSFtEiJHxMGGDxd0OR9Lug",  "TOURS", SourceType.YOUTUBE_RSS, 1),
        // Bald and Bankrupt — культовый русскоязычный путешественник, был в КГ
        ChannelSource("UCxDZs_ltFFvn0FDHT6kmoXA",  "TOURS", SourceType.YOUTUBE_RSS, 2),
        // Mark Wiens — еда и путешествия по Азии
        ChannelSource("UCnT37BcNGEoMkdDflNJPc5A",  "TOURS", SourceType.YOUTUBE_RSS, 1),
    )

    // ── Хорошие новости — позитивный контент ──────────────────────────────────
    // Открытия, рекорды, добрые истории, достижения — противовес негативу
    val GOOD_NEWS = listOf(
        ChannelSource("goodnewsru",         "GOOD", SourceType.TELEGRAM, 1), // Хорошие новости
        ChannelSource("pozitiv_kg",         "GOOD", SourceType.TELEGRAM, 2), // Позитив Кыргызстан
        ChannelSource("dobroe_utro_kg",     "GOOD", SourceType.TELEGRAM, 1), // Доброе утро КГ
        ChannelSource("positivnews",        "GOOD", SourceType.TELEGRAM, 1), // позитивные новости
        ChannelSource("worldgoodnews",      "GOOD", SourceType.TELEGRAM, 1), // мировые добрые новости
    )

    // ── Звёзды, шоубиз, знаменитости ────────────────────────────────────────
    val STARS_ALWAYS = listOf(
        ChannelSource("starhit",            "STARS", SourceType.TELEGRAM, 1), // StarHit — шоубиз RU
        ChannelSource("showbiz_kg",         "STARS", SourceType.TELEGRAM, 2), // Шоубиз Кыргызстан
        ChannelSource("musickg",            "STARS", SourceType.TELEGRAM, 2), // Музыка КГ — ырчылар
        ChannelSource("peopletalkru",       "STARS", SourceType.TELEGRAM, 1), // People Talk
        ChannelSource("tmz_news",           "STARS", SourceType.TELEGRAM, 1), // TMZ-стиль
        ChannelSource("cosmo_ru",           "STARS", SourceType.TELEGRAM, 1), // Cosmopolitan Россия
    )

    // ── Здоровье, медицина, питание ──────────────────────────────────────────
    val HEALTH_ALWAYS = listOf(
        ChannelSource("healthkg",           "HEALTH", SourceType.TELEGRAM, 2), // Здоровье КГ
        ChannelSource("doctorpiter",        "HEALTH", SourceType.TELEGRAM, 1), // Доктор Питер — медицина
        ChannelSource("medicalru",          "HEALTH", SourceType.TELEGRAM, 1), // Медицина и здоровье
        ChannelSource("zdorovieinfo",       "HEALTH", SourceType.TELEGRAM, 1), // Здоровье.инфо
        ChannelSource("lifehacker_ru",      "HEALTH", SourceType.TELEGRAM, 1), // Лайфхакер — здоровье
        ChannelSource("psychologykg",       "HEALTH", SourceType.TELEGRAM, 2), // Психология КГ
    )

    // ── Деньги, финансы, личный бюджет ───────────────────────────────────────
    val MONEY_ALWAYS = listOf(
        ChannelSource("fingramota_kg",      "MONEY", SourceType.TELEGRAM, 2), // Финграмотность КГ
        ChannelSource("rbc_economics",      "MONEY", SourceType.TELEGRAM, 1), // РБК Экономика
        ChannelSource("tinkoff_journal",    "MONEY", SourceType.TELEGRAM, 1), // Тинькофф Журнал
        ChannelSource("nbrkg",              "MONEY", SourceType.TELEGRAM, 2), // Нацбанк КГ (курсы, ставки)
        ChannelSource("money_kg",           "MONEY", SourceType.TELEGRAM, 2), // Финансы Кыргызстан
        ChannelSource("invest_simple_ru",   "MONEY", SourceType.TELEGRAM, 1), // Инвестиции просто
    )

    // ── Лайфхаки, советы, жизнь, рецепты ────────────────────────────────────
    val LIFE_ALWAYS = listOf(
        ChannelSource("lifehacks_kg",       "LIFE", SourceType.TELEGRAM, 2), // Лайфхаки для КГ
        ChannelSource("lifehacker_ru",      "LIFE", SourceType.TELEGRAM, 1), // Лайфхакер.ру
        ChannelSource("recipekg",           "LIFE", SourceType.TELEGRAM, 2), // Рецепты КГ — кулинария
        ChannelSource("sovetdoma",          "LIFE", SourceType.TELEGRAM, 1), // Советы по дому
        ChannelSource("psychology_life",    "LIFE", SourceType.TELEGRAM, 1), // Психология жизни
        ChannelSource("mama_kg",            "LIFE", SourceType.TELEGRAM, 2), // Мамы Кыргызстана — дети, семья
    )

    // ── Нишевые (одинаковы для всех) ─────────────────────────────────────────
    val NICHE_ALWAYS = listOf(
        ChannelSource("avtoradar",       "AUTO",    SourceType.TELEGRAM),
        ChannelSource("driveru",         "AUTO",    SourceType.TELEGRAM),
        ChannelSource("motor_ru",        "AUTO",    SourceType.TELEGRAM),
        ChannelSource("buro247",         "FASHION", SourceType.TELEGRAM),
        ChannelSource("vogue_russia",    "FASHION", SourceType.TELEGRAM),
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

        // 4. Технологии и гаджеты — всегда
        sources.addAll(TECH_ALWAYS)

        // 5. Нишевые
        sources.addAll(NICHE_ALWAYS)

        // 6. YouTube мировые новости
        sources.addAll(YOUTUBE_WORLD_NEWS)

        // 7. YouTube боевые виды спорта — MMA, бокс, борьба
        sources.addAll(COMBAT_SPORTS_YOUTUBE)

        // 8. Туризм КГ — иностранцы о нашей природе, Telegram + YouTube
        sources.addAll(TOURS_KG)
        sources.addAll(TOURS_YOUTUBE)

        // 9. Хорошие новости — позитивный контент, баланс негатива
        sources.addAll(GOOD_NEWS)

        // 10. Звёзды, шоубиз, знаменитости — развлекательный контент
        sources.addAll(STARS_ALWAYS)

        // 11. Здоровье — медицина, питание, психология
        sources.addAll(HEALTH_ALWAYS)

        // 12. Деньги — личные финансы, курсы, советы
        sources.addAll(MONEY_ALWAYS)

        // 13. Лайфхаки — советы, рецепты, дом, семья
        sources.addAll(LIFE_ALWAYS)

        return sources.distinctBy { it.handle }
    }

    /** Только Telegram-каналы для TelegramParser */
    fun getTelegramSources(ctx: UserLocale.UserContext) =
        getSources(ctx).filter { it.type == SourceType.TELEGRAM }

    /** Только YouTube-каналы для YouTubeRssParser */
    fun getYoutubeSources(ctx: UserLocale.UserContext) =
        getSources(ctx).filter { it.type == SourceType.YOUTUBE_RSS }
}
