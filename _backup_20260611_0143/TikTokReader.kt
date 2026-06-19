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
import kotlinx.coroutines.launch

// Веб-версия приложения (Firebase Hosting) — открывается если нет приложения
private const val WEB_BASE = "https://ticker247.web.app"

/**
 * Формирует ссылку для шаринга.
 * Получатель без приложения → веб-страница с превью + кнопка «Скачать»
 * Получатель с приложением → (будущий deep link) откроет статью напрямую
 */
private fun buildShareUrl(item: NewsItem): String {
    val enc = Uri.encode(item.url.take(400))
    val title = Uri.encode(item.title.take(120))
    return "$WEB_BASE/?s=$enc&t=$title"
}

@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
fun TikTokReader(
    items: List<NewsItem>,
    startIndex: Int,
    onBack: () -> Unit
) {
    if (items.isEmpty()) { onBack(); return }

    val pagerState = rememberPagerState(
        initialPage = startIndex.coerceIn(0, items.lastIndex),
        pageCount = { items.size }
    )

    Box(Modifier.fillMaxSize().background(Color.Black)) {
        VerticalPager(
            state = pagerState,
            modifier = Modifier.fillMaxSize()
        ) { page ->
            TikTokPage(
                item = items[page],
                onBack = onBack
            )
        }

        // Кнопка закрыть
        IconButton(
            onClick = onBack,
            modifier = Modifier
                .align(Alignment.TopStart)
                .padding(top = 48.dp, start = 12.dp)
        ) {
            Box(
                Modifier.size(36.dp).clip(RoundedCornerShape(50))
                    .background(Color.Black.copy(0.5f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Default.Close, null, tint = Color.White, modifier = Modifier.size(20.dp))
            }
        }

        // Индикатор страниц справа — анимированный
        Column(
            modifier = Modifier
                .align(Alignment.CenterEnd)
                .padding(end = 8.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            val total   = minOf(items.size, 20)
            val current = pagerState.currentPage
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
    var expanded   by remember { mutableStateOf(true) }
    val scope      = rememberCoroutineScope()

    // AI резюме — загружается лениво при первом развёртывании
    var aiSummary  by remember { mutableStateOf<GeminiSummarizer.AiSummary?>(null) }
    var aiLoading  by remember { mutableStateOf(false) }
    var aiError    by remember { mutableStateOf(false) }

    LaunchedEffect(expanded, item.url) {
        if (expanded && aiSummary == null && !aiLoading && !aiError) {
            aiLoading = true
            aiSummary = GeminiSummarizer.summarize(item.title, item.summary, item.url)
            aiLoading = false
            if (aiSummary == null) aiError = true
        }
    }

    Box(Modifier.fillMaxSize()) {

        // Фон: фото с блюром + тёмный оверлей
        if (item.imageUrl != null) {
            AsyncImage(
                model = item.imageUrl,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize().blur(if (expanded) 0.dp else 8.dp)
            )
        } else {
            Box(
                Modifier.fillMaxSize().background(
                    Brush.radialGradient(listOf(accentCol.copy(0.3f), Color(0xFF0A0A0F)))
                )
            )
        }

        // Тёмный градиент
        Box(
            Modifier.fillMaxSize().background(
                Brush.verticalGradient(
                    0f   to Color.Black.copy(0.15f),
                    0.35f to Color.Black.copy(0.45f),
                    1f   to Color.Black.copy(0.93f)
                )
            )
        )

        if (expanded) {
            // ── Режим чтения ─────────────────────────────────────────────────
            Column(
                Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(start = 20.dp, end = 20.dp, top = 96.dp, bottom = 100.dp)
            ) {
                // Бейдж категории
                Box(
                    Modifier.clip(RoundedCornerShape(50))
                        .background(accentCol.copy(0.25f))
                        .padding(horizontal = 10.dp, vertical = 4.dp)
                ) {
                    Text(style.label, fontSize = 11.sp, color = accentCol, fontWeight = FontWeight.ExtraBold)
                }
                Spacer(Modifier.height(14.dp))

                // Заголовок
                Text(
                    item.title,
                    fontSize = 20.sp, fontWeight = FontWeight.ExtraBold,
                    color = Color.White, lineHeight = 28.sp
                )

                Spacer(Modifier.height(16.dp))

                // ── AI-резюме: ЧТО / ГДЕ / КОГДА ────────────────────────────
                when {
                    aiLoading -> {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.padding(vertical = 8.dp)
                        ) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(14.dp),
                                color = accentCol, strokeWidth = 2.dp
                            )
                            Text("AI анализирует...", fontSize = 13.sp,
                                color = Color.White.copy(0.5f))
                        }
                    }
                    aiSummary != null -> {
                        AiSummaryCard(summary = aiSummary!!, accent = accentCol)
                        Spacer(Modifier.height(14.dp))
                    }
                    // Нет ключа или ошибка — показываем обычный текст
                }

                // Оригинальный текст
                if (item.summary.isNotEmpty() && item.summary != item.title) {
                    Text(
                        item.summary,
                        fontSize = 15.sp, color = Color.White.copy(0.80f),
                        lineHeight = 23.sp
                    )
                    Spacer(Modifier.height(16.dp))
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
            }
        } else {
            // ── Компактный вид ────────────────────────────────────────────────
            Column(
                Modifier
                    .align(Alignment.BottomStart)
                    .padding(start = 20.dp, end = 72.dp, bottom = 88.dp)
                    .clickable { expanded = true }
            ) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        Modifier.clip(RoundedCornerShape(50))
                            .background(accentCol.copy(0.25f))
                            .padding(horizontal = 8.dp, vertical = 3.dp)
                    ) {
                        Text(style.label, fontSize = 10.sp, color = accentCol, fontWeight = FontWeight.ExtraBold)
                    }
                    Text(item.source.trimStart('@'), fontSize = 12.sp, color = Color.White.copy(0.7f))
                    Text("·", color = Color.White.copy(0.4f))
                    Text(timeAgo(item.publishedAt), fontSize = 12.sp, color = Color.White.copy(0.5f))
                }
                Spacer(Modifier.height(10.dp))
                Text(
                    item.title,
                    fontSize = 19.sp, fontWeight = FontWeight.ExtraBold,
                    color = Color.White, lineHeight = 26.sp,
                    maxLines = 4, overflow = TextOverflow.Ellipsis
                )
                Spacer(Modifier.height(8.dp))
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Box(Modifier.size(5.dp).clip(RoundedCornerShape(50)).background(accentCol.copy(0.6f)))
                    Text("Нажми чтобы читать", fontSize = 12.sp, color = Color.White.copy(0.45f))
                }
            }
        }

        // ── Кнопки действий справа ────────────────────────────────────────────
        Column(
            Modifier
                .align(Alignment.BottomEnd)
                .padding(end = 14.dp, bottom = 88.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            // Поделиться — ссылка ведёт на ticker247.web.app если нет приложения
            ActionButton(
                icon = Icons.Default.Share,
                label = "Поделиться"
            ) {
                val shareUrl = buildShareUrl(item)
                val shareText = buildString {
                    append("⚡ ${item.title}\n\n")
                    if (item.summary.isNotEmpty()) append("${item.summary.take(300)}\n\n")
                    append("🔗 $shareUrl\n")
                    append("📱 Ticker 24/7 — новости в реальном времени")
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
