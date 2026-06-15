package com.mirlanmamytov.ticker247

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.shadow
import androidx.compose.foundation.border
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.mirlanmamytov.ticker247.data.model.NewsItem
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

// ─── Стили категорий ──────────────────────────────────────────────────────────

// ── Стиль категории: accent — цвет бейджа/иконки, cardBg — фон плитки (светлый)
// cardGrad — два цвета для мягкого градиента на тексте-стороне плитки
data class CategoryStyle(
    val accent: Color,
    val cardBg: Color,          // основной фон текстовой стороны плитки
    val cardGrad: List<Color>,  // градиент внутри плитки для глубины
    val darkCardBg: Color,      // не используется (оставлен для совместимости)
    val label: String
)

fun newsItemStyle(category: String): CategoryStyle = when (category) {
    // 🇰🇬  Кыргызстан — небесно-синий, флаговый
    "KG"       -> CategoryStyle(Color(0xFF1565C0), Color(0xFFE8F2FF),
                    listOf(Color(0xFFE8F2FF), Color(0xFFD0E8FF)), Color(0xFF0D1A2E), "🇰🇬 КГ")
    // ⚡  Срочно — горячий красно-оранжевый (не кричащий, но тревожный)
    "URGENT"   -> CategoryStyle(Color(0xFFD32F2F), Color(0xFFFFF0EE),
                    listOf(Color(0xFFFFF0EE), Color(0xFFFFE0DC)), Color(0xFF2E1610), "⚡ СРОЧНО")
    // 🌍  Мир — глубокий бирюзовый
    "WORLD"    -> CategoryStyle(Color(0xFF00838F), Color(0xFFE6F7F8),
                    listOf(Color(0xFFE6F7F8), Color(0xFFCCF0F3)), Color(0xFF0A2126), "🌍 МИР")
    // ⚽  Спорт — насыщенный янтарь
    "SPORT"    -> CategoryStyle(Color(0xFFE65100), Color(0xFFFFF8F0),
                    listOf(Color(0xFFFFF8F0), Color(0xFFFFEDD5)), Color(0xFF2E1C00), "⚽ СПОРТ")
    // 🎬  Кино/культура — глубокий фиолетовый
    "CULTURE"  -> CategoryStyle(Color(0xFF7B1FA2), Color(0xFFF8F0FF),
                    listOf(Color(0xFFF8F0FF), Color(0xFFEDD5FF)), Color(0xFF220A2E), "🎭 КУЛЬТУРА")
    // 🚗  Авто — нефтяной зелёный
    "AUTO"     -> CategoryStyle(Color(0xFF2E7D32), Color(0xFFF0FBF0),
                    listOf(Color(0xFFF0FBF0), Color(0xFFDDF5DD)), Color(0xFF0A2010), "🚗 АВТО")
    // 👗  Мода — тёплая роза
    "FASHION"  -> CategoryStyle(Color(0xFFC2185B), Color(0xFFFFF0F5),
                    listOf(Color(0xFFFFF0F5), Color(0xFFFFD6E8)), Color(0xFF2E0018), "👗 МОДА")
    // ✈️  Туризм — небо
    "TOURS"    -> CategoryStyle(Color(0xFF0277BD), Color(0xFFEDF6FF),
                    listOf(Color(0xFFEDF6FF), Color(0xFFD4ECFF)), Color(0xFF0D1E2E), "✈️ ТУРЫ")
    // 📱  Технологии — электрический синий (как экран телефона)
    "TECH"     -> CategoryStyle(Color(0xFF0066FF), Color(0xFFEEF4FF),
                    listOf(Color(0xFFEEF4FF), Color(0xFFD0E4FF)), Color(0xFF001433), "📱 ТЕХНО")
    // 😊  Хорошие новости — тёплый солнечный жёлто-оранжевый
    "GOOD"     -> CategoryStyle(Color(0xFFF59E0B), Color(0xFFFFFBEB),
                    listOf(Color(0xFFFFFBEB), Color(0xFFFEF3C7)), Color(0xFF1C1000), "😊 ХОРОШЕЕ")
    // ⭐  Звёзды — пурпурный, гламурный
    "STARS"    -> CategoryStyle(Color(0xFF9C27B0), Color(0xFFF9F0FF),
                    listOf(Color(0xFFF9F0FF), Color(0xFFEDD5FF)), Color(0xFF220033), "⭐ ЗВЁЗДЫ")
    // 🏥  Здоровье — свежий зелёный
    "HEALTH"   -> CategoryStyle(Color(0xFF00897B), Color(0xFFE8F8F5),
                    listOf(Color(0xFFE8F8F5), Color(0xFFCCF0EA)), Color(0xFF00201D), "🏥 ЗДОРОВЬЕ")
    // 💰  Деньги — золотой
    "MONEY"    -> CategoryStyle(Color(0xFFFF8F00), Color(0xFFFFF8E1),
                    listOf(Color(0xFFFFF8E1), Color(0xFFFFECB3)), Color(0xFF1C0F00), "💰 ДЕНЬГИ")
    // 💡  Лайфхаки — яркий лимонный
    "LIFE"     -> CategoryStyle(Color(0xFF689F38), Color(0xFFF1F8E9),
                    listOf(Color(0xFFF1F8E9), Color(0xFFDCEDC8)), Color(0xFF112000), "💡 ЛАЙФХАК")
    // 📰  Новости (дефолт) — тёплый серо-синий
    else       -> CategoryStyle(Color(0xFF37474F), Color(0xFFF4F6F8),
                    listOf(Color(0xFFF4F6F8), Color(0xFFE8ECF0)), Color(0xFF0D1A2E), "📰 НОВОСТИ")
}

fun timeAgo(timestamp: Long): String {
    val diff = System.currentTimeMillis() - timestamp
    val min  = diff / 60_000
    val h    = min / 60
    return when {
        min < 1  -> "только что"
        min < 60 -> "$min мин"
        h < 24   -> "$h ч"
        else     -> "${h / 24} дн"
    }
}

/**
 * Открывает YouTube-видео: сначала пробует приложение YouTube,
 * если не установлено — открывает в браузере. Никогда не крашит.
 */
fun openYouTube(context: Context, url: String) {
    try {
        val appIntent = Intent(Intent.ACTION_VIEW, Uri.parse(url)).apply {
            setPackage("com.google.android.youtube")
        }
        context.startActivity(appIntent)
    } catch (e: ActivityNotFoundException) {
        // YouTube не установлен — открываем в браузере
        try {
            context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
        } catch (e2: Exception) {
            android.util.Log.e("Ticker247", "Cannot open URL: $url")
        }
    }
}

val feedTabs      = listOf("Все", "🇰🇬 КГ", "⚡ Срочно", "😊 Хорошее", "⭐ Звёзды", "🏥 Здоровье", "💰 Деньги", "💡 Лайфхак", "🌍 Мир", "Новости", "Спорт", "📱 Техно", "✈️ Туры", "Валюта", "Крипта", "Кино", "Авто", "Мода")
val tabCategories = listOf("ALL", "KG", "URGENT", "GOOD", "STARS", "HEALTH", "MONEY", "LIFE", "WORLD", "NEWS", "SPORT", "TECH", "TOURS", "CURRENCY", "CRYPTO", "CULTURE", "AUTO", "FASHION")

// ─── Группировка в плитки ─────────────────────────────────────────────────────

sealed class TileGroup {
    data class Large(val item: NewsItem) : TileGroup()
    data class Pair(val a: NewsItem, val b: NewsItem) : TileGroup()
    data class Solo(val item: NewsItem) : TileGroup()
}

/** Группируем список в паттерн Lumia: Large → Pair → Large → Pair ... */
fun buildTileGroups(items: List<NewsItem>): List<TileGroup> {
    val groups  = mutableListOf<TileGroup>()
    val large   = mutableListOf<NewsItem>()
    val small   = mutableListOf<NewsItem>()

    val medium = mutableListOf<NewsItem>()
    items.forEach { item ->
        val hasPhoto = item.imageUrl != null
        val isLong   = item.summary.length > 80
        val isUrgent = item.priority >= 2 || item.category == "URGENT"
        when {
            // LARGE: есть фото, или срочное с текстом, или видео
            hasPhoto || (isUrgent && isLong) || item.isVideo -> large.add(item)
            // SMALL: короткий заголовок без тела
            item.summary.isBlank() || item.summary.length < 40 -> small.add(item)
            // MEDIUM: всё остальное
            else -> medium.add(item)
        }
    }

    // Чередуем: LARGE → два MEDIUM → три SMALL → повтор
    val lIter = large.iterator()
    val mIter = medium.iterator()
    val sIter = small.iterator()
    while (lIter.hasNext() || mIter.hasNext() || sIter.hasNext()) {
        if (lIter.hasNext()) groups.add(TileGroup.Large(lIter.next()))
        if (mIter.hasNext()) {
            val a = mIter.next()
            val b = if (mIter.hasNext()) mIter.next() else null
            if (b != null) groups.add(TileGroup.Pair(a, b))
            else groups.add(TileGroup.Solo(a))
        }
        if (sIter.hasNext()) {
            val a = sIter.next()
            val b = if (sIter.hasNext()) sIter.next() else null
            if (b != null) groups.add(TileGroup.Pair(a, b))
            else groups.add(TileGroup.Solo(a))
        }
    }
    return groups
}

// Спам-паттерны: крипто-реклама, рекламные посты
private val SPAM_PATTERNS = Regex(
    // Крипто-спам
    """\$(BTC|ETH|SOL|DOGE|BOMBIE|PEPE|SHIB|FLOKI|BONK|WIF)\b|""" +
    """airdrop|mint now|buy now|presale|whitelist|nft drop|how much.*earn|""" +
    // Игровой кликбейт — Майнкрафт, Роблокс и т.д.
    """minecraft|майнкрафт|roblox|роблокс|fortnite|форtnite|gta online|""" +
    """в игре.*ограбил|ограбление в игре|игрок.*украл|украл в minecraft|""" +
    """эпичное ограбл|жителя манкрафт|жителя minecraft|""" +
    // Блогерский псевдоконтент
    """блогер.*заработал|блогер.*миллион|тиктокер|youtuber|ютубер.*скандал|""" +
    """стример.*задонатил|донат на стриме|""" +
    // Дайджесты и рубрики "контента дня" — не новость
    """все важные новости за|главные новости за|итоги (дня|недели|месяца)|дайджест|""" +
    """что случилось за|топ-\d+ новост|лучшее за (день|неделю)|обзор (дня|недели)|""" +
    """фото дня|фото недели|видео дня|цитата дня|цифра дня|картинка дня|""" +
    """мем дня|gif дня|факт дня|слово дня|рецепт дня|афоризм дня|""" +
    // Общий промо-спам
    """подпишись и получи|переходи по ссылке|реферальн|промокод|""" +
    // Банковская и финансовая реклама — замаскированная под новость
    """открой.{0,20}(счёт|карт|вклад|счет)|кэшбэк.{0,30}(получ|верн|начисл)|""" +
    """бесплатн.{0,15}(счёт|обслужив|карт)|выгодн.{0,15}(условия|тариф|ставк)|""" +
    """(процент|ставка).{0,20}(годовых|в год)|рефинансир|ипотек.{0,20}от \d|""" +
    """оформи.{0,20}(карт|кредит|займ|вклад)|получи.{0,15}(карт|кредит|кэшбэк)|""" +
    """партнёрск.{0,10}материал|на правах рекламы|реклама\s*$|""" +
    // Скрытая реклама через "новость"
    """только до \d|успей до \d|предложение действует|""" +
    """скидка \d+%|акция|спецпредложен|выгода \d""",
    RegexOption.IGNORE_CASE
)

