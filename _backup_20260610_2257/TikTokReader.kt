package com.mirlanmamytov.ticker247

import androidx.compose.animation.core.*
import androidx.compose.foundation.background
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
import android.content.Intent
import androidx.compose.foundation.clickable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll

/**
 * TikTok-стиль: вертикальный pager на весь экран.
 * Свайп вверх = следующая новость, вниз = предыдущая.
 */
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

        // Индикатор страницы справа
        Column(
            modifier = Modifier
                .align(Alignment.CenterEnd)
                .padding(end = 8.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            val total = minOf(items.size, 20) // показываем max 20 точек
            val current = pagerState.currentPage
            repeat(total) { i ->
                Box(
                    Modifier.size(if (i == current % total) 8.dp else 5.dp)
                        .clip(RoundedCornerShape(50))
                        .background(
                            if (i == current % total) Color.White else Color.White.copy(0.35f)
                        )
                )
            }
        }
    }
}

@Composable
private fun TikTokPage(item: NewsItem, onBack: () -> Unit) {
    val context   = LocalContext.current
    val style     = newsItemStyle(item.category)
    val accentCol = style.accent
    var expanded  by remember { mutableStateOf(false) }

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
                    Brush.radialGradient(
                        listOf(accentCol.copy(0.3f), Color(0xFF0A0A0F))
                    )
                )
            )
        }

        // Тёмный градиент снизу
        Box(
            Modifier.fillMaxSize().background(
                Brush.verticalGradient(
                    0f to Color.Black.copy(0.2f),
                    0.4f to Color.Black.copy(0.5f),
                    1f to Color.Black.copy(0.92f)
                )
            )
        )

        // Контент
        if (expanded) {
            // Развёрнутый текст (режим чтения)
            Column(
                Modifier.fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(start = 20.dp, end = 20.dp, top = 100.dp, bottom = 80.dp)
            ) {
                // Бейдж
                Box(
                    Modifier.clip(RoundedCornerShape(6.dp))
                        .background(accentCol.copy(0.2f))
                        .padding(horizontal = 10.dp, vertical = 4.dp)
                ) {
                    Text(style.label, fontSize = 11.sp, color = accentCol, fontWeight = FontWeight.Bold)
                }
                Spacer(Modifier.height(12.dp))
                Text(
                    item.title,
                    fontSize = 20.sp, fontWeight = FontWeight.Bold,
                    color = Color.White, lineHeight = 28.sp
                )
                if (item.summary.isNotEmpty() && item.summary != item.title) {
                    Spacer(Modifier.height(12.dp))
                    Text(
                        item.summary,
                        fontSize = 16.sp, color = Color.White.copy(0.85f),
                        lineHeight = 24.sp
                    )
                }
                Spacer(Modifier.height(16.dp))
                Text(item.source, fontSize = 13.sp, color = accentCol, fontWeight = FontWeight.Medium)
            }
        } else {
            // Компактный вид — свайп вверх для чтения
            Column(
                Modifier.align(Alignment.BottomStart)
                    .padding(start = 20.dp, end = 64.dp, bottom = 80.dp)
                    .clickable { expanded = true }
            ) {
                // Бейдж + источник
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        Modifier.clip(RoundedCornerShape(6.dp))
                            .background(accentCol.copy(0.2f))
                            .padding(horizontal = 8.dp, vertical = 3.dp)
                    ) {
                        Text(style.label, fontSize = 10.sp, color = accentCol, fontWeight = FontWeight.Bold)
                    }
                    Text(item.source, fontSize = 12.sp, color = Color.White.copy(0.7f))
                    Text("·", color = Color.White.copy(0.5f))
                    Text(timeAgo(item.publishedAt), fontSize = 12.sp, color = Color.White.copy(0.7f))
                }
                Spacer(Modifier.height(10.dp))
                Text(
                    item.title,
                    fontSize = 19.sp, fontWeight = FontWeight.Bold,
                    color = Color.White, lineHeight = 26.sp,
                    maxLines = 4, overflow = TextOverflow.Ellipsis
                )
                Spacer(Modifier.height(8.dp))
                // Подсказка
                Text("Нажми чтобы читать дальше ↓", fontSize = 12.sp, color = Color.White.copy(0.45f))
            }
        }

        // Кнопки действий справа
        Column(
            Modifier.align(Alignment.BottomEnd)
                .padding(end = 14.dp, bottom = 80.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Поделиться
            IconButton(onClick = {
                val body = item.summary.take(800)
                val shareText = "⚡ ${item.title}\n\n${body}\n\n${item.source}\n📱 via Ticker 24/7"
                context.startActivity(
                    Intent.createChooser(
                        Intent(Intent.ACTION_SEND).apply {
                            type = "text/plain"
                            putExtra(Intent.EXTRA_TEXT, shareText)
                        }, "Поделиться"
                    )
                )
            }) {
                Box(
                    Modifier.size(44.dp).clip(RoundedCornerShape(50))
                        .background(Color.White.copy(0.15f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Default.Share, null, tint = Color.White, modifier = Modifier.size(22.dp))
                }
            }
        }
    }
}
