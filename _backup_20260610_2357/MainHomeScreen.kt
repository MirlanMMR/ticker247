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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
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

data class CategoryStyle(val accent: Color, val cardBg: Color, val darkCardBg: Color, val label: String)

fun newsItemStyle(category: String): CategoryStyle = when (category) {
    "KG"       -> CategoryStyle(Color(0xFF00B0FF), Color(0xFFE1F5FE), Color(0xFF0D1A2E), "🇰🇬 КГ")
    "CURRENCY" -> CategoryStyle(Color(0xFF00C853), Color(0xFFE8F5E9), Color(0xFF1B2E22), "ВАЛЮТА")
    "CRYPTO"   -> CategoryStyle(Color(0xFF9C6FFF), Color(0xFFEDE7F6), Color(0xFF1E1730), "КРИПТА")
    "URGENT"   -> CategoryStyle(Color(0xFFFF6B4A), Color(0xFFFBE9E7), Color(0xFF2E1610), "⚡ СРОЧНО")
    "SPORT"    -> CategoryStyle(Color(0xFFFF8C42), Color(0xFFFFF3E0), Color(0xFF2E1C00), "СПОРТ")
    "WORLD"    -> CategoryStyle(Color(0xFF26C6DA), Color(0xFFE0F7FA), Color(0xFF0A2126), "🌍 МИР")
    "TOURS"    -> CategoryStyle(Color(0xFF29B6F6), Color(0xFFE1F5FE), Color(0xFF0D1E2E), "ТУРЫ")
    "CULTURE"  -> CategoryStyle(Color(0xFFE040FB), Color(0xFFF3E5F5), Color(0xFF220A2E), "КИНО")
    "AUTO"     -> CategoryStyle(Color(0xFFFF9100), Color(0xFFFFF8E1), Color(0xFF2E1C00), "АВТО")
    "FASHION"  -> CategoryStyle(Color(0xFFFF80AB), Color(0xFFFCE4EC), Color(0xFF2E0018), "МОДА")
    "VIRAL"    -> CategoryStyle(Color(0xFFFF4081), Color(0xFFFCE4EC), Color(0xFF280D18), "ВИРУСНОЕ")
    else       -> CategoryStyle(Color(0xFF42A5F5), Color(0xFFE3F2FD), Color(0xFF0D1A2E), "НОВОСТИ")
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

fun sortItems(items: List<NewsItem>, cat: String): List<NewsItem> {
    val filtered = when (cat) {
        "ALL" -> items
        "URGENT" -> {
            val u = items.filter { it.category == "URGENT" }
            u.ifEmpty {
                items.filter { it.category in setOf("NEWS", "SPORT", "KG") }
                    .sortedByDescending { it.priority * 1_000_000L + it.publishedAt }.take(15)
            }
        }
        else -> items.filter { it.category == cat }
    }
    return filtered
        .distinctBy { it.url.ifEmpty { it.title } }
        .sortedWith(
            compareByDescending<NewsItem> {
                if (cat == "ALL" && it.category == "KG") 1 else 0
            }.thenByDescending { it.priority * 1000L + it.publishedAt / 1000L }
        )
}

// ─── Главный экран ────────────────────────────────────────────────────────────

@Composable
fun MainHomeScreen() {
    val isDark = isSystemInDarkTheme()

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

    HomeContent(
        isDark = isDark,
        selectedTab = 0,
        onTabSelected = { },
        onOpenTikTok = { items, startIdx ->
            val item = items.getOrNull(startIdx)
            // Видео → открыть в YouTube, статью → TikTok-ридер
            if (item != null && item.isVideo && item.url.isNotEmpty()) {
                openYouTube(context, item.url)
            } else {
                tikTokItems = items
                tikTokStart = startIdx
            }
        }
    )
}

// ─── Контент главного экрана ──────────────────────────────────────────────────

@Composable
fun HomeContent(
    isDark: Boolean,
    selectedTab: Int,
    onTabSelected: (Int) -> Unit,
    onOpenTikTok: (List<NewsItem>, Int) -> Unit
) {
    val bgColor     = if (isDark) Color(0xFF0A0A0F) else Color(0xFFF2F4F8)
    val textColor   = if (isDark) Color(0xFFEAEAEA) else Color(0xFF1A1A2E)
    val subColor    = if (isDark) Color(0xFF9CA3AF) else Color(0xFF6B7280)
    val accentColor = Color(0xFF00D4FF)
    val lazyListState = rememberLazyListState()

    Scaffold(
        containerColor = bgColor,
        bottomBar = {
            Box(
                Modifier.fillMaxWidth().background(bgColor).padding(vertical = 5.dp),
                contentAlignment = Alignment.Center
            ) {
                Text("Created by MMR®", fontSize = 10.sp, color = subColor.copy(0.6f), fontWeight = FontWeight.Light)
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
                HeroCarousel(isDark = isDark, onOpenTikTok = onOpenTikTok)
            }

            // 3. Финансы — всегда видимая секция
            item(key = "finance") {
                FinanceSection(isDark = isDark, onOpenTikTok = onOpenTikTok)
            }

            // 3. Плитки (три размера + живая анимация)
            item(key = "tile_grid") {
                NewsTileGrid(
                    isDark = isDark,
                    selectedTab = selectedTab,
                    textColor = textColor,
                    subColor = subColor,
                    onOpenTikTok = onOpenTikTok
                )
            }
        }
    }
}

// ─── Тикер-заголовок (изолированный — не вызывает рекомпоз родителя) ──────────

@Composable
fun TickerHeader(bgColor: Color, accentColor: Color) {
    val text by DataBridge.tickerFlow.collectAsState()
    TickerBar(text = text, bgColor = bgColor, accentColor = accentColor)
}

// ─── Бегущая строка ───────────────────────────────────────────────────────────

@Composable
fun TickerBar(text: String, bgColor: Color, accentColor: Color) {
    if (text.isEmpty()) return

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

    val SPEED = 130f
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
        // Бесшовный цикл: snap 0f → -cycleW (идентичный контент)
        while (isActive) {
            offsetX.snapTo(0f)
            offsetX.animateTo(
                -cycleW,
                tween((cycleW / SPEED * 1000f).toInt().coerceAtLeast(3000), easing = LinearEasing)
            )
        }
    }

    Box(
        Modifier.fillMaxWidth().height(38.dp)
            .background(Brush.horizontalGradient(listOf(bgColor, accentColor.copy(0.06f), bgColor)))
            .clipToBounds()
            .pointerInput(Unit) {
                detectTapGestures(onPress = { paused = true; tryAwaitRelease(); paused = false })
            }
    ) {
        Text(
            text = fullCycle + fullCycle + fullCycle,
            color = if (paused) accentColor.copy(0.5f) else accentColor,
            style = tickerStyle,
            maxLines = 1,
            overflow = TextOverflow.Clip,
            modifier = Modifier
                .align(Alignment.CenterStart)
                .wrapContentWidth(unbounded = true)
                .offset { IntOffset(offsetX.value.dp.roundToPx(), 0) }
        )
        Box(Modifier.width(24.dp).fillMaxHeight().align(Alignment.CenterStart)
            .background(Brush.horizontalGradient(listOf(bgColor, Color.Transparent))))
        Box(Modifier.width(24.dp).fillMaxHeight().align(Alignment.CenterEnd)
            .background(Brush.horizontalGradient(listOf(Color.Transparent, bgColor))))
        if (paused) Text("⏸", fontSize = 11.sp, color = accentColor.copy(0.6f),
            modifier = Modifier.align(Alignment.CenterEnd).padding(end = 28.dp))
    }
}