/**
 * ЖИЗНЕННО ВАЖНЫЕ НОВОСТИ — автоматически становятся URGENT с priority=3
 * Критерий: новость меняет твой день, требует немедленных действий или касается безопасности.
 *
 * Категории:
 * 🔌 Коммунальные отключения — вода, свет, газ, тепло
 * 🚧 Дороги и транспорт    — перекрытия, пробки, ДТП с жертвами, закрытие перевалов
 * 🌍 Стихийные бедствия    — землетрясение, наводнение, оползень, ураган, пожар
 * 🚨 Чрезвычайные ситуации — теракт, взрыв, стрельба, эвакуация, режим ЧС
 * 🏥 Здоровье и эпидемии   — вспышка болезни, карантин, отравление массовое
 * 💰 Цены на базовые товары — резкий рост цен на хлеб, топливо, лекарства
 * ✈️ Транспортные сбои     — отмена рейсов, закрытие аэропорта, забастовка
 * 🏫 Школы и работа        — внеплановые выходные, перенос рабочих дней
 * 🌐 Интернет и связь      — отключение интернета, блокировки
 */
private val VITAL_NEWS_REGEX = Regex(
    // 🔌 Коммуналка
    """отключ.{0,15}(вод|свет|электр|газ|тепл|горяч|отоплен)""" + "|" +
    """(вод|свет|электр|газ|тепл|горяч|отоплен).{0,15}отключ""" + "|" +
    """плановое отключ|аварийн.{0,10}отключ|веерн.{0,10}отключ""" + "|" +
    """нет (воды|света|электричества|газа|тепла)|перебо.{0,10}(вод|электр|газ)""" + "|" +

    // 🚧 Дороги и перевалы
    """перекрыт.{0,20}(дорог|трасс|улиц|проезд|движен)""" + "|" +
    """(дорог|трасс|перевал|шоссе|проспект).{0,20}(перекрыт|закрыт|блокир|недоступ)""" + "|" +
    """закрыт.{0,15}(перевал|тоннель|мост)|перевал.{0,15}закрыт""" + "|" +
    """(ток-мок|торугарт|иркештам|кара-балта|чуйск).{0,30}(закрыт|перекрыт)""" + "|" +
    """движение.{0,15}(ограничен|затруднен|перекрыт)|пробк.{0,10}(км|часов|бишкек)""" + "|" +
    """дтп.{0,20}(погиб|жертв|смерт|трое|четверо|пятеро|\d+ человек)""" + "|" +

    // 🌍 Стихийные бедствия
    """землетрясен|сейсм|толчк.{0,10}балл""" + "|" +
    """наводнен|паводк|затоплен|селевой|сель |оползен""" + "|" +
    """ураган|смерч|буря.{0,10}(бишкек|кг|кыргыз)|штормовое предупрежден""" + "|" +
    """лавин.{0,15}(сошла|опасност|риск)|снежн.{0,10}занос""" + "|" +
    """крупный пожар|пожар.{0,15}(жил|квартал|рынок|склад|погиб)""" + "|" +

    // 🚨 ЧС и безопасность
    """чрезвычайн.{0,10}(ситуац|положен|режим)|режим чс|введён чс""" + "|" +
    """эвакуац.{0,15}(объявл|начат|провед)|(взрыв|стрельб|теракт).{0,30}(бишкек|ош|кг)""" + "|" +
    """массов.{0,15}(задержан|арест|беспоряд)|комендантск.{0,10}час""" + "|" +

    // 🏥 Здоровье массовое
    """вспышк.{0,15}(болезн|инфекц|отравлен)|карантин.{0,15}(введ|объявл)""" + "|" +
    """массов.{0,15}отравлен|эпидемия|эпидем.{0,10}(угроз|опасн)""" + "|" +

    // 💰 Цены на базовое
    """(цен.{0,10}хлеб|цен.{0,10}бензин|цен.{0,10}топлив).{0,20}(вырос|поднял|повысил|рост)""" + "|" +
    """(бензин|дизель|топливо).{0,20}(подорожал|дефицит|нет на заправк)""" + "|" +

    // ✈️ Транспортные сбои
    """аэропорт.{0,20}(закрыт|приостановл|отмен)|рейс.{0,15}(отменён|задержан.{0,10}час)""" + "|" +
    """(маршрутк|автобус|трамвай|троллейбус).{0,20}(забасто|прекрат|не ход)""" + "|" +

    // 🏫 Школы / выходные
    """(школ|учебн).{0,20}(закрыт|карантин|переносят|внеплановый)""" + "|" +
    """нерабочий день|перенос.{0,15}(выходн|рабоч)|дополнительный выходной""" + "|" +

    // 🌐 Интернет и связь
    """(интернет|связь|сеть).{0,20}(отключ|заблокир|недоступ|упал)""" + "|" +
    """блокировк.{0,15}(telegram|whatsapp|instagram|tiktok|youtube|vpn)""" + "|" +

    // 🥊 Победа кыргызского бойца — национальная гордость, это СРОЧНО
    // Любая победа наших на мировом уровне (UFC, чемпионат мира, Олимпиада)
    """(кыргыз|кырг|kg).{0,40}(победил|нокаут|чемпион|золото|медаль|выиграл|титул)""" + "|" +
    """(победил|нокаут|чемпион|золото|медаль|выиграл|титул).{0,40}(кыргыз|кырг)""" + "|" +
    """(джумагулов|досмагамбетов|сидаков|кулибеков|жамшидов|карымов|темиров).{0,30}(победил|нокаут|выиграл|чемпион|титул)""",
    RegexOption.IGNORE_CASE
)

// Авто-определение категории по ключевым словам (когда категория = "NEWS" или дефолт)
fun enrichCategory(item: NewsItem): NewsItem {
    val t = (item.title + " " + item.summary).lowercase()

    // ── Жизненно важные новости — максимальный приоритет для ЛЮБЫХ источников ──
    if (VITAL_NEWS_REGEX.containsMatchIn(t)) {
        return item.copy(category = "URGENT", priority = maxOf(item.priority, 3))
    }

    // Если канал-источник "мягкой" категории (кино/мода/звёзды) но контент явно про другое — переопределяем
    val softCategories = setOf("CULTURE", "FASHION", "STARS", "GOOD", "LIFE")
    val overrideable = item.category in softCategories
    if (item.category !in setOf("NEWS", "НОВОСТИ", "") && !overrideable) return item
    val cat = when {
        Regex(
            // Общий спорт
            "матч|гол|победа|турнир|чемпионат|спорт|команда|игрок|тренер|лига|кубок|финал|олимпи|" +
            "футбол|баскетбол|атлет|медаль|" +
            // ═══ Боевые виды — расширенный список ═══
            // Единоборства
            "бокс|боксёр|боксер|нокаут|нокдаун|тайский бокс|кикбокс|" +
            "борьба|борец|грепплинг|самбо|дзюдо|вольн.{0,5}борьб|греко.{0,5}борьб|" +
            "mma|ммa|смешанн.{0,10}едино|единоборств|" +
            "ufc|bellator|one championship|akt|ufight|wbc|wba|wbo|ibf|" +
            // Конкретные кыргызские бойцы и имена
            "джумагулов|dosmagambetov|досмагамбетов|сидаков|карымов|" +
            "кулибеков|жамшидов|жакыпов|темиров|осмонов|" +
            // Термины поединка
            "бой|поединок|октагон|ринг|раунд|судьи|нокаут|сабмишн|" +
            "болевой|удушающ|тейкдаун|нокдаун|рефери останов|" +
            // Весовые категории
            "легковес|полусредн|средн.{0,5}вес|тяжеловес|полутяжел|" +
            "bantamweight|featherweight|lightweight|heavyweight"
        ).containsMatchIn(t) -> "SPORT"
        Regex("фильм|кино|сериал|актёр|режиссёр|премьера|концерт|театр|выставк|музык|netflix|marvel|disney|оскар|кинофест").containsMatchIn(t) -> "CULTURE"
        Regex("смартфон|телефон|iphone|samsung|xiaomi|google pixel|oneplus|android|ios|приложени|гаджет|процессор|snapdragon|чипсет|камер мегапикс|обзор телефон|новый телефон|flagship|флагман|планшет|ноутбук|искусственный интеллект|нейросет|chatgpt|gemini ai|openai|nvidia|intel|amd|чип|нейро|технолог|гаджет").containsMatchIn(t) -> "TECH"
        Regex("авто|машин|автомобил|мотоцикл|дтп|гаи|tesla|bmw|mercedes|toyota|honda|электрокар|колес|шин").containsMatchIn(t) -> "AUTO"
        Regex("тур|отдых|отель|курорт|виза|авиа|билет|рейс|пляж|море|горы|иссык|бали|турци|египет|отпуск|путешеств").containsMatchIn(t) -> "TOURS"
        Regex("мод|стиль|одежд|бренд|коллекц|дизайнер|тренд|fashion|beauty|красот|макияж|vogue").containsMatchIn(t) -> "FASHION"
        Regex("кыргыз|кыргызстан|бишкек|ош|жалал|нарын|токмок|кумтор|атамбаев|жапаров|садыр|алмазбек").containsMatchIn(t) -> "KG"
        // ⭐ Звёзды, шоубиз, знаменитости
        Regex(
            "звезда|звёзды|знаменит|селебрити|celebrity|шоубиз|шоу-биз|" +
            "певец|певица|актёр|актриса|музыкант|художник|поп.звезда|" +
            "новый альбом|клип вышел|концерт.{0,15}(бишкек|москв|алматы)|" +
            "разошлись|развелись|поженились|беременна.{0,20}(звезда|певица|актриса)|" +
            "инстаграм.{0,20}(взорвал|опубликовал)|тикток.{0,20}(вирусный|миллион)|" +
            "дрейк|бейонсе|тейлор свифт|канье|рианна|эминем|" +
            "нурлан|скриптонит|jah khalib|баста|моргенштерн|инди|" +
            "кыргызча ыр|ырчы|артист"
        ).containsMatchIn(t) -> "STARS"
        // 🏥 Здоровье, медицина, питание
        Regex(
            "здоровь|здоровый|здоровая|медицин|врач|больниц|клиник|лечен|диагноз|" +
            "болезн|симптом|препарат|лекарств|таблетк|витамин|иммунитет|" +
            "диет|питани|калори|похудел|набрал вес|спортивн.{0,10}питани|" +
            "психолог|стресс|выгорани|тревог|депресси|ментальн|" +
            "онкологи|рак.{0,10}(лечени|стади|обнаруж)|инсульт|инфаркт|диабет|" +
            "вакцин|прививк|эпидеми|вирус.{0,10}(грипп|корь|коклюш)|" +
            "народн.{0,10}средств|рецепт.{0,15}здоровь|польза.{0,15}(еды|фруктов|овощей)|" +
            "сон.{0,15}(польза|вред|норма)|вред.{0,15}(сахар|соль|жир|алкогол)"
        ).containsMatchIn(t) -> "HEALTH"
        // 💰 Деньги, финансы, экономика личная
        Regex(
            "зарплат|доход|расход|бюджет|накоплени|сбережен|инвестиц|вклад|депозит|" +
            "кредит|займ|долг|ипотек|ставка|инфляц|" +
            "как заработать|пассивный доход|финансов.{0,10}(грамотн|свобод|план)|" +
            "налог|пенси|соцвыплат|пособи|льгот|субсиди|" +
            "цен.{0,15}(выросл|упал|снизил|повысил)|дорожает|дешевеет|" +
            "курс (доллар|евро|рубл|юан|тенге|сом)|обменник|валют|" +
            "биржа|акции|облигац|дивиденд|портфель|брокер"
        ).containsMatchIn(t) -> "MONEY"
        // 💡 Лайфхаки, советы, жизнь
        Regex(
            "лайфхак|совет|как (сделать|убрать|починить|сэкономить|приготовить|выбрать)|" +
            "топ.{0,5}(советов|способов|правил|лайфхак)|" +
            "рецепт|готовим|блюдо|кухн|вкусно|быстро приготов|" +
            "уборк|чистот|порядок дома|организ.{0,10}(дом|пространств)|" +
            "отношени.{0,15}(совет|психолог|пара|семь)|" +
            "воспитани.{0,10}(детей|ребёнк)|дети|ребёнок.{0,15}(развити|здоровь)|" +
            "экономи.{0,15}(деньги|свет|воду|газ)|умный дом|" +
            "лично.{0,10}(рост|развити)|привычк|мотивац|продуктивност"
        ).containsMatchIn(t) -> "LIFE"
        // 😊 Хорошие новости — позитивные события, добрые истории, достижения
        Regex(
            "спас|помог|благотворит|подарил|рекорд установ|мировой рекорд|открыт.{0,15}(больниц|школ|парк|центр)|" +
            "впервые в истории|достижен|прорыв|изобрет|открыт.{0,10}лечен|победил болезн|" +
            "добр.{0,10}(дел|поступок|сосед|человек)|помощь|волонтёр|" +
            "иностран.{0,20}(восхищ|влюбил|кыргыз|восторг)|турист.{0,20}(влюбил|восхит|восторг|красот)|" +
            "(красота|природа|горы|иссык-куль).{0,20}(иностран|турист|восхит|поразил|удивил)|" +
            "счастлив|радост|праздник|юбилей.{0,10}(кг|бишкек)|" +
            "позитив|вдохновля|мотивир|добро"
        ).containsMatchIn(t) -> "GOOD"
        // 📰 Происшествия, криминал, события — если не попало в другие рубрики
        Regex(
            "напал|напали|атаковал|укус|ограбил|украл|задержал|арест|осудил|приговор|убийств|убил|" +
            "погиб|жертв|пострадал|ранен|госпитализ|скорую|полици|следствен|прокурор|суд|" +
            "мошенник|мошенничеств|обвинили|обвиняем|преступник|преступлени|кража|разбой|" +
            "авария|пожар|взрыв|обрушил|наводнен|затоплен|эвакуац|чп|чрезвычайн"
        ).containsMatchIn(t) -> "WORLD"
        else -> item.category
    }
    return if (cat != item.category) item.copy(category = cat) else item
}

