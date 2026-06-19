package com.mirlanmamytov.ticker247

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.gestures.detectTapGestures
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
                    listOf(Color(0xFFF8F0FF), Color(0xFFEDD5FF)), Color(0xFF220A2E), "🎬 КИНО")
    // 🚗  Авто — нефтяной зелёный
    "AUTO"     -> CategoryStyle(Color(0xFF2E7D32), Color(0xFFF0FBF0),
                    listOf(Color(0xFFF0FBF0), Color(0xFFDDF5DD)), Color(0xFF0A2010), "🚗 АВТО")
    // 👗  Мода — тёплая роза
    "FASHION"  -> CategoryStyle(Color(0xFFC2185B), Color(0xFFFFF0F5),
                    listOf(Color(0xFFFFF0F5), Color(0xFFFFD6E8)), Color(0xFF2E0018), "👗 МОДА")
    // ✈️  Туризм — небо
    "TOURS"    -> CategoryStyle(Color(0xFF0277BD), Color(0xFFEDF6FF),
                    listOf(Color(0xFFEDF6FF), Color(0xFFD4ECFF)), Color(0xFF0D1E2E), "✈️ ТУРЫ")
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

val feedTabs      = listOf("Все", "🇰🇬 КГ", "⚡ Срочно", "🌍 Мир", "Новости", "Спорт", "Валюта", "Крипта", "Кино", "Авто", "Мода", "Туры")
val tabCategories = listOf("ALL", "KG", "URGENT", "WORLD", "NEWS", "SPORT", "CURRENCY", "CRYPTO", "CULTURE", "AUTO", "FASHION", "TOURS")

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

    items.forEach { item ->
        val isLarge = item.imageUrl != null || item.priority >= 2 ||
                item.category in setOf("URGENT", "KG", "CURRENCY", "CRYPTO")
        if (isLarge) large.add(item) else small.add(item)
    }

    // Чередуем: одна большая → два маленьких → повтор
    val lIter = large.iterator()
    val sIter = small.iterator()
    while (lIter.hasNext() || sIter.hasNext()) {
        if (lIter.hasNext()) groups.add(TileGroup.Large(lIter.next()))
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
    // Общий промо-спам
    """подпишись и получи|переходи по ссылке|реферальн|промокод""",
    RegexOption.IGNORE_CASE
)

