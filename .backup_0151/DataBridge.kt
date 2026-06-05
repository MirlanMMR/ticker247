package com.mirlanmamytov.ticker247

import androidx.compose.runtime.mutableStateListOf
import com.mirlanmamytov.ticker247.data.model.NewsItem

object DataBridge {
    // Простые строки для уведомления и бегущей строки
    val currentNewsList = mutableStateListOf<String>()

    // Структурированные новости для ленты с фото
    val newsItems = mutableStateListOf<NewsItem>()

    // Тикер-строка — одна длинная строка для бегущей строки
    var tickerLine: String = ""
}