/** Нормализует заголовок для сравнения: lowercase, без знаков, первые 55 символов */
private fun titleKey(title: String): String =
    title.lowercase()
        .replace(Regex("[^a-zа-яё0-9 ]"), "")
        .replace(Regex("\\s+"), " ")
        .trim()
        .take(55)

/**
 * Вычисляет popularity-score для новости на основе трёх сигналов:
 *
 * 1. sourceCount  — сколько независимых источников написали об этой теме.
 *                   3 источника написали одно и то же = тема важна для региона.
 * 2. telegramViews — реальные просмотры в Telegram-канале. 50K просмотров ≠ случайная новость.
 * 3. isTrending    — тема совпадает с тем что прямо сейчас ищут в Google KG/RU.
 *
 * Итоговый score используется при сортировке вместе с приоритетом и свежестью.
 */
private fun popularityScore(item: NewsItem): Int {
    var score = 0
    // 1. Google Trends KG — самый сильный сигнал: тема актуальна прямо сейчас в регионе
    if (item.isTrending) score += 60
    // 2. Просмотры Telegram — реальный интерес людей: логарифмическая шкала
    //    1K→10, 10K→20, 100K→30, 1M→40 очков
    if (item.telegramViews > 0) {
        score += (Math.log10(item.telegramViews.toDouble()) * 10).toInt().coerceIn(0, 40)
    }
    // 3. Кросс-источниковый сигнал: каждый дополнительный источник +20 очков
    score += (item.sourceCount - 1) * 20
    return score
}

fun sortItems(items: List<NewsItem>, cat: String): List<NewsItem> {
    val maxAge = 30L * 24 * 60 * 60 * 1000  // 30 дней максимум
    val now = System.currentTimeMillis()

    // Категории которые НЕ показываем в общей ленте (у них свои плитки)
    val excludedFromFeed = setOf("CURRENCY", "CRYPTO")

    val filtered = when (cat) {
        "ALL" -> items
            .filter { it.category !in excludedFromFeed }
            .filter { now - it.publishedAt < maxAge }
            .filter { !SPAM_PATTERNS.containsMatchIn(it.title) }
            .filter { it.title.length > 15 }
            .map { enrichCategory(it) }
        "URGENT" -> items
            .filter { it.category == "URGENT" || it.priority >= 3 }
            .filter { now - it.publishedAt < maxAge }
            .filter { !SPAM_PATTERNS.containsMatchIn(it.title) }
        else -> items
            .filter { it.category == cat }
            .filter { now - it.publishedAt < maxAge }
            .filter { !SPAM_PATTERNS.containsMatchIn(it.title) }
    }

    // ── Кросс-источниковый рейтинг ────────────────────────────────────────────
    // Считаем сколько раз одна тема встречается среди разных источников.
    // Важно делать ДО дедупа — после дедупа останется только одна запись.
    val titleCountMap = mutableMapOf<String, Int>()
    val titleViewsMap = mutableMapOf<String, Int>()  // суммируем просмотры по теме
    val titleTrendMap = mutableMapOf<String, Boolean>()
    filtered.forEach { item ->
        val tk = titleKey(item.title)
        titleCountMap[tk] = (titleCountMap[tk] ?: 0) + 1
        titleViewsMap[tk] = (titleViewsMap[tk] ?: 0) + item.telegramViews
        if (item.isTrending) titleTrendMap[tk] = true
    }

    // Обогащаем items агрегированными данными
    val enriched = filtered.map { item ->
        val tk = titleKey(item.title)
        val count   = titleCountMap[tk] ?: 1
        val views   = titleViewsMap[tk] ?: item.telegramViews
        val trend   = titleTrendMap[tk] ?: item.isTrending
        if (count != item.sourceCount || views != item.telegramViews || trend != item.isTrending)
            item.copy(sourceCount = count, telegramViews = views, isTrending = trend)
        else item
    }

    // Сортируем перед дедупом — лучший экземпляр (с фото/видео) остаётся
    val preSorted = enriched.sortedWith(
        compareByDescending<NewsItem> {
            when {
                it.isVideo          -> 4
                it.imageUrl != null -> 3
                cat == "ALL" && it.category == "KG" -> 2
                else                -> 0
            }
        }.thenByDescending { it.priority * 1000L + it.publishedAt / 1000L }
    )

    // Дедупликация: по URL, потом по нормализованному заголовку
    val seenUrls   = mutableSetOf<String>()
    val seenTitles = mutableSetOf<String>()
    val deduped = preSorted.filter { item ->
        val url = item.url.ifEmpty { item.title }
        val tk  = titleKey(item.title)
        if (url in seenUrls || (tk.length >= 30 && tk in seenTitles)) return@filter false
        seenUrls   += url
        if (tk.length >= 30) seenTitles += tk
        true
    }

    // ── Финальная сортировка с popularity score ───────────────────────────────
    val sorted = deduped.sortedWith(
        compareByDescending<NewsItem> {
            // Прочитанные всегда внизу (Instagram-логика)
            if (DataBridge.isSeen(it.url)) return@compareByDescending Long.MIN_VALUE
            // Базовый score: приоритет + свежесть (в часах, max 72h) + popularity
            val freshness = ((72 * 60 * 60 * 1000L - (now - it.publishedAt).coerceAtLeast(0L)) / 3_600_000L).coerceIn(0L, 72L)
            val pop  = popularityScore(it).toLong()
            val base = when {
                cat == "ALL" && it.category == "KG"   -> 200L
                cat == "ALL" && it.category == "GOOD" -> 120L  // хорошие новости поднимаем — баланс настроения
                cat == "ALL" && it.category == "TOURS"-> 80L   // туризм/вдохновение тоже вверх
                it.isVideo          -> 150L
                it.imageUrl != null -> 100L
                else                -> 0L
            }
            // Итоговый ранг: priority (0-3) * 1000 + популярность + свежесть + тип
            it.priority * 1000L + pop + freshness * 2 + base
        }
    )

    return if (cat == "ALL") interleave(sorted) else sorted
}

/** Перемешивает список чтобы одинаковые источники/категории не шли подряд */
private fun interleave(items: List<NewsItem>): List<NewsItem> {
    // Группируем по категории
    val byCategory = items.groupBy { it.category }.values.map { it.toMutableList() }
    val result = mutableListOf<NewsItem>()
    var hasMore = true
    while (hasMore) {
        hasMore = false
        for (group in byCategory) {
            if (group.isNotEmpty()) {
                result.add(group.removeFirst())
                hasMore = true
            }
        }
    }
    return result
}

// ─── Главный экран ────────────────────────────────────────────────────────────

