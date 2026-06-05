package com.mirlanmamytov.ticker247

import android.content.Intent
import android.net.Uri
import androidx.compose.runtime.LaunchedEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.activity.compose.BackHandler
import androidx.browser.customtabs.CustomTabsIntent
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import coil.compose.AsyncImage
import com.mirlanmamytov.ticker247.data.model.NewsItem
import com.mirlanmamytov.ticker247.reader.ReaderScreen
import com.mirlanmamytov.ticker247.ui.screens.OnboardingViewModel

enum class MainAppCategory(val title: String) {
    CURRENCY("💱 Валюта"), CRYPTO("🪙 Крипта"), NEWS("📰 Новости"),
    SPORT("🥊 Спорт"), TOURS("✈️ Туры"), FUEL("⛽ Топливо"),
    EV_CHARGING("🔋 EV"), TRENDS("🔥 Тренды"), CULTURE("🎬 Кино"),
    TECH("💻 Технологии"), AUTO("🚗 Авто"), FASHION("👗 Мода"),
    REALTY("🏠 Недвижимость"), VIRAL("🔥 Вирусное")
}

val feedTabs = listOf("Все", "⚡ Срочно", "Новости", "Спорт", "Валюта", "Крипта", "🔥 Вирусное", "Технологии", "Кино", "Авто", "Мода", "Туры")
val tabCategories = listOf("ALL", "URGENT", "NEWS", "SPORT", "CURRENCY", "CRYPTO", "VIRAL", "TECH", "CULTURE", "AUTO", "FASHION", "TOURS")

data class CategoryStyle(val accent: Color, val cardBg: Color, val darkCardBg: Color, val label: String)

