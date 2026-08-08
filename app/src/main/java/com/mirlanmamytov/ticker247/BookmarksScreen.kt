package com.mirlanmamytov.ticker247

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.mirlanmamytov.ticker247.data.db.AppDatabase
import com.mirlanmamytov.ticker247.data.model.NewsItem
import kotlinx.coroutines.launch

/** Экран сохранённых новостей — читает из локальной Room-базы (bookmarks). */
@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
fun BookmarksScreen(
    onBack: () -> Unit,
    onOpenItem: (List<NewsItem>, Int) -> Unit
) {
    val context = LocalContext.current
    val dao = remember { AppDatabase.getInstance(context).bookmarkDao() }
    val bookmarks by dao.getAll().collectAsState(initial = emptyList())
    val scope = rememberCoroutineScope()

    Scaffold(
        containerColor = Color(0xFF2A2D34),
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.bookmarks_title), fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = stringResource(R.string.back))
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color(0xFF2A2D34), titleContentColor = Color(0xFFFFFFFF), navigationIconContentColor = Color(0xFFFFFFFF))
            )
        }
    ) { padding ->
        if (bookmarks.isEmpty()) {
            Column(
                Modifier.fillMaxSize().padding(padding),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Icon(Icons.Default.Bookmark, contentDescription = null,
                    tint = Color(0xFF00D4FF).copy(0.25f), modifier = Modifier.size(56.dp))
                Spacer(Modifier.height(12.dp))
                Text(stringResource(R.string.bookmarks_empty_title), fontSize = 15.sp, color = Color(0xFFB4B2A9))
                Spacer(Modifier.height(4.dp))
                Text(stringResource(R.string.bookmarks_empty_sub),
                    fontSize = 13.sp, color = Color(0xFF888780))
            }
            return@Scaffold
        }

        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            items(bookmarks, key = { it.url.ifEmpty { it.title } }) { item ->
                BookmarkCard(
                    item = item,
                    onClick = { onOpenItem(bookmarks, bookmarks.indexOf(item)) },
                    onRemove = { scope.launch { dao.delete(item) } }
                )
            }
        }
    }
}

@Composable
private fun BookmarkCard(item: NewsItem, onClick: () -> Unit, onRemove: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(Color(0xFF4A4E58))
            .clickable(onClick = onClick)
            .padding(10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (!item.imageUrl.isNullOrBlank()) {
            AsyncImage(
                model = item.imageUrl,
                contentDescription = null,
                modifier = Modifier.size(64.dp).clip(RoundedCornerShape(10.dp))
            )
            Spacer(Modifier.width(10.dp))
        }
        Column(Modifier.weight(1f)) {
            Text(
                item.title, fontSize = 14.sp, fontWeight = FontWeight.SemiBold,
                color = Color(0xFFFFFFFF), maxLines = 2, overflow = TextOverflow.Ellipsis
            )
            Spacer(Modifier.height(4.dp))
            Text(item.source.trimStart('@'), fontSize = 12.sp, color = Color(0xFFB4B2A9))
        }
        IconButton(onClick = onRemove) {
            Icon(Icons.Default.DeleteOutline, contentDescription = stringResource(R.string.reader_share_bookmark_remove), tint = Color(0xFF888780))
        }
    }
}