@Composable
fun MainHomeScreen() {
    var tikTokItems by remember { mutableStateOf<List<NewsItem>>(emptyList()) }
    var tikTokStart by remember { mutableStateOf(0) }
    var youTubeUrl by remember { mutableStateOf<String?>(null) }

    // Deep link из уведомлений
    // Наблюдаем и за newsItems — при тапе приложение может только запускаться,
    // новости ещё не загружены. Как только они придут — повторяем поиск.
    val newsItemsState by DataBridge.newsItemsFlow.collectAsState()
    LaunchedEffect(DataBridge.pendingArticleUrl, DataBridge.pendingTab, newsItemsState) {
        val url = DataBridge.pendingArticleUrl
        val tab = DataBridge.pendingTab
        if (url.isNotEmpty()) {
            if (newsItemsState.isNotEmpty()) {
                // Ищем в ALL + URGENT чтобы точно найти
                val sorted = (sortItems(newsItemsState, "ALL") +
                             sortItems(newsItemsState, "URGENT")).distinctBy { it.url.ifEmpty { it.title } }
                val idx = sorted.indexOfFirst { it.url == url }
                if (idx >= 0) {
                    tikTokItems = sorted
                    tikTokStart = idx
                }
                // Очищаем только после того как новости загружены (иначе ждём следующего срабатывания)
                DataBridge.pendingArticleUrl = ""
            }
            // Если newsItemsState пуст — LaunchedEffect перезапустится когда новости придут
        }
        if (tab.isNotEmpty() && newsItemsState.isNotEmpty()) {
            DataBridge.pendingTab = ""
        }
    }

    // YouTube плеер — поверх всего
    youTubeUrl?.let { ytUrl ->
        androidx.activity.compose.BackHandler { youTubeUrl = null }
        com.mirlanmamytov.ticker247.reader.YouTubePlayerScreen(
            url = ytUrl,
            onClose = { youTubeUrl = null }
        )
        return
    }

    // Свайп "назад" на Samsung и кнопка Back: закрываем читалку, не приложение
    androidx.activity.compose.BackHandler(enabled = tikTokItems.isNotEmpty()) {
        tikTokItems = emptyList()
    }

    if (tikTokItems.isNotEmpty()) {
        TikTokReader(
            items = tikTokItems,
            startIndex = tikTokStart,
            onBack = { tikTokItems = emptyList() }
        )
        return
    }

    val context = androidx.compose.ui.platform.LocalContext.current

    var selectedTab by remember { mutableStateOf(0) }

    // Если пришёл pendingTab (из уведомления) — переключаем вкладку
    LaunchedEffect(DataBridge.pendingTab, newsItemsState) {
        val tab = DataBridge.pendingTab
        if (tab.isNotEmpty() && newsItemsState.isNotEmpty()) {
            val idx = tabCategories.indexOf(tab)
            if (idx >= 0) selectedTab = idx
            DataBridge.pendingTab = ""
        }
    }

    HomeContent(
        selectedTab = selectedTab,
        onTabSelected = { selectedTab = it },
        onOpenTikTok = { items, startIdx ->
            val item = items.getOrNull(startIdx)
            // Отмечаем как прочитанное — уйдёт вниз ленты при следующем рендере
            if (item != null) DataBridge.markSeen(item.url)
            when {
                // Видео → YouTube плеер внутри приложения
                item != null && item.isVideo && item.url.isNotEmpty() ->
                    youTubeUrl = item.url
                // Пустая новость (нет текста и нет фото) → открыть в браузере
                item != null && item.summary.isBlank() && item.imageUrl == null && item.url.isNotEmpty() -> {
                    try {
                        context.startActivity(Intent(Intent.ACTION_VIEW, android.net.Uri.parse(item.url)))
                    } catch (_: Exception) {}
                }
                // Нормальная новость → TikTok ридер
                item != null -> {
                    tikTokItems = items
                    tikTokStart = startIdx
                }
            }
        }
    )
}

// ─── Полноэкранный загрузчик — единый бренд-цвет ─────────────────────────────

@Composable
fun SplashLoadingScreen() {
    val inf = rememberInfiniteTransition(label = "splash")
    val pulseAlpha by inf.animateFloat(
        initialValue = 0.3f, targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(900, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "dot"
    )

    Box(
        Modifier.fillMaxSize().background(
            Brush.verticalGradient(listOf(Color(0xFF080810), Color(0xFF0A0A1A), Color(0xFF0D1020)))
        ),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text("⚡", fontSize = 52.sp)
            Spacer(Modifier.height(16.dp))
            Text("Ticker 24/7", fontSize = 36.sp, fontWeight = FontWeight.Black, color = Color.White, letterSpacing = (-0.5).sp)
            Spacer(Modifier.height(12.dp))
            Text("Тихо о важном", fontSize = 17.sp, fontWeight = FontWeight.Medium, color = Color(0xFF00D4FF), letterSpacing = 1.5.sp)
            Spacer(Modifier.height(48.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                repeat(3) { i ->
                    Box(
                        Modifier.size(5.dp).alpha(if (i == 1) pulseAlpha else pulseAlpha * 0.6f)
                            .background(Color(0xFF00D4FF), androidx.compose.foundation.shape.CircleShape)
                    )
                }
            }
        }
        Text(
            "Created by MMR Lab®",
            fontSize = 13.sp, color = Color(0xFF00E676),
            fontWeight = FontWeight.Bold, letterSpacing = 0.5.sp,
            modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 40.dp)
        )
    }
}

// ─── Контент главного экрана ──────────────────────────────────────────────────

@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
fun HomeContent(
    selectedTab: Int,
    onTabSelected: (Int) -> Unit,
    onOpenTikTok: (List<NewsItem>, Int) -> Unit
) {
    // Тёплый белый фон — карточки "парят"
    val bgColor     = Color(0xFFF0F4F8)
    val textColor   = Color(0xFF111827)
    val subColor    = Color(0xFF6B7280)
    val accentColor = Color(0xFF1D4ED8)
    val lazyListState = rememberLazyListState()

    // Полноэкранный загрузчик — пока нет новостей показываем весь экран в бренд-цвете
    val allItemsNow by DataBridge.newsItemsFlow.collectAsState()
    if (allItemsNow.isEmpty()) {
        SplashLoadingScreen()
        return
    }

    val context = LocalContext.current
    var isRefreshing by remember { mutableStateOf(false) }
    val refreshScope = rememberCoroutineScope()

    Scaffold(
        containerColor = bgColor,
        bottomBar = {
            Box(
                Modifier.fillMaxWidth().background(bgColor).padding(vertical = 5.dp),
                contentAlignment = Alignment.Center
            ) {
                Row(verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Box(Modifier.size(4.dp).clip(RoundedCornerShape(50)).background(Color(0xFF1D4ED8).copy(0.4f)))
                    Text("MMR® Lab", fontSize = 10.sp, color = subColor.copy(0.5f),
                        fontWeight = FontWeight.SemiBold, letterSpacing = 1.sp)
                    Box(Modifier.size(4.dp).clip(RoundedCornerShape(50)).background(Color(0xFF1D4ED8).copy(0.4f)))
                }
            }
        }
    ) { padding ->
        Column(
            modifier = Modifier.fillMaxSize().padding(padding)
        ) {
            TickerHeader()
            val pullState = androidx.compose.material3.pulltorefresh.rememberPullToRefreshState()
            androidx.compose.material3.pulltorefresh.PullToRefreshBox(
                state = pullState,
                isRefreshing = isRefreshing,
                onRefresh = {
                    refreshScope.launch {
                        isRefreshing = true
                        DataBridge.clearSeen()
                        com.mirlanmamytov.ticker247.service.TickerForegroundService.refresh(context)
                        kotlinx.coroutines.delay(2000L)
                        isRefreshing = false
                    }
                },
                modifier = Modifier.fillMaxSize(),
                indicator = {
                    androidx.compose.material3.pulltorefresh.PullToRefreshDefaults.Indicator(
                        state = pullState,
                        isRefreshing = isRefreshing,
                        color = Color(0xFF7C3AED),
                        containerColor = bgColor,
                        modifier = Modifier.align(Alignment.TopCenter)
                    )
                }
            ) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            state = lazyListState,
            contentPadding = PaddingValues(bottom = 24.dp)
        ) {

            // 2. Hero-карусель на всю ширину
            item(key = "hero_carousel") {
                HeroCarousel(onOpenTikTok = onOpenTikTok)
            }

            // 3. Две отдельные плитки: Валюта и Крипта
            item(key = "finance") {
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Box(Modifier.weight(1f)) { CurrencyTile() }
                    Box(Modifier.weight(1f)) { CryptoTile() }
                }
            }

            // 4. Плитки (три размера + живая анимация, случайный порядок)
            item(key = "tile_grid") {
                NewsTileGrid(
                    category = tabCategories[selectedTab],
                    textColor = textColor,
                    subColor = subColor,
                    onOpenTikTok = onOpenTikTok
                )
            }

            // 5. Финал ленты
            item(key = "feed_end") {
                FeedEndCard()
            }
        }
        } // конец PullToRefreshBox
        } // конец Column
    }
}

// ─── Тикер-заголовок (изолированный — не вызывает рекомпоз родителя) ──────────

@Composable
fun TickerHeader(bgColor: Color = Color(0xFFF5F7FA), accentColor: Color = Color(0xFF0070F3)) {
    val text by DataBridge.tickerFlow.collectAsState()
    // Показываем заглушку пока данные грузятся — бар всегда виден
    val displayText = text.ifEmpty { "⏳ Загрузка курсов и новостей…     ·     💵 USD  ·  🪙 BTC  ·  📰 Ticker 24/7" }
    TickerBar(text = displayText)
}

// ─── Бегущая строка ───────────────────────────────────────────────────────────
// Тёмный фон для контраста на светлой странице (как у Bloomberg/Reuters)

