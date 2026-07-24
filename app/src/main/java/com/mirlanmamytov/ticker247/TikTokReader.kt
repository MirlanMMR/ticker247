package com.mirlanmamytov.ticker247

import android.content.Intent
import android.net.Uri
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.pager.VerticalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.mirlanmamytov.ticker247.data.model.NewsItem
import com.mirlanmamytov.ticker247.network.GeminiSummarizer
import com.mirlanmamytov.ticker247.network.OgImageFetcher
import com.mirlanmamytov.ticker247.reader.ArticleExtractor
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Ссылка для шаринга — TG-канал Ticker 24/7 на языке пользователя:
 * получатель видит живой канал с новостями и ссылкой на приложение.
 */
private fun buildShareUrl(): String {
    val lang = java.util.Locale.getDefault().language
    val cyrillic = setOf("ru", "ky", "kk", "uz", "tg", "be", "uk", "bg", "sr", "mk")
    val channel = when {
        lang in cyrillic -> "t247feed"
        lang == "es" -> "t247feed_es"
        lang == "pt" -> "t247feed_pt"
        else -> "t247feed_en"
    }
    // telegram.me вместо t.me: операторы ряда стран блокируют t.me,
    // а telegram.me открывается и браузером, и приложением Telegram
    return "https://telegram.me/$channel"
}

