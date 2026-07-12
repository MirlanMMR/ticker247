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
    // Тип контента
    val isVideo: Boolean = false,
    // Крипта
    val cryptoName: String? = null,
    val cryptoSymbol: String? = null,
    val cryptoPrice: Double? = null,
    val cryptoChange24h: Double? = null,
    val cryptoIconUrl: String? = null,
    // Популярность — сколько источников написали об этой теме (кросс-источниковый рейтинг)
    val sourceCount: Int = 1,
    // Просмотры из Telegram (парсим из HTML канала)
    val telegramViews: Int = 0,
    // Совпадает с Google Trends KG
    val isTrending: Boolean = false,
    // Язык контента — для фильтрации по языку устройства
    val language: String = "ru",
    // Масштаб: "local" — локальные/региональные, "world" — мировые
    val scope: String = "world",
    // ── Редакторские маркеры (хэштеги в постах TG-канала) ────────────────
    // #срочно → category=URGENT; #важно → тикер ⚡; #карусель → hero-карусель
    val isEditorImportant: Boolean = false,
    val isEditorCarousel: Boolean = false,
    // #метка: <текст> — произвольный бейдж на карточке («ВИДЕО ДНЯ», «ИСТОРИЯ»...)
    val editorLabel: String? = null,
    // Время жизни поста (#3д / #12ч): null = стандартные 24 часа
    val expiresAt: Long? = null
)