@Composable
fun TickerBar(text: String) {
    if (text.isBlank()) return

    val barBg    = Color(0xFF050508)
    val barText  = Color(0xFF00D4FF)
    val density  = LocalDensity.current
    val textMeasurer = rememberTextMeasurer()
    val tickerStyle  = TextStyle(
        fontSize = 13.sp,
        fontFamily = FontFamily.Monospace,
        fontWeight = FontWeight.SemiBold
    )
    val separator = "   ·   "
    // Повторяем текст 4 раза — экран всегда заполнен, нет ощущения "пустоты"
    val displayText = "$text$separator$text$separator$text$separator$text$separator"

    // Ширина одного цикла = один повтор текста + разделитель
    val cycle = remember(text) {
        textMeasurer.measure(text + separator, tickerStyle).size.width.toFloat().coerceAtLeast(1f)
    }

    val speedPxPerMs = with(density) { 90.dp.toPx() / 1000f }  // 90 dp/s

    val offset  = remember { Animatable(0f) }
    var paused  by remember { mutableStateOf(false) }

    // Перезапускается при смене текста — сбрасывает позицию и анимирует с начала
    LaunchedEffect(text, paused) {
        if (paused) { offset.stop(); return@LaunchedEffect }
        offset.snapTo(0f)
        while (isActive) {
            val durationMs = (cycle / speedPxPerMs).toInt().coerceAtLeast(50)
            offset.animateTo(-cycle, animationSpec = tween(durationMs, easing = LinearEasing))
            offset.snapTo(0f)
        }
    }

    Column(Modifier.fillMaxWidth()) {
        Box(
            Modifier
                .fillMaxWidth()
                .height(36.dp)
                .background(barBg)
                .clipToBounds()
                // Тап — пауза/возобновление
                .pointerInput(Unit) {
                    detectTapGestures { paused = !paused }
                }
                // Горизонтальный свайп — ручная прокрутка
                .pointerInput(Unit) {
                    detectHorizontalDragGestures(
                        onDragStart = { paused = true },
                        onHorizontalDrag = { change, dragX ->
                            change.consume()
                            val newVal = (offset.value + dragX).coerceIn(-cycle, 0f)
                            kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.Main)
                                .launch { offset.snapTo(newVal) }
                        },
                        onDragEnd    = { paused = false },
                        onDragCancel = { paused = false }
                    )
                }
        ) {
            Text(
                text = displayText,
                color = if (paused) barText.copy(0.6f) else barText,
                style = tickerStyle,
                maxLines = 1,
                overflow = TextOverflow.Clip,
                modifier = Modifier
                    .align(Alignment.CenterStart)
                    .wrapContentWidth(unbounded = true)
                    .offset { IntOffset(offset.value.toInt(), 0) }
            )
            // Fade-края
            Box(Modifier.width(24.dp).fillMaxHeight().align(Alignment.CenterStart)
                .background(Brush.horizontalGradient(listOf(barBg, Color.Transparent))))
            Box(Modifier.width(24.dp).fillMaxHeight().align(Alignment.CenterEnd)
                .background(Brush.horizontalGradient(listOf(Color.Transparent, barBg))))
            // Иконка паузы
            if (paused) {
                Text("⏸", fontSize = 10.sp,
                    modifier = Modifier.align(Alignment.CenterEnd).padding(end = 28.dp))
            }
        }
        // Accent line
        Box(Modifier.fillMaxWidth().height(2.dp).background(
            Brush.horizontalGradient(listOf(
                Color(0xFF6366F1), Color(0xFFA855F7), Color(0xFFEC4899), Color(0xFF6366F1)
            ))
        ))
    }
}

// ─── Плитка ВАЛЮТА — переворачивается по курсам, тап → BottomSheet ─────────────

@OptIn(ExperimentalMaterial3Api::class, androidx.compose.animation.ExperimentalAnimationApi::class)
@Composable
fun CurrencyTile() {
    val allItems by DataBridge.newsItemsFlow.collectAsState()
    val currency = remember(allItems) { allItems.firstOrNull { it.category == "CURRENCY" } }
    if (currency == null) return

    val rates = remember(currency) {
        currency.title.split("|").map { it.trim() }.filter { it.isNotEmpty() }
    }
    if (rates.isEmpty()) return

    var currentIdx by remember { mutableStateOf(0) }
    var showSheet  by remember { mutableStateOf(false) }

    LaunchedEffect(rates.size) {
        while (isActive) {
            kotlinx.coroutines.delay(2800)
            currentIdx = (currentIdx + 1) % rates.size
        }
    }

    Box(
        Modifier.fillMaxWidth().height(130.dp)
            .clip(RoundedCornerShape(16.dp))
            .clickable { showSheet = true }
    ) {
        // Яркий зелёный фон — как в EasyCounting
        Box(Modifier.fillMaxSize().background(
            Brush.linearGradient(
                listOf(Color(0xFF00C853), Color(0xFF00E676), Color(0xFF69F0AE)),
                start = androidx.compose.ui.geometry.Offset(0f, 0f),
                end   = androidx.compose.ui.geometry.Offset(Float.POSITIVE_INFINITY, Float.POSITIVE_INFINITY)
            )
        ))

        androidx.compose.animation.AnimatedContent(
            targetState = currentIdx,
            transitionSpec = {
                (androidx.compose.animation.slideInVertically { -it } +
                 androidx.compose.animation.fadeIn(tween(350)))
                    .togetherWith(
                 androidx.compose.animation.slideOutVertically { it } +
                 androidx.compose.animation.fadeOut(tween(350)))
            },
            label = "currency_flip"
        ) { idx ->
            val parts = rates.getOrNull(idx)?.split(" ") ?: return@AnimatedContent
            val code  = parts.getOrElse(0) { "" }
            val value = parts.getOrElse(1) { "" }
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("💱 ВАЛЮТА", fontSize = 9.sp, color = Color(0xFF00401A).copy(0.7f),
                        fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
                    Spacer(Modifier.height(6.dp))
                    Text(code, fontSize = 20.sp, color = Color(0xFF00401A),
                        fontWeight = FontWeight.ExtraBold)
                    Text("${value} сом", fontSize = 22.sp, color = Color.White,
                        fontWeight = FontWeight.Black)
                }
            }
        }

        // Точки
        Row(Modifier.align(Alignment.BottomCenter).padding(bottom = 6.dp),
            horizontalArrangement = Arrangement.spacedBy(3.dp)
        ) {
            repeat(minOf(rates.size, 10)) { i ->
                Box(Modifier.size(if (i == currentIdx) 5.dp else 3.dp)
                    .clip(RoundedCornerShape(50))
                    .background(if (i == currentIdx) Color.White else Color.White.copy(0.3f)))
            }
        }
    }

    if (showSheet) {
        ModalBottomSheet(onDismissRequest = { showSheet = false }) {
            CurrencyDetailSheet(currency = currency)
        }
    }
}

@Composable
fun CurrencyDetailSheet(currency: NewsItem) {
    val textColor = Color(0xFF0A0A0A)
    val subColor  = Color(0xFF6B7280)
    val rates = currency.title.split("|").map { it.trim() }.filter { it.isNotEmpty() }

    Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp).padding(bottom = 40.dp)) {
        Text("💱 Курсы валют к сому", fontSize = 18.sp, fontWeight = FontWeight.ExtraBold,
            color = textColor, modifier = Modifier.padding(bottom = 4.dp))
        Text(timeAgo(currency.publishedAt), fontSize = 12.sp, color = subColor,
            modifier = Modifier.padding(bottom = 16.dp))

        rates.forEach { rate ->
            val parts = rate.split(" ")
            val code  = parts.getOrElse(0) { "" }
            val value = parts.getOrElse(1) { "" }
            Row(
                Modifier.fillMaxWidth().padding(vertical = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(Modifier.size(40.dp).clip(RoundedCornerShape(10.dp))
                        .background(Color(0xFFE8F5E9)), contentAlignment = Alignment.Center) {
                        Text(code, fontSize = 12.sp, color = Color(0xFF2E7D32), fontWeight = FontWeight.Bold)
                    }
                    Text(code, fontSize = 16.sp, fontWeight = FontWeight.SemiBold, color = textColor)
                }
                Text("$value сом", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = Color(0xFF2E7D32))
            }
            Box(Modifier.fillMaxWidth().height(0.5.dp).background(Color(0xFFE5E7EB)))
        }
    }
}

// ─── Плитка КРИПТА — переворачивается по монетам, тап → BottomSheet ──────────

@OptIn(ExperimentalMaterial3Api::class, androidx.compose.animation.ExperimentalAnimationApi::class)
@Composable
fun CryptoTile() {
    val allItems by DataBridge.newsItemsFlow.collectAsState()
    val cryptoOrder = listOf("BTC", "ETH", "SOL", "BNB", "XRP", "DOGE", "ADA", "AVAX")
    val cryptos  = remember(allItems) {
        val all = allItems.filter { it.category == "CRYPTO" }
        val sorted = cryptoOrder.mapNotNull { sym -> all.firstOrNull { it.cryptoSymbol == sym } } +
                     all.filter { it.cryptoSymbol !in cryptoOrder }
        sorted.take(8)
    }
    if (cryptos.isEmpty()) return

    var currentIdx by remember { mutableStateOf(0) }
    var showSheet  by remember { mutableStateOf(false) }

    LaunchedEffect(cryptos.size) {
        while (isActive) {
            kotlinx.coroutines.delay(3200)
            currentIdx = (currentIdx + 1) % cryptos.size
        }
    }

    Box(
        Modifier.fillMaxWidth().height(130.dp)
            .clip(RoundedCornerShape(16.dp))
            .clickable { showSheet = true }
    ) {
        // Фиолетовый фон — крипта
        Box(Modifier.fillMaxSize().background(
            Brush.linearGradient(listOf(Color(0xFF1A0A3E), Color(0xFF4A148C)))
        ))

        androidx.compose.animation.AnimatedContent(
            targetState = currentIdx,
            transitionSpec = {
                (androidx.compose.animation.slideInVertically { -it } +
                 androidx.compose.animation.fadeIn(tween(350)))
                    .togetherWith(
                 androidx.compose.animation.slideOutVertically { it } +
                 androidx.compose.animation.fadeOut(tween(350)))
            },
            label = "crypto_flip"
        ) { idx ->
            val coin   = cryptos.getOrNull(idx) ?: return@AnimatedContent
            val price  = coin.cryptoPrice ?: 0.0
            val change = coin.cryptoChange24h ?: 0.0
            val isUp   = change >= 0
            val changeColor = if (isUp) Color(0xFF69F0AE) else Color(0xFFFF6B6B)

            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("🪙 КРИПТА", fontSize = 9.sp, color = Color.White.copy(0.6f),
                        fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
                    Spacer(Modifier.height(4.dp))
                    if (coin.cryptoIconUrl != null) {
                        AsyncImage(model = coin.cryptoIconUrl, contentDescription = null,
                            modifier = Modifier.size(26.dp).clip(RoundedCornerShape(50)),
                            contentScale = ContentScale.Crop)
                        Spacer(Modifier.height(3.dp))
                    }
                    Text(coin.cryptoSymbol ?: "", fontSize = 14.sp, color = Color(0xFFCE93D8),
                        fontWeight = FontWeight.ExtraBold)
                    Text(
                        if (price > 1000) "$${"%,.0f".format(price)}" else "$${"%.3f".format(price)}",
                        fontSize = 20.sp, color = Color.White, fontWeight = FontWeight.Bold
                    )
                    Row(verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(2.dp)
                    ) {
                        Text(if (isUp) "▲" else "▼", fontSize = 10.sp, color = changeColor)
                        Text("${"%.2f".format(Math.abs(change))}%", fontSize = 11.sp,
                            color = changeColor, fontWeight = FontWeight.SemiBold)
                    }
                }
            }
        }

        // Точки
        Row(Modifier.align(Alignment.BottomCenter).padding(bottom = 6.dp),
            horizontalArrangement = Arrangement.spacedBy(3.dp)
        ) {
            repeat(minOf(cryptos.size, 8)) { i ->
                Box(Modifier.size(if (i == currentIdx) 5.dp else 3.dp)
                    .clip(RoundedCornerShape(50))
                    .background(if (i == currentIdx) Color.White else Color.White.copy(0.3f)))
            }
        }
    }

    if (showSheet) {
        ModalBottomSheet(onDismissRequest = { showSheet = false }) {
            CryptoDetailSheet(cryptos = cryptos)
        }
    }
}

