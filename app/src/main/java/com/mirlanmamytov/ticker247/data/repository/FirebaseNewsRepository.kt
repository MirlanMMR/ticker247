package com.mirlanmamytov.ticker247.data.repository

import android.util.Log
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ValueEventListener
import com.mirlanmamytov.ticker247.DataBridge
import com.mirlanmamytov.ticker247.data.model.NewsItem
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

object FirebaseNewsRepository {

    private val database = FirebaseDatabase.getInstance("https://ticker247-default-rtdb.asia-southeast1.firebasedatabase.app")

    private fun newsLangPath(): String {
        val lang = java.util.Locale.getDefault().language
        val cyrillicLangs = setOf("ru", "ky", "uk", "be", "bg", "sr", "mk")
        return when {
            lang in cyrillicLangs -> "news/ru"
            lang == "es" -> "news/es"
            lang == "pt" -> "news/pt"
            lang == "en" -> "news/en"
            else -> "news/en"  // fallback — English
        }
    }

    suspend fun fetchNews(): List<NewsItem> = suspendCancellableCoroutine { cont ->
        database.getReference(newsLangPath()).addListenerForSingleValueEvent(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                try {
                    val items = mutableListOf<NewsItem>()
                    val itemsSnapshot = snapshot.child("items")
                    for (child in itemsSnapshot.children) {
                        val item = NewsItem(
                            url = child.child("url").getValue(String::class.java) ?: "",
                            title = child.child("title").getValue(String::class.java) ?: "",
                            summary = child.child("summary").getValue(String::class.java) ?: "",
                            imageUrl = child.child("imageUrl").getValue(String::class.java),
                            source = child.child("source").getValue(String::class.java) ?: "",
                            category = child.child("category").getValue(String::class.java) ?: "NEWS",
                            publishedAt = child.child("publishedAt").getValue(Long::class.java) ?: System.currentTimeMillis(),
                            priority = child.child("priority").getValue(Int::class.java) ?: 0,
                            language = child.child("language").getValue(String::class.java) ?: "ru",
                            scope = child.child("scope").getValue(String::class.java) ?: "world"
                        )
                        if (item.title.isNotEmpty()) items.add(item)
                    }
                    Log.d("Firebase", "Loaded ${items.size} news items")
                    cont.resume(items)
                } catch (e: Exception) {
                    Log.e("Firebase", "Parse error: ${e.message}")
                    cont.resume(emptyList())
                }
            }

            override fun onCancelled(error: DatabaseError) {
                Log.e("Firebase", "Database error: ${error.message}")
                cont.resumeWithException(error.toException())
            }
        })
    }

    suspend fun fetchViral(): List<NewsItem> = suspendCancellableCoroutine { cont ->
        database.getReference("viral").addListenerForSingleValueEvent(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                try {
                    val items = mutableListOf<NewsItem>()
                    // Определяем регионы по системному языку
                    val sysLang = java.util.Locale.getDefault().language
                    val sysCountry = java.util.Locale.getDefault().country
                    val regions = when {
                        sysLang == "ky" -> listOf("kg", "ru")
                        sysCountry == "KG" -> listOf("kg", "ru", "world")
                        sysCountry == "KZ" -> listOf("kz", "kg", "ru")
                        sysCountry == "RU" -> listOf("ru", "kg", "world")
                        sysLang == "ru"  -> listOf("ru", "kg", "world")
                        else -> listOf("world", "kg", "ru")
                    }
                    regions.forEach { region ->
                        val label = when(region) {
                            "kg" -> "🔥 ВИРАЛЬНО В КГ"
                            "ru" -> "🔥 ВИРАЛЬНО В РФ"
                            "kz" -> "🔥 ВИРАЛЬНО В КЗ"
                            else -> "🌍 ВИРАЛЬНО В МИРЕ"
                        }
                        for (child in snapshot.child(region).children) {
                            val url = child.child("url").getValue(String::class.java) ?: ""
                            val item = NewsItem(
                                url = url,
                                title = child.child("title").getValue(String::class.java) ?: "",
                                summary = child.child("summary").getValue(String::class.java) ?: "",
                                imageUrl = child.child("imageUrl").getValue(String::class.java),
                                source = label,
                                category = "VIRAL",
                                publishedAt = child.child("publishedAt").getValue(Long::class.java) ?: System.currentTimeMillis(),
                                priority = 1,
                                isVideo = url.contains("youtube.com") || url.contains("youtu.be")
                            )
                            if (item.title.isNotEmpty()) items.add(item)
                        }
                    }
                    cont.resume(items)
                } catch (e: Exception) {
                    cont.resume(emptyList())
                }
            }
            override fun onCancelled(error: DatabaseError) { cont.resume(emptyList()) }
        })
    }

    suspend fun fetchIndices(): List<String> = suspendCancellableCoroutine { cont ->
        database.getReference("indices/items").addListenerForSingleValueEvent(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                val items = snapshot.children.mapNotNull {
                    it.child("display").getValue(String::class.java)
                }
                cont.resume(items)
            }
            override fun onCancelled(error: DatabaseError) { cont.resume(emptyList()) }
        })
    }

    suspend fun fetchSpamPatterns(): List<String> = suspendCancellableCoroutine { cont ->
        database.getReference("config/spam_patterns").addListenerForSingleValueEvent(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                val patterns = snapshot.children.mapNotNull { it.getValue(String::class.java) }
                Log.d("Firebase", "Spam patterns loaded: ${patterns.size}")
                cont.resume(patterns)
            }
            override fun onCancelled(error: DatabaseError) {
                Log.w("Firebase", "Spam patterns failed, using defaults")
                cont.resume(emptyList())
            }
        })
    }

    // ── Редакторские Telegram-каналы ─────────────────────────────────────────
    // Имена читаются из /config/editorial_channels (бэкенд публикует при каждом
    // запуске). Фолбэк — зашитые имена, если Firebase недоступен.

    private val EDITORIAL_FALLBACK = mapOf(
        "ru" to "t247feed",
        "en" to "t247feed_en",
        "es" to "t247feed_es",
        "pt" to "t247feed_pt",
        "gl" to "t247_gl"   // глобальный — посты для всех регионов
    )

    /** Глобальный редакторский канал (кэш обновляется в fetchEditorialChannel) */
    fun globalEditorialChannel(): String =
        (editorialCache ?: EDITORIAL_FALLBACK)["gl"] ?: EDITORIAL_FALLBACK.getValue("gl")

    @Volatile private var editorialCache: Map<String, String>? = null

    suspend fun fetchEditorialChannel(): String {
        val channels = try {
            suspendCancellableCoroutine<Map<String, String>> { cont ->
                database.getReference("config/editorial_channels")
                    .addListenerForSingleValueEvent(object : ValueEventListener {
                        override fun onDataChange(snapshot: DataSnapshot) {
                            val map = snapshot.children.mapNotNull { child ->
                                val v = child.getValue(String::class.java)
                                if (child.key != null && !v.isNullOrBlank()) child.key!! to v else null
                            }.toMap()
                            cont.resume(map)
                        }
                        override fun onCancelled(error: DatabaseError) { cont.resume(emptyMap()) }
                    })
            }.also { if (it.isNotEmpty()) editorialCache = it }
        } catch (e: Exception) { emptyMap() }

        val effective = channels.ifEmpty { editorialCache ?: EDITORIAL_FALLBACK }
        val lang = java.util.Locale.getDefault().language
        val cyrillicLangs = setOf("ru", "ky", "kk", "uz", "tg", "be", "uk", "bg", "sr", "mk")
        val key = when {
            lang in cyrillicLangs -> "ru"
            lang == "es" -> "es"
            lang == "pt" -> "pt"
            else -> "en"
        }
        return effective[key] ?: EDITORIAL_FALLBACK.getValue(key)
    }

    // ── Выключатель «местные по стране» ──────────────────────────────────────
    // /config/country_locals: false отключает подмену местных удалённо.
    // Отсутствие узла = включено (фича активна по умолчанию).
    @Volatile private var countryLocalsFlag: Boolean = true

    fun countryLocalsEnabled(): Boolean = countryLocalsFlag

    suspend fun refreshCountryLocalsFlag() {
        try {
            suspendCancellableCoroutine<Unit> { cont ->
                database.getReference("config/country_locals")
                    .addListenerForSingleValueEvent(object : ValueEventListener {
                        override fun onDataChange(snapshot: DataSnapshot) {
                            countryLocalsFlag = snapshot.getValue(Boolean::class.java) ?: true
                            cont.resume(Unit)
                        }
                        override fun onCancelled(error: DatabaseError) { cont.resume(Unit) }
                    })
            }
        } catch (_: Exception) {}
    }

    fun updateDataBridge(items: List<NewsItem>) {
        val tickerItems = items
            .filter { it.category in setOf("CURRENCY", "CRYPTO", "URGENT", "TRENDS") }
            .take(10)
            .map { it.title }
        DataBridge.setTickerAndNews(tickerItems, items)
    }
}