fun newsItemStyle(category: String): CategoryStyle = when (category) {
    "CURRENCY" -> CategoryStyle(Color(0xFF00C853), Color(0xFFE8F5E9), Color(0xFF1A3D2B), "ВАЛЮТА")
    "CRYPTO"   -> CategoryStyle(Color(0xFF9C6FFF), Color(0xFFEDE7F6), Color(0xFF261A45), "КРИПТА")
    "URGENT"   -> CategoryStyle(Color(0xFFFF6B4A), Color(0xFFFBE9E7), Color(0xFF3D1A0F), "СРОЧНО")
    "SPORT"    -> CategoryStyle(Color(0xFFFF8C42), Color(0xFFFFF3E0), Color(0xFF3D2200), "СПОРТ")
    "TOURS"    -> CategoryStyle(Color(0xFF29B6F6), Color(0xFFE1F5FE), Color(0xFF0A2233), "ТУРЫ")
    "FUEL"     -> CategoryStyle(Color(0xFFFFD740), Color(0xFFFFFDE7), Color(0xFF2E2600), "ТОПЛИВО")
    "EV"       -> CategoryStyle(Color(0xFF18FFFF), Color(0xFFE0F7FA), Color(0xFF002B2E), "EV")
    "TRENDS"   -> CategoryStyle(Color(0xFFFF4081), Color(0xFFFCE4EC), Color(0xFF2D0A18), "ТРЕНДЫ")
    "CULTURE"  -> CategoryStyle(Color(0xFFE040FB), Color(0xFFF3E5F5), Color(0xFF2A0A33), "КИНО")
    "TECH"     -> CategoryStyle(Color(0xFF00E676), Color(0xFFE8F5E9), Color(0xFF003D1A), "ТЕХНОЛОГИИ")
    "AUTO"     -> CategoryStyle(Color(0xFFFF9100), Color(0xFFFFF8E1), Color(0xFF3D2200), "АВТО")
    "FASHION"  -> CategoryStyle(Color(0xFFFF80AB), Color(0xFFFCE4EC), Color(0xFF3D0020), "МОДА")
    "REALTY"   -> CategoryStyle(Color(0xFF69F0AE), Color(0xFFE8F5E9), Color(0xFF003320), "НЕДВИЖИМОСТЬ")
    "VIRAL"    -> CategoryStyle(Color(0xFFFF4081), Color(0xFFFCE4EC), Color(0xFF2D0A18), "ВИРУСНОЕ")
    else       -> CategoryStyle(Color(0xFF42A5F5), Color(0xFFE3F2FD), Color(0xFF0D1F33), "НОВОСТИ")
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainHomeScreen(viewModel: OnboardingViewModel = hiltViewModel()) {

    val isOnboardingDone by viewModel.isOnboardingDone.collectAsState()
    var showSettings by remember { mutableStateOf(false) }
    var readerUrl by remember { mutableStateOf<String?>(null) }
    var readerTitle by remember { mutableStateOf("") }
    var readerSource by remember { mutableStateOf("") }
    var selectedTab by remember { mutableStateOf(0) }
    val selectedCategories = remember { mutableStateListOf<MainAppCategory>() }
    var showManualSetup by remember { mutableStateOf(false) }
    val context = LocalContext.current

    val isDark = isSystemInDarkTheme()
    val bgColor      = if (isDark) Color(0xFF0A0A0F) else Color(0xFFF2F4F8)
    val surfaceColor = if (isDark) Color(0xFF13131A) else Color(0xFFFFFFFF)
    val textColor    = if (isDark) Color(0xFFEAEAEA) else Color(0xFF1A1A2E)
    val subColor     = if (isDark) Color(0xFF6B7280) else Color(0xFF9CA3AF)
    val accentColor  = Color(0xFF00D4FF)

    // Reader
    if (readerUrl != null) {
        BackHandler { readerUrl = null }
        ReaderScreen(
            url = readerUrl!!, title = readerTitle, source = readerSource,
            isDark = isDark, onBack = { readerUrl = null }
        )
        return
    }

    // Загрузка DataStore
    if (isOnboardingDone == null) {
        Box(Modifier.fillMaxSize().background(bgColor), contentAlignment = Alignment.Center) {
            CircularProgressIndicator(color = accentColor)
        }
        return
    }

    // Онбординг
    if (isOnboardingDone == false || showSettings) {
        if (showSettings) BackHandler { showSettings = false }
        OnboardingContent(
            selectedCategories = selectedCategories,
            showManualSetup = showManualSetup,
            textColor = textColor, surfaceColor = surfaceColor,
            bgColor = bgColor, subTextColor = subColor,
            onShowManualSetup = { showManualSetup = true },
            onAcceptRecommended = {
                selectedCategories.addAll(MainAppCategory.values())
                viewModel.saveAndFinish("AUTO")
                showSettings = false
            },
            onAcceptCustom = {
                viewModel.saveAndFinish("AUTO")
                showSettings = false
            }
        )
        return
    }

    // Назад на вкладке
    if (selectedTab != 0) BackHandler { selectedTab = 0 }

    // Данные — читаем из StateFlow для надёжного обновления UI
    val allItems by DataBridge.newsItemsFlow.collectAsStateWithLifecycle()
    val tickerText by DataBridge.tickerFlow.collectAsStateWithLifecycle()

    // Scroll-state для отслеживания "прочитанных" новостей
    val lazyListState = androidx.compose.foundation.lazy.rememberLazyListState()

    // Когда элемент уходит за верхний край — помечаем прочитанным
    LaunchedEffect(lazyListState.firstVisibleItemIndex) {
        val idx = lazyListState.firstVisibleItemIndex
        if (idx > 0) {
            // Берём элемент что только что ушёл вверх (учитываем header items)
            val headerCount = if (tickerText.isNotEmpty()) 3 else 2 // header + ticker + tabs
            val newsIdx = idx - headerCount
            if (newsIdx >= 0 && newsIdx < allItems.size) {
                val item = allItems[newsIdx]
                com.mirlanmamytov.ticker247.data.repository.NewsBuffer.markSeen(item.url)
            }
        }
    }

    val filteredItems: List<NewsItem> = when (tabCategories.getOrElse(selectedTab) { "ALL" }) {
        "ALL"    -> allItems.toList()
        "URGENT" -> allItems.filter { it.category == "URGENT" }.ifEmpty {
            allItems.filter { it.category in setOf("NEWS", "SPORT", "TECH") }
                .sortedByDescending { it.priority }.take(15)
        }
        else -> allItems.filter { it.category == tabCategories.getOrElse(selectedTab) { "ALL" } }
    }

    // Один большой LazyColumn — избегаем weight(1f) который не работает в некоторых конфигурациях
    val sorted = if (allItems.isNotEmpty()) {
        try { filteredItems.sortedByDescending { it.priority * 1000L + it.publishedAt / 1000 } }
        catch (e: Exception) { filteredItems }
    } else emptyList()

    Box(Modifier.fillMaxSize().background(bgColor)) {
        LazyColumn(Modifier.fillMaxSize(), state = lazyListState) {
            // Шапка с отступом под статус-бар
            item {
                Row(
                    Modifier.fillMaxWidth().background(bgColor)
                        .statusBarsPadding()
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("⚡", fontSize = 18.sp)
                    Spacer(Modifier.width(6.dp))
                    Text("Ticker 24/7", fontWeight = FontWeight.Bold, fontSize = 19.sp, color = textColor,
                        modifier = Modifier.weight(1f))
                    IconButton(onClick = { showSettings = true }) {
                        Icon(Icons.Default.Settings, null, tint = subColor, modifier = Modifier.size(20.dp))
                    }
                }
            }

            // Бегущая строка
            if (tickerText.isNotEmpty()) {
                item { TickerBar(text = tickerText, bgColor = surfaceColor, accentColor = accentColor) }
            }

            // Табы
            item {
                LazyRow(
                    modifier = Modifier.padding(vertical = 10.dp),
                    contentPadding = PaddingValues(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(feedTabs.size) { i ->
                        val selected = i == selectedTab
                        Box(
                            Modifier.clip(RoundedCornerShape(20.dp))
                                .background(if (selected) accentColor else accentColor.copy(0.1f))
                                .clickable { selectedTab = i }
                                .padding(horizontal = 16.dp, vertical = 8.dp)
                        ) {
                            Text(feedTabs[i], fontSize = 13.sp,
                                fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
                                color = if (selected) Color(0xFF0A0A0F) else textColor)
                        }
                    }
                }
            }

            // Лента
            if (allItems.isEmpty()) {
                item {
                    Box(
                        Modifier.fillMaxWidth().height(400.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            CircularProgressIndicator(color = accentColor)
                            Spacer(Modifier.height(16.dp))
                            Text("Загружаем данные...", color = textColor, fontSize = 16.sp)
                            Spacer(Modifier.height(8.dp))
                            Text("Можете свернуть — тикер работает в фоне.", color = subColor, fontSize = 13.sp)
                        }
                    }
                }
            } else {
                items(sorted, key = { it.url + it.title }) { item ->
                    when (item.category) {
                        "CURRENCY" -> CurrencyCard(item, isDark, textColor, subColor)
                        "CRYPTO"   -> CryptoCard(item, isDark, textColor, subColor)
                        else -> FeedCard(item, isDark, textColor, subColor) {
                            if (item.url.isNotEmpty()) {
                                try {
                                    CustomTabsIntent.Builder().setShowTitle(true).build()
                                        .launchUrl(context, Uri.parse(item.url))
                                } catch (e: Exception) {
                                    context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(item.url)))
                                }
                            }
                        }
                    }
                }
                item { Spacer(Modifier.height(80.dp)) }
            }
        }

        // Футер поверх контента внизу
        Box(
            Modifier.align(Alignment.BottomCenter).fillMaxWidth()
                .background(bgColor.copy(alpha = 0.95f))
                .navigationBarsPadding()
                .padding(vertical = 6.dp),
            contentAlignment = Alignment.Center
        ) {
            Text("Created by MMR®", fontSize = 11.sp, color = subColor, fontWeight = FontWeight.Light)
        }
    }
}