@Composable
fun CryptoDetailSheet(cryptos: List<NewsItem>) {
    val textColor = Color(0xFF0A0A0A)
    val subColor  = Color(0xFF6B7280)

    Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp).padding(bottom = 40.dp)) {
        Text("🪙 Криптовалюты", fontSize = 18.sp, fontWeight = FontWeight.ExtraBold,
            color = textColor, modifier = Modifier.padding(bottom = 16.dp))

        cryptos.forEach { coin ->
            val price  = coin.cryptoPrice ?: 0.0
            val change = coin.cryptoChange24h ?: 0.0
            val isUp   = change >= 0
            val changeColor = if (isUp) Color(0xFF2E7D32) else Color(0xFFE53935)

            Row(Modifier.fillMaxWidth().padding(vertical = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (coin.cryptoIconUrl != null) {
                        AsyncImage(model = coin.cryptoIconUrl, contentDescription = null,
                            modifier = Modifier.size(36.dp).clip(RoundedCornerShape(50)),
                            contentScale = ContentScale.Crop)
                    } else {
                        Box(Modifier.size(36.dp).clip(RoundedCornerShape(50))
                            .background(Color(0xFFEDE7F6)), contentAlignment = Alignment.Center) {
                            Text(coin.cryptoSymbol?.take(2) ?: "?", fontSize = 12.sp)
                        }
                    }
                    Column {
                        Text(coin.cryptoSymbol ?: "", fontSize = 15.sp,
                            fontWeight = FontWeight.Bold, color = textColor)
                        Text(coin.cryptoName ?: "", fontSize = 11.sp, color = subColor)
                    }
                }
                Column(horizontalAlignment = Alignment.End) {
                    Text(
                        if (price > 1000) "$${"%,.0f".format(price)}" else "$${"%.4f".format(price)}",
                        fontSize = 15.sp, fontWeight = FontWeight.Bold, color = textColor
                    )
                    Text("${if (isUp) "▲" else "▼"} ${"%.2f".format(Math.abs(change))}%",
                        fontSize = 11.sp, color = changeColor)
                }
            }
            Box(Modifier.fillMaxWidth().height(0.5.dp).background(Color(0xFFE5E7EB)))
        }
    }
}

// ─── Hero-карусель: HorizontalPager на всю ширину + точки ────────────────────

@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
fun HeroCarousel(onOpenTikTok: (List<NewsItem>, Int) -> Unit) {
    val allItems by DataBridge.newsItemsFlow.collectAsState()

    // Отбираем: приоритет 2+ или есть фото — только новости, без крипты/валюты
    val heroItems = remember(allItems) {
        val isNews = { it: NewsItem -> it.category !in setOf("CURRENCY", "CRYPTO") && it.cryptoSymbol == null }
        (allItems.filter { isNews(it) && (it.priority >= 2 || it.category == "URGENT") } +
         allItems.filter { isNews(it) && it.imageUrl != null })
            .distinctBy { it.url.ifEmpty { it.title } }
            .take(10)
    }

    if (heroItems.isEmpty()) return

    val pagerState = androidx.compose.foundation.pager.rememberPagerState(pageCount = { heroItems.size })
    val coroutineScope = rememberCoroutineScope()

    // Автопрокрутка каждые 5 секунд
    LaunchedEffect(pagerState) {
        while (isActive) {
            kotlinx.coroutines.delay(5000)
            val next = (pagerState.currentPage + 1) % heroItems.size
            try { pagerState.animateScrollToPage(next) } catch (_: Exception) {}
        }
    }

    Column(Modifier.fillMaxWidth().padding(bottom = 12.dp)) {
        // Пейджер — фиксированная высота 240dp (никогда не крашит в LazyColumn)
        Box(Modifier.fillMaxWidth().height(240.dp)) {
            androidx.compose.foundation.pager.HorizontalPager(
                state = pagerState,
                modifier = Modifier.fillMaxSize(),
                pageSpacing = 0.dp
            ) { page ->
                val item = heroItems[page]
                HeroCard(
                    item = item,
                    onClick = { onOpenTikTok(heroItems, page) }
                )
            }
        }

        // Точки-индикаторы
        Row(
            Modifier.fillMaxWidth().padding(top = 10.dp),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically
        ) {
            heroItems.forEachIndexed { i, _ ->
                val isSelected = i == pagerState.currentPage
                val dotSize by androidx.compose.animation.core.animateDpAsState(
                    targetValue = if (isSelected) 20.dp else 6.dp,
                    animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy),
                    label = "dot_$i"
                )
                Box(
                    Modifier
                        .padding(horizontal = 2.dp)
                        .height(6.dp).width(dotSize)
                        .clip(RoundedCornerShape(50))
                        .background(
                            if (isSelected) Color(0xFF6366F1) else Color(0xFFCBD5E1)
                        )
                )
            }
        }
    }
}

