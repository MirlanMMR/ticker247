package com.mirlanmamytov.ticker247.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "bookmarks")
data class NewsItem(
    @PrimaryKey val url: String,
    val title: String,
    val summary: String,
    val imageUrl: String?,
    val source: String,
    val category: String,
    val publishedAt: Long = System.currentTimeMillis(),
    val priority: Int = 0,
    // Крипта
    val cryptoName: String? = null,
    val cryptoSymbol: String? = null,
    val cryptoPrice: Double? = null,
    val cryptoChange24h: Double? = null,
    val cryptoIconUrl: String? = null
)