@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
fun TikTokReader(
    items: List<NewsItem>,
    startIndex: Int,
    onBack: () -> Unit
) {
    if (items.isEmpty()) { onBack(); return }

    val LOOP = 10_000
    val safeStart = startIndex.coerceIn(0, items.lastIndex)
    val pagerState = rememberPagerState(
        initialPage = LOOP / 2 - (LOOP / 2 % items.size) + safeStart,
        pageCount = { LOOP }
    )

    Box(Modifier.fillMaxSize().background(Color.Black)) {
            VerticalPager(
                state = pagerState,
                modifier = Modifier.fillMaxSize(),
                // Лёгкий свайп: достаточно протянуть 15% экрана чтобы перейти к следующей новости
                flingBehavior = androidx.compose.foundation.pager.PagerDefaults.flingBehavior(
                    state = pagerState,
                    snapPositionalThreshold = 0.15f
                )
            ) { page ->
                TikTokPage(item = items[page % items.size], onBack = onBack)
            }

            // Кнопка закрыть
            IconButton(
                onClick = onBack,
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(top = 8.dp, start = 12.dp)
            ) {
                Box(
                    Modifier.size(36.dp).clip(RoundedCornerShape(50))
                        .background(Color.Black.copy(0.5f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Default.Close, null, tint = Color.White, modifier = Modifier.size(20.dp))
                }
            }

            // Индикатор страниц справа
            Column(
                modifier = Modifier
                    .align(Alignment.CenterEnd)
                    .padding(end = 8.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                val total   = minOf(items.size, 20)
                val current = pagerState.currentPage % items.size
                repeat(total) { i ->
                    val isActive = i == current % total
                    val size by animateDpAsState(
                        targetValue = if (isActive) 10.dp else 5.dp,
                        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy),
                        label = "dot"
                    )
                    Box(
                        Modifier.size(size)
                            .clip(RoundedCornerShape(50))
                            .background(if (isActive) Color.White else Color.White.copy(0.35f))
                    )
                }
            }
        }
}

@Composable
private fun TikTokPage(item: NewsItem, onBack: () -> Unit) {
    val context    = LocalContext.current
    val style      = newsItemStyle(item.category)
    val accentCol  = style.accent

    // AI резюме — только по запросу пользователя
    var aiSummary   by remember { mutableStateOf<GeminiSummarizer.AiSummary?>(null) }
    var aiLoading   by remember { mutableStateOf(false) }
    var aiRequested by remember { mutableStateOf(false) }

    // Фото — если нет в посте, подгружаем через OgImageFetcher
    var resolvedImage by remember(item.url) { mutableStateOf(item.imageUrl) }
    LaunchedEffect(item.url) {
        if (resolvedImage == null) {
            resolvedImage = withContext(Dispatchers.IO) {
                OgImageFetcher.fetch(item.url, item.title, item.category)
            }
        }
    }

    // Полный текст статьи — подгружаем ТОЛЬКО если summary реально пустой или
    // дублирует заголовок. Короткие summary расширяет бэкенд (уже с переводом) —
    // тянуть сырой текст с сайта нельзя: он придёт на языке оригинала
    val isEmptyBody = item.summary.isBlank() ||
        item.summary.trimEnd('.', ' ') == item.title.trimEnd('.', ' ')
    var articleBody by remember(item.url) { mutableStateOf(if (isEmptyBody) "" else item.summary) }
    var articleLoading by remember(item.url) { mutableStateOf(false) }

    LaunchedEffect(item.url) {
        // Если тело короткое/пустое и есть URL — извлекаем статью целиком,
        // чтобы читатель мог остаться в приложении
        if (isEmptyBody && item.url.isNotEmpty() && !item.url.contains("t.me/")) {
            articleLoading = true
            val result = withContext(Dispatchers.IO) {
                ArticleExtractor.extract(item.url).getOrNull()
            }
            articleBody = result?.body?.takeIf { it.length > 50 } ?: ""
            articleLoading = false
        }
    }

    val scope = rememberCoroutineScope()
    val onAiRequest = {
        if (!aiLoading && aiSummary == null) {
            aiRequested = true
            aiLoading = true
            scope.launch {
                val bodyForAi = articleBody.ifEmpty { item.summary }
                aiSummary = GeminiSummarizer.summarize(item.title, bodyForAi, item.url)
                aiLoading = false
            }
        }
    }

    // Честное предупреждение вместо фильтрации: деликатные темы (насилие,
    // эксплицитный контент, суицид) не скрываем из ленты — размываем только
    // обложку до тапа. Заголовок и текст остаются читаемыми как есть.
    val isSensitive = remember(item.url) {
        com.mirlanmamytov.ticker247.util.SensitiveContent.isSensitive(item.title, item.summary)
    }
    var revealSensitive by remember(item.url) { mutableStateOf(false) }
    val imageBlur = if (isSensitive && !revealSensitive) 28.dp else 0.dp

    Column(
        Modifier
            .fillMaxSize()
            .background(Color(0xFF0A0A0F))
            .verticalScroll(rememberScrollState())
    ) {
        // ── Фото отдельным блоком сверху ────────────────────────────────────
        Box(
            Modifier.fillMaxWidth().wrapContentHeight()
        ) {
            if (resolvedImage != null) {
                AsyncImage(
                    model = resolvedImage,
                    contentDescription = null,
                    contentScale = ContentScale.FillWidth,
                    modifier = Modifier.fillMaxWidth().blur(imageBlur),
                    onError = { _ -> resolvedImage = null }
                )
            } else {
                // Цветной градиент с эмодзи категории вместо нерелевантного Unsplash фото
                val categoryEmoji = when (item.category) {
                    "URGENT"   -> "⚡"
                    "KG"       -> "🇰🇬"
                    "WORLD"    -> "🌍"
                    "SPORT"    -> "🏆"
                    "CRYPTO"   -> "🪙"
                    "CURRENCY" -> "💵"
                    "TECH"     -> "💻"
                    "HEALTH"   -> "🏥"
                    "AUTO"     -> "🚗"
                    "CULTURE"  -> "🎭"
                    "TOURS"    -> "✈️"
                    "FASHION"  -> "👗"
                    "STARS"    -> "⭐"
                    "GOOD"     -> "😊"
                    "MONEY"    -> "💰"
                    else       -> "📰"
                }
                Box(
                    Modifier.fillMaxSize()
                        .background(
                            Brush.verticalGradient(
                                listOf(accentCol.copy(0.4f), Color(0xFF0A0A0F))
                            )
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Text(categoryEmoji, fontSize = 64.sp)
                }
            }
            // Нижний градиент
            Box(
                Modifier.fillMaxSize().background(
                    Brush.verticalGradient(
                        0f to Color.Transparent,
                        0.7f to Color.Transparent,
                        1f to Color(0xFF0A0A0F)
                    )
                )
            )
            // Бейдж: СРОЧНО или редакторская метка (#метка:)
            val readerBadge = item.editorLabel ?: style.label.takeIf { item.category == "URGENT" }
            if (readerBadge != null) {
                Box(
                    Modifier.align(Alignment.BottomStart).padding(14.dp)
                        .clip(RoundedCornerShape(50))
                        .background(accentCol.copy(0.9f))
                        .padding(horizontal = 10.dp, vertical = 4.dp)
                ) {
                    Text(readerBadge, fontSize = 11.sp, color = Color.White, fontWeight = FontWeight.ExtraBold)
                }
            }
            // Предупреждение о деликатной теме — тап по обложке снимает размытие
            if (isSensitive && !revealSensitive) {
                Box(
                    Modifier.fillMaxSize()
                        .clickable { revealSensitive = true },
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Box(
                            Modifier.clip(RoundedCornerShape(50))
                                .background(Color.Black.copy(0.6f))
                                .padding(horizontal = 14.dp, vertical = 7.dp)
                        ) {
                            Text("⚠️ Деликатная тема", fontSize = 13.sp, color = Color.White,
                                fontWeight = FontWeight.Bold)
                        }
                        Spacer(Modifier.height(6.dp))
                        Text("Нажмите, чтобы посмотреть", fontSize = 11.sp,
                            color = Color.White.copy(0.7f))
                    }
                }
            }
        }

        // ── Текстовый контент на тёмном фоне ────────────────────────────────
        Column(
            Modifier
                .fillMaxWidth()
                .padding(start = 20.dp, end = 20.dp, top = 12.dp, bottom = 100.dp)
        ) {
            Text(
                item.title,
                fontSize = 20.sp, fontWeight = FontWeight.ExtraBold,
                color = Color.White, lineHeight = 28.sp
            )
            // Прозрачность автоперевода: не латаем конкретные слова
            // (идиомы/титулы неизбежно иногда переводятся криво), а честно
            // предупреждаем и даём оригинал по тапу
            if (item.translated && !item.origTitle.isNullOrBlank()) {
                var showOriginal by remember(item.url) { mutableStateOf(false) }
                Spacer(Modifier.height(6.dp))
                Text(
                    "🌐 Переведено автоматически, возможны неточности · показать оригинал",
                    fontSize = 11.sp, color = Color(0xFF00D4FF).copy(0.75f),
                    modifier = Modifier.clickable { showOriginal = !showOriginal }
                )
                if (showOriginal) {
                    Spacer(Modifier.height(4.dp))
                    Text(
                        item.origTitle,
                        fontSize = 13.sp, color = Color.White.copy(0.6f),
                        fontStyle = androidx.compose.ui.text.font.FontStyle.Italic,
                        lineHeight = 18.sp
                    )
                }
            }
            Spacer(Modifier.height(16.dp))

                // ── AI-резюме: ЧТО / ГДЕ / КОГДА ────────────────────────────
                when {
                    aiLoading -> {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.padding(vertical = 8.dp)
                        ) {
                            CircularProgressIndicator(modifier = Modifier.size(14.dp), color = accentCol, strokeWidth = 2.dp)
                            Text("AI анализирует...", fontSize = 13.sp, color = Color.White.copy(0.5f))
                        }
                    }
                    aiSummary != null -> {
                        AiSummaryCard(summary = aiSummary!!, accent = accentCol)
                        Spacer(Modifier.height(14.dp))
                    }
                    else -> {}
                }

                // Тело статьи — сначала загруженное, потом summary
                // Если извлечение полной статьи не удалось — не теряем короткое
                // описание из RSS, показываем хотя бы его
                val bodyText = articleBody.ifEmpty { item.summary }
                when {
                    articleLoading && bodyText.isEmpty() -> {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.padding(vertical = 8.dp)
                        ) {
                            CircularProgressIndicator(modifier = Modifier.size(14.dp), color = accentCol, strokeWidth = 2.dp)
                            Text("Загружаем статью...", fontSize = 13.sp, color = Color.White.copy(0.5f))
                        }
                    }
                    bodyText.isNotEmpty() -> {
                        Text(bodyText, fontSize = 15.sp, color = Color.White.copy(0.85f), lineHeight = 24.sp)
                        Spacer(Modifier.height(16.dp))
                    }
                }

                // Метаданные
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(item.source.trimStart('@'), fontSize = 13.sp,
                        color = accentCol, fontWeight = FontWeight.SemiBold)
                    Text("·", color = Color.White.copy(0.4f))
                    Text(timeAgo(item.publishedAt), fontSize = 13.sp, color = Color.White.copy(0.5f))
                }

                // Кнопка «Читать полностью» если есть URL
                if (item.url.isNotEmpty()) {
                    Spacer(Modifier.height(16.dp))
                    OutlinedButton(
                        onClick = {
                            try { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(item.url))) }
                            catch (_: Exception) {}
                        },
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = accentCol),
                        border = BorderStroke(1.dp, accentCol.copy(0.5f)),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text("Читать на сайте →", fontSize = 13.sp)
                    }
                }

            Spacer(Modifier.height(20.dp))

            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                IconButton(onClick = {
                    val shareText = buildString {
                        append("⚡ ${item.title}\n\n")
                        if (item.summary.isNotEmpty()) append("${item.summary.take(300)}\n\n")
                        append("📲 Ticker 24/7 — новости в реальном времени\n")
                        append(buildShareUrl())
                    }
                    context.startActivity(
                        Intent.createChooser(
                            Intent(Intent.ACTION_SEND).apply {
                                type = "text/plain"
                                putExtra(Intent.EXTRA_TEXT, shareText)
                                putExtra(Intent.EXTRA_SUBJECT, item.title)
                            }, "Поделиться новостью"
                        )
                    )
                }) {
                    Icon(Icons.Default.Share, null, tint = Color.White.copy(0.7f))
                }
            }
        }
    }
}

