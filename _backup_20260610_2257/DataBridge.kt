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

    fun setTickerAndNews(ticker: List<String>, news: List<NewsItem>) {
        android.util.Log.d("DataBridge", "setTickerAndNews: ${news.size} items")
        currentNewsList = ticker
        _newsItems.value = news
        android.util.Log.d("DataBridge", "ticker size=" + ticker.size + " first=" + ticker.firstOrNull())
        _ticker.value = ticker.filter { it.isNotEmpty() }.joinToString("     ·     ")
        android.util.Log.d("DataBridge", "ticker value len=" + _ticker.value.length)
        android.util.Log.d("DataBridge", "flow updated: ${_newsItems.value.size}")
    }

    fun setNewsItems(items: List<NewsItem>) {
        android.util.Log.d("DataBridge", "setNewsItems: ${items.size} items")
        _newsItems.value = items
    }

    var pendingArticleUrl: String by mutableStateOf("")
    var pendingTab: String by mutableStateOf("")
}