@Composable
fun HeroCard(item: NewsItem, onClick: () -> Unit) {
    val style = newsItemStyle(item.category)
    var imageUrl by remember(item.url) { mutableStateOf(item.imageUrl) }
    LaunchedEffect(item.url) {
        // og:image → Wikipedia → категорийный fallback (работает и для Telegram без URL)
        if (item.imageUrl == null)
            imageUrl = com.mirlanmamytov.ticker247.network.OgImageFetcher.fetch(item.url, item.title, item.category)
    }
    Box(
        Modifier.fillMaxSize()
            .padding(horizontal = 12.dp)
            .clip(RoundedCornerShape(18.dp))
            .clickable(onClick = onClick)
    ) {
        // Фото
        if (imageUrl != null) {
            AsyncImage(
                model = imageUrl,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
        } else {
            Box(Modifier.fillMaxSize().background(
                Brush.linearGradient(listOf(style.accent.copy(0.5f), Color(0xFF0A0A1A)))
            ))
        }
        // Градиент снизу
        Box(Modifier.fillMaxSize().background(
            Brush.verticalGradient(0f to Color.Transparent, 0.45f to Color.Black.copy(0.2f), 1f to Color.Black.copy(0.88f))
        ))
        // Видео-бейдж
        if (item.isVideo) {
            Box(Modifier.align(Alignment.TopEnd).padding(12.dp)
                .clip(RoundedCornerShape(6.dp)).background(Color.Red.copy(0.9f))
                .padding(horizontal = 8.dp, vertical = 3.dp)
            ) { Text("▶ YouTube", fontSize = 10.sp, color = Color.White, fontWeight = FontWeight.Bold) }
        }
        // Текст внизу
        Column(Modifier.align(Alignment.BottomStart).padding(14.dp)) {
            Box(Modifier.clip(RoundedCornerShape(6.dp)).background(style.accent.copy(0.25f))
                .padding(horizontal = 8.dp, vertical = 3.dp)
            ) { Text(style.label, fontSize = 10.sp, color = style.accent, fontWeight = FontWeight.Bold) }
            Spacer(Modifier.height(6.dp))
            Text(item.title, fontSize = 16.sp, fontWeight = FontWeight.Bold,
                color = Color.White, lineHeight = 22.sp, maxLines = 3, overflow = TextOverflow.Ellipsis)
            Spacer(Modifier.height(4.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(item.source, fontSize = 12.sp, color = style.accent, fontWeight = FontWeight.Medium)
                Text("·", fontSize = 12.sp, color = Color.White.copy(0.5f))
                Text(timeAgo(item.publishedAt), fontSize = 12.sp, color = Color.White.copy(0.6f))
            }
        }
    }
}

// ─── Старая карусель (оставляем на случай отката) ─────────────────────────────

@Composable
fun FeaturedCarousel(
    isDark: Boolean,
    textColor: Color,
    onOpenTikTok: (List<NewsItem>, Int) -> Unit
) {
    // Читаем newsItemsFlow здесь — рекомпоз только этого composable
    val allItems by DataBridge.newsItemsFlow.collectAsState()

    val featured = remember(allItems) {
        (allItems.filter { it.category == "URGENT" || it.priority >= 2 } +
                allItems.filter { it.imageUrl != null && it.category in setOf("KG", "WORLD", "NEWS") })
            .distinctBy { it.url.ifEmpty { it.title } }
            .take(8)
    }

    if (featured.isEmpty()) return

    val bgColor = if (isDark) Color(0xFF0A0A0F) else Color(0xFFF2F4F8)

    Column(Modifier.fillMaxWidth().padding(top = 8.dp)) {
        Text(
            "  ⚡ Главное",
            fontSize = 13.sp,
            color = textColor.copy(0.6f),
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.padding(start = 16.dp, bottom = 8.dp)
        )
        LazyRow(
            contentPadding = PaddingValues(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            items(featured.size, key = { featured[it].url.ifEmpty { featured[it].title } }) { idx ->
                CarouselCard(
                    item = featured[idx],
                    onClick = { onOpenTikTok(featured, idx) }
                )
            }
        }
        Spacer(Modifier.height(8.dp))
        Box(Modifier.fillMaxWidth().height(1.dp).background(
            Brush.horizontalGradient(listOf(Color.Transparent,
                Color(0xFF00D4FF).copy(0.2f), Color.Transparent))
        ))
    }
}

@Composable
fun CarouselCard(item: NewsItem, onClick: () -> Unit) {
    val style = newsItemStyle(item.category)
    var imageUrl by remember(item.url) { mutableStateOf(item.imageUrl) }
    LaunchedEffect(item.url) {
        if (item.imageUrl == null && item.url.isNotBlank())
            imageUrl = com.mirlanmamytov.ticker247.network.OgImageFetcher.fetch(item.url, item.title, item.category)
    }
    Box(
        Modifier.width(220.dp).height(140.dp)
            .clip(RoundedCornerShape(14.dp))
            .clickable(onClick = onClick)
    ) {
        if (imageUrl != null) {
            AsyncImage(
                model = imageUrl,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
        } else {
            Box(Modifier.fillMaxSize().background(
                Brush.linearGradient(listOf(style.accent.copy(0.4f), Color(0xFF0A0A0F)))
            ))
        }
        // Тёмный градиент
        Box(Modifier.fillMaxSize().background(
            Brush.verticalGradient(0f to Color.Transparent, 1f to Color.Black.copy(0.8f))
        ))
        // Бейдж
        Box(
            Modifier.align(Alignment.TopStart).padding(8.dp)
                .clip(RoundedCornerShape(6.dp))
                .background(style.accent.copy(0.2f))
                .padding(horizontal = 6.dp, vertical = 2.dp)
        ) {
            Text(style.label, fontSize = 9.sp, color = style.accent, fontWeight = FontWeight.Bold)
        }
        // Заголовок
        Text(
            item.title,
            fontSize = 13.sp, fontWeight = FontWeight.SemiBold,
            color = Color.White, lineHeight = 18.sp,
            maxLines = 3, overflow = TextOverflow.Ellipsis,
            modifier = Modifier.align(Alignment.BottomStart).padding(10.dp)
        )
    }
}

// ─── Табы ─────────────────────────────────────────────────────────────────────

@Composable
fun CategoryTabRow(
    tabs: List<String>, selectedIndex: Int, onTabSelected: (Int) -> Unit,
    accentColor: Color, textColor: Color
) {
    LazyRow(
        modifier = Modifier.padding(vertical = 8.dp),
        contentPadding = PaddingValues(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        items(tabs.size) { i ->
            val selected = i == selectedIndex
            Box(
                Modifier.clip(RoundedCornerShape(20.dp))
                    .background(if (selected) accentColor else accentColor.copy(0.1f))
                    .clickable { onTabSelected(i) }
                    .padding(horizontal = 14.dp, vertical = 7.dp)
            ) {
                Text(
                    tabs[i], fontSize = 13.sp,
                    fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
                    color = if (selected) Color(0xFF0A0A0F) else textColor
                )
            }
        }
    }
}


// ─── Сетка плиток — Nokia Lumia стиль ────────────────────────────────────────

// Паттерны рядов: случайный микс (seed от дня = каждый день другой порядок)
private val TILE_PATTERNS = listOf(
    listOf(TileSize.LARGE),
    listOf(TileSize.MEDIUM, TileSize.MEDIUM),
    listOf(TileSize.SMALL, TileSize.SMALL, TileSize.SMALL),
    listOf(TileSize.LARGE),
    listOf(TileSize.MEDIUM, TileSize.MEDIUM),
    listOf(TileSize.SMALL, TileSize.SMALL, TileSize.SMALL),
    listOf(TileSize.MEDIUM, TileSize.MEDIUM),
    listOf(TileSize.LARGE),
    listOf(TileSize.SMALL, TileSize.SMALL, TileSize.SMALL),
    listOf(TileSize.MEDIUM, TileSize.MEDIUM),
)

@Composable
fun NewsTileGrid(
    category: String = "ALL",
    textColor: Color,
    subColor: Color,
    onOpenTikTok: (List<NewsItem>, Int) -> Unit
) {
    val allItems by DataBridge.newsItemsFlow.collectAsState()
    val sorted = remember(allItems, category) { sortItems(allItems, category) }

    if (allItems.isEmpty()) return  // HomeContent уже показал SplashLoadingScreen

    // Случайный порядок паттернов — seed от дня, стабилен в рамках дня
    val shuffledPatterns = remember {
        val daySeed = System.currentTimeMillis() / (24 * 60 * 60 * 1000)
        TILE_PATTERNS.shuffled(java.util.Random(daySeed))
    }

    Column(Modifier.fillMaxWidth().padding(horizontal = 10.dp)) {
        var itemIdx = 0
        var rowCount = 0
        while (itemIdx < sorted.size) {
            // Ряд 4: первый слот контекстной рекламы (AdMob)
            if (rowCount == 4) {
                ContextualAdSlot()
                Spacer(Modifier.height(8.dp))
            }
            // Ряд 7: партнёрский слот "Реклама · Сотрудничество"
            if (rowCount == 7) {
                PromoSlot()
                Spacer(Modifier.height(8.dp))
            }
            // Ряд 10: второй слот контекстной рекламы (AdMob)
            if (rowCount == 10) {
                ContextualAdSlot()
                Spacer(Modifier.height(8.dp))
            }

            val pattern = shuffledPatterns[rowCount % shuffledPatterns.size]
            val available = sorted.size - itemIdx

            // Достаточно ли новостей для этого паттерна?
            if (available < pattern.size) {
                // Добиваем оставшиеся как MEDIUM
                val remaining = sorted.subList(itemIdx, sorted.size)
                if (remaining.isNotEmpty()) {
                    if (remaining.size == 1) {
                        val i0 = itemIdx
                        LiveTile(remaining[0], TileSize.MEDIUM, textColor, subColor) { onOpenTikTok(sorted, i0) }
                    } else {
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            remaining.forEachIndexed { offset, item ->
                                val capturedIdx = itemIdx + offset
                                Box(Modifier.weight(1f)) {
                                    LiveTile(item, TileSize.MEDIUM, textColor, subColor) { onOpenTikTok(sorted, capturedIdx) }
                                }
                            }
                        }
                    }
                }
                break
            }

            // ВАЖНО: захватываем itemIdx в val до лямбды — иначе тап открывает не ту новость
            when (pattern.size) {
                1 -> {
                    val i0 = itemIdx
                    LiveTile(sorted[i0], pattern[0], textColor, subColor) { onOpenTikTok(sorted, i0) }
                    itemIdx++
                }
                2 -> {
                    val i0 = itemIdx; val i1 = itemIdx + 1
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Box(Modifier.weight(1f)) { LiveTile(sorted[i0], pattern[0], textColor, subColor) { onOpenTikTok(sorted, i0) } }
                        Box(Modifier.weight(1f)) { LiveTile(sorted[i1], pattern[1], textColor, subColor) { onOpenTikTok(sorted, i1) } }
                    }
                    itemIdx += 2
                }
                3 -> {
                    val i0 = itemIdx; val i1 = itemIdx + 1; val i2 = itemIdx + 2
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Box(Modifier.weight(1f)) { LiveTile(sorted[i0], pattern[0], textColor, subColor) { onOpenTikTok(sorted, i0) } }
                        Box(Modifier.weight(1f)) { LiveTile(sorted[i1], pattern[1], textColor, subColor) { onOpenTikTok(sorted, i1) } }
                        Box(Modifier.weight(1f)) { LiveTile(sorted[i2], pattern[2], textColor, subColor) { onOpenTikTok(sorted, i2) } }
                    }
                    itemIdx += 3
                }
            }
            Spacer(Modifier.height(8.dp))
            rowCount++
        }
    }
}

// ─── Размеры плиток ───────────────────────────────────────────────────────────

enum class TileSize { LARGE, MEDIUM, SMALL }

// ─── Живая плитка с Lumia flip-анимацией ─────────────────────────────────────

@OptIn(androidx.compose.animation.ExperimentalAnimationApi::class)
@Composable
fun LiveTile(
    item: NewsItem,
    size: TileSize,
    textColor: Color,
    subColor: Color,
    onClick: () -> Unit
) {
    val style    = newsItemStyle(item.category)
    val isSeen   = DataBridge.isSeen(item.url)   // прочитанные приглушаем
    // og:image подгружается лениво если в RSS фото не было
    var resolvedImageUrl by remember(item.url) { mutableStateOf(item.imageUrl) }
    var showPhoto by remember(item.url) { mutableStateOf(false) }
    val flipInterval = remember(item.url) { (3500L..9000L).random() }

    // Фоновая загрузка фото: og:image → Wikipedia → категорийный fallback
    LaunchedEffect(item.url) {
        if (item.imageUrl == null) {
            // Для новостей с URL — пробуем og:image страницы
            // Для Telegram (нет URL или t.me) — сразу Wikipedia по заголовку
            val fetched = com.mirlanmamytov.ticker247.network.OgImageFetcher.fetch(
                articleUrl = item.url,
                title = item.title,
                category = item.category
            )
            if (fetched != null) resolvedImageUrl = fetched
        }
        // Анимация: стартует как только фото есть (сразу или после подгрузки)
        while (isActive) {
            if (resolvedImageUrl != null) {
                kotlinx.coroutines.delay(flipInterval)
                showPhoto = !showPhoto
            } else {
                kotlinx.coroutines.delay(1000L) // ждём появления фото
            }
        }
    }
    val hasPhoto = resolvedImageUrl != null

    val height = when (size) {
        TileSize.LARGE  -> 190.dp
        TileSize.MEDIUM -> 130.dp
        TileSize.SMALL  -> 90.dp
    }
    val corner = when (size) {
        TileSize.LARGE  -> 14.dp
        TileSize.MEDIUM -> 12.dp
        TileSize.SMALL  -> 10.dp
    }

    Box(
        Modifier.fillMaxWidth().height(height)
            .shadow(
                elevation = if (size == TileSize.LARGE) 6.dp else 3.dp,
                shape = RoundedCornerShape(corner),
                ambientColor = style.accent.copy(if (isSeen) 0.05f else 0.15f),
                spotColor = style.accent.copy(if (isSeen) 0.06f else 0.18f)
            )
            .clip(RoundedCornerShape(corner))
            .graphicsLayer { alpha = if (isSeen) 0.55f else 1f }  // прочитанные приглушены
            .clickable(onClick = onClick)
    ) {
        androidx.compose.animation.AnimatedContent(
            targetState = showPhoto && hasPhoto,
            transitionSpec = {
                (androidx.compose.animation.slideInVertically { -it } +
                 androidx.compose.animation.fadeIn(tween(400)))
                    .togetherWith(
                 androidx.compose.animation.slideOutVertically { it } +
                 androidx.compose.animation.fadeOut(tween(400)))
            },
            label = "flip_${item.url}"
        ) { isPhoto ->
            if (isPhoto) {
                Box(Modifier.fillMaxSize()) {
                    AsyncImage(
                        model = resolvedImageUrl, contentDescription = null,
                        contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize(),
                        onError = { showPhoto = false }
                    )
                    Box(Modifier.fillMaxSize().background(
                        Brush.verticalGradient(0f to Color.Transparent, 1f to Color.Black.copy(0.6f))
                    ))
                    Text(item.source.trimStart('@'), fontSize = 10.sp, color = style.accent,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.align(Alignment.BottomStart).padding(8.dp))
                    if (item.isVideo) {
                        Box(Modifier.align(Alignment.Center).size(36.dp)
                            .clip(RoundedCornerShape(50)).background(Color.Red.copy(0.85f)),
                            contentAlignment = Alignment.Center
                        ) { Text("►", fontSize = 14.sp, color = Color.White) }
                    }
                }
            } else {
                // Текстовая сторона — градиент + цветная левая черта
                Box(
                    Modifier.fillMaxSize()
                        .background(Brush.linearGradient(
                            colors = style.cardGrad,
                            start = androidx.compose.ui.geometry.Offset(0f, 0f),
                            end = androidx.compose.ui.geometry.Offset(0f, Float.POSITIVE_INFINITY)
                        ))
                ) {
                    Box(Modifier.width(3.dp).fillMaxHeight().background(style.accent.copy(0.55f)))
                    Column(
                        Modifier.fillMaxSize()
                            .padding(start = if (size == TileSize.SMALL) 10.dp else 13.dp)
                            .padding(top = if (size == TileSize.SMALL) 7.dp else 10.dp)
                            .padding(end = if (size == TileSize.SMALL) 7.dp else 10.dp)
                            .padding(bottom = if (size == TileSize.SMALL) 7.dp else 8.dp)
                    ) {
                        Box(Modifier.clip(RoundedCornerShape(50))
                            .background(style.accent.copy(0.12f))
                            .padding(horizontal = 7.dp, vertical = 2.dp)
                        ) {
                            Text(style.label,
                                fontSize = if (size == TileSize.SMALL) 8.sp else 9.sp,
                                color = style.accent, fontWeight = FontWeight.ExtraBold,
                                letterSpacing = 0.3.sp)
                        }
                        Spacer(Modifier.height(5.dp))
                        Text(
                            item.title,
                            fontSize = when (size) { TileSize.LARGE -> 14.sp; TileSize.MEDIUM -> 12.sp; TileSize.SMALL -> 11.sp },
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF111827),
                            lineHeight = when (size) { TileSize.LARGE -> 21.sp; TileSize.MEDIUM -> 17.sp; TileSize.SMALL -> 15.sp },
                            maxLines = when (size) { TileSize.LARGE -> 4; TileSize.MEDIUM -> 3; TileSize.SMALL -> 2 },
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f),
                            letterSpacing = (-0.2).sp
                        )
                        Spacer(Modifier.height(2.dp))
                        Row(verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            if (item.isVideo) {
                                Box(Modifier.clip(RoundedCornerShape(3.dp))
                                    .background(Color.Red.copy(0.85f))
                                    .padding(horizontal = 4.dp, vertical = 1.dp)
                                ) { Text("▶", fontSize = 8.sp, color = Color.White) }
                            }
                            Text(item.source.trimStart('@'), fontSize = 9.sp,
                                color = style.accent.copy(0.8f), fontWeight = FontWeight.SemiBold)
                            Text("·", fontSize = 9.sp, color = subColor.copy(0.5f))
                            Text(timeAgo(item.publishedAt), fontSize = 9.sp, color = subColor)
                        }
                    }
                }
            }
        }
    }
}