// ── БЕГУЩАЯ СТРОКА ──────────────────────────────────────────────────────────

@Composable
fun TickerBar(text: String, bgColor: Color, accentColor: Color) {
    if (text.isEmpty()) return
    val repeated = "$text  ·  $text  ·  $text  ·  "
    val offsetX = remember { Animatable(400f) }
    var paused by remember { mutableStateOf(false) }
    LaunchedEffect(text, paused) {
        if (paused) return@LaunchedEffect
        offsetX.animateTo(-2200f, infiniteRepeatable(tween(18000, easing = LinearEasing), RepeatMode.Restart))
    }
    Column {
        Box(
            Modifier.fillMaxWidth()
                .background(Brush.horizontalGradient(listOf(bgColor, accentColor.copy(0.06f), bgColor)))
                .pointerInput(Unit) { detectTapGestures(onPress = { paused = true; tryAwaitRelease(); paused = false }) }
        ) {
            Text(repeated, color = if (paused) accentColor.copy(0.5f) else accentColor,
                fontSize = 13.sp, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.SemiBold,
                maxLines = 1, overflow = TextOverflow.Clip,
                modifier = Modifier.padding(vertical = 10.dp).offset(x = offsetX.value.dp))
            Box(Modifier.width(20.dp).fillMaxHeight().align(Alignment.CenterStart)
                .background(Brush.horizontalGradient(listOf(bgColor, Color.Transparent))))
            Box(Modifier.width(20.dp).fillMaxHeight().align(Alignment.CenterEnd)
                .background(Brush.horizontalGradient(listOf(Color.Transparent, bgColor))))
            if (paused) {
                Box(Modifier.align(Alignment.CenterEnd).padding(end = 28.dp)
                    .background(accentColor.copy(0.2f), RoundedCornerShape(6.dp))
                    .padding(horizontal = 8.dp, vertical = 3.dp)) { Text("⏸", fontSize = 11.sp) }
            }
        }
        Box(Modifier.fillMaxWidth().height(1.dp).background(
            Brush.horizontalGradient(listOf(Color.Transparent, accentColor.copy(0.6f), accentColor, accentColor.copy(0.6f), Color.Transparent))))
    }
}

