package com.mirlanmamytov.ticker247

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.mirlanmamytov.ticker247.data.model.NewsItem
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

object DataBridge {
    private val _newsItems = MutableStateFlow<List<NewsItem>>(emptyList())
    val newsItemsFlow: StateFlow<List<NewsItem>> = _newsItems.asStateFlow()
    val newsItems: List<NewsItem> get() = _newsItems.value

    private val _ticker = MutableStateFlow("")
    val tickerFlow: StateFlow<String> = _ticker.asStateFlow()
    var tickerLine: String
        get() = _ticker.value
        set(v) { _ticker.value = v }

    var currentNewsList: List<String> by mutableStateOf(emptyList())

    // ── Instagram-логика: трекаем просмотренные статьи ───────────────────────
    // url → время просмотра (для автоочистки через 24 часа)
    private val seenItems = LinkedHashMap<String, Long>(256)

    fun markSeen(url: String) {
        if (url.isBlank()) return
        seenItems[url] = System.currentTimeMillis()
        // Чистим старше 24 часов чтобы контент снова мог появиться
        val cutoff = System.currentTimeMillis() - 24 * 60 * 60 * 1000L
        seenItems.entries.removeAll { it.value < cutoff }
    }

    fun isSeen(url: String): Boolean = url.isNotBlank() && seenItems.containsKey(url)

    fun clearSeen() = seenItems.clear()
    // ─────────────────────────────────────────────────────────────────────────

    fun setTickerAndNews(ticker: List<String>, news: List<NewsItem>) {
        android.util.Log.d("DataBridge", "setTickerAndNews: ${news.size} items")
        currentNewsList = ticker
        _newsItems.value = news
        _ticker.value = ticker.filter { it.isNotEmpty() }.joinToString("     ·     ")
    }

    fun setNewsItems(items: List<NewsItem>) {
        _newsItems.value = items
    }

    var pendingArticleUrl: String by mutableStateOf("")
    var pendingArticleTitle: String = ""
    var pendingTab: String by mutableStateOf("")

    @Volatile var isAppVisible: Boolean = false
}
