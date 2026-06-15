package com.mirlanmamytov.ticker247.reader

import android.content.Intent
import android.net.Uri
import androidx.compose.animation.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.outlined.BookmarkBorder
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.mirlanmamytov.ticker247.data.model.NewsItem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ArticleScreen(
    item: NewsItem,
    isDark: Boolean,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val bgColor      = if (isDark) Color(0xFF0A0A0F) else Color(0xFFFAFAFA)
    val surfaceColor = if (isDark) Color(0xFF13131A) else Color(0xFFFFFFFF)
    val textColor    = if (isDark) Color(0xFFEAEAEA) else Color(0xFF1A1A2E)
    val subColor     = if (isDark) Color(0xFF6B7280) else Color(0xFF9CA3AF)
    val accentColor  = Color(0xFF00D4FF)

    // Состояние загрузки
    var content by remember { mutableStateOf<ArticleContent?>(null) }
    var isLoading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }

    // Загрузка контента
    LaunchedEffect(item.url) {
        isLoading = true
        error = null
        try {
            val isTelegram = item.source.startsWith("@") || item.url.contains("t.me/")
            if (isTelegram || item.summary.length > 200) {
                // Для Телеграма и длинных постов — показываем сразу
                content = ArticleExtractor.fromNewsItem(
                    title = item.title,
                    summary = item.summary.ifEmpty { item.title },
                    imageUrl = item.imageUrl,
                    source = item.source,
                    publishedAt = item.publishedAt
                )
                isLoading = false
                // Параллельно пробуем получить полный текст если есть URL
                if (item.url.isNotEmpty() && !isTelegram) {
                    val full = withContext(Dispatchers.IO) {
                        ArticleExtractor.extract(item.url).getOrNull()
                    }
                    if (full != null && full.body.length > content!!.body.length) {
                        content = full
                    }
                }
            } else if (item.url.isNotEmpty()) {
                // Для веб-статей — парсим
                val result = withContext(Dispatchers.IO) {
                    ArticleExtractor.extract(item.url)
                }
                content = result.getOrNull() ?: ArticleExtractor.fromNewsItem(
                    item.title, item.summary, item.imageUrl, item.source, item.publishedAt
                )
                isLoading = false
            } else {
                content = ArticleExtractor.fromNewsItem(
                    item.title, item.summary, item.imageUrl, item.source, item.publishedAt
                )
                isLoading = false
            }
        } catch (e: Exception) {
            content = ArticleExtractor.fromNewsItem(
                item.title, item.summary, item.imageUrl, item.source, item.publishedAt
            )
            isLoading = false
        }
    }

    Box(Modifier.fillMaxSize().background(bgColor)) {
        LazyColumn(Modifier.fillMaxSize()) {

            // Обложка с градиентом
            item {
                Box(Modifier.fillMaxWidth().height(280.dp)) {
                    if (item.imageUrl != null) {
                        AsyncImage(
                            model = item.imageUrl,
                            contentDescription = null,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxSize()
                        )
                        // Градиент снизу для читаемости текста
                        Box(
                            Modifier.fillMaxSize().background(
                                Brush.verticalGradient(
                                    0f to Color.Transparent,
                                    0.6f to bgColor.copy(0.5f),
                                    1f to bgColor
                                )
                            )
                        )
                    } else {
                        Box(
                            Modifier.fillMaxSize().background(
                                Brush.radialGradient(
                                    listOf(accentColor.copy(0.15f), bgColor)
                                )
                            )
                        )
                    }

                    // Кнопка назад
                    IconButton(
                        onClick = onBack,
                        modifier = Modifier.padding(top = 40.dp, start = 8.dp).align(Alignment.TopStart)
                    ) {
                        Box(
                            Modifier.size(36.dp).clip(RoundedCornerShape(50))
                                .background(bgColor.copy(0.7f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(Icons.Default.ArrowBack, null, tint = textColor, modifier = Modifier.size(20.dp))
                        }
                    }
                }
            }

            // Метаданные
            item {
                Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp)) {

                    // Категория + время чтения
                    Row(
                        Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            Modifier.clip(RoundedCornerShape(6.dp))
                                .background(accentColor.copy(0.15f))
                                .padding(horizontal = 10.dp, vertical = 4.dp)
                        ) {
                            Text(
                                item.category, fontSize = 11.sp,
                                color = accentColor, fontWeight = FontWeight.Bold
                            )
                        }
                        content?.let {
                            Text(
                                "⏱ ${it.readTimeMin} мин", fontSize = 12.sp, color = subColor
                            )
                        }
                    }

                    Spacer(Modifier.height(12.dp))

                    // Заголовок
                    Text(
                        content?.title ?: item.title,
                        fontSize = 22.sp, fontWeight = FontWeight.Bold,
                        color = textColor, lineHeight = 30.sp
                    )

                    Spacer(Modifier.height(12.dp))

                    // Источник и дата
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(item.source, fontSize = 13.sp, color = accentColor, fontWeight = FontWeight.SemiBold)
                        Text("·", fontSize = 13.sp, color = subColor)
                        Text(
                            content?.publishedDate ?: timeAgo(item.publishedAt),
                            fontSize = 13.sp, color = subColor
                        )
                    }

                    Spacer(Modifier.height(16.dp))
                    HorizontalDivider(color = textColor.copy(0.08f))
                    Spacer(Modifier.height(16.dp))
                }
            }

            // Контент
            if (isLoading) {
                item {
                    Box(Modifier.fillMaxWidth().height(200.dp), contentAlignment = Alignment.Center) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            CircularProgressIndicator(color = accentColor, modifier = Modifier.size(32.dp))
                            Spacer(Modifier.height(12.dp))
                            Text("Загружаем статью...", color = subColor, fontSize = 14.sp)
                        }
                    }
                }
            } else {
                item {
                    Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp)) {
                        val paragraphs = (content?.body ?: item.summary)
                            .split("\n\n", "\n").filter { it.trim().length > 5 }

                        paragraphs.forEach { para ->
                            val trimmed = para.trim()
                            when {
                                trimmed.startsWith("#") -> {
                                    Text(
                                        trimmed.trimStart('#').trim(),
                                        fontSize = 18.sp, fontWeight = FontWeight.Bold,
                                        color = textColor, modifier = Modifier.padding(bottom = 8.dp, top = 16.dp)
                                    )
                                }
                                trimmed.startsWith(">") -> {
                                    Box(
                                        Modifier.fillMaxWidth()
                                            .padding(vertical = 8.dp)
                                            .clip(RoundedCornerShape(4.dp))
                                            .background(accentColor.copy(0.08f))
                                            .padding(start = 12.dp, top = 8.dp, bottom = 8.dp, end = 8.dp)
                                    ) {
                                        Text(
                                            trimmed.trimStart('>').trim(),
                                            fontSize = 15.sp, color = textColor.copy(0.85f),
                                            fontStyle = FontStyle.Italic
                                        )
                                    }
                                }
                                else -> {
                                    Text(
                                        trimmed,
                                        fontSize = 16.sp, color = textColor.copy(0.9f),
                                        lineHeight = 26.sp,
                                        modifier = Modifier.padding(bottom = 14.dp)
                                    )
                                }
                            }
                        }

                        Spacer(Modifier.height(24.dp))
                    }
                }
            }

            // Кнопки действий
            item {
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    // Поделиться — полный текст статьи
                    OutlinedButton(
                        onClick = {
                            val body = content?.body?.take(800) ?: item.summary.take(800)
                            val suffix = if ((content?.body?.length ?: 0) > 800) "..." else ""
                            val shareText = buildString {
                                append("${item.title}\n\n")
                                if (body.isNotEmpty() && body != item.title) append("$body$suffix\n\n")
                                if (item.url.startsWith("http")) append("🔗 ${item.url}\n\n")
                                append("📲 Подписывайся: https://t.me/t247feed")
                            }
                            val intent = Intent(Intent.ACTION_SEND).apply {
                                type = "text/plain"
                                putExtra(Intent.EXTRA_TEXT, shareText)
                                putExtra(Intent.EXTRA_SUBJECT, item.title)
                            }
                            context.startActivity(Intent.createChooser(intent, "Поделиться"))
                        },
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(12.dp),
                        border = BorderStroke(1.dp, accentColor.copy(0.4f))
                    ) {
                        Icon(Icons.Default.Share, null, tint = accentColor, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Поделиться", color = accentColor, fontSize = 14.sp)
                    }

                    // Открыть в браузере (опционально)
                    if (item.url.isNotEmpty() && !item.url.contains("t.me/")) {
                        OutlinedButton(
                            onClick = {
                                context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(item.url)))
                            },
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(12.dp),
                            border = BorderStroke(1.dp, textColor.copy(0.2f))
                        ) {
                            Text("В браузере", color = subColor, fontSize = 14.sp)
                        }
                    }
                }
                Spacer(Modifier.height(40.dp))
            }
        }
    }
}

private fun timeAgo(ms: Long): String {
    val min = (System.currentTimeMillis() - ms) / 60_000
    return when {
        min < 1   -> "только что"
        min < 60  -> "$min мин назад"
        min < 1440 -> "${min / 60} ч назад"
        else -> "${min / 1440} д назад"
    }
}