// ── AI-карточка: ЧТО / ГДЕ / КОГДА ──────────────────────────────────────────

@Composable
private fun AiSummaryCard(
    summary: GeminiSummarizer.AiSummary,
    accent: Color
) {
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(Color.White.copy(0.08f))
            .padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        // Заголовок карточки
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Text("✦", fontSize = 12.sp, color = accent)
            Text("AI-резюме", fontSize = 11.sp, color = accent,
                fontWeight = FontWeight.ExtraBold, letterSpacing = 0.5.sp)
        }

        // Суть одной фразой
        if (summary.brief.isNotEmpty()) {
            Text(
                summary.brief,
                fontSize = 14.sp, color = Color.White.copy(0.92f),
                lineHeight = 21.sp, fontWeight = FontWeight.Medium
            )
        }

        Spacer(Modifier.height(2.dp))
        Box(Modifier.fillMaxWidth().height(0.5.dp).background(Color.White.copy(0.12f)))
        Spacer(Modifier.height(2.dp))

        // ЧТО / ГДЕ / КОГДА
        AiRow("ЧТО", summary.what, accent)
        if (summary.where != "не указано") AiRow("ГДЕ", summary.where, accent)
        if (summary.`when` != "не указано") AiRow("КОГДА", summary.`when`, accent)
    }
}

@Composable
private fun AiRow(label: String, value: String, accent: Color) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.Top
    ) {
        Box(
            Modifier
                .padding(top = 2.dp)
                .clip(RoundedCornerShape(4.dp))
                .background(accent.copy(0.15f))
                .padding(horizontal = 5.dp, vertical = 2.dp)
        ) {
            Text(label, fontSize = 9.sp, color = accent, fontWeight = FontWeight.ExtraBold,
                letterSpacing = 0.5.sp)
        }
        Text(value, fontSize = 13.sp, color = Color.White.copy(0.85f), lineHeight = 19.sp)
    }
}

// ── Вспомогательная кнопка действия ──────────────────────────────────────────

@Composable
private fun ActionButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    onClick: () -> Unit
) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        IconButton(onClick = onClick) {
            Box(
                Modifier.size(46.dp).clip(RoundedCornerShape(50))
                    .background(Color.White.copy(0.15f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(icon, null, tint = Color.White, modifier = Modifier.size(22.dp))
            }
        }
        Text(label, fontSize = 10.sp, color = Color.White.copy(0.6f))
    }
}