// Авто-определение категории по ключевым словам (когда категория = "NEWS" или дефолт)
fun enrichCategory(item: NewsItem): NewsItem {
    if (item.category !in setOf("NEWS", "НОВОСТИ", "")) return item
    val t = (item.title + " " + item.summary).lowercase()
    val cat = when {
        Regex("матч|гол|победа|турнир|чемпионат|спорт|команда|игрок|тренер|лига|кубок|финал|олимпи|футбол|баскетбол|бокс|борьба|борец|атлет|медаль").containsMatchIn(t) -> "SPORT"
        Regex("фильм|кино|сериал|актёр|режиссёр|премьера|концерт|театр|выставк|музык|netflix|marvel|disney|оскар|кинофест").containsMatchIn(t) -> "CULTURE"
        Regex("авто|машин|автомобил|мотоцикл|дтп|гаи|tesla|bmw|mercedes|toyota|honda|электрокар|колес|шин").containsMatchIn(t) -> "AUTO"
        Regex("тур|отдых|отель|курорт|виза|авиа|билет|рейс|пляж|море|горы|иссык|бали|турци|египет|отпуск|путешеств").containsMatchIn(t) -> "TOURS"
        Regex("мод|стиль|одежд|бренд|коллекц|дизайнер|тренд|fashion|beauty|красот|макияж|vogue").containsMatchIn(t) -> "FASHION"
        Regex("кыргыз|кыргызстан|бишкек|ош|жалал|нарын|токмок|кумтор|атамбаев|жапаров|садыр|алмазбек").containsMatchIn(t) -> "KG"
        Regex("вирус|мем|тренд|tiktok|instagram|viral|хайп|флешмоб|челлендж|миллион просмотр|разлетел").containsMatchIn(t) -> "VIRAL"
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

fun sortItems(items: List<NewsItem>, cat: String): List<NewsItem> {
    val maxAge = 30L * 24 * 60 * 60 * 1000  // 30 дней максимум
    val now = System.currentTimeMillis()

    // Категории которые НЕ показываем в общей ленте (у них свои плитки)
    val excludedFromFeed = setOf("CURRENCY", "CRYPTO")

    val filtered = when (cat) {
        "ALL" -> items
            .filter { it.category !in excludedFromFeed }
            .filter { now - it.publishedAt < maxAge }                    // не старше 30 дней
            .filter { !SPAM_PATTERNS.containsMatchIn(it.title) }         // без спама
            .filter { it.title.length > 15 }                             // без пустышек
            .map { enrichCategory(it) }                                   // авто-категоризация
        "URGENT" -> {
            // Только реально срочные: category==URGENT (взрыв/теракт/катастрофа/breaking)
            // или priority==3 (победа наших атлетов) — НЕ priority>=2 (это просто важные источники)
            items.filter { it.category == "URGENT" || it.priority >= 3 }
                .filter { now - it.publishedAt < maxAge }
                .filter { !SPAM_PATTERNS.containsMatchIn(it.title) }
        }
        else -> items
            .filter { it.category == cat }
            .filter { now - it.publishedAt < maxAge }
            .filter { !SPAM_PATTERNS.containsMatchIn(it.title) }
    }

    // Сортируем сначала — лучшие экземпляры (с фото/видео) идут первыми при дедупе
    val preSorted = filtered.sortedWith(
        compareByDescending<NewsItem> {
            when {
                it.isVideo            -> 4   // видео — максимум
                it.imageUrl != null   -> 3   // фото
                cat == "ALL" && it.category == "KG" -> 2
                else                  -> 0
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

    return deduped.sortedWith(
        compareByDescending<NewsItem> {
            when {
                cat == "ALL" && it.category == "KG" -> 4
                it.isVideo            -> 3   // видео — сначала
                it.imageUrl != null   -> 2   // потом фото
                else                  -> 0
            }
        }.thenByDescending { it.priority * 1000L + it.publishedAt / 1000L }
    )
}

// ─── Главный экран ────────────────────────────────────────────────────────────

@Composable
fun MainHomeScreen() {
    var tikTokItems by remember { mutableStateOf<List<NewsItem>>(emptyList()) }
    var tikTokStart by remember { mutableStateOf(0) }

    // Deep link из уведомлений
    LaunchedEffect(DataBridge.pendingArticleUrl, DataBridge.pendingTab) {
        val url = DataBridge.pendingArticleUrl
        if (url.isNotEmpty()) {
            val sorted = sortItems(DataBridge.newsItems, "ALL")
            val idx = sorted.indexOfFirst { it.url == url }
            if (idx >= 0) { tikTokItems = sorted; tikTokStart = idx }
            DataBridge.pendingArticleUrl = ""
        }
        DataBridge.pendingTab = ""
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

    HomeContent(
        selectedTab = selectedTab,
        onTabSelected = { selectedTab = it },
        onOpenTikTok = { items, startIdx ->
            val item = items.getOrNull(startIdx)
            when {
                // Видео → YouTube
                item != null && item.isVideo && item.url.isNotEmpty() ->
                    openYouTube(context, item.url)
                // Пустая новость (нет текста и нет фото) → открыть в браузере
                item != null && item.summary.isBlank() && item.imageUrl == null && item.url.isNotEmpty() -> {
                    try {
                        context.startActivity(Intent(Intent.ACTION_VIEW, android.net.Uri.parse(item.url)))
                    } catch (_: Exception) {}
                }
                // Нормальная новость → TikTok ридер
                item != null ->  {
                    tikTokItems = items
                    tikTokStart = startIdx
                }
            }
        }
    )
}

// ─── Контент главного экрана ──────────────────────────────────────────────────

@Composable
fun HomeContent(
    selectedTab: Int,
    onTabSelected: (Int) -> Unit,
    onOpenTikTok: (List<NewsItem>, Int) -> Unit
) {
    // Тёплый белый фон — карточки "парят"
    val bgColor     = Color(0xFFF0F4F8)   // тёплый light slate
    val textColor   = Color(0xFF111827)   // почти чёрный — не резкий
    val subColor    = Color(0xFF6B7280)
    val accentColor = Color(0xFF1D4ED8)   // насыщенный indigo-blue
    val lazyListState = rememberLazyListState()

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
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            state = lazyListState,
            contentPadding = PaddingValues(bottom = 24.dp)
        ) {
            // 1. Тикер (только бегущая строка, без кнопок)
            item(key = "ticker_header") {
                TickerHeader(bgColor = bgColor, accentColor = accentColor)
            }

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
    }
}

// ─── Тикер-заголовок (изолированный — не вызывает рекомпоз родителя) ──────────

@Composable
fun TickerHeader(bgColor: Color = Color(0xFFF5F7FA), accentColor: Color = Color(0xFF0070F3)) {
    val text by DataBridge.tickerFlow.collectAsState()
    TickerBar(text = text)
}

// ─── Бегущая строка ───────────────────────────────────────────────────────────
// Тёмный фон для контраста на светлой странице (как у Bloomberg/Reuters)

@Composable
fun TickerBar(text: String) {
    if (text.isEmpty()) return

    // Градиент indigo → deep violet — «вечерний эфир», тонкая электро-полоска снизу
    val barGrad  = Brush.horizontalGradient(listOf(Color(0xFF1E1B4B), Color(0xFF3730A3), Color(0xFF1E1B4B)))
    val barBg    = Color(0xFF1E1B4B)   // используется только для fade-краёв
    val barText  = Color(0xFFF0ABFC)   // мягкий violet-pink — не режет глаза на тёмном
    val density      = LocalDensity.current
    val textMeasurer = rememberTextMeasurer()
    val tickerStyle  = TextStyle(
        fontSize = 13.sp,
        fontFamily = FontFamily.Monospace,
        fontWeight = FontWeight.SemiBold
    )
    val separator = "          ·          "
    val fullCycle = text + separator

    val cycleWidthDp = remember(fullCycle) {
        val px = textMeasurer.measure(fullCycle, tickerStyle).size.width.toFloat()
        with(density) { px.toDp() }
    }

    // 90 dp/s — плавнее, чуть медленнее предыдущего (было 130)
    val SPEED = 90f
    val offsetX = remember(text) { Animatable(420f) }
    var paused  by remember { mutableStateOf(false) }

    LaunchedEffect(text, paused) {
        if (paused) return@LaunchedEffect
        val cycleW = cycleWidthDp.value
        // Первый проход: вход справа
        offsetX.snapTo(420f)
        offsetX.animateTo(
            -cycleW,
            tween(((420f + cycleW) / SPEED * 1000f).toInt(), easing = LinearEasing)
        )
        // Бесшовный цикл: snap 0f → -cycleW (идентичный контент, скачка не видно)
        while (isActive) {
            offsetX.snapTo(0f)
            offsetX.animateTo(
                -cycleW,
                tween((cycleW / SPEED * 1000f).toInt().coerceAtLeast(4000), easing = LinearEasing)
            )
        }
    }

    Column(Modifier.fillMaxWidth()) {
      Box(
        Modifier.fillMaxWidth().height(36.dp)
            .background(barGrad)
            .clipToBounds()
            .pointerInput(Unit) {
                detectTapGestures(onPress = { paused = true; tryAwaitRelease(); paused = false })
            }
      ) {
        Text(
            text = fullCycle + fullCycle + fullCycle,
            color = if (paused) barText.copy(0.35f) else barText,
            style = tickerStyle,
            maxLines = 1,
            overflow = TextOverflow.Clip,
            modifier = Modifier
                .align(Alignment.CenterStart)
                .wrapContentWidth(unbounded = true)
                .offset { IntOffset(offsetX.value.dp.roundToPx(), 0) }
        )
        // Fade-края под цвет градиента
        Box(Modifier.width(32.dp).fillMaxHeight().align(Alignment.CenterStart)
            .background(Brush.horizontalGradient(listOf(barBg, Color.Transparent))))
        Box(Modifier.width(32.dp).fillMaxHeight().align(Alignment.CenterEnd)
            .background(Brush.horizontalGradient(listOf(Color.Transparent, barBg))))
        if (paused) Text("⏸", fontSize = 10.sp, color = barText.copy(0.5f),
            modifier = Modifier.align(Alignment.CenterEnd).padding(end = 36.dp))
      }
      // Accent line — электрическая полоска под бегущей строкой
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
        // Зелёный фон — деньги
        Box(Modifier.fillMaxSize().background(
            Brush.linearGradient(listOf(Color(0xFF1B5E20), Color(0xFF388E3C)))
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
                    Text("💱 ВАЛЮТА", fontSize = 9.sp, color = Color.White.copy(0.6f),
                        fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
                    Spacer(Modifier.height(6.dp))
                    Text(code, fontSize = 20.sp, color = Color(0xFF69F0AE),
                        fontWeight = FontWeight.ExtraBold)
                    Text("${value} сом", fontSize = 22.sp, color = Color.White,
                        fontWeight = FontWeight.Bold)
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
    val cryptos  = remember(allItems) { allItems.filter { it.category == "CRYPTO" }.take(8) }
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

    // Отбираем: приоритет 2+ или есть фото — максимум 10 карточек
    val heroItems = remember(allItems) {
        (allItems.filter { it.priority >= 2 || it.category == "URGENT" } +
         allItems.filter { it.imageUrl != null })
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
        if (item.imageUrl == null && item.url.isNotBlank())
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

    if (allItems.isEmpty()) {
        Box(
            Modifier.fillMaxWidth().height(420.dp)
                .background(Brush.verticalGradient(
                    listOf(Color(0xFF1E1B4B), Color(0xFF312E81), Color(0xFF1E1B4B))
                )),
            contentAlignment = Alignment.Center
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally,
                   verticalArrangement = Arrangement.spacedBy(20.dp)) {
                // Пульсирующая молния
                val infiniteTransition = rememberInfiniteTransition(label = "pulse")
                val scale by infiniteTransition.animateFloat(
                    initialValue = 0.85f, targetValue = 1.15f,
                    animationSpec = infiniteRepeatable(
                        tween(900, easing = FastOutSlowInEasing),
                        RepeatMode.Reverse
                    ), label = "scale"
                )
                val alpha by infiniteTransition.animateFloat(
                    initialValue = 0.6f, targetValue = 1f,
                    animationSpec = infiniteRepeatable(
                        tween(900, easing = FastOutSlowInEasing),
                        RepeatMode.Reverse
                    ), label = "alpha"
                )
                Box(
                    Modifier.size(88.dp)
                        .graphicsLayer(scaleX = scale, scaleY = scale, alpha = alpha)
                        .clip(RoundedCornerShape(28.dp))
                        .background(Brush.radialGradient(
                            listOf(Color(0xFF7C3AED), Color(0xFF4C1D95))
                        )),
                    contentAlignment = Alignment.Center
                ) {
                    Text("⚡", fontSize = 40.sp)
                }
                Text(
                    "Ticker 24/7",
                    fontSize = 26.sp, fontWeight = FontWeight.ExtraBold,
                    color = Color.White, letterSpacing = (-0.5).sp
                )
                Text(
                    "Загружаем новости...",
                    fontSize = 14.sp, color = Color(0xFFA78BFA)
                )
                CircularProgressIndicator(
                    modifier = Modifier.size(24.dp),
                    color = Color(0xFF7C3AED),
                    strokeWidth = 2.dp
                )
            }
        }
        return
    }

    // Случайный порядок паттернов — seed от дня, стабилен в рамках дня
    val shuffledPatterns = remember {
        val daySeed = System.currentTimeMillis() / (24 * 60 * 60 * 1000)
        TILE_PATTERNS.shuffled(java.util.Random(daySeed))
    }

    Column(Modifier.fillMaxWidth().padding(horizontal = 10.dp)) {
        var itemIdx = 0
        var rowCount = 0
        while (itemIdx < sorted.size) {
            // Реклама каждые 7 рядов
            if (rowCount > 0 && rowCount % 7 == 0) {
                AdBannerPlaceholder()
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
                        LiveTile(remaining[0], TileSize.MEDIUM, textColor, subColor) { onOpenTikTok(sorted, itemIdx) }
                    } else {
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            remaining.forEachIndexed { offset, item ->
                                Box(Modifier.weight(1f)) {
                                    LiveTile(item, TileSize.MEDIUM, textColor, subColor) { onOpenTikTok(sorted, itemIdx + offset) }
                                }
                            }
                        }
                    }
                }
                break
            }

            when (pattern.size) {
                1 -> {
                    LiveTile(sorted[itemIdx], pattern[0], textColor, subColor) { onOpenTikTok(sorted, itemIdx) }
                    itemIdx++
                }
                2 -> {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Box(Modifier.weight(1f)) { LiveTile(sorted[itemIdx], pattern[0], textColor, subColor) { onOpenTikTok(sorted, itemIdx) } }
                        Box(Modifier.weight(1f)) { LiveTile(sorted[itemIdx+1], pattern[1], textColor, subColor) { onOpenTikTok(sorted, itemIdx+1) } }
                    }
                    itemIdx += 2
                }
                3 -> {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Box(Modifier.weight(1f)) { LiveTile(sorted[itemIdx], pattern[0], textColor, subColor) { onOpenTikTok(sorted, itemIdx) } }
                        Box(Modifier.weight(1f)) { LiveTile(sorted[itemIdx+1], pattern[1], textColor, subColor) { onOpenTikTok(sorted, itemIdx+1) } }
                        Box(Modifier.weight(1f)) { LiveTile(sorted[itemIdx+2], pattern[2], textColor, subColor) { onOpenTikTok(sorted, itemIdx+2) } }
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
    // og:image подгружается лениво если в RSS фото не было
    var resolvedImageUrl by remember(item.url) { mutableStateOf(item.imageUrl) }
    val hasPhoto = resolvedImageUrl != null
    var showPhoto by remember(item.url) { mutableStateOf(false) }
    val flipInterval = remember(item.url) { (3500L..9000L).random() }

    // Фоновая загрузка og:image для статей без фото
    LaunchedEffect(item.url) {
        if (item.imageUrl == null && item.url.isNotBlank()) {
            val fetched = com.mirlanmamytov.ticker247.network.OgImageFetcher.fetch(item.url, item.title, item.category)
            if (fetched != null) resolvedImageUrl = fetched
        }
    }

    LaunchedEffect(item.url) {
        if (!hasPhoto) return@LaunchedEffect
        kotlinx.coroutines.delay(flipInterval)
        while (isActive) {
            kotlinx.coroutines.delay(flipInterval)
            showPhoto = !showPhoto
        }
    }

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
                ambientColor = style.accent.copy(0.15f),
                spotColor = style.accent.copy(0.18f)
            )
            .clip(RoundedCornerShape(corner))
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
