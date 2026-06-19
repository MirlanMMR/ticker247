package com.mirlanmamytov.ticker247.reader

import android.content.Intent
import android.net.Uri
import androidx.compose.animation.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.OpenInBrowser
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReaderScreen(
    url: String,
    title: String,
    source: String,
    isDark: Boolean,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    val bgColor = if (isDark) Color(0xFF0A0A0F) else Color(0xFFFAF8F5)
    val textColor = if (isDark) Color(0xFFEAEAEA) else Color(0xFF1A1A1A)
    val subColor = if (isDark) Color(0xFF6B7280) else Color(0xFF9CA3AF)
    val accentColor = Color(0xFF00D4FF)

    var article by remember { mutableStateOf<Article?>(null) }
    var isLoading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf(false) }

    LaunchedEffect(url) {
        scope.launch {
            isLoading = true
            error = false
            article = ArticleParser.parse(url, title, source)
            isLoading = false
            if (article == null) error = true
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = source,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Medium,
                        color = subColor
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Назад", tint = textColor)
                    }
                },
                actions = {
                    IconButton(onClick = {
                        context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
                    }) {
                        Icon(Icons.Default.OpenInBrowser, contentDescription = "Открыть в браузере", tint = subColor)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = bgColor)
            )
        },
        containerColor = bgColor
    ) { padding ->

        Box(modifier = Modifier.fillMaxSize().padding(padding)) {

            when {
                isLoading -> {
                    Column(
                        modifier = Modifier.fillMaxSize(),
                        verticalArrangement = Arrangement.Center,
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        CircularProgressIndicator(color = accentColor)
                        Spacer(Modifier.height(16.dp))
                        Text("Загружаем статью...", color = subColor, fontSize = 14.sp)
                    }
                }

                error -> {
                    // Автоматически открываем браузер если ридер не работает
                    LaunchedEffect(Unit) {
                        context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
                        onBack()
                    }
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(color = accentColor)
                    }
                }

                article != null -> {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .verticalScroll(rememberScrollState())
                            .padding(horizontal = 20.dp)
                    ) {
                        Spacer(Modifier.height(8.dp))

                        // Заголовок
                        Text(
                            text = article!!.title,
                            fontSize = 24.sp,
                            fontWeight = FontWeight.Bold,
                            color = textColor,
                            lineHeight = 32.sp
                        )

                        Spacer(Modifier.height(12.dp))

                        // Источник
                        Text(
                            text = article!!.source,
                            fontSize = 13.sp,
                            color = accentColor,
                            fontWeight = FontWeight.SemiBold
                        )

                        Spacer(Modifier.height(16.dp))

                        // Фото
                        if (!article!!.imageUrl.isNullOrEmpty()) {
                            AsyncImage(
                                model = article!!.imageUrl,
                                contentDescription = null,
                                contentScale = ContentScale.Crop,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(220.dp)
                                    .clip(RoundedCornerShape(16.dp))
                            )
                            Spacer(Modifier.height(20.dp))
                        }

                        HorizontalDivider(color = textColor.copy(alpha = 0.1f))
                        Spacer(Modifier.height(20.dp))

                        // Текст статьи
                        Text(
                            text = article!!.text,
                            fontSize = 17.sp,
                            color = textColor,
                            lineHeight = 28.sp,
                            fontFamily = FontFamily.Serif
                        )

                        Spacer(Modifier.height(40.dp))

                        // Кнопка открыть оригинал
                        OutlinedButton(
                            onClick = { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url))) },
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Text("Читать оригинал на сайте", color = subColor)
                        }

                        Spacer(Modifier.height(32.dp))
                    }
                }
            }
        }
    }
}
