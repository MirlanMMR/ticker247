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

    // Для совместимости со старым кодом (сервис читает newsItems напрямую)
    val newsItems: List<NewsItem> get() = _newsItems.value

    private val _ticker = MutableStateFlow("")
    val tickerFlow: StateFlow<String> = _ticker.asStateFlow()
    var tickerLine: String
        get() = _ticker.value
        set(v) { _ticker.value = v }

    // currentNewsList для обратной совместимости
    var currentNewsList: List<String> by mutableStateOf(emptyList())

    fun setTickerAndNews(ticker: List<String>, news: List<NewsItem>) {
        currentNewsList = ticker
        _newsItems.value = news
        _ticker.value = ticker.joinToString("  ·  ")
    }

    fun setNewsItems(items: List<NewsItem>) {
        _newsItems.value = items
    }
}