// ─── Финансовая секция ────────────────────────────────────────────────────────

@Composable
fun FinanceSection(isDark: Boolean, onOpenTikTok: (List<NewsItem>, Int) -> Unit) {
    val allItems by DataBridge.newsItemsFlow.collectAsState()
    val currency = remember(allItems) { allItems.firstOrNull { it.category == "CURRENCY" } }
    val cryptos  = remember(allItems) { allItems.filter  { it.category == "CRYPTO"   }.take(8) }

    if (currency == null && cryptos.isEmpty()) return

    val accentGreen  = Color(0xFF00C853)
    val accentPurple = Color(0xFF9C6FFF)
    val bg           = if (isDark) Color(0xFF111118) else Color(0xFFF8F9FF)
    val textColor    = if (isDark) Color(0xFFEAEAEA) else Color(0xFF1A1A2E)
    val subColor     = if (isDark) Color(0xFF9CA3AF) else Color(0xFF6B7280)

    Column(Modifier.fillMaxWidth().padding(vertical = 8.dp)) {

        // ── Валюта ────────────────────────────────────────────────────────────
        currency?.let { cur ->
            val rates = cur.title.split("|").map { it.trim() }.filter { it.isNotEmpty() }
            Column(Modifier.fillMaxWidth().padding(bottom = 6.dp)) {
                Row(Modifier.padding(start = 14.dp, bottom = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text("💱", fontSize = 14.sp)
                    Text("Валюта", fontSize = 12.sp, color = subColor, fontWeight = FontWeight.SemiBold)
                    Text("·", color = subColor.copy(0.5f))
                    Text(timeAgo(cur.publishedAt), fontSize = 11.sp, color = subColor.copy(0.6f))
                }
                LazyRow(
                    contentPadding = PaddingValues(horizontal = 12.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(rates.size, key = { rates[it] }) { i ->
                        val parts = rates[i].split(" ")
                        val code  = parts.getOrElse(0) { "" }
                        val value = parts.getOrElse(1) { "" }
                        Box(
                            Modifier.clip(RoundedCornerShape(10.dp))
                                .background(if (isDark) Color(0xFF1B2E22) else Color(0xFFE8F5E9))
                                .padding(horizontal = 12.dp, vertical = 8.dp)
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text(code, fontSize = 11.sp, color = accentGreen, fontWeight = FontWeight.Bold)
                                Spacer(Modifier.height(2.dp))
                                Text(value, fontSize = 14.sp, color = textColor, fontWeight = FontWeight.SemiBold)
                                Text("сом", fontSize = 9.sp, color = subColor)
                            }
                        }
                    }
                }
            }
        }

        // ── Крипта ────────────────────────────────────────────────────────────
        if (cryptos.isNotEmpty()) {
            Row(Modifier.padding(start = 14.dp, bottom = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Text("🪙", fontSize = 14.sp)
                Text("Крипта", fontSize = 12.sp, color = subColor, fontWeight = FontWeight.SemiBold)
            }
            LazyRow(
                contentPadding = PaddingValues(horizontal = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(cryptos.size, key = { cryptos[it].url }) { i ->
                    val coin   = cryptos[i]
                    val price  = coin.cryptoPrice ?: 0.0
                    val change = coin.cryptoChange24h ?: 0.0
                    val isUp   = change >= 0
                    val changeColor = if (isUp) accentGreen else Color(0xFFFF6B4A)
                    val allCryptos = cryptos
                    Box(
                        Modifier.clip(RoundedCornerShape(10.dp))
                            .background(if (isDark) Color(0xFF1E1730) else Color(0xFFEDE7F6))
                            .clickable { onOpenTikTok(allCryptos, i) }
                            .padding(horizontal = 12.dp, vertical = 8.dp)
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            // Иконка монеты
                            if (coin.cryptoIconUrl != null) {
                                AsyncImage(
                                    model = coin.cryptoIconUrl,
                                    contentDescription = null,
                                    modifier = Modifier.size(24.dp).clip(RoundedCornerShape(50)),
                                    contentScale = ContentScale.Crop
                                )
                            } else {
                                Text(coin.cryptoSymbol?.take(2) ?: "?", fontSize = 16.sp)
                            }
                            Spacer(Modifier.height(4.dp))
                            Text(coin.cryptoSymbol ?: coin.title.take(6),
                                fontSize = 11.sp, color = accentPurple, fontWeight = FontWeight.Bold)
                            Text(
                                if (price > 1000) "$${"%,.0f".format(price)}"
                                else "${"$"}${"%.3f".format(price)}",
                                fontSize = 13.sp, color = textColor, fontWeight = FontWeight.SemiBold
                            )
                            Row(verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(2.dp)
                            ) {
                                Text(if (isUp) "▲" else "▼", fontSize = 9.sp, color = changeColor)
                                Text("${"%.1f".format(Math.abs(change))}%",
                                    fontSize = 10.sp, color = changeColor)
                            }
                        }
                    }
                }
            }
        }

        // Разделитель
        Spacer(Modifier.height(8.dp))
        Box(Modifier.fillMaxWidth().height(1.dp).padding(horizontal = 12.dp)
            .background(Color(0xFF00D4FF).copy(0.1f)))
    }
}

// ─── Hero-карусель: HorizontalPager на всю ширину + точки ────────────────────

@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
fun HeroCarousel(isDark: Boolean, onOpenTikTok: (List<NewsItem>, Int) -> Unit) {
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
                Box(
                    Modifier
                        .padding(horizontal = 3.dp)
                        .size(if (isSelected) 8.dp else 5.dp)
                        .clip(RoundedCornerShape(50))
                        .background(
                            if (isSelected) Color(0xFF00D4FF) else Color(0xFF00D4FF).copy(0.3f)
                        )
                )
            }
        }
    }
}