// ── КАРТОЧКА ВАЛЮТ ──────────────────────────────────────────────────────────

@Composable
fun CurrencyCard(item: NewsItem, isDark: Boolean, textColor: Color, subColor: Color) {
    val style = newsItemStyle("CURRENCY")
    val cardBg = if (isDark) style.darkCardBg else style.cardBg
    val rates = item.title.split("|").map { it.trim() }.filter { it.isNotEmpty() }
    Card(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 5.dp),
        shape = RoundedCornerShape(16.dp), colors = CardDefaults.cardColors(cardBg),
        elevation = CardDefaults.cardElevation(if (isDark) 0.dp else 2.dp)) {
        Column(Modifier.padding(14.dp)) {
            Text("КУРСЫ ВАЛЮТ", fontSize = 11.sp, color = style.accent, fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
            Spacer(Modifier.height(10.dp))
            rates.forEach { rate ->
                val parts = rate.split(" ")
                Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text(parts.firstOrNull() ?: "", fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = textColor)
                    Text(parts.drop(1).joinToString(" "), fontSize = 15.sp, color = style.accent, fontFamily = FontFamily.Monospace)
                }
                if (rate != rates.last()) HorizontalDivider(color = textColor.copy(0.06f))
            }
            Spacer(Modifier.height(4.dp))
            Text("${item.source}  ·  ${timeAgo(item.publishedAt)}", fontSize = 11.sp, color = subColor)
        }
    }
}

// ── КРИПТО-КАРТОЧКА ──────────────────────────────────────────────────────────

@Composable
fun CryptoCard(item: NewsItem, isDark: Boolean, textColor: Color, subColor: Color) {
    val style = newsItemStyle("CRYPTO")
    val cardBg = if (isDark) style.darkCardBg else style.cardBg
    val change = item.cryptoChange24h ?: 0.0
    val isUp = change >= 0
    Card(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 5.dp),
        shape = RoundedCornerShape(16.dp), colors = CardDefaults.cardColors(cardBg),
        elevation = CardDefaults.cardElevation(if (isDark) 0.dp else 2.dp)) {
        Row(Modifier.fillMaxWidth().padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
            if (!item.cryptoIconUrl.isNullOrEmpty()) {
                AsyncImage(model = item.cryptoIconUrl, contentDescription = null,
                    modifier = Modifier.size(42.dp).clip(CircleShape))
                Spacer(Modifier.width(12.dp))
            }
            Column(Modifier.weight(1f)) {
                Text(item.cryptoName ?: item.title, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = textColor)
                Text(item.cryptoSymbol ?: "", fontSize = 12.sp, color = subColor)
            }
            Column(horizontalAlignment = Alignment.End) {
                Text(item.cryptoPrice?.let { "$${"%,.0f".format(it)}" } ?: "—",
                    fontSize = 16.sp, fontWeight = FontWeight.Bold, color = textColor, fontFamily = FontFamily.Monospace)
                Text("${if (isUp) "▲" else "▼"} ${"%.2f".format(Math.abs(change))}%",
                    fontSize = 13.sp, fontWeight = FontWeight.Medium,
                    color = if (isUp) Color(0xFF00C853) else Color(0xFFFF5252))
            }
        }
    }
}

// ── ОБЫЧНАЯ КАРТОЧКА ─────────────────────────────────────────────────────────