// ─── Рекламный баннер (место зарезервировано) ─────────────────────────────────

/**
 * Слайды рекламной плитки.
 * Чтобы добавить реального рекламодателя — просто замени один из слайдов
 * или добавь новый. Поле contact — ссылка куда ведёт тап.
 */
private data class PromoSlide(
    val emoji: String,
    val title: String,
    val subtitle: String,
    val contact: String,          // URL для перехода
    val accentColor: Color,
    val bgColors: List<Color>
)

// Контакт для рекламы — одно место, легко менять
private const val ADS_CONTACT = "https://t.me/tarylgan"  // замени на @mmrlab_ads после создания

// Слайды для "Реклама · Сотрудничество" — только про партнёрство
private val PROMO_SLIDES = listOf(
    PromoSlide(
        emoji = "📲",
        title = "Реклама · Сотрудничество",
        subtitle = "Напишите нам — ответим в течение дня",
        contact = ADS_CONTACT,
        accentColor = Color(0xFF10B981),
        bgColors = listOf(Color(0xFFEFFDF5), Color(0xFFD1FAE5))
    ),
    PromoSlide(
        emoji = "✍️",
        title = "Есть интересная публикация?",
        subtitle = "Разместим ваш материал в ленте — статья, анонс, интервью",
        contact = ADS_CONTACT,
        accentColor = Color(0xFF0EA5E9),
        bgColors = listOf(Color(0xFFEFF9FF), Color(0xFFE0F2FE))
    ),
)

/**
 * Нативная рекламная плитка с flip-анимацией — как новостные плитки.
 * Переворачивается между слайдами каждые 6 секунд.
 * При тапе на любой слайд — открывает контакт.
 */
// Заглушка под контекстную рекламу (Google AdMob — подключим после релиза)
@Composable
fun ContextualAdSlot() {
    val context = LocalContext.current
    Box(
        Modifier
            .fillMaxWidth()
            .height(90.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(Color(0xFFF8F4FF))
            .clickable {
                val intent = android.content.Intent(android.content.Intent.ACTION_VIEW,
                    android.net.Uri.parse(ADS_CONTACT))
                context.startActivity(intent)
            }
            .padding(horizontal = 16.dp),
        contentAlignment = Alignment.Center
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text("📢", fontSize = 28.sp)
            Column {
                Text(
                    "Здесь может быть ваша реклама",
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = Color(0xFF6366F1)
                )
                Text(
                    "Продвигайте бизнес среди читателей Ticker 24/7",
                    fontSize = 11.sp,
                    color = Color(0xFF6B7280)
                )
            }
        }
        Text(
            "реклама",
            fontSize = 9.sp,
            color = Color(0xFF9CA3AF),
            modifier = Modifier.align(Alignment.TopEnd).padding(6.dp)
        )
    }
}

@OptIn(androidx.compose.animation.ExperimentalAnimationApi::class)
@Composable
fun PromoSlot() {
    val context = LocalContext.current
    var slideIdx by remember { mutableStateOf(0) }

    // Автопереключение — такой же интервал как у новостных плиток
    LaunchedEffect(Unit) {
        while (isActive) {
            kotlinx.coroutines.delay(6000L)
            slideIdx = (slideIdx + 1) % PROMO_SLIDES.size
        }
    }

    val infiniteTransition = rememberInfiniteTransition(label = "promo_border")
    val borderAlpha by infiniteTransition.animateFloat(
        initialValue = 0.25f, targetValue = 0.7f,
        animationSpec = infiniteRepeatable(
            animation = tween(2200, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ), label = "border_alpha"
    )

    Box(
        Modifier
            .fillMaxWidth()
            .height(90.dp)                          // одна высота с SMALL-плиткой
            .shadow(3.dp, RoundedCornerShape(12.dp), ambientColor = Color(0xFF6366F1).copy(0.1f))
            .clip(RoundedCornerShape(12.dp))
    ) {
        // Flip-анимация — та же что у LiveTile (slideInVertically / slideOutVertically)
        androidx.compose.animation.AnimatedContent(
            targetState = slideIdx,
            transitionSpec = {
                (androidx.compose.animation.slideInVertically { -it } +
                 androidx.compose.animation.fadeIn(tween(350)))
                    .togetherWith(
                 androidx.compose.animation.slideOutVertically { it } +
                 androidx.compose.animation.fadeOut(tween(350)))
            },
            label = "promo_flip"
        ) { idx ->
            val slide = PROMO_SLIDES[idx]
            Box(
                Modifier
                    .fillMaxSize()
                    .background(Brush.linearGradient(slide.bgColors))
                    .border(1.5.dp, slide.accentColor.copy(borderAlpha), RoundedCornerShape(12.dp))
                    .clickable {
                        try {
                            context.startActivity(
                                android.content.Intent(
                                    android.content.Intent.ACTION_VIEW,
                                    android.net.Uri.parse(slide.contact)
                                )
                            )
                        } catch (e: Exception) { /* ignore */ }
                    }
                    .padding(horizontal = 14.dp, vertical = 10.dp)
            ) {
                // Левая цветная черта — как у новостных плиток
                Box(Modifier.width(3.dp).fillMaxHeight().background(slide.accentColor.copy(0.5f)))

                Row(
                    Modifier.fillMaxSize().padding(start = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Box(
                        Modifier.size(40.dp).clip(RoundedCornerShape(10.dp))
                            .background(slide.accentColor.copy(0.1f)),
                        contentAlignment = Alignment.Center
                    ) { Text(slide.emoji, fontSize = 20.sp) }

                    Column(Modifier.weight(1f)) {
                        Text(
                            slide.title,
                            fontSize = 12.sp, fontWeight = FontWeight.Bold,
                            color = Color(0xFF111827), maxLines = 1,
                            overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                        )
                        Text(
                            slide.subtitle,
                            fontSize = 10.sp, color = Color(0xFF6B7280),
                            lineHeight = 13.sp, maxLines = 2,
                            overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                        )
                    }
                    Text("→", fontSize = 16.sp, color = slide.accentColor)
                }

                // Метка + точки-индикаторы
                Row(
                    Modifier.align(Alignment.BottomEnd).padding(bottom = 6.dp, end = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(3.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("реклама", fontSize = 7.sp, color = slide.accentColor.copy(0.6f),
                        fontWeight = FontWeight.Bold, letterSpacing = 0.5.sp)
                    Spacer(Modifier.width(4.dp))
                    PROMO_SLIDES.indices.forEach { i ->
                        Box(
                            Modifier.size(if (i == idx) 5.dp else 3.dp)
                                .clip(RoundedCornerShape(50))
                                .background(if (i == idx) slide.accentColor else slide.accentColor.copy(0.3f))
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun AdBannerPlaceholder() {
    Box(
        Modifier.fillMaxWidth().height(52.dp)
            .shadow(1.dp, RoundedCornerShape(12.dp))
            .clip(RoundedCornerShape(12.dp))
            .background(Color(0xFFFAFBFF))
            .border(1.dp, Color(0xFFE2E8F0), RoundedCornerShape(12.dp)),
        contentAlignment = Alignment.Center
    ) {
        Row(verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Box(Modifier.size(4.dp).clip(RoundedCornerShape(50)).background(Color(0xFFCBD5E1)))
            Text("РЕКЛАМА", fontSize = 9.sp, color = Color(0xFFB0B8C4),
                fontWeight = FontWeight.Bold, letterSpacing = 2.sp)
            Box(Modifier.size(4.dp).clip(RoundedCornerShape(50)).background(Color(0xFFCBD5E1)))
        }
    }
}

// ─── Финал ленты ─────────────────────────────────────────────────────────────

@Composable
fun FeedEndCard() {
    val infiniteTransition = rememberInfiniteTransition(label = "pulse")
    val alpha by infiniteTransition.animateFloat(
        initialValue = 0.4f, targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(1800, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "alpha"
    )

    Box(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 10.dp, vertical = 16.dp)
            .height(100.dp)
            .clip(RoundedCornerShape(20.dp))
            .background(
                Brush.horizontalGradient(
                    listOf(Color(0xFF1E1B4B), Color(0xFF3730A3), Color(0xFF7C3AED), Color(0xFF1E1B4B))
                )
            ),
        contentAlignment = Alignment.Center
    ) {
        // Декоративные точки
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 24.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            repeat(5) { i ->
                Box(
                    Modifier
                        .size(4.dp)
                        .clip(RoundedCornerShape(50))
                        .background(Color.White.copy(alpha * (0.3f + i * 0.15f)))
                )
            }
        }

        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                "⚡",
                fontSize = 22.sp,
                modifier = Modifier.graphicsLayer { this.alpha = alpha }
            )
            Spacer(Modifier.height(4.dp))
            Text(
                "Ticker 24/7",
                fontSize = 14.sp,
                fontWeight = FontWeight.ExtraBold,
                color = Color.White,
                letterSpacing = 2.sp
            )
            Text(
                "обновляется каждые 5 минут",
                fontSize = 10.sp,
                color = Color.White.copy(0.5f),
                letterSpacing = 0.5.sp
            )
        }
    }
}