@Composable
fun HeroCard(item: NewsItem, onClick: () -> Unit) {
    val style = newsItemStyle(item.category)
    Box(
        Modifier.fillMaxSize()
            .padding(horizontal = 12.dp)
            .clip(RoundedCornerShape(18.dp))
            .clickable(onClick = onClick)
    ) {
        // Фото
        if (item.imageUrl != null) {
            AsyncImage(
                model = item.imageUrl,
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
    Box(
        Modifier.width(220.dp).height(140.dp)
            .clip(RoundedCornerShape(14.dp))
            .clickable(onClick = onClick)
    ) {
        if (item.imageUrl != null) {
            AsyncImage(
                model = item.imageUrl,
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

@Composable
fun NewsTileGrid(
    isDark: Boolean,
    selectedTab: Int,
    textColor: Color,
    subColor: Color,
    onOpenTikTok: (List<NewsItem>, Int) -> Unit
) {
    val allItems by DataBridge.newsItemsFlow.collectAsState()
    val sorted = remember(allItems) { sortItems(allItems, "ALL") }

    if (allItems.isEmpty()) {
        Box(Modifier.fillMaxWidth().height(300.dp), contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                CircularProgressIndicator(color = Color(0xFF00D4FF))
                Spacer(Modifier.height(12.dp))
                Text("Загружаем новости...", color = textColor, fontSize = 15.sp)
            }
        }
        return
    }

    Column(Modifier.fillMaxWidth().padding(horizontal = 10.dp)) {
        var i = 0
        var rowCount = 0
        while (i < sorted.size) {
            if (rowCount > 0 && rowCount % 6 == 0) {
                AdBannerPlaceholder()
                Spacer(Modifier.height(8.dp))
            }
            val remaining = sorted.size - i
            val item = sorted[i]
            when {
                item.priority >= 2 || (item.imageUrl != null && rowCount % 3 == 0) -> {
                    LiveTile(item, isDark, TileSize.LARGE, textColor, subColor) { onOpenTikTok(sorted, i) }
                    i++
                }
                remaining >= 2 && rowCount % 3 == 1 -> {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Box(Modifier.weight(1f)) { LiveTile(sorted[i], isDark, TileSize.MEDIUM, textColor, subColor) { onOpenTikTok(sorted, i) } }
                        Box(Modifier.weight(1f)) { LiveTile(sorted[i+1], isDark, TileSize.MEDIUM, textColor, subColor) { onOpenTikTok(sorted, i+1) } }
                    }
                    i += 2
                }
                remaining >= 3 && rowCount % 3 == 2 -> {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        repeat(3) { offset ->
                            Box(Modifier.weight(1f)) { LiveTile(sorted[i+offset], isDark, TileSize.SMALL, textColor, subColor) { onOpenTikTok(sorted, i+offset) } }
                        }
                    }
                    i += 3
                }
                else -> {
                    LiveTile(item, isDark, TileSize.MEDIUM, textColor, subColor) { onOpenTikTok(sorted, i) }
                    i++
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
    isDark: Boolean,
    size: TileSize,
    textColor: Color,
    subColor: Color,
    onClick: () -> Unit
) {
    val style    = newsItemStyle(item.category)
    val hasPhoto = item.imageUrl != null
    var showPhoto by remember(item.url) { mutableStateOf(false) }
    val flipInterval = remember(item.url) { (3000L..9000L).random() }

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
            .clip(RoundedCornerShape(corner))
            .clickable(onClick = onClick)
    ) {
        androidx.compose.animation.AnimatedContent(
            targetState = showPhoto && hasPhoto,
            transitionSpec = {
                (androidx.compose.animation.slideInVertically { -it } +
                 androidx.compose.animation.fadeIn(tween(350)))
                    .togetherWith(
                 androidx.compose.animation.slideOutVertically { it } +
                 androidx.compose.animation.fadeOut(tween(350)))
            },
            label = "flip_${item.url}"
        ) { isPhoto ->
            if (isPhoto) {
                Box(Modifier.fillMaxSize()) {
                    AsyncImage(
                        model = item.imageUrl, contentDescription = null,
                        contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize(),
                        onError = { showPhoto = false }
                    )
                    Box(Modifier.fillMaxSize().background(
                        Brush.verticalGradient(0f to Color.Transparent, 1f to Color.Black.copy(0.65f))
                    ))
                    Text(item.source, fontSize = 10.sp, color = style.accent,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.align(Alignment.BottomStart).padding(8.dp))
                    if (item.isVideo) {
                        Box(Modifier.align(Alignment.Center).size(36.dp)
                            .clip(RoundedCornerShape(50)).background(Color.Red.copy(0.85f)),
                            contentAlignment = Alignment.Center
                        ) { Text("\u25BA", fontSize = 14.sp, color = Color.White) }
                    }
                }
            } else {
                Box(
                    Modifier.fillMaxSize()
                        .background(if (isDark) style.darkCardBg else style.cardBg)
                        .padding(if (size == TileSize.SMALL) 7.dp else 10.dp)
                ) {
                    Column(Modifier.fillMaxSize()) {
                        Box(Modifier.clip(RoundedCornerShape(5.dp))
                            .background(style.accent.copy(0.15f))
                            .padding(horizontal = 5.dp, vertical = 2.dp)
                        ) {
                            Text(style.label,
                                fontSize = if (size == TileSize.SMALL) 8.sp else 9.sp,
                                color = style.accent, fontWeight = FontWeight.Bold)
                        }
                        Spacer(Modifier.height(4.dp))
                        Text(
                            item.title,
                            fontSize = when (size) { TileSize.LARGE -> 14.sp; TileSize.MEDIUM -> 12.sp; TileSize.SMALL -> 11.sp },
                            fontWeight = FontWeight.SemiBold, color = textColor,
                            lineHeight = when (size) { TileSize.LARGE -> 20.sp; TileSize.MEDIUM -> 17.sp; TileSize.SMALL -> 15.sp },
                            maxLines = when (size) { TileSize.LARGE -> 4; TileSize.MEDIUM -> 3; TileSize.SMALL -> 2 },
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f)
                        )
                        Text(timeAgo(item.publishedAt), fontSize = 9.sp, color = subColor)
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
        Modifier.fillMaxWidth().height(60.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(Color(0xFF1A1A2E).copy(0.5f)),
        contentAlignment = Alignment.Center
    ) {
        Text("\u00B7 реклама \u00B7", fontSize = 11.sp, color = Color.White.copy(0.2f))
    }
}