@Composable
fun FeedCard(item: NewsItem, isDark: Boolean, textColor: Color, subColor: Color, onTap: () -> Unit) {
    val style = newsItemStyle(item.category)
    val cardBg = if (isDark) style.darkCardBg else style.cardBg
    Card(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 5.dp).clickable { onTap() },
        shape = RoundedCornerShape(16.dp), colors = CardDefaults.cardColors(cardBg),
        elevation = CardDefaults.cardElevation(if (isDark) 0.dp else 2.dp)) {
        Row(Modifier.fillMaxWidth()) {
            Box(Modifier.width(4.dp).heightIn(min = 70.dp).fillMaxHeight()
                .background(Brush.verticalGradient(listOf(style.accent, style.accent.copy(0.3f))),
                    RoundedCornerShape(topStart = 16.dp, bottomStart = 16.dp)))
            Column(Modifier.weight(1f).padding(12.dp)) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text(style.label, fontSize = 10.sp, color = style.accent, fontWeight = FontWeight.Bold, letterSpacing = 0.8.sp)
                    Text(timeAgo(item.publishedAt), fontSize = 11.sp, color = subColor)
                }
                Spacer(Modifier.height(5.dp))
                if (!item.imageUrl.isNullOrEmpty()) {
                    AsyncImage(model = item.imageUrl, contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxWidth().height(160.dp).clip(RoundedCornerShape(10.dp)))
                    Spacer(Modifier.height(8.dp))
                }
                Text(item.title, fontSize = 15.sp, fontWeight = FontWeight.Medium, color = textColor, lineHeight = 21.sp)
                if (item.summary.isNotEmpty() && item.summary != item.title) {
                    Spacer(Modifier.height(4.dp))
                    Text(item.summary.take(120), fontSize = 13.sp, color = subColor,
                        lineHeight = 18.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
                }
            }
        }
    }
}

// ── ОНБОРДИНГ ────────────────────────────────────────────────────────────────

@Composable
private fun OnboardingContent(
    selectedCategories: MutableList<MainAppCategory>,
    showManualSetup: Boolean,
    textColor: Color, surfaceColor: Color, bgColor: Color, subTextColor: Color,
    onShowManualSetup: () -> Unit,
    onAcceptRecommended: () -> Unit,
    onAcceptCustom: () -> Unit
) {
    Box(Modifier.fillMaxSize().background(bgColor)) {
        Column(Modifier.fillMaxSize().padding(28.dp),
            verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
            Text("⚡", fontSize = 52.sp, textAlign = TextAlign.Center)
            Spacer(Modifier.height(12.dp))
            Text("Ticker 24/7", fontSize = 32.sp, fontWeight = FontWeight.Bold, color = textColor, textAlign = TextAlign.Center)
            Text("by MMR®", fontSize = 14.sp, color = subTextColor, textAlign = TextAlign.Center)
            Spacer(Modifier.height(40.dp))
            Button(onClick = onAcceptRecommended, modifier = Modifier.fillMaxWidth().height(54.dp),
                shape = RoundedCornerShape(14.dp), colors = ButtonDefaults.buttonColors(Color(0xFF007AFF))) {
                Text("Принять рекомендованные", fontSize = 16.sp, color = Color.White)
            }
            Spacer(Modifier.height(12.dp))
            if (!showManualSetup) {
                OutlinedButton(onClick = onShowManualSetup, modifier = Modifier.fillMaxWidth().height(54.dp),
                    shape = RoundedCornerShape(14.dp)) {
                    Text("Настроить под себя", fontSize = 16.sp, color = textColor)
                }
            } else {
                Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(14.dp),
                    colors = CardDefaults.cardColors(surfaceColor)) {
                    Column(Modifier.padding(16.dp)) {
                        Text("Категории", fontWeight = FontWeight.Bold, color = textColor, fontSize = 15.sp)
                        Spacer(Modifier.height(8.dp))
                        MainAppCategory.values().forEach { cat ->
                            val checked = selectedCategories.contains(cat)
                            Row(Modifier.fillMaxWidth().height(42.dp).toggleable(checked,
                                onValueChange = { if (it) selectedCategories.add(cat) else selectedCategories.remove(cat) }),
                                verticalAlignment = Alignment.CenterVertically) {
                                Checkbox(checked = checked, onCheckedChange = null)
                                Text(cat.title, Modifier.padding(start = 12.dp), color = textColor)
                            }
                        }
                    }
                }
                Spacer(Modifier.height(16.dp))
                Button(onClick = onAcceptCustom, Modifier.fillMaxWidth().height(54.dp),
                    shape = RoundedCornerShape(14.dp), colors = ButtonDefaults.buttonColors(Color(0xFF007AFF))) {
                    Text("Сохранить", fontSize = 16.sp, color = Color.White)
                }
            }
        }
        Text("Created by MMR®", fontSize = 12.sp, color = subTextColor,
            modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 24.dp), fontWeight = FontWeight.Light)
    }
}

fun timeAgo(timestamp: Long): String {
    val diff = System.currentTimeMillis() - timestamp
    val min = diff / 60_000; val h = min / 60
    return when { min < 1 -> "только что"; min < 60 -> "$min мин"; h < 24 -> "$h ч"; else -> "${h/24} дн" }
}
